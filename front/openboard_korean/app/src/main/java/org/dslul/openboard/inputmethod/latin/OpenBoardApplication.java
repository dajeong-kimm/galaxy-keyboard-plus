package org.dslul.openboard.inputmethod.latin;

import android.app.Application;
import android.content.Context;
import android.provider.MediaStore;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.kakao.sdk.common.KakaoSdk;

import org.dslul.openboard.inputmethod.backup.FullBackupWorker;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;

import java.time.Duration;

/**
 * 애플리케이션 클래스 - 앱 초기화 담당
 */
public class OpenBoardApplication extends Application {
    private static final String WORK_NAME = "image_backup_on_new";
    @Override
    public void onCreate() {
        super.onCreate();
        scheduleImageBackup(this);

        // 카카오 SDK 초기화 (실제 앱 키로 교체)
        KakaoSdk.INSTANCE.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
        Log.d("OpenBoardApplication","========================OpenBoard On Load Test=============================");

        // 로그인 상태 로깅 (참고용)
        AuthManager am = AuthManager.getInstance(getApplicationContext());
        Log.d("AuthManager", am.isLoggedIn() ? "로그인 되어 있습니다" : "로그아웃");

    }
    private void scheduleImageBackup(Context context) {
        // 1) Constraints 에 Content URI 트리거 설정
        Constraints constraints = new Constraints.Builder()
                // MediaStore.Images 변경(하위 경로 포함) 감지
                .addContentUriTrigger(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        /* triggerForDescendants= */ true
                )
                // 변화를 감지한 뒤 Work 실행까지 5초 지연(선택)
                .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                .build();

        // 2) OneTimeWorkRequest 생성
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FullBackupWorker.class)
                .setConstraints(constraints)
                // 초기 지연도 따로 걸고 싶으면 이렇게:
                // .setInitialDelay(5, TimeUnit.SECONDS)
                .build();

        // 3) 중복 등록 방지 후 enqueue
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        request
                );
    }
}