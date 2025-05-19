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
public final class SetupWizardActivity extends Activity implements View.OnClickListener {

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
//        ensureNotificationPermission();
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
        // 권한이 이미 있거나 API < 33
        onNotificationPermissionGranted();
    }

    private void onNotificationPermissionGranted() {
        // 1) WorkManager 제약 설정: 네트워크가 연결된 상태에서만 실행
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // 2) 권한 직후 전체 백업 (한 번만)
        OneTimeWorkRequest fullReq =
                new OneTimeWorkRequest.Builder(FullBackupWorker.class)
                        .setConstraints(constraints)
                        .build();
        WorkManager.getInstance(this)
                .enqueueUniqueWork(
                        "full_backup",
                        ExistingWorkPolicy.KEEP,
                        fullReq
                );

        // 3) 이후 새 사진마다 실행될 증분 백업 워커 등록
        Constraints incCons = new Constraints.Builder()
                .addContentUriTrigger(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        true
                )
                .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                // (선택) 네트워크 제약도 추가하려면 아래 한 줄 더
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest incReq =
                new OneTimeWorkRequest.Builder(IncrementalBackupWorker.class)
                        .setConstraints(incCons)
                        .build();
        WorkManager.getInstance(this)
                .enqueueUniqueWork(
                        "incremental_backup",
                        ExistingWorkPolicy.REPLACE,
                        incReq
                );
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
                mStepNumber = STEP_6_REQUEST_NOTIF_PERM;
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
                mStepNumber = STEP_7_RUN_BACKUP_WORKER;
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
    private View mActionStart;
    private View mActionNext;
    private TextView mStep1Bullet;
    private TextView mActionFinish;
    private SetupStepGroup mSetupStepGroup;
    private static final String STATE_STEP = "step";
    private int mStepNumber;
    private boolean mNeedsToAdjustStepNumberToSystemState;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_1 = 1;
    private static final int STEP_2 = 2;
    private static final int STEP_3 = 3;
    private static final int STEP_4_LOGIN = 4; // 카카오 로그인
    private static final int STEP_5_REQUEST_PHOTO_PERM = 5; // 사진 권한
    private static final int STEP_6_REQUEST_NOTIF_PERM = 6; // 알림 권한
    private static final int STEP_7_RUN_BACKUP_WORKER = 7; // 백업 실행
    private static final long STEP_7_DELAY_MS = 5_000L;      // 5초 딜레이

    private static final int STEP_LAUNCHING_IME_SETTINGS = 8;
    private static final int STEP_BACK_FROM_IME_SETTINGS = 9;

    private static final int REQ_KAKAO_LOGIN = 100;

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
        mSetupWizard = findViewById(R.id.setup_wizard);

        if (savedInstanceState == null) {
            mStepNumber = determineSetupStepNumberFromLauncher();
        } else {
            mStepNumber = savedInstanceState.getInt(STATE_STEP);
        }

        final String applicationName = getResources().getString(getApplicationInfo().labelRes);
        mWelcomeScreen = findViewById(R.id.setup_welcome_screen);
        final TextView welcomeTitle = findViewById(R.id.setup_welcome_title);
        welcomeTitle.setText(getString(R.string.setup_welcome_title, applicationName));

        mSetupScreen = findViewById(R.id.setup_steps_screen);
        final TextView stepsTitle = findViewById(R.id.setup_title);
        stepsTitle.setText(getString(R.string.setup_steps_title, applicationName));

        final SetupStepIndicatorView indicatorView =
                findViewById(R.id.setup_step_indicator);
        mSetupStepGroup = new SetupStepGroup(indicatorView);

        mStep1Bullet = findViewById(R.id.setup_step1_bullet);
        mStep1Bullet.setOnClickListener(this);
        final SetupStep step1 = new SetupStep(STEP_1, applicationName,
                mStep1Bullet, findViewById(R.id.setup_step1),
                R.string.setup_step1_title, R.string.setup_step1_instruction,
                R.string.setup_step1_finished_instruction, R.drawable.ic_setup_step1,
                R.string.setup_step1_action);
        final SettingsPoolingHandler handler = mHandler;
        step1.setAction(new Runnable() {
            @Override
            public void run() {
                invokeLanguageAndInputSettings();
                handler.startPollingImeSettings();
            }
        });
        mSetupStepGroup.addStep(step1);

        final SetupStep step2 = new SetupStep(STEP_2, applicationName,
                (TextView) findViewById(R.id.setup_step2_bullet), findViewById(R.id.setup_step2),
                R.string.setup_step2_title, R.string.setup_step2_instruction,
                0 /* finishedInstruction */, R.drawable.ic_setup_step2,
                R.string.setup_step2_action);
        step2.setAction(new Runnable() {
            @Override
            public void run() {
                invokeInputMethodPicker();
            }
        });
        mSetupStepGroup.addStep(step2);

        final SetupStep step3 = new SetupStep(STEP_3, applicationName,
                (TextView) findViewById(R.id.setup_step3_bullet), findViewById(R.id.setup_step3),
                R.string.setup_step3_title, R.string.setup_step3_instruction,
                0 /* finishedInstruction */, R.drawable.ic_setup_step3,
                R.string.setup_step3_action);
        step3.setAction(new Runnable() {
            @Override
            public void run() {
                invokeSubtypeEnablerOfThisIme();
            }
        });
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

        mActionStart = findViewById(R.id.setup_start_label);
        mActionStart.setOnClickListener(this);
        mActionNext = findViewById(R.id.setup_next);
        mActionNext.setOnClickListener(this);
        mActionFinish = findViewById(R.id.setup_finish);
        mActionFinish.setCompoundDrawablesRelativeWithIntrinsicBounds(getResources().getDrawable(R.drawable.ic_setup_finish),
                null, null, null);
        mActionFinish.setOnClickListener(this);

        // ────────── 추가: STEP_4 - 카카오 로그인 ──────────
        final SetupStep step4 = new SetupStep(
                STEP_4_LOGIN, applicationName,
                findViewById(R.id.setup_step4_bullet),
                findViewById(R.id.setup_step4),
                R.string.setup_step4_title,           // “로그인”
                R.string.setup_step4_instruction,     // “버튼을 눌러 카카오 로그인 페이지로 이동합니다.”
                0,                                     // 완료(instruction) 없음
                R.drawable.ic_setup_step4,            // 아이콘
                R.string.setup_step4_action            // “카카오 로그인”
        );
        step4.setAction(new Runnable() {
            @Override
            public void run() {
                // 카카오 로그인 화면으로 이동
                Intent intent = new Intent(SetupWizardActivity.this, KakaoLoginActivity.class);
                startActivityForResult(intent, REQ_KAKAO_LOGIN);
            }
        });


        mSetupStepGroup.addStep(step4);

        // ────────── 추가: STEP_5 - 사진 접근 권한 요청 ──────────
        final SetupStep step5 = new SetupStep(
                STEP_5_REQUEST_PHOTO_PERM, applicationName,
                findViewById(R.id.setup_step5_bullet),
                findViewById(R.id.setup_step5),
                R.string.setup_step5_title,           // “사진 접근 권한 설정”
                R.string.setup_step5_instruction,     // “버튼을 눌러 사진 권한을 요청합니다.”
                0,
                R.drawable.ic_setup_step5,
                R.string.setup_step5_action           // “사진 권한 요청”
        );
        step5.setAction(new Runnable() {
            @Override
            public void run() {
                // 기존 ensureMediaPermission() 호출
                ensureMediaPermission();
            }
        });
        mSetupStepGroup.addStep(step5);

        // ────────── 추가: STEP_6 - 알림 권한 요청 ──────────
        final SetupStep step6 = new SetupStep(
                STEP_6_REQUEST_NOTIF_PERM, applicationName,
                findViewById(R.id.setup_step6_bullet),
                findViewById(R.id.setup_step6),
                R.string.setup_step6_title,           // “알림 권한 설정”
                R.string.setup_step6_instruction,     // “버튼을 눌러 알림 권한을 요청합니다.”
                0,
                R.drawable.ic_setup_step6,
                R.string.setup_step6_action           // “알림 권한 요청”
        );
        step6.setAction(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= 33) {
                    ActivityCompat.requestPermissions(
                            SetupWizardActivity.this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            REQ_NOTIF_PERMS
                    );
                } else {
                    // API<33 이면 바로 7단계로
                    mStepNumber = STEP_7_RUN_BACKUP_WORKER;
                    updateSetupStepView();
                }
            }
        });
        mSetupStepGroup.addStep(step6);

        // ────────── 추가: STEP_7 - Backup WorkManager 실행 ──────────
        final SetupStep step7 = new SetupStep(
                STEP_7_RUN_BACKUP_WORKER, applicationName,
                findViewById(R.id.setup_step7_bullet),
                findViewById(R.id.setup_step7),
                R.string.setup_step7_title,           // “백업 시작”
                R.string.setup_step7_instruction,     // “5초 뒤 메인 페이지로 이동합니다.”
                0,
                R.drawable.ic_setup_step7,
                R.string.setup_step7_action           // “백업 실행”
        );
        step7.setAction(new Runnable() {
            @Override
            public void run() {
                // WorkManager 제약 설정 후 enqueue
                Constraints constraints = new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build();
                OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(FullBackupWorker.class)
                        .setConstraints(constraints)
                        .build();
                WorkManager.getInstance(getApplicationContext()).enqueue(req);

                // 5초 딜레이 후 메인(SearchActivity)로 이동
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(SetupWizardActivity.this, SearchActivity.class));
                        finish();
                    }
                }, STEP_7_DELAY_MS);
            }
        });
        mSetupStepGroup.addStep(step7);

    }

    // onActivityResult 추가
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_KAKAO_LOGIN) {
            if (resultCode == RESULT_OK) {
                // 로그인 성공 → 5단계(사진 권한)로 이동
                mStepNumber = STEP_5_REQUEST_PHOTO_PERM;
                updateSetupStepView();
            } else {
                // 로그인 실패나 취소 → 필요하면 토스트
                Toast.makeText(this, "로그인이 취소되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onClick(final View v) {
//        if (v == mActionFinish) {
//            finish();
//            return;
//        }
        if (v == mActionFinish) {
            if (mStepNumber == STEP_3) {
                // 3단계 완료 시 4단계(로그인)로 이동
                mStepNumber = STEP_4_LOGIN;
                updateSetupStepView();
            } else {
                // 나머지는 원래대로 종료
                finish();
            }
            return;
        }
        final int currentStep = determineSetupStepNumber();
        final int nextStep;
        if (v == mActionStart) {
            nextStep = STEP_1;
        } else if (v == mActionNext) {
            nextStep = mStepNumber + 1;
        } else if (v == mStep1Bullet && currentStep == STEP_2) {
            nextStep = STEP_1;
        } else {
            nextStep = mStepNumber;
        }
        if (mStepNumber != nextStep) {
            mStepNumber = nextStep;
            updateSetupStepView();
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
        mSetupWizard.setVisibility(View.VISIBLE);
        final boolean welcomeScreen = (mStepNumber == STEP_WELCOME);
        mWelcomeScreen.setVisibility(welcomeScreen ? View.VISIBLE : View.GONE);
        mSetupScreen.setVisibility(welcomeScreen ? View.GONE : View.VISIBLE);
        if (welcomeScreen) {
            if (ENABLE_WELCOME_VIDEO) {
                showAndStartWelcomeVideo();
            } else {
                hideWelcomeVideoAndShowWelcomeImage();
            }
            return;
        }
//        hideAndStopWelcomeVideo();
//        final boolean isStepActionAlreadyDone = mStepNumber < determineSetupStepNumber();
//        mSetupStepGroup.enableStep(mStepNumber, isStepActionAlreadyDone);
//        mActionNext.setVisibility(isStepActionAlreadyDone ? View.VISIBLE : View.GONE);
//        mActionFinish.setVisibility((mStepNumber == STEP_3) ? View.VISIBLE : View.GONE);
        hideAndStopWelcomeVideo();

        // 기존 시스템 상태 기반 완료 판정은 유지하되,
        // STEP_3 에선 “다음”만, “완료”는 숨김
        boolean isDone = mStepNumber < determineSetupStepNumber();
        mSetupStepGroup.enableStep(mStepNumber, isDone);
        if (mStepNumber == STEP_3) {
            mActionNext.setVisibility(View.VISIBLE);
            mActionFinish.setVisibility(View.GONE);
        } else {
            mActionNext.setVisibility(isDone ? View.VISIBLE : View.GONE);
            mActionFinish.setVisibility(View.GONE);
        }
    }

    static final class SetupStep implements View.OnClickListener {
        public final int mStepNo;
        private final View mStepView;
        private final TextView mBulletView;
        private final int mActivatedColor;
        private final int mDeactivatedColor;
        private final String mInstruction;
        private final String mFinishedInstruction;
        private final TextView mActionLabel;
        private Runnable mAction;

        public SetupStep(final int stepNo, final String applicationName, final TextView bulletView,
                         final View stepView, final int title, final int instruction,
                         final int finishedInstruction, final int actionIcon, final int actionLabel) {
            mStepNo = stepNo;
            mStepView = stepView;
            mBulletView = bulletView;
            final Resources res = stepView.getResources();
            mActivatedColor = res.getColor(R.color.setup_text_action);
            mDeactivatedColor = res.getColor(R.color.setup_text_dark);

            final TextView titleView = mStepView.findViewById(R.id.setup_step_title);
            titleView.setText(res.getString(title, applicationName));
            mInstruction = (instruction == 0) ? null
                    : res.getString(instruction, applicationName);
            mFinishedInstruction = (finishedInstruction == 0) ? null
                    : res.getString(finishedInstruction, applicationName);

            mActionLabel = mStepView.findViewById(R.id.setup_step_action_label);
            mActionLabel.setText(res.getString(actionLabel));
            if (actionIcon == 0) {
                final int paddingEnd = mActionLabel.getPaddingEnd();
                mActionLabel.setPaddingRelative(paddingEnd, 0, paddingEnd, 0);
            } else {
                mActionLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        res.getDrawable(actionIcon), null, null, null);
            }
        }

        public void setEnabled(final boolean enabled, final boolean isStepActionAlreadyDone) {
            mStepView.setVisibility(enabled ? View.VISIBLE : View.GONE);
            mBulletView.setTextColor(enabled ? mActivatedColor : mDeactivatedColor);
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
        private final SetupStepIndicatorView mIndicatorView;
        private final ArrayList<SetupStep> mGroup = new ArrayList<>();

        public SetupStepGroup(final SetupStepIndicatorView indicatorView) {
            mIndicatorView = indicatorView;
        }

        public void addStep(final SetupStep step) {
            mGroup.add(step);
        }

        public void enableStep(final int enableStepNo, final boolean isStepActionAlreadyDone) {
            for (final SetupStep step : mGroup) {
                step.setEnabled(step.mStepNo == enableStepNo, isStepActionAlreadyDone);
            }
            mIndicatorView.setIndicatorPosition(enableStepNo - STEP_1, mGroup.size());
        }
    }
}
