package com.example.storythere.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;
import java.util.Date;

@Entity(tableName = "authors")
public class Author {
    @PrimaryKey
    @NonNull
    private String authorId;
    
    private String name;
    private String biography;
    private String birthDate;
    private String deathDate; // optional
    private String nationality;
    private String photoUrl;
    private int totalBooks;
    
    public Author() {
        // Required for Firestore deserialization
    }
    
    @Ignore
    public Author(String authorId, String name, String biography) {
        this.authorId = authorId;
        this.name = name;
        this.biography = biography;
        this.totalBooks = 0;
    }
    
    // Getters and Setters
    @NonNull
    public String getAuthorId() { return authorId; }
    public void setAuthorId(@NonNull String authorId) { this.authorId = authorId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }
    
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    
    // Firebase field mapping for birthdayDate
    public String getBirthdayDate() { return birthDate; }
    public void setBirthdayDate(String birthdayDate) { this.birthDate = birthdayDate; }
    
    public String getDeathDate() { return deathDate; }
    public void setDeathDate(String deathDate) { this.deathDate = deathDate; }
    
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    
    public int getTotalBooks() { return totalBooks; }
    public void setTotalBooks(int totalBooks) { this.totalBooks = totalBooks; }
    
    // Helper methods
    public String getLifeSpan() {
        if (birthDate == null) return "";
        if (deathDate == null) {
            return birthDate + " - Present";
        }
        return birthDate + " - " + deathDate;
    }
} 