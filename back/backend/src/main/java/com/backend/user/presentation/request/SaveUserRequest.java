package com.backend.user.presentation.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SaveUserRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private Integer infoCount;
}
