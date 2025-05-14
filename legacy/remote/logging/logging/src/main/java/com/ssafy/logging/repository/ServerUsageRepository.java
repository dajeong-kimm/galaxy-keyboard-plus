package com.ssafy.logging.repository;

import com.ssafy.logging.domain.ServerUsageLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface ServerUsageRepository
    extends ReactiveMongoRepository<ServerUsageLog, String> {
  Flux<ServerUsageLog> findByTimestampBetween(Instant start, Instant end);
}
