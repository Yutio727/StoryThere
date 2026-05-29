package com.example.storythere.api;

import com.example.storythere.api.model.ApiAuthor;
import com.example.storythere.api.model.ApiAudiobook;
import com.example.storythere.api.model.ApiBook;
import com.example.storythere.api.model.ApiUser;
import com.example.storythere.api.model.ApiUserAudiobook;
import com.example.storythere.api.model.ApiUserBook;
import com.example.storythere.api.model.AssetUploadResponse;
import com.example.storythere.api.model.AudiobookInteractionRequest;
import com.example.storythere.api.model.AudiobookRecommendationEventRequest;
import com.example.storythere.api.model.AudiobookWriteRequest;
import com.example.storythere.api.model.AuthorWriteRequest;
import com.example.storythere.api.model.BookInteractionRequest;
import com.example.storythere.api.model.BookRecommendationEventRequest;
import com.example.storythere.api.model.BookWriteRequest;
import com.example.storythere.api.model.SyncMeRequest;
import com.example.storythere.api.model.TrackingWriteResponse;
import com.example.storythere.api.model.UserLibraryStateRequest;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
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

    @DELETE("v1/me/books/{bookId}")
    Call<TrackingWriteResponse> deleteMyBook(@Path("bookId") long bookId);

    @PATCH("v1/me/books/{bookId}/library-state")
    Call<TrackingWriteResponse> updateMyBookLibraryState(
        @Path("bookId") long bookId,
        @Body UserLibraryStateRequest request
    );

    @GET("v1/audiobooks")
    Call<List<ApiAudiobook>> getAudiobooks(
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @GET("v1/me/audiobooks")
    Call<List<ApiUserAudiobook>> getMyAudiobooks(
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @DELETE("v1/me/audiobooks/{audiobookId}")
    Call<TrackingWriteResponse> deleteMyAudiobook(@Path("audiobookId") long audiobookId);

    @PATCH("v1/me/audiobooks/{audiobookId}/library-state")
    Call<TrackingWriteResponse> updateMyAudiobookLibraryState(
        @Path("audiobookId") long audiobookId,
        @Body UserLibraryStateRequest request
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

    @GET("v1/authors")
    Call<List<ApiAuthor>> searchAuthors(
        @Query("limit") int limit,
        @Query("offset") int offset,
        @Query("query") String query
    );

    @GET("v1/authors/{authorId}")
    Call<ApiAuthor> getAuthorById(@Path("authorId") long authorId);

    @GET("v1/authors/{authorId}/books")
    Call<List<ApiBook>> getAuthorBooks(
        @Path("authorId") long authorId,
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @GET("v1/authors/{authorId}/audiobooks")
    Call<List<ApiAudiobook>> getAuthorAudiobooks(
        @Path("authorId") long authorId,
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @POST("v1/admin/authors")
    Call<ApiAuthor> createAuthor(@Body AuthorWriteRequest request);

    @PATCH("v1/admin/authors/{authorId}")
    Call<ApiAuthor> updateAuthor(
        @Path("authorId") long authorId,
        @Body AuthorWriteRequest request
    );

    @DELETE("v1/admin/authors/{authorId}")
    Call<TrackingWriteResponse> deleteAuthor(@Path("authorId") long authorId);

    @POST("v1/admin/books")
    Call<ApiBook> createBook(@Body BookWriteRequest request);

    @PATCH("v1/admin/books/{bookId}")
    Call<ApiBook> updateBook(
        @Path("bookId") long bookId,
        @Body BookWriteRequest request
    );

    @DELETE("v1/admin/books/{bookId}")
    Call<TrackingWriteResponse> deleteBook(@Path("bookId") long bookId);

    @POST("v1/admin/audiobooks")
    Call<ApiAudiobook> createAudiobook(@Body AudiobookWriteRequest request);

    @PATCH("v1/admin/audiobooks/{audiobookId}")
    Call<ApiAudiobook> updateAudiobook(
        @Path("audiobookId") long audiobookId,
        @Body AudiobookWriteRequest request
    );

    @DELETE("v1/admin/audiobooks/{audiobookId}")
    Call<TrackingWriteResponse> deleteAudiobook(@Path("audiobookId") long audiobookId);

    @Multipart
    @POST("v1/admin/assets/{assetType}")
    Call<AssetUploadResponse> uploadAsset(
        @Path("assetType") String assetType,
        @Part MultipartBody.Part upload
    );
}
