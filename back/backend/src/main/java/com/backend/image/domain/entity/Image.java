package com.backend.image.domain.entity;

import com.backend.image.domain.converter.ImageTypeConverter;
import com.backend.image.domain.type.ImageType;
import com.backend.plan.domain.entity.Plan;
import com.backend.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "image")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Image {
    @Id
    @Column(name = "image_id", nullable = false)
    private UUID imageId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "access_id", nullable = false, length = 255)
    private String accessId;

    @Column(name = "image_time", nullable = false)
    private LocalDateTime imageTime;

    @Column(name = "upload_time", nullable = false)
    private LocalDateTime uploadTime;

    @Convert(converter = ImageTypeConverter.class)
    @Column(name = "type", nullable = false)
    private ImageType type;

    @Column(name = "star")
    private boolean star = false;

    @Column(name = "content")
    private String content;

    @OneToMany(mappedBy = "image")
    private List<Plan> plans;


    @PrePersist
    public void prePersist() {

        this.uploadTime = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0).toLocalDateTime();
    }

    // 이미지 즐겨찾기 등록/해제
    public void setStar(Boolean star) {
        this.star = star;
    }
}
