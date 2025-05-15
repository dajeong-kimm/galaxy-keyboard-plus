package com.backend.global.config.async;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);  // 최소 4개의 스레드
        executor.setMaxPoolSize(8);   // 최대 8개의 스레드 (CPU 코어 수의 2배)
        executor.setQueueCapacity(50); // 대기 큐 크기 50
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}
