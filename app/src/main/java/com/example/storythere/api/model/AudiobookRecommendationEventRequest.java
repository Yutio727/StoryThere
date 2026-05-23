package com.example.storythere.api.model;

public class AudiobookRecommendationEventRequest {
    public long audiobookId;
    public String eventType;
    public Integer slotIndex;
    public Double score;
    public Double completion;

    public AudiobookRecommendationEventRequest(long audiobookId, String eventType) {
        this.audiobookId = audiobookId;
        this.eventType = eventType;
    }
}
