// src/main/java/com/gateway/config/RagRateLimitConfig.java
package com.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import reactor.core.publisher.Mono;

@Configuration
public class RagRateLimitConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * Reactive API용 RedisConnectionFactory 빈 등록
     * – application.yml의 host/port를 사용
     * – @Primary로 지정하여 자동 구성 빈보다 우선적으로 주입되게 함
     */
    @Bean
    @Primary
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisHost, redisPort);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * Gateway RequestRateLimiter 빈
     * – ReactiveRedisConnectionFactory를 내부에서 사용
     */
    @Bean("ragServerRateLimiter")
    public RedisRateLimiter ragServerRateLimiter() {
        // 분당 1토큰, 버스트 최대10토큰, 요청당 1토큰 소모
        return new RedisRateLimiter(17, 1000, 1);
    }

    /**
     * 사용자별 KeyResolver (JWT or IP)
     */
    @Bean("userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                return Mono.just("rag-user:" + token.hashCode());
            }
            var remote = exchange.getRequest().getRemoteAddress();
            String key = (remote != null)
                ? "rag-ip:" + remote.getAddress().getHostAddress()
                : "rag-anonymous";
            return Mono.just(key);
        };
    }
}
