package com.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getPath().toString();
        String method = exchange.getRequest().getMethod() != null
            ? exchange.getRequest().getMethod().name()
            : "UNKNOWN";
        String clientIp = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");

        logger.info("[🌐 REQUEST] {} {} from {}", method, requestPath, clientIp != null ? clientIp : "unknown");

        return chain.filter(exchange)
            .doOnError(error -> {
                logger.error("[⛔ ERROR] {} {} → {}", method, requestPath, error.getMessage());
            })
            .doFinally(signalType -> {
                ServerHttpResponse response = exchange.getResponse();
                int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;
                logger.info("[📤 RESPONSE] {} {} → {}", method, requestPath, statusCode);
            });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
