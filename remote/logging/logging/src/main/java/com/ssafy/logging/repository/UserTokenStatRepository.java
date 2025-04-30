// repository/UserTokenStatRepository.java
package com.ssafy.logging.repository;

import com.ssafy.logging.domain.UserTokenStat;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface UserTokenStatRepository 
    extends ReactiveMongoRepository<UserTokenStat, String> { }
