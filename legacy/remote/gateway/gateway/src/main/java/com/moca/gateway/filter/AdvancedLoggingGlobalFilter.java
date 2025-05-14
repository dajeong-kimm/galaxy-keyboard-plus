package com.moca.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.core.io.buffer.DataBufferUtils;
import java.nio.charset.StandardCharsets;

import reactor.core.publisher.Mono;

@Component
public class AdvancedLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedLoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().toString();
        String method = request.getMethod().name();
        
        logger.info("[Request] {} {}", method, requestPath);
        request.getHeaders().forEach((name, values) -> values.forEach(value -> {
            logger.info("[Request Header] {}: {}", name, value);
        }));

        return DataBufferUtils.join(request.getBody())
            .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
            .flatMap(dataBuffer -> {
                byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bodyBytes);
                DataBufferUtils.release(dataBuffer);

                String bodyString = new String(bodyBytes, StandardCharsets.UTF_8);
                if (!bodyString.isBlank()) {
                    logger.info("[Request Body] {}", bodyString);
                }

                // 새로운 body로 다시 요청 만들어서 넘기기
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("Content-Length", Integer.toString(bodyBytes.length))
                    .build();

                ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

                return chain.filter(mutatedExchange).then(
                    Mono.fromRunnable(() -> {
                        int statusCode = exchange.getResponse().getStatusCode() != null 
                            ? exchange.getResponse().getStatusCode().value() 
                            : 500;
                        logger.info("[Response] {} -> HTTP {}", requestPath, statusCode);
                        // (응답 Body까지 로깅하려면 서버쪽 지원이 필요함. 여기는 상태코드까지만!)
                    })
                );
            });
    }

    @Override
    public int getOrder() {
        return -2; // 높은 우선순위
    }
}
