package com.backend.image.presentation.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponse {
    private UUID imageId;
    private String accessId;
    private LocalDateTime imageTime;
    private Boolean star;
    private String content;
}
