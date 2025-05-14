// File: org.dslul.openboard.inputmethod.latin.network.ReissueRequest
package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

/**
 * 토큰 재발급 요청용 DTO
 */
public class ReissueRequest {
    @SerializedName("refreshToken")
    private final String refreshToken;

    public ReissueRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
