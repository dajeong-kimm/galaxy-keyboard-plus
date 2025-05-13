package org.dslul.openboard.inputmethod.backup;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitInstance {
    private static final String BASE_URL = "http://k12e201.p.ssafy.io:8090/";

    private static ImageUploadApi api;

    public static ImageUploadApi getApi() {
        if (api == null) {
            synchronized (RetrofitInstance.class) {
                if (api == null) {
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)    // 연결 시도 제한 시간
                            .writeTimeout(120, TimeUnit.SECONDS)     // 요청(업로드) 타임아웃
                            .readTimeout(120, TimeUnit.SECONDS)      // 응답 대기 타임아웃
                            .addInterceptor(logging)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    api = retrofit.create(ImageUploadApi.class);
                }
            }
        }
        return api;
    }
}
