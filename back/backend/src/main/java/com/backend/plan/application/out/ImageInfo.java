package com.backend.plan.application.out;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ImageInfo {
    private UUID imageId;
    private String accessId;
    private LocalDateTime imageTime;
    private boolean star = false;
    private String content;
}
