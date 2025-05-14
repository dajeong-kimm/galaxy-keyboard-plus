package com.auth.repository;

import com.auth.domain.AuthTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, UUID> {}