package com.example.storythere;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy;
import com.itextpdf.kernel.geom.Rectangle;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PDFParser {
    private static final String TAG = "PDFParser";
    private static final float SCALE_FACTOR = 3.0f; // Scale images up by 2x

    public static class ParsedPage {
        public final String text;
        public final List<Bitmap> images;
        public final int pageNumber;
        public final float pageWidth;
        public final float pageHeight;

        public ParsedPage(String text, List<Bitmap> images, int pageNumber, float pageWidth, float pageHeight) {
            this.text = text;
            this.images = images;
            this.pageNumber = pageNumber;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
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
                PdfPage page = pdfDoc.getPage(i);
                Rectangle pageSize = page.getPageSize();
                
                String text = "";
                try {
                    // Use LocationTextExtractionStrategy to preserve text positioning
                    LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                    text = PdfTextExtractor.getTextFromPage(page, strategy);
                    
                    // If no text found, try SimpleTextExtractionStrategy
                    if (text.trim().isEmpty()) {
                        text = PdfTextExtractor.getTextFromPage(page, new SimpleTextExtractionStrategy());
                    }

                    // Clean up the text while preserving line breaks
                    text = cleanText(text);
                } catch (Exception e) {
                    Log.e(TAG, "Error extracting text from page " + i + ": " + e.getMessage());
                }

                // Extract images
                List<Bitmap> images = new ArrayList<>();
                try {
                    PdfDictionary pageDict = page.getPdfObject();
                    PdfDictionary resources = pageDict.getAsDictionary(PdfName.Resources);
                    if (resources != null) {
                        PdfDictionary xObjects = resources.getAsDictionary(PdfName.XObject);
                        if (xObjects != null) {
                            for (PdfName name : xObjects.keySet()) {
                                PdfStream stream = xObjects.getAsStream(name);
                                if (stream != null && PdfName.Image.equals(stream.getAsName(PdfName.Subtype))) {
                                    try {
                                        PdfImageXObject image = new PdfImageXObject(stream);
                                        byte[] imageBytes = image.getImageBytes();
                                        if (imageBytes != null && imageBytes.length > 0) {
                                            // Get image dimensions from PDF
                                            int width = (int) image.getWidth();
                                            int height = (int) image.getHeight();
                                            
                                            // Create bitmap with original dimensions
                                            BitmapFactory.Options options = new BitmapFactory.Options();
                                            options.inSampleSize = 1; // No downsampling
                                            options.inScaled = false; // Don't scale
                                            options.inDensity = 0; // Use original density
                                            options.inTargetDensity = 0; // Use original density
                                            
                                            Bitmap originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
                                            if (originalBitmap != null) {
                                                // Scale up the bitmap
                                                Matrix matrix = new Matrix();
                                                matrix.postScale(SCALE_FACTOR, SCALE_FACTOR);
                                                Bitmap scaledBitmap = Bitmap.createBitmap(
                                                    originalBitmap, 
                                                    0, 0, 
                                                    originalBitmap.getWidth(), 
                                                    originalBitmap.getHeight(), 
                                                    matrix, 
                                                    true
                                                );
                                                originalBitmap.recycle(); // Free up memory
                                                images.add(scaledBitmap);
                                            }
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
                    pages.add(new ParsedPage(text, images, i, pageSize.getWidth(), pageSize.getHeight()));
                }
            }
            
            pdfDoc.close();
            reader.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing PDF: " + e.getMessage(), e);
        }
        
        return pages;
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
} 