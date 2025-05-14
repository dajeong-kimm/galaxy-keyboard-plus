package com.backend.global.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponseDto<T> {
    private String httpStatus;
    private Boolean isSuccess;
    private String message;
    private int code;
    private T result;
}
