package com.backend.plan.presentation.controller;

import com.backend.global.common.response.BaseResponse;
import com.backend.plan.application.in.PlanAlarmToggleInDto;
import com.backend.plan.application.in.SavePlanInDto;
import com.backend.plan.application.out.PlanDetailOutDto;
import com.backend.plan.application.out.PlanThumbnailOutDto;
import com.backend.plan.application.service.PlanService;
import com.backend.plan.presentation.request.PlanAlarmToggleRequest;
import com.backend.plan.presentation.request.SavePlanRequest;
import com.backend.plan.presentation.response.PlanDetailResponse;
import com.backend.plan.presentation.response.PlanListResponse;
import com.backend.plan.presentation.response.PlanPreviewResponse;
import com.backend.plan.presentation.response.SavePlanResponse;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    private final ModelMapper modelMapper;


    @PostMapping
    public BaseResponse<SavePlanResponse> savePlan(@RequestBody SavePlanRequest request) {
        SavePlanInDto inDto = modelMapper.map(request, SavePlanInDto.class);
        SavePlanResponse response = modelMapper.map(planService.savePlan(inDto), SavePlanResponse.class);

        return new BaseResponse<>(response);
    }

    @GetMapping("/preview")
    public BaseResponse<PlanPreviewResponse> getPlanPreview(@RequestParam UUID userId) {
        List<PlanThumbnailOutDto> plans = planService.getPlanPreview(userId);
        PlanPreviewResponse response = new PlanPreviewResponse(plans);
        return new BaseResponse<>(response);
    }

    @GetMapping
    public BaseResponse<PlanListResponse> getPlanList(@RequestParam UUID userId, @RequestParam(defaultValue = "0") int page){
        int pageSize = 30;
        Pageable pageable = PageRequest.of(page, pageSize);

        Page<PlanThumbnailOutDto> plans = planService.getPlanList(userId, pageable);

        PlanListResponse response = new PlanListResponse(
                plans.getContent(),  // 이미지 목록
                plans.getTotalPages(), // 총 페이지 수
                plans.getTotalElements(), // 총 이미지 개수
                plans.getNumber(), // 현재 페이지 번호
                plans.isLast() // 마지막 페이지 여부
        );

        return new BaseResponse<>(response);
    }


    @GetMapping("/{planId}")
    public BaseResponse<PlanDetailResponse> getPlanDetail(@PathVariable UUID planId) {
        PlanDetailOutDto outDto = planService.getPlanDetail(planId);
        PlanDetailResponse response = modelMapper.map(outDto, PlanDetailResponse.class);
        return new BaseResponse<>(response);
    }

    @DeleteMapping("/{planId}")
    public BaseResponse<Void> deletePlan(@PathVariable UUID planId) {
        planService.deletePlan(planId);
        return new BaseResponse<>();
    }

    @PostMapping("/{planId}/alarm")
    public BaseResponse<Void> togglePlanAlarm(
            @PathVariable UUID planId,
            @RequestBody PlanAlarmToggleRequest request
    ) {
        PlanAlarmToggleInDto inDto = new PlanAlarmToggleInDto(planId, request.isAlarmTf());

        planService.updateAlarmTF(inDto);
        return new BaseResponse<>();
    }
}
