package com.backend.global.common.exception;

import com.backend.global.common.response.BaseResponseStatus;
import lombok.Getter;

@Getter
public class BaseException extends RuntimeException{
    private final BaseResponseStatus status;


    public BaseException(BaseResponseStatus status) {
        this.status = status;
    }
}
