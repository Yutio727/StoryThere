package com.example.storythere.api.model;

public class BookRecommendationEventRequest {
    public long bookId;
    public String eventType;
    public Integer slotIndex;
    public Double score;
    public Double completion;

    public BookRecommendationEventRequest(long bookId, String eventType) {
        this.bookId = bookId;
        this.eventType = eventType;
    }
}
