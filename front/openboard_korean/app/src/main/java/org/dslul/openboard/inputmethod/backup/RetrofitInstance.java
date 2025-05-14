package org.dslul.openboard.inputmethod.backup;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitInstance {
    private static final String RAG_BASE_URL = "http://k12e201.p.ssafy.io:8090/";
    private static final String BACK_BASE_URL = "http://k12e201.p.ssafy.io:8083/";

    private static ImageUploadApi uploadApi;
    private static ImageFilterApi filterApi;

    public static ImageUploadApi getUploadApi() {
        if (uploadApi == null) {
            synchronized (RetrofitInstance.class) {
                if (uploadApi == null) {
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)    // 연결 시도 제한 시간
                            .writeTimeout(120, TimeUnit.SECONDS)     // 요청(업로드) 타임아웃
                            .readTimeout(120, TimeUnit.SECONDS)      // 응답 대기 타임아웃
                            .addInterceptor(logging)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(RAG_BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    uploadApi = retrofit.create(ImageUploadApi.class);
                }
            }
        }
        return uploadApi;
    }


    public static ImageFilterApi getFilterApi() {
        if (filterApi == null) {
            synchronized (RetrofitInstance.class) {
                if (filterApi == null) {
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)    // 연결 시도 제한 시간
                            .writeTimeout(120, TimeUnit.SECONDS)     // 요청(업로드) 타임아웃
                            .readTimeout(120, TimeUnit.SECONDS)      // 응답 대기 타임아웃
                            .addInterceptor(logging)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BACK_BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    filterApi = retrofit.create(ImageFilterApi.class);
                }
            }
        }
        return filterApi;
    }
}
