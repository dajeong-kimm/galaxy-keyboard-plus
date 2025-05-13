package org.dslul.openboard.inputmethod.latin.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toolbar;

import com.kakao.sdk.common.KakaoSdk;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.BuildConfig;

public class SearchActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        // 1) 툴바 세팅 (플랫폼 Activity + 플랫폼 Toolbar)
        Toolbar toolbar = findViewById(R.id.toolbar);
        // setActionBar 은 API 21+ 에서만
        setActionBar(toolbar);

        // 2) 프래그먼트 붙이기
        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SearchPageFragment())
                    .commit();
        }

        KakaoSdk.init(getApplicationContext(), BuildConfig.KAKAO_NATIVE_APP_KEY);
    }
}
