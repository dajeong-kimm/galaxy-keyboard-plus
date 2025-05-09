package com.backend.plan.domain.entity;

import com.backend.image.domain.entity.Image;
import com.backend.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plan")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Plan {
    @Id
    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "plan_time", nullable = false)
    private LocalDateTime planTime;

    @Column(name = "plan_content")
    private String planContent;

    @ManyToOne
    @JoinColumn(name = "image_id")
    private Image image;

    @Column(name = "alarm_tf")
    private boolean alarmTf = true;

    // 이미지 즐겨찾기 등록/해제
    public void setAlarmTf(Boolean alarmTf) {
        this.alarmTf = alarmTf;
    }
}
