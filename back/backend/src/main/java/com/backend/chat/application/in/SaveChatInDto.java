package com.backend.chat.application.in;

import com.backend.chat.domain.type.Sender;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SaveChatInDto {
    private UUID userId;
    private Sender sender;
    private String message;
    private List<ResponseItem> items;
}
