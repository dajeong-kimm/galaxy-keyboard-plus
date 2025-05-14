package com.backend.user.presentation.controller;

import com.backend.global.common.response.BaseResponse;
import com.backend.user.application.in.UserSaveInDto;
import com.backend.user.application.in.UserUpdateInDto;
import com.backend.user.application.out.UserOutDto;
import com.backend.user.application.service.UserService;
import com.backend.user.presentation.request.SaveUserRequest;
import com.backend.user.presentation.request.UpdateUserRequest;
import com.backend.user.presentation.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ModelMapper modelMapper;

    @PostMapping
    public BaseResponse<Void> saveUser(@Valid @RequestBody SaveUserRequest request){
        UserSaveInDto inDto = modelMapper.map(request, UserSaveInDto.class);
        userService.saveUser(inDto);
        return new BaseResponse<>();
    }

    @DeleteMapping("/{userId}")
    public BaseResponse<Void> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return new BaseResponse<>();
    }

    @GetMapping("/{userId}")
    public BaseResponse<UserResponse> getUser(@PathVariable UUID userId) {
        UserOutDto outDto = userService.getUser(userId); // UserService에서 유저 정보 조회
        UserResponse response = modelMapper.map(outDto, UserResponse.class);

        return new BaseResponse<>(response);
    }

    @PostMapping("/{userId}/update")
    public BaseResponse<UserResponse> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserUpdateInDto inDto = new UserUpdateInDto(userId, request.getInfoCount());
        UserOutDto outDto = userService.updateUser(inDto);
        UserResponse response = modelMapper.map(outDto, UserResponse.class);
        return new BaseResponse<>(response);
    }
}
