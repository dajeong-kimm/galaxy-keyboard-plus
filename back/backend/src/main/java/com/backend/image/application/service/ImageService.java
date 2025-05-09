package com.backend.image.application.service;

import com.backend.global.common.exception.BaseException;
import com.backend.global.common.response.BaseResponseStatus;
import com.backend.image.application.in.DeleteImagesInDto;
import com.backend.image.application.in.SaveImageInDto;
import com.backend.image.application.out.ImageCheckOutDto;
import com.backend.image.application.out.ImageOutDto;
import com.backend.image.application.out.ImageThumbnailOutDto;
import com.backend.image.application.out.SaveImageOutDto;
import com.backend.image.domain.entity.Image;
import com.backend.image.repository.ImageRepository;
import com.backend.user.domain.entity.User;
import com.backend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import org.modelmapper.TypeToken;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final ModelMapper modelMapper;

    public SaveImageOutDto saveImage(SaveImageInDto inDto) {

        User user = userRepository.findById(inDto.getUserId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_USER));

        // imageTime이 String 형태로 넘어왔으므로 LocalDateTime으로 변환
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
        LocalDateTime imageTime = LocalDateTime.parse(inDto.getImageTime(), formatter);

        Image image = Image.builder()
                .imageId(UUID.randomUUID())
                .user(user)
                .accessId(inDto.getAccessId())
                .imageTime(imageTime)  // 변환된 imageTime 사용
                .type(inDto.getType())
                .content(inDto.getContent())
                .build();

        return modelMapper.map(imageRepository.save(image), SaveImageOutDto.class);
    }

    public List<ImageThumbnailOutDto> getStarPreview(UUID userId) {
        List<Image> images = imageRepository.findTop3ByUser_UserIdAndStarTrueOrderByImageTimeDesc(userId);

        Type listType = new TypeToken<List<ImageThumbnailOutDto>>() {}.getType();
        return modelMapper.map(images, listType);
    }

    public Page<ImageThumbnailOutDto> getStarredImages(UUID userId, Pageable pageable) {
        Page<Image> images = imageRepository.findByUser_UserIdAndStarTrueOrderByImageTimeDesc(userId, pageable);

        return images.map(image -> modelMapper.map(image, ImageThumbnailOutDto.class));
    }

    public Page<ImageThumbnailOutDto> getAllImages(UUID userId, Pageable pageable) {
        Page<Image> images = imageRepository.findByUser_UserIdAndStarFalseOrderByImageTimeDesc(userId, pageable);

        return images.map(image -> modelMapper.map(image, ImageThumbnailOutDto.class));
    }

    public ImageOutDto getImageById(UUID imageId){
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_IMAGE));
        return modelMapper.map(image, ImageOutDto.class);
    }
    public void deleteImage(UUID imageId){
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_IMAGE));
        imageRepository.delete(image);
    }

    @Transactional
    public List<UUID> deleteImages(DeleteImagesInDto inDto){
        List<UUID> failedIds = new ArrayList<>();

        for (UUID imageId : inDto.getImageIds()) {
            try {
                imageRepository.findById(imageId)
                        .ifPresentOrElse(
                                imageRepository::delete,
                                () -> failedIds.add(imageId)  // 존재하지 않는 경우
                        );
            } catch (Exception e) {
                failedIds.add(imageId); // 삭제 중 예외 발생한 경우
            }
        }

        // 전부 실패한 경우
        if (failedIds.size() == inDto.getImageIds().size()) {
            throw new BaseException(BaseResponseStatus.NO_IMAGE_DELETED);
        }

        return failedIds;
    }

    @Transactional // for dirty checking
    public void unstarImage(UUID imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_IMAGE));

        image.setStar(false);
    }

    @Transactional // for dirty checking
    public void starImage(UUID imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_IMAGE));

        image.setStar(true);
    }

    public ImageCheckOutDto imageExist(UUID userId, String accessId) {
        boolean exist = imageRepository.existsByUser_UserIdAndAccessId(userId, accessId);
        return new ImageCheckOutDto(exist);
    }
}
