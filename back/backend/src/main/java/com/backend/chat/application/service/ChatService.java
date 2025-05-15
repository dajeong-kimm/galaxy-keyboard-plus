package com.backend.chat.application.service;

import com.backend.chat.application.in.SaveChatInDto;
import com.backend.chat.application.out.ChatOutDto;
import com.backend.chat.application.in.ResponseItem;
import com.backend.chat.application.out.ResponseOutDto;
import com.backend.chat.domain.entity.Chat;
import com.backend.chat.domain.entity.Response;
import com.backend.chat.repository.ChatRepository;
import com.backend.chat.repository.ResponseRepository;
import com.backend.global.common.exception.BaseException;
import com.backend.global.common.response.BaseResponseStatus;
import com.backend.user.domain.entity.User;
import com.backend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    @Transactional
    @Async
    public CompletableFuture<Void> saveChat(SaveChatInDto inDto) {
        User user = userRepository.findById(inDto.getUserId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NO_EXIST_USER));

        Chat chat = Chat.builder()
                .chatId(UUID.randomUUID())
                .user(user)
                .sender(inDto.getSender())
                .message(inDto.getMessage())
                .chatTime(LocalDateTime.now())
                .build();

        if (inDto.getItems() != null) {
            for (ResponseItem res : inDto.getItems()) {
                Response response = Response.builder()
                        .responseId(UUID.randomUUID())
                        .accessId(res.getAccessId())
                        .text(res.getText())
                        .build();
                chat.addResponse(response); // 양방향 관계 설정
            }
        }

        chatRepository.save(chat); // cascade로 인해 responses도 저장됨
        return CompletableFuture.completedFuture(null); // 비동기 작업이 끝났음을 반환
    }

    @Async
    public CompletableFuture<Page<ChatOutDto>> getRecentChats(UUID userId, Pageable pageable) {
        Page<Chat> chats = chatRepository.findByUser_UserIdOrderByChatTimeDesc(userId, pageable);

        Page<ChatOutDto> result = chats.map(chat -> {
            List<ResponseOutDto> responseItems = chat.getResponses().stream()
                    .map(res -> new ResponseOutDto(res.getResponseId(), res.getAccessId(), res.getText()))
                    .collect(Collectors.toList());

            return new ChatOutDto(
                    chat.getChatId(),
                    chat.getSender(),
                    chat.getMessage(),
                    chat.getChatTime(),
                    responseItems
            );
        });

        return CompletableFuture.completedFuture(result);
    }
}
