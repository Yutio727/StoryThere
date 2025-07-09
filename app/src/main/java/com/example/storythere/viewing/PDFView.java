package com.example.storythere.viewing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.List;
import android.util.LruCache;
import androidx.core.content.res.ResourcesCompat;

import com.example.storythere.R;
import com.example.storythere.parsers.PDFParser;

public class PDFView extends View {
    private static final String TAG = "PDFView";
    private List<PDFParser.ParsedPage> pages;
    private TextPaint textPaint;
    private Paint imagePaint;
    private float scale = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private float pageSpacing = 32f; // Space between pages
    private PDFParser.TextSettings defaultSettings;
    private boolean isHardwareAccelerated = false;
    private RectF tempRect = new RectF(); // Reusable rect for drawing
    
    // Fix LruCache implementation
    private LruCache<String, StaticLayout> layoutCache;
    private LruCache<Integer, Bitmap> scaledBitmapCache;
    private int lastWidth = 0;

    public PDFView(Context context) {
        super(context);
        init();
    }

    public PDFView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PDFView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize caches first
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8; // Use 1/8th of available memory
        
        layoutCache = new LruCache<String, StaticLayout>(50) {
            @Override
            protected int sizeOf(String key, StaticLayout value) {
                // Approximate size of StaticLayout
                return value.getLineCount() * 100; // Rough estimate
            }
        };
        
        scaledBitmapCache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                // Size in KB
                return bitmap.getByteCount() / 1024;
            }
            
            @Override
            protected void entryRemoved(boolean evicted, Integer key, Bitmap oldValue, Bitmap newValue) {
                if (evicted && oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };

        // Initialize paints
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(ResourcesCompat.getFont(getContext(), R.font.montserrat));
        imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        imagePaint.setFilterBitmap(true);
        
        // Create default settings
        defaultSettings = new PDFParser.TextSettings();
        
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null);
        isHardwareAccelerated = true;

        // Update theme colors after everything is initialized
        updateThemeColors();
    }

    public void updateThemeColors() {
        int textColor = getContext().getResources().getColor(R.color.text_activity_primary, getContext().getTheme());
        textPaint.setColor(textColor);
        setBackgroundColor(android.graphics.Color.TRANSPARENT); // Let RecyclerView background show through
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.d(TAG, "View attached to window - updating theme colors");
        updateThemeColors();
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Configuration changed - updating theme colors");
        updateThemeColors();
    }

    private void clearBitmapCache() {
        scaledBitmapCache.evictAll();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearBitmapCache();
    }

    public void setPages(List<PDFParser.ParsedPage> pages) {
        this.pages = pages;
        updateThemeColors(); // Ensure theme is applied
        if (pages != null && !pages.isEmpty()) {
            // Find first non-null page to get settings
            for (PDFParser.ParsedPage page : pages) {
                if (page != null) {
                    defaultSettings = page.textSettings;
                    break;
                }
            }
            // Apply text settings
            textPaint.setTextSize(defaultSettings.fontSize);
            textPaint.setLetterSpacing(defaultSettings.letterSpacing);
            // Don't set text alignment on textPaint as it's handled by StaticLayout
            textPaint.setTextAlign(Paint.Align.LEFT);
            
            // Clear caches when pages change
            layoutCache.evictAll();
            clearBitmapCache();
        }
        requestLayout();
        invalidate();
    }

    private StaticLayout getCachedLayout(String text, int width, Layout.Alignment align, float lineSpacing) {
        String key = text + width + align + lineSpacing;
        StaticLayout layout = layoutCache.get(key);
        if (layout == null) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(align)
                .setLineSpacing(0, lineSpacing)
                .setIncludePad(true)
                .setMaxLines(Integer.MAX_VALUE)
                .setEllipsize(null)
                .build();
            layoutCache.put(key, layout);
        }
        return layout;
    }

    private Bitmap getScaledBitmap(PDFParser.ImageInfo imageInfo, float targetWidth) {
        int key = imageInfo.bitmap.hashCode() + (int)targetWidth;
        Bitmap scaledBitmap = scaledBitmapCache.get(key);
        if (scaledBitmap == null || scaledBitmap.isRecycled()) {
            float scale = targetWidth / imageInfo.width;
            scaledBitmap = Bitmap.createScaledBitmap(
                imageInfo.bitmap,
                (int)(imageInfo.width * scale),
                (int)(imageInfo.height * scale),
                true
            );
            scaledBitmapCache.put(key, scaledBitmap);
        }
        return scaledBitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateThemeColors(); // Ensure theme is applied on every draw
        super.onDraw(canvas);
        if (pages == null || pages.isEmpty()) {
            return;
        }

        // Check if width changed
        int currentWidth = getWidth();
        if (currentWidth != lastWidth) {
            layoutCache.evictAll();
            lastWidth = currentWidth;
        }

        // Save canvas state
        canvas.save();
        // Apply scale
        canvas.scale(scale, scale);

        float currentY = getPaddingTop();
        float availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();

        // Draw pages
        for (int i = 0; i < pages.size(); i++) {
            PDFParser.ParsedPage page = pages.get(i);
            if (page == null) {
                // Draw loading indicator or placeholder for null pages
                currentY += defaultSettings.fontSize * 2;
                continue;
            }

            // Use precomputed layout for EPUB/TXT if available
            if (page.precomputedLayout != null) {
                canvas.save();
                canvas.translate(getPaddingLeft(), currentY);
                page.precomputedLayout.draw(canvas);
                canvas.restore();
                currentY += page.precomputedLayout.getHeight() + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                continue;
            }

            // For blank pages, draw a placeholder
            if (page.text == null || page.text.isEmpty()) {
                // Calculate page height based on aspect ratio
                float pageAspect = page.pageWidth / page.pageHeight;
                float pageHeight = availableWidth / pageAspect;
                // Draw a subtle border for blank pages
                Paint borderPaint = new Paint();
                borderPaint.setColor(0x20000000); // Semi-transparent border
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(1);
                tempRect.set(getPaddingLeft(), currentY, getPaddingLeft() + availableWidth, currentY + pageHeight);
                canvas.drawRect(tempRect, borderPaint);
                currentY += pageHeight + pageSpacing;
                continue;
            }

            // Draw text (PDF fallback)
            if (page.text != null && !page.text.isEmpty()) {
                String[] paragraphs = page.text.split("\n");
                for (String paragraph : paragraphs) {
                    if (paragraph.trim().isEmpty()) {
                        currentY += page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                        continue;
                    }
                    Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
                    switch (page.textSettings.textAlignment) {
                        case CENTER: align = Layout.Alignment.ALIGN_CENTER; break;
                        case RIGHT: align = Layout.Alignment.ALIGN_OPPOSITE; break;
                        default: align = Layout.Alignment.ALIGN_NORMAL;
                    }
                    StaticLayout layout = getCachedLayout(
                        paragraph,
                        (int)availableWidth,
                        align,
                        page.textSettings.lineHeight
                    );
                    canvas.save();
                    canvas.translate(getPaddingLeft(), currentY);
                    layout.draw(canvas);
                    canvas.restore();
                    currentY += layout.getHeight() + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                }
            }

            // Draw images
            if (page.images != null && !page.images.isEmpty()) {
                for (PDFParser.ImageInfo imageInfo : page.images) {
                    if (imageInfo.bitmap != null) {
                        currentY += page.textSettings.fontSize * page.textSettings.lineHeight;
                        float imgAspect = imageInfo.width / imageInfo.height;
                        float scaledWidth = availableWidth;
                        float scaledHeight = scaledWidth / imgAspect;
                        float imageX = getPaddingLeft();
                        if (page.textSettings.textAlignment == Paint.Align.CENTER) {
                            imageX = getPaddingLeft() + (availableWidth - scaledWidth) / 2f;
                        } else if (page.textSettings.textAlignment == Paint.Align.RIGHT) {
                            imageX = getWidth() - getPaddingRight() - scaledWidth;
                        }
                        Bitmap scaledBitmap = getScaledBitmap(imageInfo, availableWidth);
                        tempRect.set(imageX, currentY, imageX + scaledWidth, currentY + scaledHeight);
                        canvas.drawBitmap(scaledBitmap, null, tempRect, imagePaint);
                        currentY += scaledHeight + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                    }
                }
            }
            // Add spacing between pages
            currentY += pageSpacing;
        }
        // Restore canvas state
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float totalHeight = getPaddingTop() + getPaddingBottom();
        float availableWidth = width - getPaddingLeft() - getPaddingRight();
        if (pages != null && !pages.isEmpty()) {
            for (PDFParser.ParsedPage page : pages) {
                if (page == null) {
                    totalHeight += defaultSettings.fontSize * 2;
                    continue;
                }
                // Use precomputed layout for EPUB/TXT if available
                if (page.precomputedLayout != null) {
                    totalHeight += page.precomputedLayout.getHeight() + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                    continue;
                }
                // For blank pages, use page dimensions to calculate height
                if (page.text == null || page.text.isEmpty()) {
                    float pageAspect = page.pageWidth / page.pageHeight;
                    float pageHeight = availableWidth / pageAspect;
                    totalHeight += pageHeight + pageSpacing;
                    continue;
                }
                if (page.text != null && !page.text.isEmpty()) {
                    String[] paragraphs = page.text.split("\n");
                    for (String paragraph : paragraphs) {
                        if (paragraph.trim().isEmpty()) {
                            totalHeight += page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                            continue;
                        }
                        Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
                        switch (page.textSettings.textAlignment) {
                            case CENTER: align = Layout.Alignment.ALIGN_CENTER; break;
                            case RIGHT: align = Layout.Alignment.ALIGN_OPPOSITE; break;
                            default: align = Layout.Alignment.ALIGN_NORMAL;
                        }
                        StaticLayout layout = StaticLayout.Builder.obtain(paragraph, 0, paragraph.length(), textPaint, (int) availableWidth)
                            .setAlignment(align)
                            .setLineSpacing(0, page.textSettings.lineHeight)
                            .setIncludePad(true)
                            .build();
                        totalHeight += layout.getHeight() + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                    }
                }
                if (page.images != null) {
                    for (PDFParser.ImageInfo imageInfo : page.images) {
                        if (imageInfo.bitmap != null) {
                            float imgAspect = imageInfo.width / imageInfo.height;
                            float scaledWidth = availableWidth;
                            float scaledHeight = scaledWidth / imgAspect;
                            totalHeight += scaledHeight + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                        }
                    }
                }
                totalHeight += pageSpacing;
            }
        }
        int measuredHeight = (int) (totalHeight * scale);
        setMeasuredDimension(width, measuredHeight);
    }

    /**
     * Call this method when the system is running low on memory
     */
    public void onTrimMemory() {
        layoutCache.evictAll();
        clearBitmapCache();
    }
} 