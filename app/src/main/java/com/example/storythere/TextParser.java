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
                text.append(line).append(" ");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ParsedText("", false);
        }

        String content = text.toString();
        boolean isRussian = isTextPrimarilyRussian(content);
        return new ParsedText(content, isRussian);
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