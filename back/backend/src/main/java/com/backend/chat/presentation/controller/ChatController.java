package com.backend.chat.presentation.controller;

import com.backend.chat.application.in.SaveChatInDto;
import com.backend.chat.application.out.ChatOutDto;
import com.backend.chat.application.service.ChatService;
import com.backend.chat.presentation.request.SaveChatRequest;
import com.backend.chat.presentation.response.ChatResponse;
import com.backend.global.common.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ModelMapper modelMapper;

    @PostMapping
    public BaseResponse<Void> saveChat(@Valid @RequestBody SaveChatRequest request) {
        SaveChatInDto inDto = modelMapper.map(request, SaveChatInDto.class);
        chatService.saveChat(inDto);
        return new BaseResponse<>();
    }

    @GetMapping
    public BaseResponse<ChatResponse> getRecentChats(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "0") int page
    ) {
        int pageSize = 20;

        Pageable pageable = PageRequest.of(page, pageSize);

        Page<ChatOutDto> chats = chatService.getRecentChats(userId, pageable);
        ChatResponse response = new ChatResponse(
                chats.getContent(),
                chats.getTotalPages(),
                chats.getTotalElements(),
                chats.getNumber(),
                chats.isLast()
        );

        return new BaseResponse<>(response);
    }
}
