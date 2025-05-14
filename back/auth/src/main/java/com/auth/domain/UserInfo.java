package com.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_info")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfo {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false) // id는 DB에서 생성되므로 앱에서 업데이트하지 않음
    private UUID id;

    @Column(name = "kakao_email", nullable = false, unique = true)
    private String kakaoEmail;

}
