package com.backend.user.application.in;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserSaveInDto {
    private UUID userId;
    private Integer infoCount;
}
