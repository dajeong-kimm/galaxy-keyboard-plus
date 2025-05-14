package com.ssafy.logging.consumer;

import com.ssafy.logging.domain.ServerUsageLog;
import com.ssafy.logging.domain.TokenUsageLog;
import com.ssafy.logging.dto.ServerUsageEvent;
import com.ssafy.logging.dto.TokenUsageEvent;
import com.ssafy.logging.repository.ServerUsageRepository;
import com.ssafy.logging.repository.TokenUsageRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingConsumer {
    private final KafkaReceiver<String, TokenUsageEvent> tokenReceiver;
    private final KafkaReceiver<String, ServerUsageEvent> serverReceiver;
    private final TokenUsageRepository tokenRepo;
    private final ServerUsageRepository serverRepo;

    @PostConstruct
    public void start() {
        // Token Usage Events
        tokenReceiver.receive()
                .doOnNext(record ->
                        log.info("Received TokenUsageEvent: key={} value={}",
                                record.key(), record.value()))
                .map(record -> record.value())
                .map(evt -> {
                    TokenUsageLog logEntity = new TokenUsageLog();
                    logEntity.setUserId(evt.getUserId());
                    logEntity.setTokenCount(evt.getTokenCount());
                    logEntity.setTimestamp(Instant.ofEpochMilli(evt.getTimestamp()));
                    log.info("Mapped to TokenUsageLog: {}", logEntity);
                    return logEntity;
                })
                .flatMap(logEntity -> tokenRepo.save(logEntity)
                        .doOnNext(saved -> log.info("Saved TokenUsageLog with id={}", saved.getId())))
                .doOnError(e -> log.error("Error processing TokenUsageEvent stream", e))
                .subscribe();

        // Server Usage Events
        serverReceiver.receive()
                .doOnNext(record ->
                        log.info("Received ServerUsageEvent: key={} value={}",
                                record.key(), record.value()))
                .map(record -> record.value())
                .map(evt -> {
                    ServerUsageLog logEntity = new ServerUsageLog();
                    logEntity.setServerId(evt.getServerId());
                    logEntity.setTimestamp(Instant.ofEpochMilli(evt.getTimestamp()));
                    log.info("Mapped to ServerUsageLog: {}", logEntity);
                    return logEntity;
                })
                .flatMap(logEntity -> serverRepo.save(logEntity)
                        .doOnNext(saved -> log.info("Saved ServerUsageLog with id={}", saved.getId())))
                .doOnError(e -> log.error("Error processing ServerUsageEvent stream", e))
                .subscribe();
    }

}
