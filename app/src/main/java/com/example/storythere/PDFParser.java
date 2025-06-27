package com.example.storythere;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import android.text.StaticLayout;

public class PDFParser {
    private static final String TAG = "PDFParser";
    private static final float SCALE_FACTOR = 1.0f;
    private static final long MAX_FILE_SIZE_FOR_IMAGES = 20 * 1024 * 1024; // 20MB in bytes
    private final Context context;
    private final Uri pdfUri;
    private PdfDocument pdfDoc;
    private PdfReader reader;
    private long fileSize;

    public PDFParser(Context context, Uri pdfUri) throws IOException {
        this.context = context;
        this.pdfUri = pdfUri;
        
        // Get file size
        try (InputStream inputStream = context.getContentResolver().openInputStream(pdfUri)) {
            if (inputStream != null) {
                this.fileSize = inputStream.available();
            }
        }
        
        InputStream inputStream = context.getContentResolver().openInputStream(pdfUri);
        if (inputStream == null) {
            throw new IOException("Failed to open PDF file");
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

    public PdfDocument getPdfDocument() {
        return pdfDoc;
    }

    private boolean shouldLoadImages() {
        return fileSize <= MAX_FILE_SIZE_FOR_IMAGES;
    }

    public ParsedPage parsePage(int pageNumber, TextSettings settings) {
        if (pdfDoc == null || pageNumber < 1 || pageNumber > pdfDoc.getNumberOfPages()) {
            return null;
        }

        try {
            Log.d(TAG, "Processing page " + pageNumber);
            PdfPage page = pdfDoc.getPage(pageNumber);
            Rectangle pageSize = page.getPageSize();
            
            // Extract text
            String text = extractText(page);
            if (text != null && !text.trim().isEmpty()) {
                Log.d(TAG, "Extracted text length: " + text.length());
            } else {
                Log.d(TAG, "No text found on page " + pageNumber);
            }

            // Extract images
            List<ImageInfo> images = new ArrayList<>();

            // Only load images if file size is within limit
            if (shouldLoadImages()) {
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
                                            
                                            // Log image format information
                                            try {
                                                String format = getImageFormat(imageBytes);
                                                Log.d(TAG, "Image format detected: " + format);
                                                
                                                // Skip certain problematic formats that Android doesn't handle well
                                                if (format.equals("Unknown") || format.equals("Unknown (too small)")) {
                                                    Log.w(TAG, "Skipping image with unknown/unsupported format");
                                                    continue;
                                                }
                                                
                                                // Log detailed diagnostics
                                                String diagnostics = getImageDiagnostics(imageBytes);
                                                Log.d(TAG, "Image diagnostics: " + diagnostics);
                                            } catch (Exception e) {
                                                Log.d(TAG, "Could not determine image format");
                                            }

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

                                            // Try to decode the image with better error handling
                                            Bitmap originalBitmap = null;
                                            String errorMessage = null;
                                            
                                            try {
                                                // First attempt: Try with default settings
                                                BitmapFactory.Options options = new BitmapFactory.Options();
                                                options.inSampleSize = 1;
                                                options.inScaled = false;
                                                options.inDensity = 0;
                                                options.inTargetDensity = 0;
                                                options.inMutable = true;
                                                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                                                
                                                // Check bounds first
                                                options.inJustDecodeBounds = true;
                                                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
                                                
                                                if (options.outWidth > 0 && options.outHeight > 0) {
                                                    // Check if image is too large and needs downsampling
                                                    int maxDimension = 2048; // Max dimension to prevent memory issues
                                                    int sampleSize = 1;
                                                    while ((options.outWidth / sampleSize) > maxDimension || 
                                                           (options.outHeight / sampleSize) > maxDimension) {
                                                        sampleSize *= 2;
                                                    }
                                                    
                                                    if (sampleSize > 1) {
                                                        Log.d(TAG, "Image is large (" + options.outWidth + "x" + options.outHeight + 
                                                              "), using sample size " + sampleSize + " for downsampling");
                                                    }
                                                    
                                                    // Valid dimensions, try to decode
                                                    options.inJustDecodeBounds = false;
                                                    options.inSampleSize = sampleSize;
                                                    originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
                                                    
                                                    if (originalBitmap == null) {
                                                        errorMessage = "Failed to decode image with default settings";
                                                    }
                                                } else {
                                                    errorMessage = "Invalid image dimensions: " + options.outWidth + "x" + options.outHeight;
                                                }
                                            } catch (OutOfMemoryError e) {
                                                errorMessage = "Out of memory while decoding image";
                                                Log.e(TAG, "OutOfMemoryError decoding image: " + e.getMessage());
                                            } catch (Exception e) {
                                                errorMessage = "Exception while decoding image: " + e.getMessage();
                                                Log.e(TAG, "Exception decoding image: " + e.getMessage());
                                            }
                                            
                                            // If first attempt failed, try with different settings
                                            if (originalBitmap == null) {
                                                try {
                                                    Log.d(TAG, "First attempt failed: " + errorMessage + ". Trying with RGB_565 config...");
                                                    
                                                    BitmapFactory.Options fallbackOptions = new BitmapFactory.Options();
                                                    fallbackOptions.inSampleSize = 1;
                                                    fallbackOptions.inScaled = false;
                                                    fallbackOptions.inDensity = 0;
                                                    fallbackOptions.inTargetDensity = 0;
                                                    fallbackOptions.inMutable = true;
                                                    fallbackOptions.inPreferredConfig = Bitmap.Config.RGB_565; // Try RGB_565 instead
                                                    
                                                    originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, fallbackOptions);
                                                    
                                                    if (originalBitmap != null) {
                                                        Log.d(TAG, "Successfully decoded image with RGB_565 config: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
                                                    } else {
                                                        Log.e(TAG, "Failed to decode image with RGB_565 config as well");
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Fallback decoding also failed: " + e.getMessage());
                                                }
                                            }
                                            
                                            // If still failed, try with downsampling
                                            if (originalBitmap == null) {
                                                try {
                                                    Log.d(TAG, "Trying with aggressive downsampling (sample size 4)...");
                                                    
                                                    BitmapFactory.Options downsampleOptions = new BitmapFactory.Options();
                                                    downsampleOptions.inSampleSize = 4; // Aggressive downsampling
                                                    downsampleOptions.inScaled = false;
                                                    downsampleOptions.inDensity = 0;
                                                    downsampleOptions.inTargetDensity = 0;
                                                    downsampleOptions.inMutable = true;
                                                    downsampleOptions.inPreferredConfig = Bitmap.Config.RGB_565;
                                                    
                                                    originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, downsampleOptions);
                                                    
                                                    if (originalBitmap != null) {
                                                        Log.d(TAG, "Successfully decoded image with downsampling: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
                                                    } else {
                                                        Log.e(TAG, "Failed to decode image even with downsampling");
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Downsampling attempt also failed: " + e.getMessage());
                                                }
                                            }
                                            
                                            // Last resort: try with different color configs
                                            if (originalBitmap == null) {
                                                try {
                                                    Log.d(TAG, "Trying with ALPHA_8 config as last resort...");
                                                    
                                                    BitmapFactory.Options alphaOptions = new BitmapFactory.Options();
                                                    alphaOptions.inSampleSize = 1;
                                                    alphaOptions.inScaled = false;
                                                    alphaOptions.inDensity = 0;
                                                    alphaOptions.inTargetDensity = 0;
                                                    alphaOptions.inMutable = true;
                                                    alphaOptions.inPreferredConfig = Bitmap.Config.ALPHA_8; // Try ALPHA_8
                                                    
                                                    originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, alphaOptions);
                                                    
                                                    if (originalBitmap != null) {
                                                        Log.d(TAG, "Successfully decoded image with ALPHA_8 config: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
                                                    } else {
                                                        Log.e(TAG, "All decoding attempts failed");
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(TAG, "ALPHA_8 attempt also failed: " + e.getMessage());
                                                }
                                            }
                                            
                                            // If we successfully decoded the image, add it
                                            if (originalBitmap != null) {
                                                Log.d(TAG, "Successfully decoded image: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());
                                                images.add(new ImageInfo(originalBitmap, x, y, width, height));
                                                Log.d(TAG, "Added image at position: " + x + "," + y);
                                            } else {
                                                Log.w(TAG, "Skipping image that could not be decoded: " + errorMessage);
                                                
                                                // Create a placeholder image to indicate where the image should be
                                                try {
                                                    Bitmap placeholder = createPlaceholderImage((int)width, (int)height);
                                                    if (placeholder != null) {
                                                        images.add(new ImageInfo(placeholder, x, y, width, height));
                                                        Log.d(TAG, "Added placeholder image for failed decode");
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Failed to create placeholder image: " + e.getMessage());
                                                }
                                            }
                                        } else {
                                            Log.e(TAG, "Image bytes are null or empty");
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error processing image: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error extracting images: " + e.getMessage());
                }
            }

            // Only return page if it has content
            if ((text != null && !text.trim().isEmpty()) || !images.isEmpty()) {
                Log.d(TAG, "Adding page " + pageNumber + " with " + 
                    (text != null ? text.length() : 0) + " chars and " + 
                    images.size() + " images");
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

    private String extractText(PdfPage page) {
        try {
            // Try LocationTextExtractionStrategy first
            LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
            String text = PdfTextExtractor.getTextFromPage(page, strategy);
            
            if (text == null || text.trim().isEmpty()) {
                Log.d(TAG, "No text found with LocationTextExtractionStrategy, trying SimpleTextExtractionStrategy");
                // Fall back to SimpleTextExtractionStrategy
                SimpleTextExtractionStrategy simpleStrategy = new SimpleTextExtractionStrategy();
                text = PdfTextExtractor.getTextFromPage(page, simpleStrategy);
            }
            
            return cleanText(text);
        } catch (Exception e) {
            Log.e(TAG, "Error extracting text: " + e.getMessage());
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
        
        // Replace single newlines (not part of a double newline) with a space
        text = text.replaceAll("(?<!\n)\n(?!\n)", " ");

        // Trim leading/trailing whitespace from the whole text, but DO NOT split into lines and rejoin
        return text.trim();
    }

    private static String getImageFormat(byte[] imageBytes) {
        if (imageBytes.length < 4) {
            return "Unknown (too small)";
        }
        
        // Check for common image format signatures
        if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8) {
            return "JPEG";
        } else if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == (byte) 0x50 && 
                   imageBytes[2] == (byte) 0x4E && imageBytes[3] == (byte) 0x47) {
            return "PNG";
        } else if (imageBytes[0] == (byte) 0x47 && imageBytes[1] == (byte) 0x49 && 
                   imageBytes[2] == (byte) 0x46) {
            return "GIF";
        } else if (imageBytes[0] == (byte) 0x42 && imageBytes[1] == (byte) 0x4D) {
            return "BMP";
        } else if (imageBytes[0] == (byte) 0x52 && imageBytes[1] == (byte) 0x49 && 
                   imageBytes[2] == (byte) 0x46 && imageBytes[3] == (byte) 0x46) {
            return "WebP";
        } else {
            return "Unknown";
        }
    }

    private static String getImageDiagnostics(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "Null or empty image data";
        }
        
        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("Image size: ").append(imageBytes.length).append(" bytes, ");
        
        if (imageBytes.length >= 4) {
            diagnostics.append("First 4 bytes: ");
            for (int i = 0; i < 4; i++) {
                diagnostics.append(String.format("%02X ", imageBytes[i] & 0xFF));
            }
            diagnostics.append(", ");
        }
        
        // Check for common corruption patterns
        boolean allZeros = true;
        boolean allSame = true;
        byte firstByte = imageBytes[0];
        
        for (int i = 0; i < Math.min(imageBytes.length, 100); i++) { // Check first 100 bytes
            if (imageBytes[i] != 0) allZeros = false;
            if (imageBytes[i] != firstByte) allSame = false;
        }
        
        if (allZeros) {
            diagnostics.append("WARNING: All zeros detected (likely corrupted)");
        } else if (allSame) {
            diagnostics.append("WARNING: All same bytes detected (likely corrupted)");
        } else {
            diagnostics.append("Data appears valid");
        }
        
        return diagnostics.toString();
    }

    private static Bitmap createPlaceholderImage(int width, int height) {
        try {
            // Create a simple placeholder bitmap
            Bitmap placeholder = Bitmap.createBitmap(Math.max(width, 100), Math.max(height, 100), Bitmap.Config.ARGB_8888);
            
            // Fill with a light gray background
            placeholder.eraseColor(0xFFE0E0E0);
            
            // Draw a simple border
            android.graphics.Canvas canvas = new android.graphics.Canvas(placeholder);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(0xFF808080);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            canvas.drawRect(0, 0, placeholder.getWidth() - 1, placeholder.getHeight() - 1, paint);
            
            // Draw "Image" text
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(0xFF404040);
            paint.setTextSize(16);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            canvas.drawText("Image", placeholder.getWidth() / 2f, placeholder.getHeight() / 2f, paint);
            
            return placeholder;
        } catch (Exception e) {
            Log.e(TAG, "Error creating placeholder image: " + e.getMessage());
            return null;
        }
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
        public String text;
        public List<ImageInfo> images;
        public int pageNumber;
        public final float pageWidth;
        public final float pageHeight;
        public final TextSettings textSettings;
        // For EPUB/TXT: precomputed layout (nullable)
        @androidx.annotation.Nullable
        public StaticLayout precomputedLayout;

        public ParsedPage(String text, List<ImageInfo> images, int pageNumber, float pageWidth, 
                         float pageHeight, TextSettings textSettings) {
            this.text = text;
            this.images = images;
            this.pageNumber = pageNumber;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.textSettings = textSettings;
            this.precomputedLayout = null;
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