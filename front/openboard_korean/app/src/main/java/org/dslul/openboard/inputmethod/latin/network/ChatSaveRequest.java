package org.dslul.openboard.inputmethod.latin.network;

import org.dslul.openboard.inputmethod.latin.network.dto.ChatItem;

import java.util.List;

public class ChatSaveRequest {
    private String userId;
    private String sender;          // "user" | "bot"
    private String message;         // 마크다운 가능
    private List<ChatItem> items;   // bot일 때만

    public ChatSaveRequest(String userId, String sender, String message, List<ChatItem> items) {
        this.userId = userId;
        this.sender = sender;
        this.message = message;
        this.items = items;
    }

    public String getUserId() {
        return userId;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public List<ChatItem> getItems() {
        return items;
    }
}