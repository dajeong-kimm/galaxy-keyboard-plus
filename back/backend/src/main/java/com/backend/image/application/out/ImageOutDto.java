package com.backend.image.application.out;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ImageOutDto {
    private UUID imageId;
    private String accessId;
    private LocalDateTime imageTime;
    private Boolean star;
    private String content;
}
