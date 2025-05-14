package org.dslul.openboard.inputmethod.latin.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * API 요청을 위한 인터페이스
 */
public interface ApiService {

    /**
     * 카카오 로그인 API 엔드포인트
     * @param request 카카오 액세스 토큰을 포함한 요청 객체
     * @return 서버 응답
     */
    @POST("/auth/kakao/login")
    Call<AuthResponse> kakaoLogin(@Body KakaoLoginRequest request);
}