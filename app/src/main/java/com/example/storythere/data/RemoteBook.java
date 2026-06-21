package com.example.storythere.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "remote_books", primaryKeys = {"id", "ownerUid"})
public class RemoteBook {
    private long id;

    @NonNull
    private String ownerUid = "";
    private String title;
    private String author;
    private String fileUrl;
    private String fileType;
    private String image;
    private String annotation;
    private String license;
    private int recommendationRank;
    private long cachedAtMillis;
    private String recommendationSource;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getAnnotation() { return annotation; }
    public void setAnnotation(String annotation) { this.annotation = annotation; }

    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }

    @NonNull
    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(@NonNull String ownerUid) { this.ownerUid = ownerUid; }

    public int getRecommendationRank() { return recommendationRank; }
    public void setRecommendationRank(int recommendationRank) { this.recommendationRank = recommendationRank; }

    public long getCachedAtMillis() { return cachedAtMillis; }
    public void setCachedAtMillis(long cachedAtMillis) { this.cachedAtMillis = cachedAtMillis; }

    public String getRecommendationSource() { return recommendationSource; }
    public void setRecommendationSource(String recommendationSource) { this.recommendationSource = recommendationSource; }
}
