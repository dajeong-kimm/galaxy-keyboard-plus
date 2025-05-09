package com.backend.global.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@AllArgsConstructor
public enum BaseResponseStatus {
    /**
     * 200: 요청 성공
     **/
    SUCCESS(HttpStatus.OK, true, 200, "요청에 성공하였습니다."),


    /**
     * 400: 클라이언트 오류
     */
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, false, 400, "잘못된 입력값입니다. 다시 확인해주세요."),
    NO_EXIST_USER(HttpStatus.NOT_FOUND, false, 404, "존재하지 않는 사용자입니다."),
    DUPLICATE_USER(HttpStatus.CONFLICT, false, 409, "이미 존재하는 사용자입니다."),

    NO_EXIST_IMAGE(HttpStatus.NOT_FOUND, false, 404, "존재하지 않는 사진입니다."),
    NO_IMAGE_DELETED(HttpStatus.BAD_REQUEST, false, 400, "요청한 이미지가 전부 삭제되지 않았습니다."),

    NO_EXIST_PLAN(HttpStatus.NOT_FOUND, false, 404, "존재하지 않는 일정입니다."),


    /**
     * 500: 서버 오류
     */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, false, 500, "서버 내부 오류가 발생했습니다."),

    ;
    private final HttpStatusCode httpStatusCode;  // HttpStatus로 변경
    private final boolean isSuccess;
    private final int code;
    private final String message;
}
