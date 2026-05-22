package com.example.storythere.api.model;

import com.google.gson.annotations.SerializedName;

public class ApiUser {
    public long id;

    @SerializedName("firebase_uid")
    public String firebaseUid;

    public String email;
    public String displayName;
    public String role;
    public String photoURL;
    public String dateOfBirth;
    public String sex;

    @SerializedName("recommendationAgeBucket")
    public String recommendationAgeBucket;

    public String createdAt;
    public String lastLoginAt;
    public String updatedAt;
}
