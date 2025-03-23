package com.example.aipodcast.network;

import com.example.aipodcast.models.NewsResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface NewsApiService {

    @GET("top-headlines")
    Call<NewsResponse> getTopHeadlines(
            @Query("category") String category,
            @Query("country") String country,
            @Query("apiKey") String apiKey
    );
}
