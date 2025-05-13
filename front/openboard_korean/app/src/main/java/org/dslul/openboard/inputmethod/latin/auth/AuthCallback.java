package org.dslul.openboard.inputmethod.latin.auth;

public interface AuthCallback {
    /**
     * 로그인 성공 시 호출됨
     */
    void onLoginSuccess(String userId);

    /**
     * 로그인 실패 시 호출됨
     */
    void onLoginFailure(String errorMessage);

    /**
     * 로그아웃 성공 시 호출됨
     */
    void onLogoutSuccess();
}