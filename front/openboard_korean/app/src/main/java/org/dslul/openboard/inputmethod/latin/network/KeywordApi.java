package org.dslul.openboard.inputmethod.latin.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface KeywordApi {
    @GET("/search/keyword/exists/")
    Call<KeywordExistsResponse> exists(
            @Query("user_id") String userId,
            @Query("keyword") String keyword
    );

    @GET("/search/keyword/images/")
    Call<KeywordImagesResponse> getImages(
            @Query("user_id") String userId,
            @Query("keyword") String keyword,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );
}
