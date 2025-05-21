package com.example.storythere;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

public class PDFView extends View {
    private static final String TAG = "PDFView";
    private List<PDFParser.ParsedPage> pages;
    private TextPaint textPaint;
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
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        imagePaint.setFilterBitmap(true);
    }

    public void setPages(List<PDFParser.ParsedPage> pages) {
//        Log.d(TAG, "Setting pages: " + (pages != null ? pages.size() : 0) + " pages");
        this.pages = pages;
        if (pages != null && !pages.isEmpty()) {
            // Apply text settings from first page
            PDFParser.TextSettings settings = pages.get(0).textSettings;
            textPaint.setTextSize(settings.fontSize);
            textPaint.setLetterSpacing(settings.letterSpacing);
            textPaint.setTextAlign(settings.textAlignment);

        }
        requestLayout();
        invalidate();
    }

    public void setScale(float scale) {
//        Log.d(TAG, "Setting scale: " + scale);
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
//            Log.d(TAG, "No pages to draw");
            return;
        }

//        Log.d(TAG, "Drawing " + pages.size() + " pages");
        canvas.save();
        canvas.scale(scale, scale);

        float currentY = getPaddingTop();
        float availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();

        for (int i = 0; i < pages.size(); i++) {
            PDFParser.ParsedPage page = pages.get(i);

            // Draw text
            if (page.text != null && !page.text.isEmpty()) {
                String[] paragraphs = page.text.split("\n");
//                Log.d(TAG, "Page " + (i + 1) + " has " + paragraphs.length + " paragraphs");
                
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
                    StaticLayout layout = StaticLayout.Builder.obtain(paragraph, 0, paragraph.length(), textPaint, (int) availableWidth)
                        .setAlignment(align)
                        .setLineSpacing(0, page.textSettings.lineHeight)
                        .build();
                    canvas.save();
                    canvas.translate(getPaddingLeft(), currentY);
                    layout.draw(canvas);
                    canvas.restore();
                    currentY += layout.getHeight() + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                }
            }

            // Draw images
            if (page.images != null && !page.images.isEmpty()) {
//                Log.d(TAG, "Drawing " + page.images.size() + " images for page " + (i + 1));
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
                        RectF destRect = new RectF(imageX, currentY, imageX + scaledWidth, currentY + scaledHeight);
                        canvas.drawBitmap(imageInfo.bitmap, null, destRect, imagePaint);
                        currentY += scaledHeight + page.textSettings.fontSize * page.textSettings.paragraphSpacing;
                    }
                }
            }

            // Add spacing between pages
            currentY += pageSpacing;
        }

//        Log.d(TAG, "Finished drawing at Y: " + currentY);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float totalHeight = getPaddingTop() + getPaddingBottom();
        float availableWidth = width - getPaddingLeft() - getPaddingRight();
        if (pages != null && !pages.isEmpty()) {
            for (PDFParser.ParsedPage page : pages) {
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
} 