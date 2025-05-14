package com.backend.image.presentation.request;

import com.backend.image.domain.type.ImageType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SaveImageRequest {
    @NotNull
    private UUID userId;
    @NotNull
    private String accessId;

    private String imageTime;
    private ImageType type;
    private String content;
}
