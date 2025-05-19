package org.dslul.openboard.inputmethod.latin.search;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.login.KakaoLoginActivity;
import org.dslul.openboard.inputmethod.latin.settings.SettingsActivity;

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // menu_search.xml 인플레이트
        getMenuInflater().inflate(R.menu.menu_search, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            // 설정 화면으로 이동
            startActivity(new Intent(this, SettingsActivity.class));
            // 이 두 리소스를 방금 만든 애니메이션으로 교체
            overridePendingTransition(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
            );
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
