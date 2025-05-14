package com.backend.plan.application.in;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavePlanInDto {
    private UUID userId;
    private String planTime;
    private String planContent;
    private UUID imageId;
}
