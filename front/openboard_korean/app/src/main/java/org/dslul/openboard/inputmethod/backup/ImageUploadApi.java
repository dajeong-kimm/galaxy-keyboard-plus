package org.dslul.openboard.inputmethod.backup;


import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ImageUploadApi {
    @Multipart
    @POST("rag/upload-image/")
    Call<Void> uploadImage(
            @Header("Authorization") String authHeader,
            @Part("user_id") RequestBody userId,
            @Part("access_id") RequestBody accessId,
            @Part("image_time") RequestBody imageTime,
            @Part MultipartBody.Part file
    );
}
