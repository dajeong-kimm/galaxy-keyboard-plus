package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class MessageResponse {
    @SerializedName("type")
    private String type;
    @SerializedName("answer")
    private String answer;

    @SerializedName("photo_ids")
    private List<String> photoIds = new ArrayList<>();

    // getters
    public String getType()      { return type; }
    public String getAnswer()    { return answer; }
    public List<String> getPhotoIds() { return photoIds; }

    public MessageResponse setAnswer(String answer) {
        this.answer = answer;
        return this;
    }
}
