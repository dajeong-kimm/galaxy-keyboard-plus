package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

public class ClipBoardResponse {
    @SerializedName("userId")
    private String userId;

    @SerializedName("value")
    private String value;

    @SerializedName("type")
    private String type;

    @SerializedName("created_at")
    private String created_at;

    // Getter/Setter 추가
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }
}