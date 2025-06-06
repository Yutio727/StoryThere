package com.example.storythere;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;

public class EPUBParser {
    private static final String TAG = "EPUBParser";
    private Context context;
    private Book book;
    private List<String> textContent;
    private List<Bitmap> images;

    public EPUBParser(Context context) {
        this.context = context;
        this.textContent = new ArrayList<>();
        this.images = new ArrayList<>();
    }

    public boolean parse(Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for EPUB file");
                return false;
            }

            EpubReader epubReader = new EpubReader();
            book = epubReader.readEpub(inputStream);
            inputStream.close();

            // Extract text content
            for (Resource resource : book.getContents()) {
                if (resource.getMediaType().toString().contains("html")) {
                    String content = new String(resource.getData());
                    // Basic HTML cleaning - you might want to use a proper HTML parser
                    content = content.replaceAll("<[^>]*>", "");
                    textContent.add(content);
                }
            }

            // Extract images
            for (Resource resource : book.getResources().getAll()) {
                if (resource.getMediaType().toString().contains("image")) {
                    byte[] imageData = resource.getData();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                    if (bitmap != null) {
                        images.add(bitmap);
                    }
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing EPUB file: " + e.getMessage());
            return false;
        }
    }

    public List<String> getTextContent() {
        return textContent;
    }

    public List<Bitmap> getImages() {
        return images;
    }

    public String getTitle() {
        return book != null ? book.getTitle() : "";
    }

    public String getAuthor() {
        return book != null && !book.getMetadata().getAuthors().isEmpty() 
            ? book.getMetadata().getAuthors().get(0).toString() 
            : "";
    }
} 