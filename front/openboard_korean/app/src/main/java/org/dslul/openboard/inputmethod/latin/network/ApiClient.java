// File: org.dslul.openboard.inputmethod.latin.network.ApiClient
package org.dslul.openboard.inputmethod.latin.network;

import android.content.Context;
import android.util.Log;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.data.SecureStorage;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Route;
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
    private static ClipboardService clipboardService;
    private static ApiService       apiService;

    private static ChatStorageApi chatStorageApi;

    private static KeywordApi keywordApi;

    /* ─────────────────────────── 업로드 전용 인스턴스 ─────────────────────────── */
    private static Retrofit        uploadRetrofit;
    private static ImageUploadApi  uploadApi;

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

        /* 401 처리용 Authenticator */
        Authenticator tokenAuthenticator = new Authenticator() {
            @Override public Request authenticate(Route route, okhttp3.Response resp) throws IOException {
                // 401이지만 이미 리트라이한 요청이면 null → 최종 실패
                if (resp.request().header("Authorization-Retry") != null) return null;

                String refresh = AuthManager.getInstance(ctx).getRefreshToken();
                if (refresh == null || refresh.isEmpty()) return null;

                // ── 토큰 리프레시 동기 호출 ──
                Retrofit bare = new Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                ApiService bareApi = bare.create(ApiService.class);

                retrofit2.Response<AuthResponse> r = bareApi.reissue(
                        new ReissueRequest(refresh)).execute();

                if (!r.isSuccessful() || r.body() == null) {
                    Log.e("ApiClient", "❌ 토큰 갱신 실패 code=" + r.code());
                    return null;
                }

                AuthResponse ar = r.body();

                // AuthManager 메모리 캐시 및 SecureStorage 동시 갱신(updateTokens 메서드에서 둘 다 처리)
                AuthManager.getInstance(ctx)
                        .updateTokens(ar.getAccessToken(), ar.getRefreshToken());
                Log.i("ApiClient", "🔄 액세스 토큰 갱신 성공");

                // ④ 새 토큰으로 원 요청 재시도 (무한루프 방지 헤더 추가)
                return resp.request().newBuilder()
                        .header("Authorization", "Bearer " + ar.getAccessToken())
                        .header("Authorization-Retry", "true")
                        .build();
            }
        };

        // ── ② 로깅 Interceptor ────────────────────────────
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BODY);

        /* ✨ 기본 클라이언트용 Dispatcher 추가 */
        Dispatcher defaultDispatcher = new Dispatcher();
        defaultDispatcher.setMaxRequestsPerHost(10); // ← 호스트당 동시 요청 5 → 10 으로 상향
        // defaultDispatcher.setMaxRequests(64);     // (원래 값 64 그대로라 굳이 지정 안 해도 됨)

        // ── ③ OkHttp 클라이언트 ───────────────────────────
        OkHttpClient ok = new OkHttpClient.Builder()
                .dispatcher(defaultDispatcher)
                .addInterceptor(authInterceptor)
                .addInterceptor(logger)
                .authenticator(tokenAuthenticator)
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
        chatStorageApi = retrofit.create(ChatStorageApi.class);
        clipboardService = retrofit.create(ClipboardService.class);
        keywordApi = retrofit.create(KeywordApi.class);

        Log.d("ApiClient", "★ Retrofit 초기화 완료");
    }

    /**  이미지 업로드 전용 Retrofit 서비스 */
    public static synchronized ImageUploadApi getDedicatedImageUploadApi(Context ctx) {
        if (uploadApi != null) return uploadApi;

        /* ① 전용 Dispatcher */
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(64);
        dispatcher.setMaxRequestsPerHost(16);

        /* ② 공통 토큰·로깅 인터셉터 재사용 ----------------------------- */
        Interceptor authInterceptor = chain -> {
            String access = AuthManager.getInstance(ctx).getAccessToken();
            Request req = chain.request().newBuilder()
                    .header("Authorization", access == null ? "" : "Bearer " + access)
                    .build();
            return chain.proceed(req);
        };

        /* 401 처리용 Authenticator */
        Authenticator tokenAuthenticator = new Authenticator() {
            @Override public Request authenticate(Route route, okhttp3.Response resp) throws IOException {
                // 401이지만 이미 리트라이한 요청이면 null → 최종 실패
                if (resp.request().header("Authorization-Retry") != null) return null;

                String refresh = AuthManager.getInstance(ctx).getRefreshToken();
                if (refresh == null || refresh.isEmpty()) return null;

                // ── 토큰 리프레시 동기 호출 ──
                Retrofit bare = new Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                ApiService bareApi = bare.create(ApiService.class);

                retrofit2.Response<AuthResponse> r = bareApi.reissue(
                        new ReissueRequest(refresh)).execute();

                if (!r.isSuccessful() || r.body() == null) {
                    Log.e("ApiClient", "❌ 토큰 갱신 실패 code=" + r.code());
                    return null;
                }

                AuthResponse ar = r.body();

                // AuthManager 메모리 캐시 및 SecureStorage 동시 갱신(updateTokens 메서드에서 둘 다 처리)
                AuthManager.getInstance(ctx)
                        .updateTokens(ar.getAccessToken(), ar.getRefreshToken());
                Log.i("ApiClient", "🔄 액세스 토큰 갱신 성공");

                // ④ 새 토큰으로 원 요청 재시도 (무한루프 방지 헤더 추가)
                return resp.request().newBuilder()
                        .header("Authorization", "Bearer " + ar.getAccessToken())
                        .header("Authorization-Retry", "true")
                        .build();
            }
        };

        HttpLoggingInterceptor logger = new HttpLoggingInterceptor()
                .setLevel(HttpLoggingInterceptor.Level.BODY);

        /* ③ 업로드 전용 OkHttpClient */
        OkHttpClient uploadClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .addInterceptor(authInterceptor)
                .addInterceptor(logger)
                .authenticator(tokenAuthenticator)
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout   (TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout  (TIMEOUT, TimeUnit.SECONDS)
                .build();

        /* ④ 업로드 전용 Retrofit */
        uploadRetrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(uploadClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        uploadApi = uploadRetrofit.create(ImageUploadApi.class);
        return uploadApi;
    }

    /** 어디서든 호출 가능한 getter */
    public static ChatApiService getChatApiService() { return chatApiService; }
    public static ApiService   getApiService()      { return apiService;   }


    public static ClipboardService getClipboardService() {return clipboardService;}

    public static ChatStorageApi getChatStorageApi() { return chatStorageApi; }

    public static KeywordApi getKeywordApi() { return keywordApi; }
}
