package com.moca.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionCheckServiceImpl implements ConnectionCheckService {

    private final DatabaseClient databaseClient;
    private final ReactiveRedisConnectionFactory redisConnectionFactory;

    @Override
    public Mono<Map<String, Object>> checkConnections() {
        Mono<String> postgresStatus = databaseClient.sql("SELECT 1")
            .fetch().first()
            .map(r -> "UP")
            .onErrorReturn("DOWN");

        Mono<String> redisStatus = redisConnectionFactory.getReactiveConnection()
            .ping()
            .map(p -> "UP")
            .onErrorReturn("DOWN");

        return Mono.zip(postgresStatus, redisStatus)
            .map(tuple -> {
                Map<String, Object> result = new HashMap<>();
                result.put("postgres", tuple.getT1());
                result.put("redis", tuple.getT2());
                result.put("timestamp", LocalDateTime.now().toString());
                return result;
            });
    }

}
