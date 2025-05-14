package com.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KakaoAuthClient {

    private final WebClient web = WebClient.builder()
            .baseUrl("https://kapi.kakao.com") // 기본 URL 설정
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .build();



    // KakaoAuthClient.java 에 추가 또는 수정할 메소드 예시
    public Map<String, Object> getKakaoUserProfile(String kakaoAccessToken) {
        System.out.println("카카오 사용자 정보 요청 - 전달받은 AccessToken: " + kakaoAccessToken.substring(0, 10) + "..."); // 토큰 전체 로깅은 보안상 주의

        try {
            Map<?, ?> responseBody = web.get().uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        System.err.println("카카오 /v2/user/me API 호출 실패: " + clientResponse.statusCode() + ", 응답: " + errorBody);
                                        return Mono.error(new RuntimeException("카카오 사용자 정보 요청 실패: " + errorBody + " (상태 코드: " + clientResponse.statusCode() + ")"));
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block(); // block()은 동기 처리. 비동기 처리를 원한다면 Mono를 반환하도록 수정.

            if (responseBody == null) {
                throw new RuntimeException("카카오 사용자 정보 API로부터 null 응답을 받았습니다.");
            }

            // 필요한 정보만 추출하여 반환
            Map<String, Object> userProfile = new HashMap<>();
            userProfile.put("id", responseBody.get("id")); // 카카오 사용자 고유 ID (Long 타입)

            Object kakaoAccountObj = responseBody.get("kakao_account");
            if (kakaoAccountObj instanceof Map) {
                Map<?, ?> kakaoAccount = (Map<?, ?>) kakaoAccountObj;
                userProfile.put("email", kakaoAccount.get("email"));
                // 필요하다면 다른 정보도 추가 (예: 닉네임)
                // Object profileObj = kakaoAccount.get("profile");
                // if (profileObj instanceof Map) {
                //     Map<?, ?> profile = (Map<?, ?>) profileObj;
                //     userProfile.put("nickname", profile.get("nickname"));
                // }
            } else if (responseBody.containsKey("kakao_account") && kakaoAccountObj == null) {
                // kakao_account 필드는 있는데 null인 경우 (예: 이메일 동의 안함)
                System.out.println("kakao_account 정보가 null 입니다. 사용자가 관련 정보 제공에 동의하지 않았을 수 있습니다.");
            }


            System.out.println("카카오 사용자 정보 조회 성공: " + userProfile);
            return userProfile;

        } catch (Exception e) {
            System.err.println("getKakaoUserProfile 메소드 내에서 예외 발생: " + e.getMessage());
            // e.printStackTrace(); // 상세 스택 트레이스
            throw new RuntimeException("카카오 사용자 프로필 조회 중 심각한 오류 발생.", e);
        }
    }

    public String fetchEmail(String at) {
        Map<?, ?> body = web.get().uri("https://kapi.kakao.com/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + at)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return (String) ((Map<?, ?>) body.get("kakao_account")).get("email");
    }
}
