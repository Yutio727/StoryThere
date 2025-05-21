package com.example.storythere;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.List;

public class PDFView extends View {
    private static final String TAG = "PDFView";
    private List<PDFParser.ParsedPage> pages;
    private Paint textPaint;
    private Paint imagePaint;
    private float scale = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private float pageSpacing = 32f; // Space between pages

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
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setPages(List<PDFParser.ParsedPage> pages) {
        Log.d(TAG, "Setting pages: " + (pages != null ? pages.size() : 0) + " pages");
        this.pages = pages;
        if (pages != null && !pages.isEmpty()) {
            // Apply text settings from first page
            PDFParser.TextSettings settings = pages.get(0).textSettings;
            textPaint.setTextSize(settings.fontSize);
            textPaint.setLetterSpacing(settings.letterSpacing);
            textPaint.setTextAlign(settings.textAlignment);
            Log.d(TAG, "Applied text settings: fontSize=" + settings.fontSize + 
                      ", letterSpacing=" + settings.letterSpacing + 
                      ", alignment=" + settings.textAlignment);
        }
        requestLayout();
        invalidate();
    }

    public void setScale(float scale) {
        Log.d(TAG, "Setting scale: " + scale);
        this.scale = scale;
        requestLayout();
        invalidate();
    }

    public void setTranslate(float x, float y) {
        this.translateX = x;
        this.translateY = y;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pages == null || pages.isEmpty()) {
            Log.d(TAG, "No pages to draw");
            return;
        }

        Log.d(TAG, "Drawing " + pages.size() + " pages");
        canvas.save();
        canvas.scale(scale, scale);

        float currentY = getPaddingTop();
        Log.d(TAG, "Starting Y position: " + currentY);

        for (int i = 0; i < pages.size(); i++) {
            PDFParser.ParsedPage page = pages.get(i);
            Log.d(TAG, "Drawing page " + (i + 1) + 
                      ", text length: " + (page.text != null ? page.text.length() : 0) + 
                      ", images: " + (page.images != null ? page.images.size() : 0));

            // Draw page content
            if (page.text != null && !page.text.isEmpty()) {
                String[] paragraphs = page.text.split("\n");
                Log.d(TAG, "Page " + (i + 1) + " has " + paragraphs.length + " paragraphs");
                
                for (String paragraph : paragraphs) {
                    if (paragraph.trim().isEmpty()) {
                        currentY += page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                        continue;
                    }

                    // Calculate text position based on alignment
                    float x = getPaddingLeft();
                    switch (page.textSettings.textAlignment) {
                        case CENTER:
                            x = page.pageWidth / 2;
                            break;
                        case RIGHT:
                            x = page.pageWidth - getPaddingRight();
                            break;
                    }

                    // Draw text
                    canvas.drawText(paragraph, x, currentY, textPaint);
                    currentY += page.textSettings.fontSize * page.textSettings.lineHeight;
                }
            }

            // Draw images after all text
            if (page.images != null && !page.images.isEmpty()) {
                Log.d(TAG, "Drawing " + page.images.size() + " images for page " + (i + 1));
                for (PDFParser.ImageInfo imageInfo : page.images) {
                    if (imageInfo.bitmap != null) {
                        // Add extra line break before image
                        currentY += page.textSettings.fontSize * page.textSettings.lineHeight;
                        
                        // Calculate image position
                        float imageX = imageInfo.x;
                        if (page.textSettings.textAlignment == Paint.Align.CENTER) {
                            imageX = (page.pageWidth - imageInfo.width) / 2;
                        } else if (page.textSettings.textAlignment == Paint.Align.RIGHT) {
                            imageX = page.pageWidth - imageInfo.width - getPaddingRight();
                        }
                        
                        Log.d(TAG, "Drawing image at Y: " + currentY + ", X: " + imageX);
                        canvas.drawBitmap(imageInfo.bitmap, imageX, currentY, imagePaint);
                        currentY += imageInfo.height + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                    }
                }
            }

            // Add spacing between pages
            currentY += pageSpacing;
        }

        Log.d(TAG, "Finished drawing at Y: " + currentY);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (pages != null && !pages.isEmpty()) {
            float totalHeight = getPaddingTop() + getPaddingBottom();
            float maxWidth = 0;
            
            for (PDFParser.ParsedPage page : pages) {
                // Calculate page width including padding
                float pageWidth = page.pageWidth + getPaddingLeft() + getPaddingRight();
                maxWidth = Math.max(maxWidth, pageWidth);
                
                // Add text height
                if (page.text != null && !page.text.isEmpty()) {
                    String[] paragraphs = page.text.split("\n");
                    totalHeight += paragraphs.length * page.textSettings.fontSize * 
                                 page.textSettings.lineHeight;
                }
                
                // Add image heights
                if (page.images != null) {
                    for (PDFParser.ImageInfo imageInfo : page.images) {
                        if (imageInfo.bitmap != null) {
                            totalHeight += imageInfo.height + 
                                         page.textSettings.fontSize * 
                                         page.textSettings.paragraphSpacing;
                        }
                    }
                }
                
                // Add page spacing
                totalHeight += pageSpacing;
            }
            
            // Apply scale to both dimensions
            int measuredWidth = (int) (maxWidth * scale);
            int measuredHeight = (int) (totalHeight * scale);
            
            //Log.d(TAG, "Measured dimensions - Width: " + measuredWidth + ", Height: " + measuredHeight +
            //          " (scale: " + scale + ")");
            
            setMeasuredDimension(measuredWidth, measuredHeight);
        }
    }
} 