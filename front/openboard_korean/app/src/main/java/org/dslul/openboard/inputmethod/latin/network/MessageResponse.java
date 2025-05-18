package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class MessageResponse {

    private String type;
    private String query;
    @SerializedName("photo_ids")
    private List<String> photoIds;
    private String answer;

    // getters
    public String getType()      { return type; }
    public String getQuery()       { return query; }
    public String getAnswer()    { return answer; }
    public List<String> getPhotoIds() { return photoIds; }

    public MessageResponse setAnswer(String answer) {
        this.answer = answer;
        return this;
    }
}
