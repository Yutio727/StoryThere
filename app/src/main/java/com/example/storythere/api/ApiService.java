package com.example.storythere.api;

import com.example.storythere.api.model.ApiAuthor;
import com.example.storythere.api.model.ApiAudiobook;
import com.example.storythere.api.model.ApiBook;
import com.example.storythere.api.model.ApiUser;
import com.example.storythere.api.model.ApiUserBook;
import com.example.storythere.api.model.AudiobookInteractionRequest;
import com.example.storythere.api.model.AudiobookRecommendationEventRequest;
import com.example.storythere.api.model.BookInteractionRequest;
import com.example.storythere.api.model.BookRecommendationEventRequest;
import com.example.storythere.api.model.SyncMeRequest;
import com.example.storythere.api.model.TrackingWriteResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Body;

public interface ApiService {
    @POST("v1/me/sync")
    Call<ApiUser> syncMe();

    @POST("v1/me/sync")
    Call<ApiUser> syncMeWithProfile(@Body SyncMeRequest request);

    @POST("v1/interactions/books")
    Call<TrackingWriteResponse> saveBookInteraction(@Body BookInteractionRequest request);

    @POST("v1/interactions/audiobooks")
    Call<TrackingWriteResponse> saveAudiobookInteraction(@Body AudiobookInteractionRequest request);

    @POST("v1/recommendation-events/books")
    Call<TrackingWriteResponse> saveBookRecommendationEvent(@Body BookRecommendationEventRequest request);

    @POST("v1/recommendation-events/audiobooks")
    Call<TrackingWriteResponse> saveAudiobookRecommendationEvent(@Body AudiobookRecommendationEventRequest request);

    @GET("v1/books")
    Call<List<ApiBook>> getBooks(
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @GET("v1/me/books")
    Call<List<ApiUserBook>> getMyBooks(
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @GET("v1/audiobooks")
    Call<List<ApiAudiobook>> getAudiobooks(
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @GET("v1/recommendations/books")
    Call<List<ApiBook>> getRecommendedBooks(
        @Query("limit") int limit
    );

    @GET("v1/recommendations/audiobooks")
    Call<List<ApiAudiobook>> getRecommendedAudiobooks(
        @Query("limit") int limit
    );

    @GET("v1/authors")
    Call<List<ApiAuthor>> getAuthors(
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @GET("v1/authors/{authorId}")
    Call<ApiAuthor> getAuthorById(@Path("authorId") long authorId);

    @GET("v1/authors/{authorId}/books")
    Call<List<ApiBook>> getAuthorBooks(
        @Path("authorId") long authorId,
        @Query("limit") int limit,
        @Query("offset") int offset
    );
}
