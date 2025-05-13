package com.backend.chat.domain.entity;

import com.backend.chat.domain.converter.SenderConverter;
import com.backend.chat.domain.type.Sender;
import com.backend.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Chat {
    @Id
    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = SenderConverter.class)
    @Column(name = "sender", nullable = false)
    private Sender sender;

    @Column(nullable = false)
    private LocalDateTime chatTime;

    @Column(name = "message", nullable = false)
    private String message;

    @Builder.Default
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Response> responses = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        // LocalDateTime에서 나노초 제거
        this.chatTime = LocalDateTime.now().withNano(0);  // nano 값 0으로 설정;
    }
    public void addResponse(Response response) {
        this.responses.add(response);
        response.setChat(this);
    }

}
