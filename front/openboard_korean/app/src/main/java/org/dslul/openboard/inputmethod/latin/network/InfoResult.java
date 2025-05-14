// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/network/InfoResult.java
package org.dslul.openboard.inputmethod.latin.network;

import com.google.gson.annotations.SerializedName;

public class InfoResult {
    /** 결과의 신뢰도 점수 */
    @SerializedName("score")
    private double score;

    /** MediaStore 등에서 사용할 고유 ID */
    @SerializedName("id")
    private String id;

    /** 보여줄 텍스트 또는 설명 */
    @SerializedName("text")
    private String text;

    // GSON이 사용하므로 빈 생성자만으로 충분합니다.
    public InfoResult() { }

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
