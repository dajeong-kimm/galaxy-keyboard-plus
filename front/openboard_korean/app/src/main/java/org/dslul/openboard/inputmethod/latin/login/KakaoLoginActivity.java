package org.dslul.openboard.inputmethod.latin.login;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.auth.AuthCallback;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.data.SecureStorage;
import org.dslul.openboard.inputmethod.latin.settings.SearchActivity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 카카오 로그인 화면
 */
public class KakaoLoginActivity extends Activity {

    private static final String TAG = "KakaoLoginActivity";

    private AuthManager authManager;
    private SecureStorage secureStorage;
    private Button btnKakaoLogin;
    private Button btnLogout;
    private ProgressBar progressBar;
    private TextView tvLoginStatus;
    private TextView tvTokenInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_kakao_login);

        // 매니저 인스턴스 초기화
        authManager = AuthManager.getInstance(this);
        authManager.setCurrentActivity(this);

        // SecureStorage 초기화
        secureStorage = SecureStorage.getInstance(this);

        // 뷰 초기화
        btnKakaoLogin = findViewById(R.id.btn_kakao_login);
        progressBar = findViewById(R.id.progress_bar);

        // 추가할 뷰 요소들 - XML 레이아웃에 이 요소들을 추가해야 함
        tvLoginStatus = findViewById(R.id.tv_login_status);
        tvTokenInfo = findViewById(R.id.tv_token_info);
        btnLogout = findViewById(R.id.btn_logout);

        if (tvLoginStatus == null || tvTokenInfo == null || btnLogout == null) {
            // 레이아웃이 아직 업데이트되지 않았다면 임시 메시지 표시
            Toast.makeText(this, "레이아웃 업데이트가 필요합니다", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "레이아웃에 필요한 뷰 요소가 없습니다. activity_kakao_login.xml을 업데이트하세요.");
            // 임시 로그 출력으로 상태 확인
            checkAndShowLoginStatus();
        } else {
            // 로그인 상태 화면에 표시
            updateLoginStatusUI();

            // 로그아웃 버튼 이벤트 설정
            btnLogout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performLogout();
                }
            });
        }

        // 로그인 버튼 이벤트 설정
        btnKakaoLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performKakaoLogin();
            }
        });

        // 키 해시 출력
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String hash = Base64.encodeToString(md.digest(), Base64.DEFAULT);
                Log.d("KeyHash", "키해시: " + hash);
            }
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() → currentActivity 갱신");
        // 화면이 다시 보일 때마다 현재 액티비티 업데이트 및 로그인 상태 갱신
        if (authManager != null) {
            authManager.setCurrentActivity(this);
        }

        // UI 갱신
        if (tvLoginStatus != null && tvTokenInfo != null) {
            updateLoginStatusUI();
        } else {
            checkAndShowLoginStatus();
        }
    }

    /**
     * 로그인 상태를 확인하고 UI에 표시
     */
    private void updateLoginStatusUI() {
        if (authManager.isLoggedIn()) {
            tvLoginStatus.setText("로그인 상태: 로그인됨");
            btnKakaoLogin.setVisibility(View.GONE);
            btnLogout.setVisibility(View.VISIBLE);

            // 토큰 정보 표시
            String userId = secureStorage.getUserId();
            String accessToken = secureStorage.getAccessToken();
            String refreshToken = secureStorage.getRefreshToken();

            StringBuilder tokenInfo = new StringBuilder();
            tokenInfo.append("사용자 ID: ").append(userId).append("\n\n");

            // 토큰 일부만 표시 (보안상)
            if (accessToken != null && accessToken.length() > 10) {
                tokenInfo.append("액세스 토큰: ")
                        .append(accessToken.substring(0, 10))
                        .append("...")
                        .append(accessToken.substring(Math.max(0, accessToken.length() - 5)))
                        .append("\n\n");
            } else {
                tokenInfo.append("액세스 토큰: 없음\n\n");
            }

            if (refreshToken != null && refreshToken.length() > 10) {
                tokenInfo.append("리프레시 토큰: ")
                        .append(refreshToken.substring(0, 10))
                        .append("...")
                        .append(refreshToken.substring(Math.max(0, refreshToken.length() - 5)));
            } else {
                tokenInfo.append("리프레시 토큰: 없음");
            }

//            tvTokenInfo.setText(tokenInfo.toString());
//            tvTokenInfo.setVisibility(View.VISIBLE);
        } else {
            tvLoginStatus.setText("로그인 상태: 로그아웃됨");
            btnKakaoLogin.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.GONE);
            tvTokenInfo.setText("");
            tvTokenInfo.setVisibility(View.GONE);
        }
    }

    /**
     * 뷰 요소가 없을 경우 로그로 상태 확인
     */
    private void checkAndShowLoginStatus() {
        if (authManager.isLoggedIn()) {
            String userId = secureStorage.getUserId();
//            String accessToken = secureStorage.getAccessToken();
//            String refreshToken = secureStorage.getRefreshToken();

//            Log.i(TAG, "로그인 상태: 로그인됨");
//            Log.i(TAG, "사용자 ID: " + userId);
//            if (accessToken != null) {
//                Log.i(TAG, "액세스 토큰: " + accessToken.substring(0, Math.min(20, accessToken.length())) + "...");
//            }
//            if (refreshToken != null) {
//                Log.i(TAG, "리프레시 토큰: " + refreshToken.substring(0, Math.min(20, refreshToken.length())) + "...");
//            }

            Toast.makeText(this, "이미 로그인되어 있습니다: " + userId, Toast.LENGTH_SHORT).show();
            btnKakaoLogin.setVisibility(View.GONE);
        } else {
            Log.i(TAG, "로그인 상태: 로그아웃됨");
            btnKakaoLogin.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 카카오 로그인 수행
     */
    private void performKakaoLogin() {
        Log.d(TAG, "버튼 눌림 → performKakaoLogin()");
        // 로딩 표시
        setLoading(true);

        // 로그인 요청
        authManager.loginWithKakao(new AuthCallback() {
            @Override
            public void onLoginSuccess(final String userId) {
                Log.d(TAG, "로그인 성공 userId=" + userId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setLoading(false);
                        Toast.makeText(KakaoLoginActivity.this,
                                "로그인 성공: " + userId, Toast.LENGTH_SHORT).show();

                        // UI 갱신
                        if (tvLoginStatus != null && tvTokenInfo != null) {
                            updateLoginStatusUI();
                        } else {
                            checkAndShowLoginStatus();
                        }
                        Intent intent = new Intent(getApplicationContext(), SearchActivity.class);
                        startActivity(intent);
                        // 로그인 성공 후 액티비티 종료 (선택적)
                        // setResult(RESULT_OK);
                         finish();
                    }
                });
            }

            @Override
            public void onLoginFailure(final String errorMessage) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setLoading(false);
                        Toast.makeText(KakaoLoginActivity.this,
                                "로그인 실패: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onLogoutSuccess() {
                // 로그인 화면에서는 사용되지 않음
            }
        });
    }

    /**
     * 로그아웃 수행
     */
    private void performLogout() {
        // 로딩 표시
        setLoading(true);

        // 로그아웃 요청
        authManager.logout(new AuthCallback() {
            @Override
            public void onLoginSuccess(String userId) {
                // 로그아웃에서는 사용되지 않음
            }

            @Override
            public void onLoginFailure(String errorMessage) {
                // 로그아웃에서는 사용되지 않음
            }

            @Override
            public void onLogoutSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setLoading(false);
                        Toast.makeText(KakaoLoginActivity.this,
                                "로그아웃 성공", Toast.LENGTH_SHORT).show();

                        // UI 갱신
                        if (tvLoginStatus != null && tvTokenInfo != null) {
                            updateLoginStatusUI();
                        } else {
                            checkAndShowLoginStatus();
                        }
                    }
                });
            }
        });
    }

    /**
     * 로딩 상태 표시
     */
    private void setLoading(boolean isLoading) {
        btnKakaoLogin.setEnabled(!isLoading);
        if (btnLogout != null) {
            btnLogout.setEnabled(!isLoading);
        }
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}