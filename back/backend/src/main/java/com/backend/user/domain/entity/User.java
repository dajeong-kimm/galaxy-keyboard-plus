package com.backend.user.domain.entity;

import com.backend.alarm.domain.entity.FcmToken;
import com.backend.image.domain.entity.Image;
import com.backend.plan.domain.entity.Plan;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "\"user\"") // PostgreSQL에서 예약어는 큰따옴표로 감싸야 함
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class User {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "info_count", nullable = false)
    private int infoCount = 0;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Image> images;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Plan> plans;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private FcmToken fcmToken;

    public void updateInfoCount(int infoCount) {
        this.infoCount = infoCount;
    }
}
