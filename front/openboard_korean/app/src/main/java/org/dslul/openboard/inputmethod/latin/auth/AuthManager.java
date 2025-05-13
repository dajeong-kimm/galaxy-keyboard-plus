package org.dslul.openboard.inputmethod.latin.auth;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.user.UserApiClient;

import org.dslul.openboard.inputmethod.latin.data.SecureStorage;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.network.ApiService;
import org.dslul.openboard.inputmethod.latin.network.AuthResponse;
import org.dslul.openboard.inputmethod.latin.network.KakaoLoginRequest;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 인증 관련 기능을 처리하는 매니저 클래스
 */
public class AuthManager {
    private static final String TAG = "AuthManager";

    // 싱글톤 인스턴스
    private static AuthManager instance;

    // 의존성
    private final Context context;
    private final ApiService apiService;
    private final SecureStorage secureStorage;

    private Activity currentActivity; // 현재 액티비티 저장

    /**
     * 생성자 - 의존성 초기화
     */
    private AuthManager(Context context) {
        if (context instanceof Activity) Log.d(TAG, "생성자 ctx=Activity");

        this.context = context.getApplicationContext();

        ApiClient.init(this.context);

        // ApiClient 사용하여 ApiService 초기화
        this.apiService = ApiClient.getApiService();
        this.secureStorage = SecureStorage.getInstance(context);

        Log.d(TAG, "AuthManager 준비 완료");
    }

    /**
     * 싱글톤 인스턴스 반환
     */
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }

        // 만약 context가 Activity라면 현재 액티비티 업데이트
        if (context instanceof Activity) {
            instance.currentActivity = (Activity) context;
        }

        return instance;
    }

    // 액티비티 컨텍스트 설정 메서드 추가
    public void setCurrentActivity(Activity activity) {
        this.currentActivity = activity;
    }

    /**
     * 카카오 로그인 처리
     */
    public void loginWithKakao(final AuthCallback callback) {
        Log.d(TAG, "loginWithKakao() 호출 (activity=" + currentActivity + ")");

        if (currentActivity == null) {                 // Activity 필수
            callback.onLoginFailure("Activity context가 없어 카카오톡 로그인을 실행할 수 없습니다.");
            return;
        }

        boolean talkAvail = UserApiClient.getInstance()
                .isKakaoTalkLoginAvailable(currentActivity);
        Log.d(TAG, "isKakaoTalkLoginAvailable=" + talkAvail);

        if (talkAvail) {
            Log.d(TAG, "→ loginWithKakaoTalk 진입");
            UserApiClient.getInstance().loginWithKakaoTalk(
                    currentActivity,
                    (OAuthToken token, Throwable error) -> {
                        if (error != null) {
                            Log.e(TAG, "카카오톡 로그인 실패", error);
                            loginWithKakaoAccount(callback);     // 오류 → 계정 로그인 폴백
                        } else {
                            Log.d(TAG, "Talk 로그인 성공, accessToken="
                                    + token.getAccessToken().substring(0,10) + "…");
                            authenticateWithServer(token.getAccessToken(), callback);
                        }
                        return Unit.INSTANCE;
                    });
        } else {
            Log.d(TAG, "→ Talk 불가, 계정 로그인으로 폴백");
            loginWithKakaoAccount(callback);                    // Talk 미설치 → 계정 로그인
        }
    }

    /**
     * 카카오 계정으로 로그인
     */
    private void loginWithKakaoAccount(final AuthCallback callback) {
        Log.d(TAG, "loginWithKakaoAccount() 시작 (activity=" + currentActivity + ")");

        // ① Activity 보장
        if (currentActivity == null) {
            callback.onLoginFailure("Activity context가 없어 카카오 로그인을 실행할 수 없습니다.");
            return;
        }

        // 계정 로그인에서도 현재 액티비티 사용
//        Context loginContext = (currentActivity != null) ? currentActivity : context;

//        UserApiClient.getInstance().loginWithKakaoAccount(loginContext, new Function2<OAuthToken, Throwable, Unit>() {
//            @Override
//            public Unit invoke(OAuthToken token, Throwable error) {
//                if (error != null) {
//                    Log.e(TAG, "카카오 계정 로그인 실패", error);
//                    callback.onLoginFailure("카카오 계정 로그인 실패: " + error.getMessage());
//                } else if (token != null) {
//                    Log.i(TAG, "카카오 계정 로그인 성공: " + token.getAccessToken());
//                    // 서버에 토큰 전달 및 인증
//                    authenticateWithServer(token.getAccessToken(), callback);
//                }
//                return Unit.INSTANCE;
//            }
//        });
        UserApiClient.getInstance().loginWithKakaoAccount(
                /* 반드시 Activity */ currentActivity,
                (OAuthToken token, Throwable error) -> {
                    if (error != null) {
                        Log.e(TAG, "카카오 계정 로그인 실패", error);
                        callback.onLoginFailure("카카오 계정 로그인 실패: " + error.getMessage());
                    } else if (token != null) {
                        Log.i(TAG, "카카오 계정 로그인 성공: " + token.getAccessToken());
                        authenticateWithServer(token.getAccessToken(), callback);
                    }
                    return Unit.INSTANCE;
                }
        );
    }

    /**
     * 백엔드 서버에 카카오 액세스 토큰을 전송하여 인증
     */
    private void authenticateWithServer(String kakaoAccessToken, final AuthCallback callback) {
        KakaoLoginRequest request = new KakaoLoginRequest(kakaoAccessToken);

        Log.d(TAG, "POST /auth/kakao/login");
        apiService.kakaoLogin(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {

                Log.d(TAG, "← 서버 응답 code=" + response.code()
                        + " bodyNull=" + (response.body()==null));

                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();

                    // 토큰 저장
                    secureStorage.saveTokens(
                            authResponse.getAccessToken(),
                            authResponse.getRefreshToken(),
                            authResponse.getUserId()
                    );

                    Log.i(TAG, "서버 인증 성공: " + authResponse.getUserId());
                    callback.onLoginSuccess(authResponse.getUserId());
                } else {
                    String errorMsg = "서버 인증 실패";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += ": " + response.errorBody().string();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "에러 메시지 읽기 실패", e);
                    }
                    Log.e(TAG, errorMsg + ", 코드: " + response.code());
                    callback.onLoginFailure(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                String errorMsg = "서버 통신 오류: " + t.getMessage();
                Log.e(TAG, "HTTP 실패", t);
                callback.onLoginFailure(errorMsg);
            }
        });
    }

    /**
     * 로그아웃 처리
     */
    public void logout(final AuthCallback callback) {
        UserApiClient.getInstance().logout(new Function1<Throwable, Unit>() {
            @Override
            public Unit invoke(Throwable error) {
                // 카카오 로그아웃 결과와 상관없이 로컬 토큰 삭제
                secureStorage.clearTokens();

                if (error != null) {
                    Log.w(TAG, "카카오 로그아웃 에러 (무시됨)", error);
                } else {
                    Log.i(TAG, "카카오 로그아웃 성공");
                }

                callback.onLogoutSuccess();
                return Unit.INSTANCE;
            }
        });
    }

    /**
     * 로그인 상태 확인
     */
    public boolean isLoggedIn() {
        return secureStorage.hasTokens();
    }

    /**
     * 저장된 사용자 ID 조회
     */
    public String getUserId() {
        return secureStorage.getUserId();
    }

    /**
     * 액세스 토큰 조회
     */
    public String getAccessToken() {
        return secureStorage.getAccessToken();
    }

    /**
     * 리프레시 토큰 조회
     */
    public String getRefreshToken() {
        return secureStorage.getRefreshToken();
    }
}