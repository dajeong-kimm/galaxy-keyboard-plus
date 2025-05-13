package org.dslul.openboard.inputmethod.latin.network;

public class MessageRequest {
    private String userId;
    private String query;

    public MessageRequest(String userId, String query) {
        this.userId = userId;
        this.query  = query;
    }

    public String getUserId() {
        return userId;
    }

    public String getQuery() {
        return query;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
