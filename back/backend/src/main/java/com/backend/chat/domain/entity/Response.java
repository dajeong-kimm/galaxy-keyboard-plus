package com.backend.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "response")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Response {
    @Id
    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "access_id", nullable = false, length = 255)
    private String accessId;

    @Column(name = "text", nullable = false)
    private String text;  // 응답 내용

    @ManyToOne
    @JoinColumn(name = "chat_id")
    private Chat chat;

    public void setChat(Chat chat){
        this.chat=chat;
    }


}
