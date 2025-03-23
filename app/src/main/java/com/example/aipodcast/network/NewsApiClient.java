package com.example.aipodcast.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NewsApiClient {

    private static final String BASE_URL = "https://newsapi.org/v2/";

    private static Retrofit retrofit;
    private static NewsApiService apiService;

    public static NewsApiService getInstance() {
        if (apiService == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            apiService = retrofit.create(NewsApiService.class);
        }
        return apiService;
    }
}
