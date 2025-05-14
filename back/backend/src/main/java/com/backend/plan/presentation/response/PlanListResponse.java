package com.backend.plan.presentation.response;

import com.backend.plan.application.out.PlanThumbnailOutDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlanListResponse {
    private List<PlanThumbnailOutDto> plans;
    private int totalPages;
    private long totalElements;
    private int currentPage;
    private boolean isLast;
}
