package com.backend.plan.presentation.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavePlanRequest {
    @NotNull
    private UUID userId;
    @NotNull
    private String planTime;
    private String planContent;
    @NotNull
    private UUID imageId;
}
