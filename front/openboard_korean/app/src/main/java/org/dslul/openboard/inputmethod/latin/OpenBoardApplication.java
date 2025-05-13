package org.dslul.openboard.inputmethod.latin;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import com.kakao.sdk.common.KakaoSdk;
import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.settings.SearchActivity;

/**
 * 애플리케이션 클래스 - 앱 초기화 담당
 */
public class OpenBoardApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 카카오 SDK 초기화 (실제 앱 키로 교체)
        KakaoSdk.INSTANCE.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
        Log.d("OpenBoardApplication","========================OpenBoard On Load Test=============================");

        // 로그인 상태 로깅 (참고용)
        AuthManager am = AuthManager.getInstance(getApplicationContext());
        Log.d("AuthManager", am.isLoggedIn() ? "로그인 되어 있습니다" : "로그아웃");

    }
}