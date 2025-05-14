package com.ssafy.logging.repository;

import com.ssafy.logging.domain.ServerRanking;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ServerRankingRepository
    extends ReactiveMongoRepository<ServerRanking, String> {
  Flux<ServerRanking> findByPeriodTypeAndPeriodKeyOrderByRank(
      String periodType, String periodKey);
}