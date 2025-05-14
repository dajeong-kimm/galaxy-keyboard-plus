package org.dslul.openboard.inputmethod.latin.network;

import org.dslul.openboard.inputmethod.latin.network.dto.ChatItem;

import java.util.List;

public class Chat {
    private String chatId;
    private String sender;
    private String message;
    private String chatTime;        // ISO-8601 ※파싱 필요
    private List<ChatItem> items;

    public String getChatId() {
        return chatId;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public String getChatTime() {
        return chatTime;
    }

    public List<ChatItem> getItems() {
        return items;
    }
}