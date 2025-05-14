// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/network/PhotoResult.java
package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

public class PhotoResult {
    /** 결과의 신뢰도 점수 */
    @SerializedName("score")
    private double score;

    /** MediaStore 등에서 사용할 고유 ID */
    @SerializedName("id")
    private String id;

    /** 이미지 설명 텍스트 */
    @SerializedName("text")
    private String text;

    public PhotoResult() { }

    public double getScore() {
        return score;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }
}
