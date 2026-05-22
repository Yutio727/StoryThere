package com.example.storythere.api;

import com.example.storythere.api.model.ApiAuthor;
import com.example.storythere.api.model.ApiAudiobook;
import com.example.storythere.api.model.ApiBook;
import com.example.storythere.api.model.ApiUser;
import com.example.storythere.api.model.SyncMeRequest;

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

    @GET("v1/books")
    Call<List<ApiBook>> getBooks(
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @GET("v1/audiobooks")
    Call<List<ApiAudiobook>> getAudiobooks(
        @Query("limit") int limit,
        @Query("offset") int offset
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
