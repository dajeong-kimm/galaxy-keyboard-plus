package com.backend.chat.application.out;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseOutDto {
    private UUID responseId;
    private String accessId;
    private String text;
}
