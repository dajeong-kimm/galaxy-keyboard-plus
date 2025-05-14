package com.backend.image.presentation.response;

import com.backend.image.application.out.ImageThumbnailOutDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ImageListResponse {
    private List<ImageThumbnailOutDto> images;
    private int totalPages;
    private long totalElements;
    private int currentPage;
    private boolean isLast;
}
