package com.example.storythere;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public class TextParser {
    private static final Pattern RUSSIAN_PATTERN = Pattern.compile("[а-яА-ЯёЁ]");

    public static class ParsedText {
        public final String content;
        public final boolean isRussian;

        public ParsedText(String content, boolean isRussian) {
            this.content = content;
            this.isRussian = isRussian;
        }
    }

    public static ParsedText parseText(Context context, Uri uri) {
        StringBuilder text = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ParsedText("", false);
        }

        String content = cleanText(text.toString());
        boolean isRussian = isTextPrimarilyRussian(content);
        return new ParsedText(content, isRussian);
    }

    private static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Only remove null bytes and replacement characters
        text = text.replace("\u0000", "")
                  .replace("\uFFFD", "");
        
        // Normalize line endings
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        
        // Remove multiple consecutive line breaks (keep at most 2)
        text = text.replaceAll("\n{3,}", "\n\n");
        
        // Clean up each line while preserving internal spacing
        String[] lines = text.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleaned.append(trimmed).append("\n");
            }
        }
        
        return cleaned.toString().trim();
    }

    static boolean isTextPrimarilyRussian(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Count Russian characters
        int russianCharCount = 0;
        int totalCharCount = 0;

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                totalCharCount++;
                if (RUSSIAN_PATTERN.matcher(String.valueOf(c)).matches()) {
                    russianCharCount++;
                }
            }
        }

        // If no letters found, default to English
        if (totalCharCount == 0) {
            return false;
        }

        // Calculate percentage of Russian characters
        double russianPercentage = (double) russianCharCount / totalCharCount;

        // If more than 30% of characters are Russian, consider it Russian text
        return russianPercentage > 0.3;
    }
} 