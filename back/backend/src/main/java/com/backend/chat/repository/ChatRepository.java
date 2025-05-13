package com.backend.chat.repository;


import com.backend.chat.domain.entity.Chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Page<Chat> findByUser_UserIdOrderByChatTimeDesc(UUID userId, Pageable pageable);
}
