package com.auth.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long at;
    private final long rt;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity}") long at,
            @Value("${jwt.refresh-token-validity}") long rt) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.at = at;
        this.rt = rt;
    }

    private String create(UUID uid, long ms) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(uid.toString())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ms))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String access(UUID id) {
        return create(id, at);
    }

    public String refresh(UUID id) {
        return create(id, rt);
    }

    public UUID parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String subject = claims.getSubject();
            if (subject == null) {
                throw new JwtException("토큰에 사용자 ID(subject)가 없습니다.");
            }

            return UUID.fromString(subject);

        } catch (JwtException | IllegalArgumentException e) {
            logger.error("❌ JWT 파싱 또는 검증 실패: {}", e.getMessage());
            throw new RuntimeException("유효하지 않은 토큰이거나 사용자 ID 파싱에 실패했습니다.", e);
        }
    }
}
