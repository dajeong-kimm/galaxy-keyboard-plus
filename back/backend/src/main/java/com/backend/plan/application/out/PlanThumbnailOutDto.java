package com.backend.plan.application.out;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlanThumbnailOutDto {
    private Boolean alarmTF;
    private LocalDateTime planTime;
    private UUID imageId;
    private String accessId;
    private Boolean star;
}
