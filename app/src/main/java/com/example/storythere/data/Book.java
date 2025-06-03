package com.example.storythere.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "books")
public class Book {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private final String title;
    private final String author;
    private final String filePath;
    private final String fileType;
    private String annotation;
    private String previewImagePath;
    private Date lastOpened;
    private int currentPage;
    private boolean isFavourite;
    private boolean isAlreadyRead;
    
    public Book(String title, String author, String filePath, String fileType) {
        this.title = title;
        this.author = author;
        this.filePath = filePath;
        this.fileType = fileType;
        this.lastOpened = new Date();
        this.currentPage = 0;
        this.isFavourite = false;
        this.isAlreadyRead = false;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getTitle() { return title; }

    public String getAuthor() { return author; }

    public String getFilePath() { return filePath; }

    public String getFileType() { return fileType; }

    public String getAnnotation() { return annotation; }
    public void setAnnotation(String annotation) { this.annotation = annotation; }
    
    public String getPreviewImagePath() { return previewImagePath; }
    public void setPreviewImagePath(String previewImagePath) { this.previewImagePath = previewImagePath; }
    
    public Date getLastOpened() { return lastOpened; }
    public void setLastOpened(Date lastOpened) { this.lastOpened = lastOpened; }
    
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }

    public boolean isFavourite() { return isFavourite; }
    public void setFavourite(boolean favourite) { isFavourite = favourite; }

    public boolean isAlreadyRead() { return isAlreadyRead; }
    public void setAlreadyRead(boolean alreadyRead) { isAlreadyRead = alreadyRead; }
} 