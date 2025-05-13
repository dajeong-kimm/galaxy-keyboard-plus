package org.dslul.openboard.inputmethod.latin.network;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import org.dslul.openboard.inputmethod.latin.BuildConfig;
/**
 * API 클라이언트 클래스
 */
public class ApiClient {
    private static final String BASE_URL = BuildConfig.SERVER_BASE_URL;
    private static final int TIMEOUT = 120; // 초 단위

    private static Retrofit sRetrofit;
    private static ApiService sApiService;

    /**
     * Retrofit 인스턴스 생성
     */
    private static Retrofit getRetrofit(Context context) {
        if (sRetrofit == null) {
            // 로깅 인터셉터 설정
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            // OkHttp 클라이언트 설정
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                    .addInterceptor(loggingInterceptor)
                    .build();

            // Retrofit 빌드
            sRetrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return sRetrofit;
    }

    /**
     * API 서비스 인터페이스 가져오기
     */
    public static ApiService getApiService(Context context) {
        if (sApiService == null) {
            sApiService = getRetrofit(context).create(ApiService.class);
        }
        return sApiService;
    }

    public static ChatApiService getChatApiService() {
        if (sRetrofit == null) {
            // 전체 패킷을 logcat에 찍어 주는 interceptor
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(log)
                    // ▼ 타임아웃 설정 -------------------------------------------------
                    .connectTimeout(TIMEOUT, TimeUnit.SECONDS)   // 연결
                    .readTimeout   (TIMEOUT, TimeUnit.SECONDS)   // 서버 응답(Body) 대기
                    .writeTimeout  (TIMEOUT, TimeUnit.SECONDS)   // 요청 Body 전송
                    // ---------------------------------------------------------------
                    .build();


            sRetrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return sRetrofit.create(ChatApiService.class);
    }

}