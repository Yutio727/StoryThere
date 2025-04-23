package com.example.storythere.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "books")
public class Book {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String title;
    private String author;
    private String filePath;
    private String fileType;
    private String annotation;
    private String previewImagePath;
    private Date lastOpened;
    private int currentPage;
    
    public Book(String title, String author, String filePath, String fileType) {
        this.title = title;
        this.author = author;
        this.filePath = filePath;
        this.fileType = fileType;
        this.lastOpened = new Date();
        this.currentPage = 0;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getAnnotation() { return annotation; }
    public void setAnnotation(String annotation) { this.annotation = annotation; }
    
    public String getPreviewImagePath() { return previewImagePath; }
    public void setPreviewImagePath(String previewImagePath) { this.previewImagePath = previewImagePath; }
    
    public Date getLastOpened() { return lastOpened; }
    public void setLastOpened(Date lastOpened) { this.lastOpened = lastOpened; }
    
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
} 