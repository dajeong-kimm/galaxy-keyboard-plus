/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.setup;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.dslul.openboard.inputmethod.backup.FullBackupWorker;
import org.dslul.openboard.inputmethod.backup.IncrementalBackupWorker;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.login.KakaoLoginActivity;
import org.dslul.openboard.inputmethod.latin.search.SearchActivity;
import org.dslul.openboard.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import org.dslul.openboard.inputmethod.latin.utils.UncachedInputMethodManagerUtils;

import java.time.Duration;
import java.util.ArrayList;

import javax.annotation.Nonnull;

// TODO: Use Fragment to implement welcome screen and setup steps.
public final class SetupWizardActivity extends Activity {

    // ────────── 퍼미션 관련 상수 ──────────
    private static final int REQ_MEDIA_PERMS = 0x31;
    private static final int REQ_NOTIF_PERMS = 0x32;

    /**
     * 버전에 맞게 요청할 미디어 읽기 권한 목록 반환
     */
    private static String[] getMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {          // Android 13
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {                                           // 23 ≤ API < 33
            return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
    }

    /**
     * 이미 허가돼 있나 확인하고, 없으면 런타임 요청
     */
    private void ensureMediaPermission() {
        String[] perms = getMediaPermissions();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQ_MEDIA_PERMS);
                return;   // 요청했으니 결과 콜백 기다림
            }
        }
        //이미 권한이 있을 경우 다음 단계로
        mStepNumber = STEP_6;
        updateSetupStepView();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            String[] perms = new String[]{Manifest.permission.POST_NOTIFICATIONS};
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQ_NOTIF_PERMS);
                return;
            }
        }
        //이미 권한이 있을 경우 다음 단계로
        mStepNumber = STEP_7;
        updateSetupStepView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_MEDIA_PERMS) {
            boolean granted = false;
            for (int g : grantResults) {
                if (g == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted) {
                // 사진 권한 허용 → 6단계(알림 권한) 화면으로 전환
                mStepNumber = STEP_6;
                updateSetupStepView();
            } else {
                Toast.makeText(this,
                        "사진 권한이 없으면 백업 기능을 사용할 수 없습니다.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQ_NOTIF_PERMS) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                // 알림 권한 허용 → 7단계(백업)로 이동
                mStepNumber = STEP_7;
                updateSetupStepView();
            } else {
                Toast.makeText(this,
                        "알림 권한이 없으면 진행 상황을 볼 수 없습니다.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    static final String TAG = SetupWizardActivity.class.getSimpleName();

    // 디버깅용
    private static final boolean FORCE_TO_SHOW_WELCOME_SCREEN = false;
    private static final boolean ENABLE_WELCOME_VIDEO = true;

    private InputMethodManager mImm;

    private View mSetupWizard;
    private View mWelcomeScreen;
    private View mSetupScreen;
    private Uri mWelcomeVideoUri;
    private VideoView mWelcomeVideoView;
    private ImageView mWelcomeImageView;
    private SetupStepGroup mSetupStepGroup;
    private TextView mTvInstructionTop;
    private Button mBtnAux;    // 좌측(보조) : step3(언어설정) 등
    private Button mBtnNext;   // 우측(메인) : 다음 / 권한요청 등
    private static final String STATE_STEP = "step";
    private int mStepNumber;
    private boolean mNeedsToAdjustStepNumberToSystemState;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_1 = 1;
    private static final int STEP_2 = 2;
    private static final int STEP_3 = 3;
    private static final int STEP_4 = 4; // 카카오 로그인
    private static final int STEP_5 = 5; // 사진 권한
    private static final int STEP_6 = 6; // 알림 권한
    private static final int STEP_7 = 7; // 백업 실행
    private static final long STEP_7_DELAY_MS = 5_000L;      // 5초 딜레이

    private static final int STEP_LAUNCHING_IME_SETTINGS = 8;
    private static final int STEP_BACK_FROM_IME_SETTINGS = 9;

    private static final int REQ_KAKAO_LOGIN = 100;

    private boolean mStartedBackup = false;
    private View mContentPane;
    private StepGaugeView mGauge;

    private SettingsPoolingHandler mHandler;

    private static final class SettingsPoolingHandler
            extends LeakGuardHandlerWrapper<SetupWizardActivity> {
        private static final int MSG_POLLING_IME_SETTINGS = 0;
        private static final long IME_SETTINGS_POLLING_INTERVAL = 200;

        private final InputMethodManager mImmInHandler;

        public SettingsPoolingHandler(@Nonnull final SetupWizardActivity ownerInstance,
                                      final InputMethodManager imm) {
            super(ownerInstance);
            mImmInHandler = imm;
        }

        @Override
        public void handleMessage(final Message msg) {
            final SetupWizardActivity setupWizardActivity = getOwnerInstance();
            if (setupWizardActivity == null) {
                return;
            }
            switch (msg.what) {
                case MSG_POLLING_IME_SETTINGS:
                    if (UncachedInputMethodManagerUtils.isThisImeEnabled(setupWizardActivity,
                            mImmInHandler)) {
                        setupWizardActivity.invokeSetupWizardOfThisIme();
                        return;
                    }
                    startPollingImeSettings();
                    break;
            }
        }

        public void startPollingImeSettings() {
            sendMessageDelayed(obtainMessage(MSG_POLLING_IME_SETTINGS),
                    IME_SETTINGS_POLLING_INTERVAL);
        }

        public void cancelPollingImeSettings() {
            removeMessages(MSG_POLLING_IME_SETTINGS);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(savedInstanceState);

        mImm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        mHandler = new SettingsPoolingHandler(this, mImm);

        setContentView(R.layout.setup_wizard);

        // Welcome 전용 시작 버튼 바인딩
        Button btnStart = findViewById(R.id.btn_start_welcome);
        btnStart.setOnClickListener(v -> {
            mStepNumber = STEP_1;          // 입력 방법 전환 단계로
            updateSetupStepView();
        });

        // 1) 게이지바 먼저 꺼내기

        mGauge = findViewById(R.id.setup_step_gauge);
        mContentPane = findViewById(R.id.step_content_pane);

        // 2) 게이지바를 넘겨 SetupStepGroup 생성
        mSetupStepGroup = new SetupStepGroup(mGauge);

        mSetupWizard = findViewById(R.id.setup_wizard);

        if (savedInstanceState == null) {
            mStepNumber = determineSetupStepNumberFromLauncher();
        } else {
            mStepNumber = savedInstanceState.getInt(STATE_STEP);
        }

        final String applicationName = getResources().getString(getApplicationInfo().labelRes);
        mWelcomeScreen = findViewById(R.id.setup_welcome_screen);
        final TextView welcomeTitle = findViewById(R.id.setup_welcome_title);
        if (welcomeTitle != null) {
            welcomeTitle.setText(getString(R.string.setup_welcome_title, applicationName));
        }

        mSetupScreen = findViewById(R.id.setup_steps_screen);
        final TextView stepsTitle = findViewById(R.id.setup_title);
        if (stepsTitle != null) {
            stepsTitle.setText(getString(R.string.setup_steps_title, applicationName));
        }

        // 3) 그다음부터 addStep() 호출
        final SetupStep step1 = new SetupStep(
                STEP_1, applicationName,
                findViewById(R.id.setup_step1),
                R.string.setup_step1_instruction,
                R.string.setup_step1_finished_instruction,
                R.string.setup_step1_action);
        step1.setAction(this::invokeLanguageAndInputSettings);
        mSetupStepGroup.addStep(step1);

        final SetupStep step2 = new SetupStep(
                STEP_2, applicationName,
                findViewById(R.id.setup_step2),
                R.string.setup_step2_instruction,
                0,
                R.string.setup_step2_action);
        step2.setAction(this::invokeInputMethodPicker);
        mSetupStepGroup.addStep(step2);

        final SetupStep step3 = new SetupStep(
                STEP_3, applicationName,
                findViewById(R.id.setup_step3),
                R.string.setup_step3_instruction,
                0,
                R.string.setup_step3_action);
        step3.setAction(this::invokeSubtypeEnablerOfThisIme);
        mSetupStepGroup.addStep(step3);

        mWelcomeVideoUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(getPackageName())
                .path(Integer.toString(R.raw.setup_welcome_video))
                .build();
        final VideoView welcomeVideoView = findViewById(R.id.setup_welcome_video);
        welcomeVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(final MediaPlayer mp) {
                // 이제 VideoView의 레이아웃이 완료되어 재생할 준비가 되었으므로,
                // 배경을 제거하여 동영상이 보이도록 한다.
                welcomeVideoView.setBackgroundResource(0);
                mp.setLooping(true);
            }
        });
        welcomeVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(final MediaPlayer mp, final int what, final int extra) {
                Log.e(TAG, "Playing welcome video causes error: what=" + what + " extra=" + extra);
                hideWelcomeVideoAndShowWelcomeImage();
                return true;
            }
        });
        mWelcomeVideoView = welcomeVideoView;
        mWelcomeImageView = findViewById(R.id.setup_welcome_image);

        // CTA 버튼 바인딩
        mBtnAux = findViewById(R.id.btn_aux);
        mBtnNext = findViewById(R.id.btn_next);

        // 메인(오른쪽) 버튼은 공통 처리 → onClick 안에서 step++ 하도록
        mBtnNext.setOnClickListener(v -> {
            mStepNumber++;
            updateSetupStepView();
        });

        // ────────── 추가: STEP_4 - 카카오 로그인 ──────────
        final SetupStep step4 = new SetupStep(
                STEP_4, applicationName,
                findViewById(R.id.setup_step4),
                R.string.setup_step4_instruction,     // “버튼을 눌러 카카오 로그인 페이지로 이동합니다.”
                0,                                     // 완료(instruction) 없음
                R.string.setup_step4_action            // “카카오 로그인”
        );
        step4.setAction(() -> {
            // 카카오 로그인 화면으로 이동
            Intent intent = new Intent(SetupWizardActivity.this, KakaoLoginActivity.class);
            startActivityForResult(intent, REQ_KAKAO_LOGIN);
        });
        mSetupStepGroup.addStep(step4);

        // ────────── 추가: STEP_5 - 사진 접근 권한 요청 ──────────
        final SetupStep step5 = new SetupStep(
                STEP_5, applicationName,
                findViewById(R.id.setup_step5),
                R.string.setup_step5_instruction,     // “버튼을 눌러 사진 권한을 요청합니다.”
                0,
                R.string.setup_step5_action           // “사진 권한 요청”
        );
        // 기존 ensureMediaPermission() 호출
        step5.setAction(this::ensureMediaPermission);
        mSetupStepGroup.addStep(step5);

        // ────────── 추가: STEP_6 - 알림 권한 요청 ──────────
        final SetupStep step6 = new SetupStep(
                STEP_6, applicationName,
                findViewById(R.id.setup_step6),
                R.string.setup_step6_instruction,     // “버튼을 눌러 알림 권한을 요청합니다.”
                0,
                R.string.setup_step6_action           // “알림 권한 요청”
        );
        step6.setAction(this::ensureNotificationPermission);
        mSetupStepGroup.addStep(step6);

        // ────────── 추가: STEP_7 - Backup WorkManager 실행 ──────────
        final SetupStep step7 = new SetupStep(
                STEP_7, applicationName,
                findViewById(R.id.setup_step7),
                R.string.setup_step7_instruction,     // “5초 뒤 메인 페이지로 이동합니다.”
                0,
                R.string.setup_step7_action           // “백업 실행”
        );
        mSetupStepGroup.addStep(step7);
    }

    private void ensureInstructionTopBound() {
        if (mTvInstructionTop == null) {
            mTvInstructionTop = findViewById(R.id.tv_step_instruction_top);
        }
    }

    // onActivityResult 추가
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_KAKAO_LOGIN) {
            if (resultCode == RESULT_OK) {
                // 로그인 성공 → 5단계(사진 권한)로 이동
                mStepNumber = STEP_5;
                updateSetupStepView();
            } else {
                // 로그인 실패나 취소 → 필요하면 토스트
                Toast.makeText(this, "로그인이 취소되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void invokeSetupWizardOfThisIme() {
        final Intent intent = new Intent();
        intent.setClass(this, SetupWizardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    private void invokeSettingsOfThisIme() {
        final Intent intent = new Intent();
//        intent.setClass(this, SettingsActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
//                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY,
//                SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON);
//        startActivity(intent);
        intent.setClass(this, SearchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY,
//                SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON);
        startActivity(intent);
    }

    void invokeLanguageAndInputSettings() {
        final Intent intent = new Intent();
        intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivity(intent);
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    void invokeInputMethodPicker() {
        // Invoke input method picker.
        mImm.showInputMethodPicker();
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    void invokeSubtypeEnablerOfThisIme() {
        final InputMethodInfo imi =
                UncachedInputMethodManagerUtils.getInputMethodInfoOf(getPackageName(), mImm);
        if (imi == null) {
            return;
        }
        final Intent intent = new Intent();
        intent.setAction(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, imi.getId());
        startActivity(intent);
    }

    private int determineSetupStepNumberFromLauncher() {
        final int stepNumber = determineSetupStepNumber();
        if (stepNumber == STEP_1) {
            return STEP_WELCOME;
        }
        if (stepNumber == STEP_3) {
            return STEP_LAUNCHING_IME_SETTINGS;
        }
        return stepNumber;
    }

    private int determineSetupStepNumber() {
        mHandler.cancelPollingImeSettings();
        if (FORCE_TO_SHOW_WELCOME_SCREEN) {
            return STEP_1;
        }
        if (!UncachedInputMethodManagerUtils.isThisImeEnabled(this, mImm)) {
            return STEP_1;
        }
        if (!UncachedInputMethodManagerUtils.isThisImeCurrent(this, mImm)) {
            return STEP_2;
        }
        return STEP_3;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, mStepNumber);
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mStepNumber = savedInstanceState.getInt(STATE_STEP);
    }

    private static boolean isInSetupSteps(final int stepNumber) {
        return stepNumber >= STEP_1 && stepNumber <= STEP_3;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // 아마도 설정 마법사가 "최근 항목" 메뉴에서 실행되었을 가능성이 있다.
        // IME(입력기)가 활성화되었거나 현재 입력기로 설정되었는지에 따라 시스템 상태가 바뀌었을 수 있으므로,
        // 설정 단계 번호를 시스템 상태에 맞게 조정해야 한다.
        if (isInSetupSteps(mStepNumber)) {
            mStepNumber = determineSetupStepNumber();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mStepNumber == STEP_LAUNCHING_IME_SETTINGS) {
            // Setting Activity를 실행할 때 흰색 화면이 깜빡이는 현상을 방지한다.
            mSetupWizard.setVisibility(View.INVISIBLE);
            invokeSettingsOfThisIme();
            mStepNumber = STEP_BACK_FROM_IME_SETTINGS;
            return;
        }
        if (mStepNumber == STEP_BACK_FROM_IME_SETTINGS) {
            finish();
            return;
        }

//        if (mStepNumber == STEP_2) {   // Step 2 화면에서만 퍼미션 체크
//            Log.d("STEP_2", "STEP_2 진입 확인");
//            ensureMediaPermission();
//        }

        updateSetupStepView();
    }

    @Override
    public void onBackPressed() {
        if (mStepNumber == STEP_1) {
            mStepNumber = STEP_WELCOME;
            updateSetupStepView();
            return;
        }
        super.onBackPressed();
    }

    void hideWelcomeVideoAndShowWelcomeImage() {
        mWelcomeVideoView.setVisibility(View.GONE);
        mWelcomeImageView.setImageResource(R.raw.setup_welcome_image);
        mWelcomeImageView.setVisibility(View.VISIBLE);
    }

    private void showAndStartWelcomeVideo() {
        mWelcomeVideoView.setVisibility(View.VISIBLE);
        mWelcomeVideoView.setVideoURI(mWelcomeVideoUri);
        mWelcomeVideoView.start();
    }

    private void hideAndStopWelcomeVideo() {
        mWelcomeVideoView.stopPlayback();
        mWelcomeVideoView.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        hideAndStopWelcomeVideo();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mNeedsToAdjustStepNumberToSystemState) {
            mNeedsToAdjustStepNumberToSystemState = false;
            mStepNumber = determineSetupStepNumber();
            updateSetupStepView();
        }
    }

    private void updateSetupStepView() {
        // welcome 화면에는 상단 안내문 뷰가 없으므로 null 보호
        if (mStepNumber != STEP_WELCOME) {
            ensureInstructionTopBound();
            mTvInstructionTop.setText(
                    mSetupStepGroup.getInstructionText(mStepNumber, /*done=*/false));
        }

        // ── 0. WelCome 화면 처리 ────────────────────────────────
        boolean welcome = (mStepNumber == STEP_WELCOME);
        mWelcomeScreen.setVisibility(welcome ? View.VISIBLE : View.GONE);
        mSetupScreen.setVisibility(welcome ? View.GONE : View.VISIBLE);

        if (welcome) {
            if (ENABLE_WELCOME_VIDEO) showAndStartWelcomeVideo();
            else hideWelcomeVideoAndShowWelcomeImage();

            updateCTA(STEP_WELCOME);
            return;
        }
        hideAndStopWelcomeVideo();

        // ── 1. 게이지 & 단계 카드 갱신 ──────────────────────────
        boolean done = mStepNumber < determineSetupStepNumber();
        mSetupStepGroup.enableStep(mStepNumber, done);

        // ── 2. CTA(버튼) 상태 갱신  ────────────────────────────
        updateCTA(mStepNumber);

        // ── 3. 7단계(백업 시작) 자동 실행 ──────────────────────
        if (mStepNumber == STEP_7 && !mStartedBackup) {
            mStartedBackup = true;

            /* 공통 제약 : 네트워크 연결 */
            Constraints netConn = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            /* ① 전체 백업 : 단 1회 */
            OneTimeWorkRequest fullReq =
                    new OneTimeWorkRequest.Builder(FullBackupWorker.class)
                            .setConstraints(netConn)
                            .build();
            WorkManager.getInstance(this)
                    .enqueueUniqueWork("full_backup", ExistingWorkPolicy.KEEP, fullReq);

            /* ② 증분 백업 : 새 사진마다 */
            Constraints incCons = new Constraints.Builder()
                    .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest incReq =
                    new OneTimeWorkRequest.Builder(IncrementalBackupWorker.class)
                            .setConstraints(incCons)
                            .build();
            WorkManager.getInstance(this)
                    .enqueueUniqueWork("incremental_backup",
                            ExistingWorkPolicy.REPLACE, incReq);

            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> {
                        startActivity(new Intent(this, SearchActivity.class));
                        finish();
                    },
                    STEP_7_DELAY_MS);
        }

        // ── 4. 카드 슬라이드 애니메이션 ─────────────────────────
        playStepSlideAnim();
    }

    private void updateCTA(int step) {
        // 모두 숨긴 뒤 필요한 것만 노출
        mBtnAux.setVisibility(View.GONE);
        mBtnNext.setVisibility(View.GONE);

        switch (step) {
            /* 1단계 – ‘시작하기’ */
            case STEP_WELCOME:    // = 앱 최초 실행
                mBtnNext.setVisibility(View.GONE);
                mBtnAux.setVisibility(View.GONE);
                break;
            /* 1단계 – 입력 방법 전환 */
            case STEP_1:
                mBtnNext.setVisibility(View.VISIBLE);
                mBtnNext.setText(R.string.setup_step1_action);
                mBtnNext.setOnClickListener(v -> invokeLanguageAndInputSettings());
                break;
            /* 2단계 – 입력 방법 전환 */
            case STEP_2:
                mBtnNext.setVisibility(View.VISIBLE);
                mBtnNext.setText(R.string.setup_step2_action);
                mBtnNext.setOnClickListener(v -> invokeInputMethodPicker());
                break;
            /* 3단계 – 추가 언어 + 다음 */
            case STEP_3:
                mBtnAux.setVisibility(View.VISIBLE);
                mBtnAux.setText(R.string.setup_step3_aux_label);
                mBtnAux.setOnClickListener(v -> invokeSubtypeEnablerOfThisIme());

                mBtnNext.setVisibility(View.VISIBLE);
                mBtnNext.setText(R.string.setup_next_action);
                mBtnNext.setOnClickListener(v -> {
                    mStepNumber = STEP_4;
                    updateSetupStepView();
                });
                break;
            /* 4단계 – 로그인 */
            case STEP_4:
                mBtnNext.setVisibility(View.VISIBLE);
                mBtnNext.setText(R.string.setup_step4_action);
                mBtnNext.setOnClickListener(v -> {
                    Intent i = new Intent(this, KakaoLoginActivity.class);
                    startActivityForResult(i, REQ_KAKAO_LOGIN);
                });
                break;
            /* 5단계 – 사진 권한 */
            case STEP_5:
                mBtnNext.setVisibility(View.VISIBLE);
                mBtnNext.setText(R.string.setup_step5_action);
                mBtnNext.setOnClickListener(v -> ensureMediaPermission());
                break;
            /* 6단계 – 알림 권한 */
            case STEP_6:
                mBtnNext.setVisibility(View.VISIBLE);
                mBtnNext.setText(R.string.setup_step6_action);
                mBtnNext.setOnClickListener(v -> ensureNotificationPermission());
                break;

            /* 7단계 – 버튼 없음 */
            case STEP_7:
                // 두 버튼 모두 숨김 (이미 기본값이 숨김)
                break;
        }
    }

    static final class SetupStep implements View.OnClickListener {
        // ── 필드 ───────────────────────────────────────────────
        final int mStepNo;
        private final View mStepView;
        private final String mInstruction;
        private final String mFinishedInstruction;
        private final TextView mActionLabel;
        private Runnable mAction;          // null   → 클릭 불가

        /**
         * title·icon 은 안 쓰므로 매개변수 3개만 받도록 단순화
         */
        SetupStep(int stepNo,
                  String appName,
                  View stepView,
                  int instructionRes,            // %1$s 로 앱 이름을 받을 수도 있음
                  int finishedInstructionRes,
                  int actionLabelRes) {

            mStepNo = stepNo;
            mStepView = stepView;

            Resources res = stepView.getResources();
            mInstruction = instructionRes == 0 ? null
                    : res.getString(instructionRes, appName);
            mFinishedInstruction = finishedInstructionRes == 0 ? null
                    : res.getString(finishedInstructionRes, appName);

            mActionLabel = stepView.findViewById(R.id.setup_step_action_label);
            mActionLabel.setText(res.getString(actionLabelRes));
        }

        public void setEnabled(final boolean enabled, final boolean isStepActionAlreadyDone) {
            mStepView.setVisibility(enabled ? View.VISIBLE : View.GONE);

            final TextView instructionView = mStepView.findViewById(
                    R.id.setup_step_instruction);
            instructionView.setText(isStepActionAlreadyDone ? mFinishedInstruction : mInstruction);
            mActionLabel.setVisibility(isStepActionAlreadyDone ? View.GONE : View.VISIBLE);
        }

        public void setAction(final Runnable action) {
            mActionLabel.setOnClickListener(this);
            mAction = action;
        }

        @Override
        public void onClick(final View v) {
            if (v == mActionLabel && mAction != null) {
                mAction.run();
                return;
            }
        }
    }

    static final class SetupStepGroup {
        private final StepGaugeView mGauge;           // ⬅︎ 게이지바 참조
        private final ArrayList<SetupStep> mGroup = new ArrayList<>();

        /**
         * gaugeView = R.id.setup_step_gauge
         */
        SetupStepGroup(StepGaugeView gaugeView) {
            mGauge = gaugeView;
        }

        void addStep(SetupStep step) {
            mGroup.add(step);
        }

        /**
         * @param no   지금 보여줄 단계 번호
         * @param done true면 단계 액션을 이미 완료함
         */
        void enableStep(int no, boolean done) {
            for (SetupStep s : mGroup)
                s.setEnabled(s.mStepNo == no, done);
            mGauge.setStep(no);
        }

        /**
         * 상단 안내문 출력을 위해 현재 단계의 문구를 돌려준다
         */
        String getInstructionText(int stepNo, boolean done) {
            for (SetupStep s : mGroup) {
                if (s.mStepNo == stepNo) {
                    return done ? s.mFinishedInstruction : s.mInstruction;
                }
            }
            return "";
        }
    }

    // ───────────────── Toss-style 슬라이드 애니메이션 ─────────────────
    private void playStepSlideAnim() {
        // ③ 헤더를 고정하고 "단계 카드 영역"만 슬라이드
        View pane = mContentPane;

        // 시작 위치 : 화면 오른쪽 25% → 0%
        pane.setTranslationX(pane.getWidth() * 0.25f);
        pane.setAlpha(0f);

        pane.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .start();
    }
}
