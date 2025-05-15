package com.backend.chat.presentation.controller;

import com.backend.chat.application.in.SaveChatInDto;
import com.backend.chat.application.out.ChatOutDto;
import com.backend.chat.application.service.ChatService;
import com.backend.chat.presentation.request.SaveChatRequest;
import com.backend.chat.presentation.response.ChatResponse;
import com.backend.global.common.response.BaseResponse;
import com.backend.image.application.service.ImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ModelMapper modelMapper;

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    @PostMapping
    public CompletableFuture<BaseResponse<Void>> saveChat(@Valid @RequestBody SaveChatRequest request) {
        SaveChatInDto inDto = modelMapper.map(request, SaveChatInDto.class);

        // 비동기적으로 saveChat을 호출하고 예외 처리 추가
        return chatService.saveChat(inDto)
                .exceptionally(ex -> {
                    log.error("채팅 저장 중 예외 발생: {}", ex.getMessage());
                    // 예외 처리 후, 기본 응답을 반환하거나 실패 처리
                    return null;  // 실패 처리 시 null 반환
                })
                .thenApply(aVoid -> new BaseResponse<Void>());  // 성공 시 응답 처리
    }

    @GetMapping
    public CompletableFuture<BaseResponse<ChatResponse>> getRecentChats(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "0") int page
    ) {
        int pageSize = 20;
        Pageable pageable = PageRequest.of(page, pageSize);

        // 비동기적으로 getRecentChats를 호출하고 예외 처리 추가
        return chatService.getRecentChats(userId, pageable)
                .exceptionally(ex -> {
                    log.error("채팅 목록 조회 중 예외 발생: {}", ex.getMessage());
                    // 예외 발생 시 기본 값 반환
                    return Page.empty();  // 빈 페이지 반환
                })
                .thenApply(chats -> {
                    ChatResponse response = new ChatResponse(
                            chats.getContent(),
                            chats.getTotalPages(),
                            chats.getTotalElements(),
                            chats.getNumber(),
                            chats.isLast()
                    );
                    return new BaseResponse<>(response);
                });
    }
}
