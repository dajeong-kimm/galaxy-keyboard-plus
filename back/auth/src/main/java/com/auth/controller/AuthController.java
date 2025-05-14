package com.auth.controller;

import com.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao/login")
    @Operation(
            summary = "카카오 로그인",
            description = "카카오 SDK Access Token을 받아 JWT Access/Refresh Token 및 사용자 ID를 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Map.class))),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
                    @ApiResponse(responseCode = "401", description = "로그인 실패")
            }
    )
    public ResponseEntity<Map<String, Object>> kakaoLoginWithSdkToken(@RequestBody Map<String, String> payload) {
        String kakaoAccessToken = payload.get("kakaoAccessToken");
        System.out.println("카카오 SDK Access Token (모바일): " + kakaoAccessToken);

        if (kakaoAccessToken == null || kakaoAccessToken.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Kakao Access Token is missing or empty."));
        }

        try {
            Map<String, Object> tokensAndUserId = authService.loginWithKakaoAccessToken(kakaoAccessToken); // AuthService의 새 메소드 호출
            return ResponseEntity.ok(tokensAndUserId);
        } catch (Exception e) {
            e.printStackTrace(); // 서버 로그
            // 실제로는 예외 유형에 따라 다른 메시지나 상태 코드 반환
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // 예: 토큰이 유효하지 않거나 사용자 정보 조회 실패 시
                    .body(Map.of("error", "Login failed with Kakao Access Token: " + e.getMessage()));
        }
    }

    /**
     * AT 재발급
     */
    @PostMapping("/reissue")
    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token을 받아 새로운 Access Token을 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "재발급 성공",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Map.class))),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
                    @ApiResponse(responseCode = "401", description = "재발급 실패")
            }
    )
    public ResponseEntity<Map<String, String>> reissue(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is missing or empty."));
        }
        try {
            return ResponseEntity.ok(authService.reissue(refreshToken));
        } catch (Exception e) {
            e.printStackTrace(); // 서버 로그
            // 실제로는 예외 유형에 따라 다른 상태 코드(예: 401 Unauthorized) 반환 고려
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token reissue failed: " + e.getMessage()));
        }
    }

    /**
     * 회원 탈퇴 API (현재 인증된 사용자 본인)
     * 요청 시 헤더에 유효한 Access Token (JWT)이 포함되어야 합니다.
     *
     * @return 성공 또는 실패 메시지
     */
    @DeleteMapping("/withdraw")
    @Operation(
            summary = "회원 탈퇴",
            description = "현재 인증된 사용자를 탈퇴 처리합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
                    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
                    @ApiResponse(responseCode = "500", description = "서버 오류")
            }
    )
    public ResponseEntity<Map<String, Object>> withdrawUser() {
        try {
            // 1. SecurityContext에서 인증된 사용자 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // 인증 정보가 없거나, 인증되지 않았거나, 익명 사용자인 경우 확인
            if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null || "anonymousUser".equals(authentication.getPrincipal())) {
                // Spring Security 필터에서 401 Unauthorized로 처리되는 것이 일반적이나, 방어적으로 추가
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "인증되지 않은 사용자입니다."));
            }

            // 2. 사용자 ID 추출
            // JwtAuthenticationFilter에서 Authentication 객체를 생성할 때,
            // Principal의 이름(authentication.getName())에 사용자 ID(Long 타입의 문자열)를 저장했다고 가정합니다.
            // 이는 JwtTokenProvider.parse()가 반환하고 필터가 설정하는 방식에 따라 달라질 수 있습니다.
            String userIdString = authentication.getName();
            Long userId = Long.parseLong(userIdString); // 문자열 ID를 Long으로 변환

            System.out.println("회원 탈퇴 API 호출됨: userId=" + userId);

            // 3. AuthService 호출하여 사용자 삭제 로직 수행
            authService.withdrawUser(userId);

            // 4. 성공 응답 반환
            return ResponseEntity.ok(Map.of("message", "회원 탈퇴가 성공적으로 처리되었습니다."));

        } catch (NumberFormatException e) {
            // 사용자 ID 파싱 실패 시 (Authentication 객체 구성 오류 가능성)
            System.err.println("회원 탈퇴 실패: 사용자 ID 파싱 오류 - " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "사용자 ID 처리 중 오류가 발생했습니다."));
        } catch (RuntimeException e) {
            // AuthService에서 사용자를 찾지 못했거나 DB 오류 등
            System.err.println("회원 탈퇴 실패: " + e.getMessage());
            e.printStackTrace();
            // 클라이언트에게는 좀 더 일반적인 메시지 전달 고려
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "회원 탈퇴 처리 중 오류가 발생했습니다: " + e.getMessage()));
        } catch (Exception e) {
            // 기타 예외 처리
            System.err.println("회원 탈퇴 중 예상치 못한 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "회원 탈퇴 중 예상치 못한 오류가 발생했습니다."));
        }
    }
}
