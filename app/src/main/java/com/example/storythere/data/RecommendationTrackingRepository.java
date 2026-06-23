package com.example.storythere.data;

import android.util.Log;

import androidx.annotation.Nullable;

import com.example.storythere.api.ApiClient;
import com.example.storythere.api.ApiService;
import com.example.storythere.api.model.AudiobookInteractionRequest;
import com.example.storythere.api.model.AudiobookRecommendationEventRequest;
import com.example.storythere.api.model.BookInteractionRequest;
import com.example.storythere.api.model.BookRecommendationEventRequest;
import com.example.storythere.api.model.TrackingWriteResponse;
import com.example.storythere.api.model.UserLibraryStateRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RecommendationTrackingRepository {
    public static final String EVENT_IMPRESSION = "impression";
    public static final String EVENT_CLICK = "click";
    public static final String EVENT_CONVERSION = "conversion";
    public static final String EVENT_COMPLETION = "completion";

    private static final String TAG = "RecommendationTracking";

    private final ApiService apiService;

    public RecommendationTrackingRepository() {
        apiService = ApiClient.getApiService();
    }

    public void trackBookProgress(long bookId, double progress) {
        trackBookProgress(bookId, progress, null, null);
    }

    public void trackBookProgress(
        long bookId,
        double progress,
        @Nullable Double rating,
        @Nullable String startDate
    ) {
        if (bookId <= 0) {
            return;
        }
        BookInteractionRequest request = new BookInteractionRequest(bookId, clampProgress(progress));
        request.rating = rating;
        request.startDate = startDate;
        enqueue(apiService.saveBookInteraction(request), "book interaction");
    }

    public void trackAudiobookProgress(long audiobookId, double progress, long playbackPositionMs) {
        trackAudiobookProgress(audiobookId, progress, playbackPositionMs, null, null, null);
    }

    public void trackAudiobookProgress(
        long audiobookId,
        double progress,
        long playbackPositionMs,
        @Nullable Double rating,
        @Nullable String startDate,
        @Nullable String completedAt
    ) {
        if (audiobookId <= 0) {
            return;
        }
        AudiobookInteractionRequest request = new AudiobookInteractionRequest(
            audiobookId,
            clampProgress(progress),
            Math.max(0L, playbackPositionMs)
        );
        request.rating = rating;
        request.startDate = startDate;
        request.completedAt = completedAt;
        enqueue(apiService.saveAudiobookInteraction(request), "audiobook interaction");
    }

    public void trackBookEvent(long bookId, String eventType) {
        trackBookEvent(bookId, eventType, null, null, null);
    }

    public void trackBookEvent(
        long bookId,
        String eventType,
        @Nullable Integer slotIndex,
        @Nullable Double score,
        @Nullable Double completion
    ) {
        if (bookId <= 0 || !isValidEventType(eventType)) {
            return;
        }
        BookRecommendationEventRequest request = new BookRecommendationEventRequest(bookId, eventType);
        request.slotIndex = slotIndex;
        request.score = score;
        request.completion = completion != null ? clampProgress(completion) : null;
        enqueue(apiService.saveBookRecommendationEvent(request), "book recommendation event");
    }

    public void trackAudiobookEvent(long audiobookId, String eventType) {
        trackAudiobookEvent(audiobookId, eventType, null, null, null);
    }

    public void trackAudiobookEvent(
        long audiobookId,
        String eventType,
        @Nullable Integer slotIndex,
        @Nullable Double score,
        @Nullable Double completion
    ) {
        if (audiobookId <= 0 || !isValidEventType(eventType)) {
            return;
        }
        AudiobookRecommendationEventRequest request =
            new AudiobookRecommendationEventRequest(audiobookId, eventType);
        request.slotIndex = slotIndex;
        request.score = score;
        request.completion = completion != null ? clampProgress(completion) : null;
        enqueue(apiService.saveAudiobookRecommendationEvent(request), "audiobook recommendation event");
    }

    public void markBookAlreadyRead(long bookId) {
        if (bookId <= 0) {
            return;
        }
        enqueue(
            apiService.updateMyBookLibraryState(bookId, new UserLibraryStateRequest(false, true)),
            "book read state"
        );
    }

    public void markAudiobookAlreadyRead(long audiobookId) {
        if (audiobookId <= 0) {
            return;
        }
        enqueue(
            apiService.updateMyAudiobookLibraryState(audiobookId, new UserLibraryStateRequest(false, true)),
            "audiobook read state"
        );
    }

    private void enqueue(Call<TrackingWriteResponse> call, String label) {
        call.enqueue(new Callback<TrackingWriteResponse>() {
            @Override
            public void onResponse(
                Call<TrackingWriteResponse> call,
                Response<TrackingWriteResponse> response
            ) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Failed to save " + label + ": " + response.code());
                }
            }

            @Override
            public void onFailure(Call<TrackingWriteResponse> call, Throwable t) {
                Log.w(TAG, "Error saving " + label, t);
            }
        });
    }

    private boolean isValidEventType(String eventType) {
        return EVENT_IMPRESSION.equals(eventType)
            || EVENT_CLICK.equals(eventType)
            || EVENT_CONVERSION.equals(eventType)
            || EVENT_COMPLETION.equals(eventType);
    }

    private double clampProgress(double progress) {
        return Math.max(0.0, Math.min(100.0, progress));
    }
}
