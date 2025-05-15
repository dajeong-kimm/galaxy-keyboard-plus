package org.dslul.openboard.inputmethod.latin.network;

import java.util.List;

public class ChatPage {
    private List<Chat> chats;
    private int    totalPages;
    private int    currentPage;
    private boolean last;

    public List<Chat> getChats() {
        return chats;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean isLast() {
        return last;
    }
}