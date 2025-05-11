package org.dslul.openboard.inputmethod.backup

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * JWT 토큰과 사용자 ID를 안전하게 저장/불러오기 위한 클래스
 * - 내부적으로 EncryptedSharedPreferences를 사용
 * - 모든 데이터는 AES-256으로 암호화되어 저장됨
 */
object TokenStore {
    // SharedPreferences 파일 이름
    private const val PREF_NAME = "auth_prefs"

    // 저장할 키 이름들
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"

    /**
     * 암호화된 SharedPreferences 인스턴스를 반환
     * - Android Keystore를 통해 AES-256 키를 생성하여 사용
     */
    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREF_NAME, // 저장될 파일명: auth_prefs.xml
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 사용자 ID와 accessToken을 안전하게 저장
     * @param context 앱 컨텍스트
     * @param userId 사용자 ID
     * @param accessToken JWT 액세스 토큰
     */
    fun saveCredentials(context: Context, userId: String, accessToken: String) {
        getPrefs(context).edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_ACCESS_TOKEN, accessToken)
            apply() // apply()는 비동기 저장, commit()은 동기
        }
    }

    /**
     * 저장된 사용자 ID 반환
     * 없을 경우 빈 문자열 반환
     */
    fun getUserId(context: Context): String {
        return getPrefs(context).getString(KEY_USER_ID, "") ?: ""
    }

    /**
     * 저장된 액세스 토큰 반환
     * 없을 경우 빈 문자열 반환
     */
    fun getAccessToken(context: Context): String {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    /**
     * 저장된 모든 사용자 정보 초기화 (로그아웃 등 시 사용)
     */
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}