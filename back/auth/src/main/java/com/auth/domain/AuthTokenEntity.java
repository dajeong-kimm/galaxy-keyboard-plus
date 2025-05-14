package com.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity @Table(name = "auth_token")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthTokenEntity {

    @Id @Column(name = "user_id")
    private UUID userId;

    @MapsId @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserInfo user;

    @Column(name="refresh_token", columnDefinition = "TEXT", nullable = false)
    private String refreshToken;


    public void updateRefreshToken(String token) { this.refreshToken = token; }
}
