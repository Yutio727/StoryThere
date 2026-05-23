package com.example.storythere.api.model;

public class BookInteractionRequest {
    public long bookId;
    public double progress;
    public Double rating;
    public String startDate;

    public BookInteractionRequest(long bookId, double progress) {
        this.bookId = bookId;
        this.progress = progress;
    }
}
