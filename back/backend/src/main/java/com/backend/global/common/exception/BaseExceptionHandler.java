package com.backend.global.common.exception;

import com.backend.global.common.response.BaseResponse;
import com.backend.global.common.response.BaseResponseStatus;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class BaseExceptionHandler {

    /**
     * BaseException - 비즈니스 로직 예외
     */
    @ExceptionHandler(BaseException.class)
    protected ResponseEntity<BaseResponse<Void>> BaseError(BaseException e) {
        log.warn("[⚠️ BaseException] {} → {}", e.getStatus(), e.getStatus().getMessage(), e);

        BaseResponse<Void> response = new BaseResponse<>(e.getStatus());
        return new ResponseEntity<>(response, response.httpStatus());
    }

    /**
     * RuntimeException - 예기치 못한 런타임 예외
     */
    @ExceptionHandler(RuntimeException.class)
    protected ResponseEntity<BaseResponse<Void>> RuntimeError(RuntimeException e) {
        log.error("[⛔ RuntimeException] {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);

        BaseResponse<Void> response = new BaseResponse<>(BaseResponseStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, response.httpStatus());
    }

    /**
     * 유효성 검사 실패 예외 처리
     * - @Valid → MethodArgumentNotValidException
     * - @Validated → ConstraintViolationException
     */
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    protected ResponseEntity<BaseResponse<Void>> ConstraintViolationError(Exception e) {
        log.warn("[⚠️ Validation Error] {}", e.getMessage(), e);

        BaseResponse<Void> response = new BaseResponse<>(BaseResponseStatus.INVALID_INPUT_VALUE);
        return new ResponseEntity<>(response, response.httpStatus());
    }
}
