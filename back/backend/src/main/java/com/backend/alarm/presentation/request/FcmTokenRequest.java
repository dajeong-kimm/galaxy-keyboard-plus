package com.backend.alarm.presentation.request;

import lombok.Getter;

import java.util.UUID;

@Getter
public class FcmTokenRequest {
    private UUID userId;
    private String token;
}
