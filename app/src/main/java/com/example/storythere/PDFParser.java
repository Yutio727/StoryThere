package com.example.storythere;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PDFParser {
    private static final String TAG = "PDFParser";

    public static class ParsedPage {
        public final String text;
        public final List<Bitmap> images;
        public final int pageNumber;

        public ParsedPage(String text, List<Bitmap> images, int pageNumber) {
            this.text = text;
            this.images = images;
            this.pageNumber = pageNumber;
        }
    }

    public static List<ParsedPage> parsePDF(Context context, Uri pdfUri) {
        List<ParsedPage> pages = new ArrayList<>();
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(pdfUri)) {
            if (inputStream == null) {
                Log.e(TAG, "Could not open PDF stream");
                return pages;
            }

            PdfReader reader = new PdfReader(inputStream);
            PdfDocument pdfDoc = new PdfDocument(reader);
            
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                // Try different text extraction strategies
                String text = "";
                try {
                    // First try with LocationTextExtractionStrategy
                    text = PdfTextExtractor.getTextFromPage(
                        pdfDoc.getPage(i),
                        new LocationTextExtractionStrategy()
                    );

                    // If text is empty or contains only special characters, try SimpleTextExtractionStrategy
                    if (text.trim().isEmpty() || containsOnlySpecialChars(text)) {
                        text = PdfTextExtractor.getTextFromPage(
                            pdfDoc.getPage(i),
                            new SimpleTextExtractionStrategy()
                        );
                    }

                    // Clean up the text
                    text = cleanText(text);
                } catch (Exception e) {
                    Log.e(TAG, "Error extracting text from page " + i + ": " + e.getMessage());
                }

                // Extract images
                List<Bitmap> images = new ArrayList<>();
                try {
                    PdfDictionary pageDict = pdfDoc.getPage(i).getPdfObject();
                    PdfDictionary resources = pageDict.getAsDictionary(PdfName.Resources);
                    if (resources != null) {
                        PdfDictionary xObjects = resources.getAsDictionary(PdfName.XObject);
                        if (xObjects != null) {
                            for (PdfName name : xObjects.keySet()) {
                                PdfStream stream = xObjects.getAsStream(name);
                                if (stream != null && PdfName.Image.equals(stream.getAsName(PdfName.Subtype))) {
                                    try {
                                        byte[] imageBytes = stream.getBytes();
                                        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                        if (bitmap != null) {
                                            images.add(bitmap);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error processing image: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error extracting images from page " + i + ": " + e.getMessage());
                }
                
                // Only add page if it has content
                if (!text.trim().isEmpty() || !images.isEmpty()) {
                    pages.add(new ParsedPage(text, images, i));
                }
            }
            
            pdfDoc.close();
            reader.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing PDF: " + e.getMessage(), e);
        }
        
        return pages;
    }

    private static boolean containsOnlySpecialChars(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        
        // Check if text contains mostly special characters or control characters
        int specialCharCount = 0;
        for (char c : text.toCharArray()) {
            if (c < 32 || (c > 126 && c < 1040) || c > 1103) { // ASCII + Cyrillic range
                specialCharCount++;
            }
        }
        
        return (double) specialCharCount / text.length() > 0.5;
    }

    private static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Replace common PDF artifacts
        text = text.replace("", "")
                  .replace("\u0000", "")
                  .replace("\uFFFD", "")
                  .replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]", ""); // Remove control characters

        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }
} 