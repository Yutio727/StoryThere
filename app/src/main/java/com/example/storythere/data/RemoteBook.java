package com.example.storythere.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "remote_books")
public class RemoteBook {
    @PrimaryKey
    private long id;

    private String title;
    private String author;
    private String fileUrl;
    private String fileType;
    private String image;
    private String annotation;

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
}
