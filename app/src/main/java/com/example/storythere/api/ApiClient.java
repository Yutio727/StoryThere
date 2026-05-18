package com.example.storythere.api;

import com.example.storythere.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static volatile Retrofit retrofit;

    private ApiClient() {
    }

    public static Retrofit getRetrofit() {
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    OkHttpClient okHttpClient = new OkHttpClient.Builder()
                        .addInterceptor(new AuthInterceptor())
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(20, TimeUnit.SECONDS)
                        .build();

                    retrofit = new Retrofit.Builder()
                        .baseUrl(ensureTrailingSlash(BuildConfig.STORYTHERE_API_BASE_URL))
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                }
            }
        }
        return retrofit;
    }

    public static ApiService getApiService() {
        return getRetrofit().create(ApiService.class);
    }

    private static String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}
