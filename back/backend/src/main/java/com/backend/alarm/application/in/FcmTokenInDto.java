package com.backend.alarm.application.in;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenInDto {
    private UUID userId;
    private String fcmToken;

}
