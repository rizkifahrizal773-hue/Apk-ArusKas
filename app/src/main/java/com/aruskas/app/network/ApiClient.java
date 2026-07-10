package com.aruskas.app.network;

import com.aruskas.app.BuildConfig;
import com.aruskas.app.ArusKasApplication;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit = null;
    private static ApiService apiService = null;

    public static synchronized Retrofit getClient() {
        if (retrofit == null) {
            OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS);

            // Dynamic Authorization Bearer Token Interceptor
            httpClientBuilder.addInterceptor(chain -> {
                Request originalRequest = chain.request();
                
                if (ArusKasApplication.getInstance() != null) {
                    com.aruskas.app.util.SessionManager sessionManager = 
                            new com.aruskas.app.util.SessionManager(ArusKasApplication.getInstance());
                    String token = sessionManager.getToken();
                    if (token != null && !token.trim().isEmpty()) {
                        Request authenticatedRequest = originalRequest.newBuilder()
                                .header("Authorization", "Bearer " + token)
                                .build();
                        return chain.proceed(authenticatedRequest);
                    }
                }
                
                return chain.proceed(originalRequest);
            });

            // Logging interceptor (only in debug builds, as per PRD.md Section 12)
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
                httpClientBuilder.addInterceptor(loggingInterceptor);
            }

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(httpClientBuilder.build())
                    .build();
        }
        return retrofit;
    }

    public static synchronized ApiService getApiService() {
        if (apiService == null) {
            apiService = getClient().create(ApiService.class);
        }
        return apiService;
    }
}
