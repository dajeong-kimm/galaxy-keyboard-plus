package com.backend.user.application.service;

import com.backend.global.common.exception.BaseException;
import com.backend.global.common.response.BaseResponseStatus;
import com.backend.user.application.in.UserSaveInDto;
import com.backend.user.application.in.UserUpdateInDto;
import com.backend.user.application.out.UserOutDto;
import com.backend.user.domain.entity.User;
import com.backend.user.presentation.request.SaveUserRequest;
import com.backend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public void saveUser(UserSaveInDto inDto){
        User user;
        user = User.builder()
                .userId(inDto.getUserId())
                .infoCount(inDto.getInfoCount())
                .build();

        if (userRepository.existsById(inDto.getUserId())) {
            throw new BaseException(BaseResponseStatus.DUPLICATE_USER);
        }

        userRepository.save(user);
    }

    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_USER));
        userRepository.delete(user);
    }

    public UserOutDto getUser(UUID userId) {
        // 유저 정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_USER));

        return new UserOutDto(user.getUserId(), user.getInfoCount());
    }

    @Transactional // for dirty checking
    public UserOutDto updateUser(UserUpdateInDto inDto) {
        User user = userRepository.findById(inDto.getUserId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_USER));
        user.updateInfoCount(inDto.getInfoCount());

        return new UserOutDto(user.getUserId(), user.getInfoCount());
    }

}
