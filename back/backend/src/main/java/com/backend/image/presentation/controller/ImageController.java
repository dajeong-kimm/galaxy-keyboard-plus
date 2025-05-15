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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {
    private final ImageService imageService;
    private final ModelMapper modelMapper;

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    @PostMapping
    public CompletableFuture<BaseResponse<SaveImageResponse>> saveImage(@RequestBody SaveImageRequest request) {
        // 비동기적으로 이미지 저장
        SaveImageInDto inDto = modelMapper.map(request, SaveImageInDto.class);
        return imageService.saveImage(inDto)
                .exceptionally(ex -> {
                    // 예외 처리
                    log.error("이미지 저장 중 오류 발생: {}", ex.getMessage());
                    return null;  // 실패 시 null 반환 또는 기본값 설정
                })
                .thenApply(outDto -> {
                    if (outDto == null) {
                        // 실패 처리
                        return new BaseResponse<>(null);
                    }
                    SaveImageResponse response = modelMapper.map(outDto, SaveImageResponse.class);
                    return new BaseResponse<>(response);
                });
    }

    @GetMapping
    public CompletableFuture<BaseResponse<ImageListResponse>> getAllImages(
            @RequestParam UUID userId, @RequestParam(defaultValue = "0") int page) {

        int pageSize = 30;
        Pageable pageable = PageRequest.of(page, pageSize);

        return imageService.getAllImages(userId, pageable)
                .exceptionally(ex -> {
                    // 예외 처리
                    log.error("이미지 목록 조회 중 오류 발생: {}", ex.getMessage());
                    return Page.empty();  // 예외 발생 시 빈 페이지 반환
                })
                .thenApply(images -> {
                    ImageListResponse response = new ImageListResponse(
                            images.getContent(),
                            images.getTotalPages(),
                            images.getTotalElements(),
                            images.getNumber(),
                            images.isLast()
                    );
                    return new BaseResponse<>(response);
                });
    }

    @GetMapping("/{imageId}")
    public CompletableFuture<BaseResponse<ImageResponse>> getImageById(@PathVariable UUID imageId) {
        return imageService.getImageById(imageId)
                .exceptionally(ex -> {
                    // 예외 처리
                    log.error("이미지 조회 중 오류 발생: {}", ex.getMessage());
                    return null;  // 예외 발생 시 null 반환 또는 기본값 설정
                })
                .thenApply(outDto -> {
                    if (outDto == null) {
                        // 실패 처리
                        return new BaseResponse<>(null);
                    }
                    ImageResponse response = modelMapper.map(outDto, ImageResponse.class);
                    return new BaseResponse<>(response);
                });
    }

    @GetMapping("/check")
    public CompletableFuture<BaseResponse<ImageCheckResponse>> checkImageExists(
            @RequestParam UUID userId, @RequestParam String accessId) {

        return imageService.imageExist(userId, accessId)
                .exceptionally(ex -> {
                    // 예외 처리
                    log.error("이미지 존재 여부 확인 중 오류 발생: {}", ex.getMessage());
                    return new ImageCheckOutDto(false);  // 예외 발생 시 기본값 반환
                })
                .thenApply(exists -> {
                    ImageCheckResponse response = modelMapper.map(exists, ImageCheckResponse.class);
                    return new BaseResponse<>(response);
                });
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
}
