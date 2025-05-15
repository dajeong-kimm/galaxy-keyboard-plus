// File: org.dslul.openboard.inputmethod.latin.network.dto.BaseResponse.java
package org.dslul.openboard.inputmethod.latin.network.dto;

import com.google.gson.annotations.SerializedName;

/** 모든 API 응답의 공통 래퍼 */
public class BaseResponse<T> {

    @SerializedName("httpStatus")
    private String httpStatus;   // "OK" 등

    @SerializedName("isSuccess")
    private boolean isSuccess;

    @SerializedName("message")
    private String message;

    @SerializedName("code")
    private int code;            // 200, 400 …

    @SerializedName("result")
    private T result;            // 실제 페이로드(제네릭)

    /* ────────────── Getter ────────────── */
    public String  getHttpStatus() { return httpStatus; }
    public boolean isSuccess()     { return isSuccess;  }
    public String  getMessage()    { return message;    }
    public int     getCode()       { return code;       }
    public T       getResult()     { return result;     }
}
