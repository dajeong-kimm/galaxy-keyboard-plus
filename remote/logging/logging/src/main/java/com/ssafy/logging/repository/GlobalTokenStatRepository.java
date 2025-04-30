// repository/GlobalTokenStatRepository.java
package com.ssafy.logging.repository;

import com.ssafy.logging.domain.GlobalTokenStat;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface GlobalTokenStatRepository 
    extends ReactiveMongoRepository<GlobalTokenStat, String> { }
