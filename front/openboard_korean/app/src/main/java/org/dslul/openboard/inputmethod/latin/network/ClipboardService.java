package org.dslul.openboard.inputmethod.latin.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ClipboardService {
    @GET("/search/clipboard/latest/")
    Call<ClipBoardResponse> getLatestClipboard(@Query("user_id") String userId);
}