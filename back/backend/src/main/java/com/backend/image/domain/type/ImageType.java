package com.backend.image.domain.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ImageType {
    PHOTO,
    INFO,
    AMBIGUOUS;

    // 입력: 소문자 문자열 → enum 매핑
    @JsonCreator
    public static ImageType from(String value) {
        if (value == null) return null;
        return ImageType.valueOf(value.toUpperCase());
    }

    // 출력: enum → 소문자 문자열로
    @JsonValue
    public String toValue() {
        return name().toLowerCase(); // PHOTO → "photo"
    }
}
