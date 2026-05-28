package com.example.storythere.api.model;

public class ApiUserAudiobook extends ApiAudiobook {
    public long userAudiobookId;
    public double progress;
    public Double rating;
    public boolean isFavourite;
    public long playbackPositionMs;
    public String completedAt;
    public String addedAt;
    public String lastOpenedAt;
    public String libraryUpdatedAt;
}
