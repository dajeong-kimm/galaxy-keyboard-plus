package org.dslul.openboard.inputmethod.backup;


import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 마지막 업로드된 이미지의 timestamp를 저장/조회하는 클래스
 */
public class UploadStateTracker {
    private static final String PREF_NAME = "upload_state";
    private static final String KEY_LAST_UPLOADED_AT = "last_uploaded_at";
    private static final String KEY_BACKED_UP_IDS = "backed_up_content_ids";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    // ---------- 📍 contentId 목록 관련 ----------
    public static Set<String> getBackedUpContentIds(Context context) {
        return new HashSet<>(getPrefs(context).getStringSet(KEY_BACKED_UP_IDS, new HashSet<>()));
    }

    // ---------- 📍 contentId 목록 관련 ----------
    /**
     * 주어진 ID 세트로 내부 저장소를 덮어씁니다.
     * (add가 아니라 replace)
     */
    public static void setBackedUpContentIds(Context context, Set<String> ids) {
        getPrefs(context)
                .edit()
                .putStringSet(KEY_BACKED_UP_IDS, new HashSet<>(ids))
                .apply();
    }
    // --------------------------------
    /**
     * 초기화 (테스트용 또는 리셋용)
     */
    public static void clear(Context context) {
        getPrefs(context).edit()
                .remove(KEY_LAST_UPLOADED_AT)
                .remove(KEY_BACKED_UP_IDS)
                .apply();
    }
}
