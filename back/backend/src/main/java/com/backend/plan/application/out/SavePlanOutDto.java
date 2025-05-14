package com.backend.plan.application.out;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavePlanOutDto {
    private UUID planId;
    private LocalDateTime planTime;
    private String planContent;
    private UUID imageId;
    private String imageAccessId;
}
