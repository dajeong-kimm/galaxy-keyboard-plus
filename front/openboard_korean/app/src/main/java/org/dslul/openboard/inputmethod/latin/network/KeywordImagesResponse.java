package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class KeywordImagesResponse {
    @SerializedName("user_id")
    public String userId;
    public String keyword;
    public int page;
    @SerializedName("page_size")
    public int pageSize;
    @SerializedName("image_ids")
    public List<String> imageIds;
}
