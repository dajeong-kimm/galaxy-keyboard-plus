package org.dslul.openboard.inputmethod.backup.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UploadImageKeywordResponse {
    @SerializedName("user_id")      private String userId;
    @SerializedName("access_id")    private String accessId;
    private String caption;
    private List<String> keywords;
    private String status;
    private String message;              // 에러·스킵 메시지
    @SerializedName("processing_time")
    private String processingTime;

    public String getMessage() { return message; }
    public String getStatus()  { return status;  }
    public String getAccessId(){ return accessId;}
}