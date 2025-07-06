package com.example.storythere.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
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
    private boolean isFavourite;
    private boolean isAlreadyRead;
    private String parsedTextPath;
    private String image; // for server-side field
    private int readingPosition; // Store reading position for all document types
    private String timeOfListen; // for word count/listening time
    private String authorId; // Link to author collection
    
    @Ignore
    public Book(String title, String author, String filePath, String fileType) {
        this.title = title;
        this.author = author;
        this.filePath = filePath;
        this.fileType = fileType;
        this.lastOpened = new Date();
        this.currentPage = 0;
        this.isFavourite = false;
        this.isAlreadyRead = false;
        this.readingPosition = 0;
    }
    
    public Book() {
        // Required for Firestore deserialization
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

    public boolean isFavourite() { return isFavourite; }
    public void setFavourite(boolean favourite) { isFavourite = favourite; }

    public boolean isAlreadyRead() { return isAlreadyRead; }
    public void setAlreadyRead(boolean alreadyRead) { isAlreadyRead = alreadyRead; }

    public String getParsedTextPath() { return parsedTextPath; }
    public void setParsedTextPath(String parsedTextPath) { this.parsedTextPath = parsedTextPath; }

    public int getReadingPosition() { return readingPosition; }
    public void setReadingPosition(int readingPosition) { this.readingPosition = readingPosition; }

    // For server books, use previewImagePath as the image
    public String getImage() { return image != null ? image : previewImagePath; }
    public void setImage(String image) { this.image = image; }

    public String getTimeOfListen() { return timeOfListen; }
    public void setTimeOfListen(String timeOfListen) { this.timeOfListen = timeOfListen; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
} 