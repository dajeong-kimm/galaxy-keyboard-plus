package org.dslul.openboard.inputmethod.latin.network.dto;

public class ChatItem {
    private String accessId;
    private String text;            // 이미지 설명

    public String getAccessId() {
        return accessId;
    }

    public String getText() {
        return text;
    }

    public void setAccessId(String accessId) {
        this.accessId = accessId;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ChatItem() {}

    public ChatItem(String accessId, String text) {
        this.accessId = accessId;
        this.text = text;
    }
}