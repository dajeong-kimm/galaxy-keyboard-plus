package com.backend.chat.application.out;

import com.backend.chat.application.in.ResponseItem;
import com.backend.chat.domain.type.Sender;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatOutDto {
    private UUID chatId;
    private Sender sender;
    private String message;
    private LocalDateTime chatTime;
    private List<ResponseOutDto> items;
}
