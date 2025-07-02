package com.example.storythere;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import okhttp3.*;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HuggingFaceService {
    private static final String TAG = "HuggingFaceService";
    private static final String API_URL = "https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-dev";
    private static final String API_KEY = "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"; // Replace with your actual token
    
    private final OkHttpClient client;
    
    public interface BookCoverCallback {
        void onSuccess(Bitmap coverImage);
        void onError(String error);
    }
    
    private String cleanBookTitle(String title) {
        if (title == null) return "";
        
        // Remove file extensions
        String cleaned = title.replaceAll("\\.(pdf|epub|txt)$", "");
        
        // Remove extra whitespace
        cleaned = cleaned.trim();
        
        return cleaned;
    }
    
    private String buildPrompt(String bookTitle, String annotation) {
        String cleanedTitle = cleanBookTitle(bookTitle);
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("\"").append(cleanedTitle).append(" book cover. Author and Cover present on a cover. Style: double exposure, magical, ominous, night sky, aesthetic art, artistic, fine art photography, romantic, aesthetic, Canon EOS 5D photo sample, ISO 100, 35mm");
        
        // Add annotation if available and not empty
        if (annotation != null && !annotation.trim().isEmpty()) {
            // Truncate annotation if too long (to avoid overly long prompts)
            String truncatedAnnotation = annotation.length() > 200 ? 
                annotation.substring(0, 200) + "..." : annotation;
            prompt.append(", ").append(truncatedAnnotation);
        }
        
        prompt.append("\"");
        
        return prompt.toString();
    }
    
    public HuggingFaceService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public void generateBookCover(String bookTitle, String annotation, BookCoverCallback callback) {
        long startTime = System.currentTimeMillis();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        
        Log.i(TAG, "=== BOOK COVER GENERATION STARTED ===");
        Log.i(TAG, "Timestamp: " + timestamp);
        Log.i(TAG, "Original Book Title: " + bookTitle);
        Log.i(TAG, "Book Annotation: " + (annotation != null ? annotation : "null"));
        Log.i(TAG, "API Endpoint: " + API_URL);
        Log.i(TAG, "API Key (first 10 chars): " + API_KEY.substring(0, Math.min(10, API_KEY.length())) + "...");
        
        CompletableFuture.runAsync(() -> {
            try {
                // Build the prompt using title and annotation
                String prompt = buildPrompt(bookTitle, annotation);
                
                Log.i(TAG, "Cleaned Book Title: " + cleanBookTitle(bookTitle));
                Log.i(TAG, "Final Prompt: " + prompt);
                
                // Create the request body
                JSONObject requestBody = new JSONObject();
                requestBody.put("inputs", prompt);
                
                String requestBodyString = requestBody.toString();
                Log.i(TAG, "Request Body: " + requestBodyString);
                Log.i(TAG, "Prompt Length: " + prompt.length() + " characters");
                
                RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), 
                    requestBodyString
                );
                
                // Create the request
                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();
                
                Log.i(TAG, "Request Headers:");
                Log.i(TAG, "  Authorization: Bearer " + API_KEY.substring(0, Math.min(10, API_KEY.length())) + "...");
                Log.i(TAG, "  Content-Type: application/json");
                Log.i(TAG, "  User-Agent: " + request.header("User-Agent"));
                
                // Execute the request
                long requestStartTime = System.currentTimeMillis();
                Log.i(TAG, "Sending HTTP request...");
                
                try (Response response = client.newCall(request).execute()) {
                    long requestEndTime = System.currentTimeMillis();
                    long requestDuration = requestEndTime - requestStartTime;
                    
                    Log.i(TAG, "=== API RESPONSE RECEIVED ===");
                    Log.i(TAG, "Response Code: " + response.code());
                    Log.i(TAG, "Response Message: " + response.message());
                    Log.i(TAG, "Request Duration: " + requestDuration + "ms");
                    
                    // Log response headers for token usage info
                    Log.i(TAG, "Response Headers:");
                    Headers headers = response.headers();
                    for (int i = 0; i < headers.size(); i++) {
                        String name = headers.name(i);
                        String value = headers.value(i);
                        Log.i(TAG, "  " + name + ": " + value);
                    }
                    
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        Log.e(TAG, "=== API REQUEST FAILED ===");
                        Log.e(TAG, "Error Code: " + response.code());
                        Log.e(TAG, "Error Body: " + errorBody);
                        Log.e(TAG, "Total Duration: " + (System.currentTimeMillis() - startTime) + "ms");
                        callback.onError("API request failed: " + response.code());
                        return;
                    }
                    
                    // Get the response body as bytes
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        Log.e(TAG, "=== EMPTY RESPONSE ===");
                        Log.e(TAG, "Response body is null");
                        Log.e(TAG, "Total Duration: " + (System.currentTimeMillis() - startTime) + "ms");
                        callback.onError("Empty response from API");
                        return;
                    }
                    
                    byte[] imageBytes = responseBody.bytes();
                    Log.i(TAG, "=== IMAGE DATA RECEIVED ===");
                    Log.i(TAG, "Image Size: " + imageBytes.length + " bytes");
                    Log.i(TAG, "Image Size (KB): " + (imageBytes.length / 1024.0) + " KB");
                    Log.i(TAG, "Image Size (MB): " + (imageBytes.length / (1024.0 * 1024.0)) + " MB");
                    
                    // Convert bytes to Bitmap
                    Log.i(TAG, "Decoding image data...");
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap == null) {
                        Log.e(TAG, "=== IMAGE DECODE FAILED ===");
                        Log.e(TAG, "Failed to decode image data");
                        Log.e(TAG, "Total Duration: " + (System.currentTimeMillis() - startTime) + "ms");
                        callback.onError("Failed to decode image data");
                        return;
                    }
                    
                    long totalDuration = System.currentTimeMillis() - startTime;
                    Log.i(TAG, "=== BOOK COVER GENERATION SUCCESS ===");
                    Log.i(TAG, "Bitmap Width: " + bitmap.getWidth() + "px");
                    Log.i(TAG, "Bitmap Height: " + bitmap.getHeight() + "px");
                    Log.i(TAG, "Bitmap Config: " + bitmap.getConfig());
                    Log.i(TAG, "Total Duration: " + totalDuration + "ms");
                    Log.i(TAG, "Memory Usage: " + (bitmap.getByteCount() / 1024) + " KB");
                    Log.i(TAG, "=== END BOOK COVER GENERATION ===");
                    
                    callback.onSuccess(bitmap);
                    
                } catch (IOException e) {
                    long totalDuration = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "=== NETWORK ERROR ===");
                    Log.e(TAG, "Network error during API call", e);
                    Log.e(TAG, "Total Duration: " + totalDuration + "ms");
                    callback.onError("Network error: " + e.getMessage());
                }
                
            } catch (Exception e) {
                long totalDuration = System.currentTimeMillis() - startTime;
                Log.e(TAG, "=== GENERAL ERROR ===");
                Log.e(TAG, "Error generating book cover", e);
                Log.e(TAG, "Total Duration: " + totalDuration + "ms");
                callback.onError("Error: " + e.getMessage());
            }
        });
    }
} 