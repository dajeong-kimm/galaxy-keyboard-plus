package com.backend.alarm.domain.entity;

import com.backend.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "fcm_token")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class FcmToken {
    @Id
    @Column(name = "fcm_token_id")
    private UUID fcmTokenId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    public void changeToken(String token){
        this.token = token;
    }

    public void changeUser(User user){
        this.user = user;
    }

}
