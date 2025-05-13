package org.dslul.openboard.inputmethod.latin;

import android.app.Application;
import com.kakao.sdk.common.KakaoSdk;
import org.dslul.openboard.inputmethod.latin.BuildConfig;

/**
 * 애플리케이션 클래스 - 앱 초기화 담당
 */
public class OpenBoardApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 카카오 SDK 초기화 (실제 앱 키로 교체)
        KakaoSdk.INSTANCE.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
    }
}