package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

/**
 * 카카오 로그인 요청 모델 클래스
 */
public class KakaoLoginRequest {
    @SerializedName("kakaoAccessToken")
    private String kakaoAccessToken;

    public KakaoLoginRequest(String kakaoAccessToken) {
        this.kakaoAccessToken = kakaoAccessToken;
    }

    public String getKakaoAccessToken() {
        return kakaoAccessToken;
    }

    public void setKakaoAccessToken(String kakaoAccessToken) {
        this.kakaoAccessToken = kakaoAccessToken;
    }
}