package org.dslul.openboard.inputmethod.latin.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * 토큰 정보를 안전하게 저장하는 클래스
 */
public class SecureStorage {
    private static final String TAG = "SecureStorage";

    // SharedPreferences 파일 이름 및 키 상수
    private static final String PREFS_FILE_NAME = "authPrefs";
    private static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";
    private static final String KEY_USER_ID = "userId";

    // 싱글톤 인스턴스
    private static SecureStorage instance;

    // EncryptedSharedPreferences 인스턴스
    private SharedPreferences securePrefs;

    /**
     * 생성자 - EncryptedSharedPreferences 초기화
     */
    private SecureStorage(Context context) {
        try {
            // 마스터 키 생성
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            // EncryptedSharedPreferences 생성
            securePrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "EncryptedSharedPreferences 초기화 실패", e);
            // 암호화된 SharedPreferences 생성 실패 시 일반 SharedPreferences로 대체
            securePrefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * 싱글톤 인스턴스 반환
     */
    public static synchronized SecureStorage getInstance(Context context) {
        if (instance == null) {
            instance = new SecureStorage(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 인증 토큰들 저장
     */
    public void saveTokens(String accessToken, String refreshToken, String userId) {
        securePrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    /**
     * 액세스 토큰 조회
     */
    public String getAccessToken() {
        return securePrefs.getString(KEY_ACCESS_TOKEN, null);
    }

    /**
     * 리프레시 토큰 조회
     */
    public String getRefreshToken() {
        return securePrefs.getString(KEY_REFRESH_TOKEN, null);
    }

    /**
     * 사용자 ID 조회
     */
    public String getUserId() {
        return securePrefs.getString(KEY_USER_ID, null);
    }

    /**
     * 토큰 유효성 확인 (토큰 존재 여부 확인)
     */
    public boolean hasTokens() {
        String accessToken = getAccessToken();
        String refreshToken = getRefreshToken();
        return accessToken != null && !accessToken.isEmpty() &&
                refreshToken != null && !refreshToken.isEmpty();
    }

    /**
     * 인증 정보 모두 삭제 (로그아웃 시 호출)
     */
    public void clearTokens() {
        securePrefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .apply();
    }
}