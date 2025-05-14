package com.ssafy.logging.repository;

import com.ssafy.logging.domain.TokenUsageLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import java.time.Instant;

public interface TokenUsageRepository 
    extends ReactiveMongoRepository<TokenUsageLog, String> {
  Flux<TokenUsageLog> findByTimestampBetween(Instant start, Instant end);
}