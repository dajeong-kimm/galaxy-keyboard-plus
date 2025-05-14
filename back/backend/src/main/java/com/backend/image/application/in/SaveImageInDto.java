package com.backend.image.application.in;

import com.backend.image.domain.type.ImageType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SaveImageInDto {
    private UUID userId;
    private String accessId;
    private String imageTime;
    private ImageType type;
    private String content;
}
