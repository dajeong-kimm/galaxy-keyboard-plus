package org.dslul.openboard.inputmethod.backup;


import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 마지막 업로드된 이미지의 timestamp를 저장/조회하는 클래스
 */
public class UploadStateTracker {
    private static final String PREF_NAME = "upload_state";
    private static final String KEY_BACKED_UP_IDS = "backed_up_content_ids";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 기존에 백업된 ID집합을 반환 */
    public static Set<String> getBackedUpContentIds(Context context) {
        return new HashSet<>(
                getPrefs(context).getStringSet(KEY_BACKED_UP_IDS, Collections.emptySet())
        );
    }

    /** 전체 교체(기존 코드) */
    public static void setBackedUpContentIds(Context context, Set<String> ids) {
        getPrefs(context)
                .edit()
                .putStringSet(KEY_BACKED_UP_IDS, new HashSet<>(ids))
                .apply();
    }

    /** ✅ 새로 업로드된 ID만 추가하는 메서드 */
    public static void addBackedUpContentIds(Context context, Set<String> newIds) {
        SharedPreferences prefs = getPrefs(context);
        // 1) 기존에 저장된 ID들 불러와서
        Set<String> all = new HashSet<>(prefs.getStringSet(KEY_BACKED_UP_IDS, Collections.emptySet()));
        // 2) 새로 업로드된 ID들 추가
        all.addAll(newIds);
        // 3) 합친 결과를 저장
        prefs.edit()
                .putStringSet(KEY_BACKED_UP_IDS, all)
                .apply();
    }

    /** 초기화 */
    public static void clear(Context context) {
        getPrefs(context).edit().clear().apply();
    }
}
