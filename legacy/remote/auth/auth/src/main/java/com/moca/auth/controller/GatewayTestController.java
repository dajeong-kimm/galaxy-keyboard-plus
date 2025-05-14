package com.moca.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gateway와 Auth 서비스 간 라우팅 테스트를 위한 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/auth/test")
public class GatewayTestController {

    /**
     * Gateway -> Auth 서비스 라우팅 테스트를 위한 엔드포인트
     * 
     * @return 테스트 응답 데이터
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> pingTest() {
        log.info("Received ping request from Gateway at {}", LocalDateTime.now());
        
        Map<String, Object> response = new HashMap<>();
        response.put("service", "auth");
        response.put("status", "ok");
        response.put("message", "Successfully routed from Gateway to Auth service");
        response.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 요청 헤더 정보를 확인하기 위한 엔드포인트
     * 
     * @param headers 요청 헤더 정보
     * @return 헤더 정보를 포함한 응답
     */
    @GetMapping("/headers")
    public ResponseEntity<Map<String, Object>> checkHeaders(@RequestHeader Map<String, String> headers) {
        log.info("Received headers check request from Gateway at {}", LocalDateTime.now());
        
        Map<String, Object> response = new HashMap<>();
        response.put("service", "auth");
        response.put("headers", headers);
        response.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 요청 바디 데이터를 확인하기 위한 엔드포인트
     * 
     * @param requestBody 요청 바디 데이터
     * @return 요청 데이터를 포함한 응답
     */
    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echoRequest(@RequestBody(required = false) Map<String, Object> requestBody) {
        log.info("Received echo request from Gateway at {}", LocalDateTime.now());
        
        Map<String, Object> response = new HashMap<>();
        response.put("service", "auth");
        response.put("received_data", requestBody != null ? requestBody : "No request body");
        response.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
}
