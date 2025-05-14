package org.dslul.openboard.inputmethod.latin.network;

import org.dslul.openboard.inputmethod.latin.network.dto.BaseResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ChatStorageApi {
    @POST("/api/v1/chats")
    Call<BaseResponse<Void>> saveChat(@Body ChatSaveRequest body);

    @GET("/api/v1/chats")
    Call<ChatHistoryResponse> getChats(
        @Query("userId") String userId,
        @Query("page")   int    page
    );
}