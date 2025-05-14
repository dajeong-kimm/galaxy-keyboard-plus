package com.moca.auth.service;

import java.util.Map;

import reactor.core.publisher.Mono;

public interface ConnectionCheckService {
    Mono<Map<String, Object>> checkConnections();
}

