package org.dslul.openboard.inputmethod.latin.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

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

    /**
     * 액세스-토큰 재발급
     * POST /auth/reissue
     *
     * body: { "refreshToken": "<리프레시-토큰>" }
     * 응답: 기존 AuthResponse 형식(새 access / refresh / userId)
     */
    @POST("/auth/reissue")
    Call<AuthResponse> reissue(@Body ReissueRequest  request);
}