package com.backend.plan.presentation.response;

import com.backend.image.domain.entity.Image;
import com.backend.user.domain.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavePlanResponse {
    private UUID planId;
    private LocalDateTime planTime;
    private String planContent;
    private UUID imageId;
    private String imageAccessId;
}
