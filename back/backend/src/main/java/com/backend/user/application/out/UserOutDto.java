package com.backend.user.application.out;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserOutDto {
    private UUID userId;
    private Integer infoCount;
}
