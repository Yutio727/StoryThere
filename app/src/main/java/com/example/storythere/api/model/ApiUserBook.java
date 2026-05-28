package com.example.storythere.api.model;

public class ApiUserBook extends ApiBook {
    public long userBookId;
    public double progress;
    public Double rating;
    public boolean isFavourite;
    public boolean isAlreadyRead;
    public String addedAt;
    public String lastOpenedAt;
    public String libraryUpdatedAt;
}
