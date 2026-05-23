package com.example.storythere.api.model;

public class AudiobookInteractionRequest {
    public long audiobookId;
    public double progress;
    public Double rating;
    public String startDate;
    public long playbackPositionMs;
    public String completedAt;

    public AudiobookInteractionRequest(long audiobookId, double progress, long playbackPositionMs) {
        this.audiobookId = audiobookId;
        this.progress = progress;
        this.playbackPositionMs = playbackPositionMs;
    }
}
