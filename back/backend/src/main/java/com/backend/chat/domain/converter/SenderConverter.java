package com.backend.chat.domain.converter;

import com.backend.chat.domain.type.Sender;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SenderConverter implements AttributeConverter<Sender, String> {
    @Override
    public String convertToDatabaseColumn(Sender attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public Sender convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Sender.valueOf(dbData.toUpperCase());
    }
}
