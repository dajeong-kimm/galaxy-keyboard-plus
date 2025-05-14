package com.backend.image.presentation.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SaveImageResponse {
    private UUID imageId;
    private String accessId;
    private String content;
}
