package com.ssafy.logging.repository;

import com.ssafy.logging.domain.WeeklyTokenStat;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface WeeklyTokenStatRepository
    extends ReactiveMongoRepository<WeeklyTokenStat, String> {
  Flux<WeeklyTokenStat> findByWeekStart(String weekStart);
}