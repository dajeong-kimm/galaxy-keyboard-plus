package com.ssafy.logging.repository;

import com.ssafy.logging.domain.DailyTokenStat;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface DailyTokenStatRepository
    extends ReactiveMongoRepository<DailyTokenStat, String> {
  Flux<DailyTokenStat> findByDate(String date);
}