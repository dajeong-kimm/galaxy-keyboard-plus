package org.dslul.openboard.inputmethod.backup

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

/**
 * 마지막 업로드된 이미지의 timestamp를 저장/조회하는 클래스
 */
object UploadStateTracker {
    private const val PREF_NAME = "upload_state"
    private const val KEY_LAST_UPLOADED_AT = "last_uploaded_at"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 마지막 업로드된 timestamp를 저장 (밀리초)
     */
    fun setLastUploadedAt(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_UPLOADED_AT, timestamp).apply()
    }

    /**
     * 마지막 업로드된 timestamp를 반환 (기본값: 0)
     */
    fun getLastUploadedAt(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_UPLOADED_AT, 0L)
    }

    /**
     * 마지막 업로드 시간이 24시간 이상 지났는지 여부 확인
     */
    fun isExpired(context: Context, thresholdMillis: Long = TimeUnit.HOURS.toMillis(24)): Boolean {
        val last = getLastUploadedAt(context)
        val now = System.currentTimeMillis()
        return (now - last) > thresholdMillis
    }

    /**
     * 초기화 (테스트용 또는 리셋용)
     */
    fun clear(context: Context) {
        getPrefs(context).edit().remove(KEY_LAST_UPLOADED_AT).apply()
    }
}