package com.gateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret}")
    private String secretKey;


    // 테스트용
    private static final List<String> PROTECTED_PATHS = List.of("/api","/rag");


    // private static final List<String> PROTECTED_PATHS = List.of("/api", "/rag");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 보호 경로 확인
        boolean requiresAuth = PROTECTED_PATHS.stream().anyMatch(path::startsWith);
        if (!requiresAuth) return chain.filter(exchange);

        // Authorization 헤더 확인
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null) {
            logger.warn("🔒 인증 실패: Authorization 헤더 없음");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (!authHeader.startsWith("Bearer ")) {
            logger.warn("🔒 인증 실패: Authorization 형식이 Bearer 아님 (값: {})", authHeader);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.getSubject();

            // 요청 헤더에 userId 추가
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            logger.warn("🔒 인증 실패: 토큰 만료됨 - {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("🔒 인증 실패: 지원하지 않는 토큰 - {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.warn("🔒 인증 실패: 잘못된 형식의 토큰 - {}", e.getMessage());
        } catch (SignatureException e) {
            logger.warn("🔒 인증 실패: 서명 검증 실패 - {}", e.getMessage());
        } catch (JwtException e) {
            logger.warn("🔒 인증 실패: JWT 처리 오류 - {}", e.getMessage());
        } catch (Exception e) {
            logger.error("🔒 인증 실패: 예기치 않은 오류 - {}", e.getMessage(), e);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
