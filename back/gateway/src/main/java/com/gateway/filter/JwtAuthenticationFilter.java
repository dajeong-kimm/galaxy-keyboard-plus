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
    private static final List<String> PROTECTED_PATHS = List.of("/api","/rag", "/search");


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // OPTIONS 메서드는 인증 체크 없이 통과
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }
        
        // 1. 특정 헤더로 인증 우회
        String path = exchange.getRequest().getURI().getPath();
        String bypass = exchange.getRequest().getHeaders().getFirst("X-BYPASS-ADMIN");

        if (("adminadmin".equals(bypass)) && (
                path.startsWith("/search") ||
                        path.startsWith("/rag/upload-image-keyword")
        )) {
            // 인증 우회 (프록시만)
            return chain.filter(exchange);
        }

        // (아래는 기존 코드 그대로)
        // 보호 경로 확인
        boolean requiresAuth = PROTECTED_PATHS.stream().anyMatch(path::startsWith);
        if (!requiresAuth) return chain.filter(exchange);

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

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            logger.warn("🔒 인증 실패: {}", e.getMessage());
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }


    @Override
    public int getOrder() {
        return 0;
    }
}
