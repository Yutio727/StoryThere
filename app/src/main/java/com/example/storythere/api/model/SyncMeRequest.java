package com.example.storythere.api.model;

import com.google.gson.annotations.SerializedName;

public class SyncMeRequest {
    @SerializedName("date_of_birth")
    public String dateOfBirth;

    @SerializedName("recommendationAgeBucket")
    public String recommendationAgeBucket;
}
