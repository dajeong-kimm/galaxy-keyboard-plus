package com.auth.service;

import com.auth.config.JwtTokenProvider;
import com.auth.domain.*;
import com.auth.oauth.KakaoAuthClient;
import com.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthClient kakao;
    private final UserInfoRepository userRepo;
    private final AuthTokenRepository tokenRepo;
    private final JwtTokenProvider jwt;
    private final WebClient webClient = WebClient.create(); // 기본 WebClient

    @Transactional // 트랜잭션 처리를 위해 추가
    public Map<String, Object> loginWithKakaoAccessToken(String kakaoAccessToken) {
        // 1. 카카오 Access Token으로 카카오 사용자 정보 조회
        Map<String, Object> kakaoUserProfile = kakao.getKakaoUserProfile(kakaoAccessToken);

        String email = (String) kakaoUserProfile.get("email");

        // DB 스키마상 kakao_email은 NOT NULL UNIQUE 이므로, 이메일이 없으면 진행 불가
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("카카오 계정에서 이메일 정보를 가져올 수 없습니다. 이메일 제공에 동의했는지 확인해주세요.");
        }

        // 2. DB에서 kakao_email을 기준으로 사용자 조회 또는 신규 생성
        UserInfo user = userRepo.findByKakaoEmail(email)
                .orElseGet(() -> {
                    UserInfo newUser = UserInfo.builder()
                            .id(UUID.randomUUID()) // 앱 내부용 새 UUID 생성
                            .kakaoEmail(email)     // 카카오 이메일 저장
                            .build();
                    UserInfo savedUser = userRepo.save(newUser);

                    try {
                        String uri = "http://backend-service:8083/api/v1/users";

                        // WebClient 비동기 호출 후 block()으로 동기 전환
                        webClient.post()
                                .uri(uri)
                                .bodyValue(Map.of(
                                        "userId", savedUser.getId().toString(),
                                        "infoCount", 0
                                ))
                                .retrieve()
                                .bodyToMono(Void.class)
                                .block(); // block()을 써야 트랜잭션 중 동기 실행 가능

                    } catch (Exception e) {
                        System.err.println("신규 사용자 백엔드 등록 실패: " + e.getMessage());
                        // throw new RuntimeException("User creation failed in backend-service");
                    }

                    return savedUser;
                });

        // 3. 자체 서비스 JWT (Access Token, Refresh Token) 생성
        //    user.getId()는 UUID 타입이므로, JwtTokenProvider가 UUID를 받거나 String으로 변환 필요
        String appAccessToken = jwt.access(user.getId()); // JwtTokenProvider 구현에 따라 달라질 수 있음
        String appRefreshToken = jwt.refresh(user.getId()); // JwtTokenProvider 구현에 따라 달라질 수 있음


        // 4. 자체 Refresh Token을 DB에 저장 또는 업데이트
        //    AuthTokenEntity의 ID는 UserInfo의 ID와 동일 (공유 PK)
        final String finalAppRefreshToken = appRefreshToken; // 람다 표현식 내에서 사용하기 위해 final 또는 effectively final로 선언
        tokenRepo.findById(user.getId())
                .ifPresentOrElse(
                        authTokenEntity -> {
                            authTokenEntity.updateRefreshToken(finalAppRefreshToken);
                            // @Transactional 어노테이션으로 인해 변경 감지(dirty checking)로 업데이트될 수 있으나,
                            // 명시적으로 save를 호출하는 것이 더 안전할 수도 있습니다 (JPA 구현체 및 설정에 따라 다름).
                            // tokenRepo.save(authTokenEntity); // 필요하다면 추가
                        },
                        () -> {
                            AuthTokenEntity newAuthTokenEntity = AuthTokenEntity.builder()
                                    // .id(user.getId()) // @MapsId를 사용하면 user 객체 설정 시 id 자동 매핑
                                    .user(user)
                                    .refreshToken(finalAppRefreshToken)
                                    .build();
                            tokenRepo.save(newAuthTokenEntity);
                        });
        // 반환할 맵 생성 및 데이터 추가
        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getId()); // Long 타입의 userId 추가
        response.put("accessToken", appAccessToken);
        response.put("refreshToken", appRefreshToken);

        return response;
//        return Map.of("accessToken", appAccessToken, "refreshToken", appRefreshToken);
    }

    @Transactional
    public Map<String,String> reissue(String oldRt) {
        // jwt.parse(oldRt)가 UUID 객체를 직접 반환하므로, 변수 타입을 UUID로 변경합니다.
        UUID uid = jwt.parse(oldRt); // 수정된 부분

        AuthTokenEntity token = tokenRepo.findById(uid)
                .orElseThrow(() -> new RuntimeException("Invalid RT: Refresh token not found in DB."));
        if (!token.getRefreshToken().equals(oldRt)) {
            throw new RuntimeException("RT mismatch: Provided refresh token does not match stored token.");
        }

        String newAt = jwt.access(uid);
        String newRt = jwt.refresh(uid);

        token.updateRefreshToken(newRt);
        return Map.of("accessToken", newAt, "refreshToken", newRt);
    }

    /**
     * 회원 탈퇴 처리 (현재 인증된 사용자 기준)
     * @param userId 탈퇴할 사용자의 ID
     * @throws RuntimeException 사용자를 찾을 수 없을 경우 (선택적 처리)
     */
    @Transactional
    public void withdrawUser(Long userId) {
        System.out.println("회원 탈퇴 요청 수신: userId=" + userId);

        // 1. 사용자 존재 여부 확인 (선택적: deleteById는 ID 없어도 오류 발생 안 함)
        boolean userExists = userRepo.existsById(userId);
        if (!userExists) {
            // 사용자가 존재하지 않는 경우, 에러를 던지거나 조용히 종료할 수 있음
            System.err.println("회원 탈퇴 실패: 사용자를 찾을 수 없습니다. userId=" + userId);
            throw new RuntimeException("User not found for withdrawal with ID: " + userId);
            // 또는 로깅만 하고 정상 종료 처리 return;
        }

        // 2. user_info 테이블에서 사용자 삭제
        // DB 스키마의 auth_token 테이블에 ON DELETE CASCADE 설정으로 인해
        // user_info 레코드가 삭제되면 auth_token 레코드도 자동으로 삭제됩니다.
        userRepo.deleteById(userId);
        System.out.println("사용자 정보 삭제 완료 (UserInfo, AuthToken 자동 삭제됨): userId=" + userId);

    }

}
