package com.backend.alarm.presentation.controller;

import com.backend.alarm.application.in.FcmTokenInDto;
import com.backend.alarm.application.service.AlarmService;
import com.backend.alarm.presentation.request.FcmTokenRequest;
import com.backend.global.common.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alarms")
public class AlarmController {
    private final AlarmService alarmService;
    private final ModelMapper modelMapper;

    @PostMapping("/token")
    public BaseResponse<Void> saveFcmToken(@Valid @RequestBody FcmTokenRequest request) {

        FcmTokenInDto inDto = modelMapper.map(request, FcmTokenInDto.class);

        alarmService.saveFcmToken(inDto);

        return new BaseResponse<>();
    }





}
