package org.dslul.openboard.inputmethod.latin.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.login.KakaoLoginActivity;

public class SearchActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.init(this);

        // 로그인 상태 확인
        AuthManager authManager = AuthManager.getInstance(this);

        // 로그인되지 않은 경우에만 로그인 화면으로 리디렉션
        if (!authManager.isLoggedIn()) {
            // 로그인되지 않은 경우 로그인 화면으로
            Intent intent = new Intent(this, KakaoLoginActivity.class);
            startActivity(intent);
            finish(); // 현재 액티비티 종료
            return; // 여기서 함수 종료
        }

        setContentView(R.layout.activity_search);

        // 프래그먼트 붙이기
        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SearchPageFragment())
                    .commit();
        }

    }
}
