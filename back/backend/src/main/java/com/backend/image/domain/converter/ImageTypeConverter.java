package com.backend.image.domain.converter;

import com.backend.image.domain.type.ImageType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ImageTypeConverter implements AttributeConverter<ImageType, String> {

    @Override
    public String convertToDatabaseColumn(ImageType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ImageType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ImageType.valueOf(dbData.toUpperCase());
    }

}
