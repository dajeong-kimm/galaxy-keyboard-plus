package org.dslul.openboard.inputmethod.latin.network;


import org.dslul.openboard.inputmethod.backup.model.UploadImageKeywordResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ImageUploadApi {
    @Multipart
    @POST("rag/upload-image-keyword/")
    Call<UploadImageKeywordResponse> uploadImageWithKeywords(
            @Part("user_id")       RequestBody userId,
            @Part("access_id")     RequestBody accessId,
            @Part("image_time")    RequestBody imageTime,
            @Part("latitude")      RequestBody latitude,
            @Part("longitude")     RequestBody longitude,
            @Part MultipartBody.Part file
    );
}
