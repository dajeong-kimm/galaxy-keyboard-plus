package com.backend.chat.presentation.response;

import com.backend.chat.application.out.ChatOutDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private List<ChatOutDto> chats;
    private int totalPages;
    private long totalElements;
    private int currentPage;
    private boolean isLast;

}
