package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class MessageResponse {
    @SerializedName("answer")
    private String answer;

    @SerializedName("photo_results")
    private List<PhotoResult> photoResults = new ArrayList<>();

    @SerializedName("info_results")
    private List<InfoResult> infoResults = new ArrayList<>();

    public String getAnswer() {
        return answer;
    }

    public List<PhotoResult> getPhotoResults() {
        return photoResults;
    }

    public List<InfoResult> getInfoResults() {
        return infoResults;
    }

    public MessageResponse setAnswer(String answer) {
        this.answer = answer;
        return this;
    }
}
