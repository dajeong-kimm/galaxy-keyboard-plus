package com.moca.auth.controller;

import com.moca.auth.service.ConnectionCheckService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final ConnectionCheckService connectionCheckService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("service", "auth");
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(healthInfo);
    }

    @GetMapping("/connections")
    public Mono<ResponseEntity<Map<String, Object>>> checkConnections() {
        return connectionCheckService.checkConnections()
            .map(result -> {
                result.put("service", "auth");
                return ResponseEntity.ok(result);
            });
    }

}
