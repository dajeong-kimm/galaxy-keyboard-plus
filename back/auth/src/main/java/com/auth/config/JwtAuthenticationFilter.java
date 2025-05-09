package com.auth.config;

import com.auth.domain.UserInfo;
import com.auth.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwt;
    private final UserInfoRepository userRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String path = req.getServletPath();
        if (path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars")
                || path.equals("/swagger-ui.html")) {
            chain.doFilter(req, res);
            return;
        }

        String token = resolveToken(req); // 헤더에서 Bearer 토큰 추출

        if (token != null) { // 토큰이 존재하면
            try {
                // 1. 토큰 파싱 및 검증 -> 사용자 ID (Long) 획득
                // jwt.parse() 내부에서 만료, 서명 등 기본 검증도 수행한다고 가정
                UUID userId = jwt.parse(token); // 반환 타입이 Long인지 확인


                // 2. Authentication 객체 생성 (Principal로 Long 타입 userId 사용)
                //    (여기서는 간단히 ROLE_USER 권한 부여, 필요시 DB 조회 후 실제 권한 설정 가능)
                List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userId, // <<< Principal: Long 타입의 사용자 ID
                        null,   // <<< Credentials: 사용 안 함 (null)
                        authorities // <<< Authorities
                );

                // 3. SecurityContext에 Authentication 객체 설정
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // 토큰 파싱/검증 실패 시 SecurityContext를 클리어하는 것이 중요
                SecurityContextHolder.clearContext();
                // 여기서 직접 에러 응답을 보내기보다, 이후 ExceptionTranslationFilter 등에서 처리하도록 둘 수 있음
                // 또는 특정 에러 응답 필요시 res.sendError() 사용 가능
            }
        } else {
            // log.trace("요청 헤더에 JWT 없음"); // 필요시 로깅 레벨 조정
        }

        chain.doFilter(req, res); // 다음 필터 호출
    }

    // Request Header에서 토큰 정보 추출 ("Bearer " 제거)
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
