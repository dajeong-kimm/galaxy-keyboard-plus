package com.backend.image.application.out;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SaveImageOutDto {
    private UUID imageId;
    private String accessId;
    private String content;
}
