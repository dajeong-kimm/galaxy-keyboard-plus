// File: org.dslul.openboard.inputmethod.latin.network.ApiClient
package org.dslul.openboard.inputmethod.latin.network;

import android.content.Context;
import android.util.Log;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 모든 API 인스턴스를 총괄하는 싱글톤.
 *  ① 토큰 헤더 자동 첨부
 *  ② 공통 타임아웃·로깅
 *  ③ Service 캐싱
 */
public final class ApiClient {
    private static final String BASE_URL = BuildConfig.SERVER_BASE_URL;
    private static final int    TIMEOUT  = 120;          // seconds

    private static volatile Retrofit retrofit;           // ① 단일 Retrofit
    private static ChatApiService   chatApiService;      // ② 서비스 캐시
    private static ApiService       apiService;

    /** 최초 한 번 앱 전체를 초기화 */
    public static void init(Context ctx) {
        if (retrofit != null) {
            Log.d("ApiClient", "→ 이미 초기화 완료, skip");
            return;                    // 이미 초기화O
        }
        Log.d("ApiClient", "★ Retrofit 초기화 시작 (ctx=" + ctx + ")");

        // ── ① 공통 Interceptor : 토큰 헤더 삽입 ─────────────
        Interceptor authInterceptor = chain -> {
            String access = AuthManager.getInstance(ctx).getAccessToken();
            Log.d("ApiClient", "  ↳ Intercept: Authorization="
                    + (access == null ? "null" : access));

            Request req = chain.request().newBuilder()
                    .header("Authorization",
                            access == null ? "" : "Bearer " + access)
                    .build();
            return chain.proceed(req);
        };

        // ── ② 로깅 Interceptor ────────────────────────────
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BODY);

        // ── ③ OkHttp 클라이언트 ───────────────────────────
        OkHttpClient ok = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logger)
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout   (TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout  (TIMEOUT, TimeUnit.SECONDS)
                .build();

        // ── ④ Retrofit 단일 인스턴스 ──────────────────────
        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // ── ⑤ 서비스 캐싱 ────────────────────────────────
        chatApiService = retrofit.create(ChatApiService.class);
        apiService     = retrofit.create(ApiService.class);

        Log.d("ApiClient", "★ Retrofit 초기화 완료");
    }

    /** 어디서든 호출 가능한 getter */
    public static ChatApiService getChatApiService() { return chatApiService; }
    public static ApiService   getApiService()      { return apiService;   }

    /** 로그인 성공 후 새 토큰이 들어오면 SecureStorage만 갈아끼우면 끝 */
}
