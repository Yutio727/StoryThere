package com.example.storythere.api.model;

public class UserLibraryStateRequest {
    public Boolean isFavourite;
    public Boolean isAlreadyRead;

    public UserLibraryStateRequest(Boolean isFavourite) {
        this.isFavourite = isFavourite;
    }

    public UserLibraryStateRequest(Boolean isFavourite, Boolean isAlreadyRead) {
        this.isFavourite = isFavourite;
        this.isAlreadyRead = isAlreadyRead;
    }
}
