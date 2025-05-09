package com.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GeneralLoggingWebFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(GeneralLoggingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod() != null
            ? exchange.getRequest().getMethod().name()
            : "UNKNOWN";

        logger.info("[🔎 WEB REQUEST] {} {}", method, path);

        return chain.filter(exchange).doFinally(signal -> {
            int statusCode = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;
            logger.info("[🔚 WEB RESPONSE] {} {} → {}", method, path, statusCode);
        });
    }
}
