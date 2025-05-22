package com.example.storythere;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Paint;
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
import com.itextpdf.kernel.pdf.PdfArray;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PDFParser {
    private static final String TAG = "PDFParser";
    private static final float SCALE_FACTOR = 1.0f;
    private PdfDocument pdfDoc;
    private PdfReader reader;

    public PDFParser(Context context, Uri pdfUri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(pdfUri);
        if (inputStream == null) {
            throw new Exception("Could not open PDF stream");
        }
        reader = new PdfReader(inputStream);
        pdfDoc = new PdfDocument(reader);
    }

    public void close() {
        try {
            if (pdfDoc != null) {
                pdfDoc.close();
            }
            if (reader != null) {
                reader.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing PDF resources: " + e.getMessage());
        }
    }

    public int getPageCount() {
        return pdfDoc != null ? pdfDoc.getNumberOfPages() : 0;
    }

    public ParsedPage parsePage(int pageNumber, TextSettings settings) {
        if (pdfDoc == null || pageNumber < 1 || pageNumber > pdfDoc.getNumberOfPages()) {
            return null;
        }

        try {
            Log.d(TAG, "Processing page " + pageNumber);
            PdfPage page = pdfDoc.getPage(pageNumber);
            Rectangle pageSize = page.getPageSize();
            
            String text = "";
            try {
                // Use LocationTextExtractionStrategy to preserve text positioning
                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                text = PdfTextExtractor.getTextFromPage(page, strategy);
                
                // If no text found, try SimpleTextExtractionStrategy
                if (text.trim().isEmpty()) {
                    Log.d(TAG, "No text found with LocationTextExtractionStrategy, trying SimpleTextExtractionStrategy");
                    text = PdfTextExtractor.getTextFromPage(page, new SimpleTextExtractionStrategy());
                }

                // Clean up the text while preserving line breaks
                text = cleanText(text);
                Log.d(TAG, "Extracted text length: " + text.length());
            } catch (Exception e) {
                Log.e(TAG, "Error extracting text from page " + pageNumber + ": " + e.getMessage());
            }

            // Extract images
            List<ImageInfo> images = new ArrayList<>();
            try {
                PdfDictionary pageDict = page.getPdfObject();
                PdfDictionary resources = pageDict.getAsDictionary(PdfName.Resources);
                if (resources != null) {
                    PdfDictionary xObjects = resources.getAsDictionary(PdfName.XObject);
                    if (xObjects != null) {
                        Log.d(TAG, "Found " + xObjects.size() + " XObjects on page " + pageNumber);
                        for (PdfName name : xObjects.keySet()) {
                            PdfStream stream = xObjects.getAsStream(name);
                            if (stream != null && PdfName.Image.equals(stream.getAsName(PdfName.Subtype))) {
                                try {
                                    PdfImageXObject image = new PdfImageXObject(stream);
                                    byte[] imageBytes = image.getImageBytes();
                                    if (imageBytes != null && imageBytes.length > 0) {
                                        Log.d(TAG, "Found image of size " + imageBytes.length + " bytes");
                                        
                                        // Get image dimensions and position from PDF
                                        float width = image.getWidth();
                                        float height = image.getHeight();
                                        float x = 0; // Default position
                                        float y = pageSize.getHeight() - height; // Default position
                                        
                                        // Try to get actual position from the stream
                                        PdfDictionary imageDict = stream.getAsDictionary(PdfName.BBox);
                                        if (imageDict != null) {
                                            PdfArray bbox = imageDict.getAsArray(PdfName.BBox);
                                            if (bbox != null && bbox.size() == 4) {
                                                x = bbox.getAsNumber(0).floatValue();
                                                y = pageSize.getHeight() - bbox.getAsNumber(3).floatValue();
                                                width = bbox.getAsNumber(2).floatValue() - x;
                                                height = bbox.getAsNumber(3).floatValue() - bbox.getAsNumber(1).floatValue();
                                            }
                                        }
                                        
                                        // Create bitmap with original dimensions
                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inSampleSize = 1; // No downsampling
                                        options.inScaled = false; // Don't scale
                                        options.inDensity = 0;
                                        options.inTargetDensity = 0; // Use original density
                                        
                                        Bitmap originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
                                        if (originalBitmap != null) {
                                            Log.d(TAG, "Successfully decoded image: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
                                            
                                            // Add image with its position (no scaling here, handled in PDFView)
                                            images.add(new ImageInfo(originalBitmap, x, y, width, height));
                                            Log.d(TAG, "Added image at position: " + x + "," + y);
                                        } else {
                                            Log.e(TAG, "Failed to decode image");
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
                Log.e(TAG, "Error extracting images from page " + pageNumber + ": " + e.getMessage());
            }
            
            // Only return page if it has content
            if (!text.trim().isEmpty() || !images.isEmpty()) {
                Log.d(TAG, "Adding page " + pageNumber + " with " + text.length() + " chars and " + images.size() + " images");
                return new ParsedPage(text, images, pageNumber, pageSize.getWidth(), pageSize.getHeight(), settings);
            } else {
                Log.d(TAG, "Skipping empty page " + pageNumber);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing page " + pageNumber + ": " + e.getMessage());
            return null;
        }
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

    public static class TextSettings {
        public float fontSize;
        public float letterSpacing;
        public Paint.Align textAlignment;
        public float lineHeight;
        public float paragraphSpacing;

        public TextSettings() {
            // Default values
            this.fontSize = 54.0f;
            this.letterSpacing = 0.0f;
            this.textAlignment = Paint.Align.LEFT;
            this.lineHeight = 1.2f;  // 120% of font size
            this.paragraphSpacing = 1.5f;  // 150% of line height
        }

        public TextSettings(float fontSize, float letterSpacing, Paint.Align textAlignment, 
                          float lineHeight, float paragraphSpacing) {
            this.fontSize = fontSize;
            this.letterSpacing = letterSpacing;
            this.textAlignment = textAlignment;
            this.lineHeight = lineHeight;
            this.paragraphSpacing = paragraphSpacing;
        }
    }

    public static class ParsedPage {
        public final String text;
        public final List<ImageInfo> images;
        public final int pageNumber;
        public final float pageWidth;
        public final float pageHeight;
        public final TextSettings textSettings;

        public ParsedPage(String text, List<ImageInfo> images, int pageNumber, float pageWidth, 
                         float pageHeight, TextSettings textSettings) {
            this.text = text;
            this.images = images;
            this.pageNumber = pageNumber;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.textSettings = textSettings;
        }
    }

    public static class ImageInfo {
        public final Bitmap bitmap;
        public final float x;
        public final float y;
        public final float width;
        public final float height;

        public ImageInfo(Bitmap bitmap, float x, float y, float width, float height) {
            this.bitmap = bitmap;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
} 