package com.example.storythere.api.model;

import java.util.List;

public class AudiobookWriteRequest {
    public Long recommendationItemId;
    public String title;
    public String author;
    public String dictor;
    public Long authorID;
    public Integer publicationYear;
    public String annotation;
    public String image;
    public String license;
    public String audioType;
    public String audioUrl;
    public Integer durationSeconds;
    public String sourceTextUrl;
    public String sourceUrl;
    public List<String> genres;
}
