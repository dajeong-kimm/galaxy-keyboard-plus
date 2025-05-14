package com.backend.image.presentation.controller;

import com.backend.global.common.response.BaseResponse;
import com.backend.image.application.in.DeleteImagesInDto;
import com.backend.image.application.in.SaveImageInDto;
import com.backend.image.application.out.ImageCheckOutDto;
import com.backend.image.application.out.ImageOutDto;
import com.backend.image.application.out.ImageThumbnailOutDto;
import com.backend.image.application.out.SaveImageOutDto;
import com.backend.image.application.service.ImageService;
import com.backend.image.presentation.request.DeleteImagesRequest;
import com.backend.image.presentation.request.SaveImageRequest;
import com.backend.image.presentation.response.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {
    private final ImageService imageService;
    private final ModelMapper modelMapper;

    @PostMapping
    public BaseResponse<SaveImageResponse> saveImage(@RequestBody SaveImageRequest request) {

        SaveImageInDto inDto = modelMapper.map(request, SaveImageInDto.class);
        SaveImageOutDto outDto = imageService.saveImage(inDto);
        SaveImageResponse response = modelMapper.map(outDto, SaveImageResponse.class);
        return new BaseResponse<>(response);
    }

    @GetMapping
    public BaseResponse<ImageListResponse> getAllImages(@RequestParam UUID userId, @RequestParam(defaultValue = "0") int page) {
        int pageSize = 30;
        Pageable pageable = PageRequest.of(page, pageSize);

        Page<ImageThumbnailOutDto> images = imageService.getAllImages(userId, pageable);

        ImageListResponse response = new ImageListResponse(
                images.getContent(),
                images.getTotalPages(),
                images.getTotalElements(),
                images.getNumber(),
                images.isLast()
        );
        return new BaseResponse<>(response);
    }

    @GetMapping("/{imageId}")
    public BaseResponse<ImageResponse> getImageById(@PathVariable UUID imageId) {
        ImageOutDto outDto = imageService.getImageById(imageId);
        ImageResponse response = modelMapper.map(outDto, ImageResponse.class);
        return new BaseResponse<>(response);
    }

    @PostMapping("/delete-multiple")
    public BaseResponse<DeleteMultipleResponse> deleteMultiple(@RequestBody DeleteImagesRequest request) {
        DeleteImagesInDto inDto = modelMapper.map(request, DeleteImagesInDto.class);
        List<UUID> failedIds = imageService.deleteImages(inDto);

        DeleteMultipleResponse response =
                new DeleteMultipleResponse(request.getImageIds().size()-failedIds.size(), failedIds.size(), failedIds);
        return new BaseResponse<>(response);
    }

    @DeleteMapping("/{imageId}")
    public BaseResponse<Void> deleteImage(@PathVariable UUID imageId) {
        imageService.deleteImage(imageId);
        return new BaseResponse<>();
    }

    @GetMapping("/star/preview")
    public BaseResponse<StarPreviewResponse> getStarPreview(@RequestParam UUID userId) {
        List<ImageThumbnailOutDto> images = imageService.getStarPreview(userId);
        StarPreviewResponse response = new StarPreviewResponse(images);
        return new BaseResponse<>(response);
    }

    @GetMapping("/star")
    public BaseResponse<ImageListResponse> getStarredImages(@RequestParam UUID userId, @RequestParam(defaultValue = "0") int page) {
        int pageSize = 30;
        Pageable pageable = PageRequest.of(page, pageSize);

        Page<ImageThumbnailOutDto> images = imageService.getStarredImages(userId, pageable);

        ImageListResponse response = new ImageListResponse(
                images.getContent(),  // 이미지 목록
                images.getTotalPages(), // 총 페이지 수
                images.getTotalElements(), // 총 이미지 개수
                images.getNumber(), // 현재 페이지 번호
                images.isLast() // 마지막 페이지 여부
        );

        return new BaseResponse<>(response);
    }

    @PostMapping("/{imageId}/star")
    public BaseResponse<Void> starImage(@PathVariable UUID imageId) {
        imageService.starImage(imageId);
        return new BaseResponse<>();
    }

    @PostMapping("/{imageId}/unstar")
    public BaseResponse<Void> unstarImage(@PathVariable UUID imageId) {
        imageService.unstarImage(imageId);
        return new BaseResponse<>();
    }

    @GetMapping("/check")
    public BaseResponse<ImageCheckResponse> checkImageExists(
            @RequestParam UUID userId,
            @RequestParam String accessId) {

        ImageCheckOutDto exists = imageService.imageExist(userId, accessId);

        ImageCheckResponse response = modelMapper.map(exists, ImageCheckResponse.class);
        return new BaseResponse<>(response);
    }
}
