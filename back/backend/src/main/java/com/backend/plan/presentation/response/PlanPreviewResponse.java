package com.backend.plan.presentation.response;

import com.backend.plan.application.out.PlanThumbnailOutDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlanPreviewResponse {
    List<PlanThumbnailOutDto> plans;
}
