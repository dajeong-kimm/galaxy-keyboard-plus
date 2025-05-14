package org.dslul.openboard.inputmethod.backup;

import org.dslul.openboard.inputmethod.backup.model.FilterImageResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ImageFilterApi {
    @GET("/api/v1/images/check")
    Call<FilterImageResponse> checkImage(
            @Query("userId") String userId,
            @Query("accessId") String accessId
    );
}
