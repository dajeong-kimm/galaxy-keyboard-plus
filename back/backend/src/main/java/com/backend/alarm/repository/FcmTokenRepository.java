package com.backend.alarm.repository;

import com.backend.alarm.domain.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {
    Optional<FcmToken> findByUser_UserId(UUID userId);
    Optional<FcmToken> findByToken(String token);

}
