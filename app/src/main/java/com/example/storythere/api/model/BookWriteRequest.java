package com.example.storythere.api.model;

import java.util.List;

public class BookWriteRequest {
    public Long recommendationItemId;
    public String title;
    public String author;
    public Long authorID;
    public Integer publicationYear;
    public String annotation;
    public String image;
    public String license;
    public String fileType;
    public String fileUrl;
    public List<String> genres;
}
