package com.backend.plan.presentation.response;

import com.backend.plan.application.out.ImageInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlanDetailResponse {
    private UUID planId;
    private LocalDateTime planTime;
    private String planContent;
    private ImageInfo image;
    private Boolean alarmTF;
}
