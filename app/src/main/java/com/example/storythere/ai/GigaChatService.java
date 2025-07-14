package com.example.storythere.ai;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.util.Properties;
public class GigaChatService {
    private static final String TAG = "GigaChatService";
    
    // Server URLs
    private static final String CHAT_COMPLETIONS_BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    private static final String AUTH_TOKEN_ENDPOINT = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    
    // Timeout settings (in milliseconds)
    private static final int CONNECTION_TIMEOUT_MS = 180_000; // 5 minutes
    
    // Your API key (base64 encoded) - will be loaded from secure source
    private static final String ENCODED_API_KEY = getApiKey();
    
    private final ExecutorService executor;
    
    public interface SummarizationCallback {
        void onSuccess(String summary);
        void onError(String error);
    }
    
    public GigaChatService() {
        this.executor = Executors.newSingleThreadExecutor();
        configureSSL();
    }
    
    private static String getApiKey() {
        return "M2NmMTNhZWUtOGU4Zi00MDQ2LTg1ZGUtMzU2ODJhZmNlZDkzOjE4MGNkZjRlLTljNzAtNDRkNS1iNzA2LWVlNTNlMGFmNWI2YQ==";
    }
    
    private void configureSSL() {
        try {
            // Create a trust manager that trusts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Set the default SSL socket factory
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            // Set default hostname verifier
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            Log.e(TAG, "Error configuring SSL: " + e.getMessage());
        }
    }
    
    public void summarizeText(String text, SummarizationCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onError("Text is empty");
            return;
        }
        
        // Clean and truncate text if needed
        String cleanedText = cleanText(text);
        String truncatedText = truncateText(cleanedText);
        
        Log.d(TAG, "Text to summarize (preview): " + 
            (truncatedText.length() > 200 ? truncatedText.substring(0, 200) + "..." : truncatedText));
        Log.d(TAG, "Text length: " + truncatedText.length());
        
        executor.execute(() -> {
            int maxRetries = 3;
            int retryCount = 0;
            
            while (retryCount < maxRetries) {
                try {
                    String token = obtainAccessToken();
                    String summary = sendChatCompletionRequest(token, truncatedText);
                    callback.onSuccess(summary);
                    return;
                } catch (Exception e) {
                    retryCount++;
                    Log.e(TAG, "Error in summarization (attempt " + retryCount + "): " + e.getMessage());
                    
                    if (retryCount >= maxRetries) {
                        callback.onError("Failed after " + maxRetries + " attempts: " + e.getMessage());
                    } else {
                        // Wait before retrying
                        try {
                            Thread.sleep(2000 * retryCount); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            callback.onError("Operation interrupted");
                            return;
                        }
                    }
                }
            }
        });
    }
    
    private String cleanText(String text) {
        if (text == null) return "";
        
        // Remove excessive whitespace and normalize
        text = text.replaceAll("\\s+", " ").trim();
        
        // Remove excessive newlines
        text = text.replaceAll("\n{3,}", "\n\n");
        
        return text;
    }
    
    private String truncateText(String text) {
        // Reduce to ~1000 tokens (approximately 4000 characters for Russian text)
        int maxLength = 500; // Reduced from 15K to 4K characters
        
        if (text.length() <= maxLength) {
            return text;
        }
        
        // Try to truncate at a sentence boundary
        String truncated = text.substring(0, maxLength);
        int lastSentenceEnd = Math.max(
            truncated.lastIndexOf(". "),
            Math.max(
                truncated.lastIndexOf("! "),
                truncated.lastIndexOf("? ")
            )
        );
        
        if (lastSentenceEnd > maxLength * 0.8) {
            return truncated.substring(0, lastSentenceEnd + 1);
        }
        
        return truncated + "...";
    }
    
    private String obtainAccessToken() throws IOException {
        Log.d(TAG, "Attempting to obtain access token...");
        Log.d(TAG, "Using API key: " + ENCODED_API_KEY);
        
        URL authUrl = new URL(AUTH_TOKEN_ENDPOINT);
        HttpsURLConnection connection = (HttpsURLConnection) authUrl.openConnection();
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setUseCaches(false);
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(CONNECTION_TIMEOUT_MS);
        connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("RqUID", "3402d161-73ac-4aee-8aa2-86157f49d06c");
        connection.addRequestProperty("Authorization", "Basic " + ENCODED_API_KEY);
        connection.addRequestProperty("User-Agent", "StoryThere/1.0");
        connection.addRequestProperty("Connection", "close");

        Log.d(TAG, "Making authentication request to: " + AUTH_TOKEN_ENDPOINT);

        try (OutputStream os = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os))) {
            writer.write("scope=GIGACHAT_API_PERS");
            writer.flush();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            
            String responseBody = builder.toString();
            Log.d(TAG, "Authentication response: " + responseBody);
            
            try {
                JSONObject jsonResponse = new JSONObject(responseBody);
                String accessToken = jsonResponse.getString("access_token");
                Log.d(TAG, "Access token obtained successfully");
                Log.d(TAG, "Access token: " + accessToken);
                return accessToken;
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse authentication response: " + responseBody);
                throw new IOException("Failed to parse JSON response: " + e.getMessage());
            }
        }
    }
    
    private String sendChatCompletionRequest(String token, String text) throws IOException {
        JSONObject requestBody = buildRequestBody(text);
        URL url = new URL(CHAT_COMPLETIONS_BASE_URL);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setUseCaches(false);
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(CONNECTION_TIMEOUT_MS);
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Authorization", "Bearer " + token);
        connection.addRequestProperty("User-Agent", "StoryThere/1.0");
        connection.addRequestProperty("Connection", "close");

        try (OutputStream os = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os))) {
            writer.write(requestBody.toString());
            writer.flush();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            
            try {
                JSONObject response = new JSONObject(builder.toString());
                Log.d(TAG, "GigaChat response: " + response.toString());
                
                // Extract the summary from the response
                JSONArray choices = response.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject message = choice.getJSONObject("message");
                    return message.getString("content");
                } else {
                    throw new IOException("No response content found");
                }
            } catch (JSONException e) {
                throw new IOException("Failed to parse JSON response: " + e.getMessage());
            }
        }
    }
    
    private JSONObject buildRequestBody(String text) {
        try {
            JSONArray messages = new JSONArray();
            
            // System message to instruct the AI - made more concise
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Создай краткую аннотацию книги на русском языке. Включи основные темы и идеи.");
            messages.put(systemMessage);

            // User message with the text to summarize
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", "Аннотация для:\n" + text);
            messages.put(userMessage);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gigachat-2");
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 300); // Reduced from 1000 to 300
            requestBody.put("temperature", 0.7); // Balanced creativity and accuracy
            
            return requestBody;
        } catch (JSONException e) {
            Log.e(TAG, "Error building request body: " + e.getMessage());
            throw new RuntimeException("Failed to build request body", e);
        }
    }
    
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
} 