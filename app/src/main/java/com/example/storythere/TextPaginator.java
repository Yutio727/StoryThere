package com.example.storythere;

import android.content.Context;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

public class TextPaginator {
    public static List<int[]> paginate(Context context, String text, PDFParser.TextSettings settings, int pageWidthPx, int pageHeightPx) {
        List<int[]> pageOffsets = new ArrayList<>();
        if (text == null || text.isEmpty()) return pageOffsets;

        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(settings.fontSize);
        paint.setLetterSpacing(settings.letterSpacing);
        paint.setColor(0xFF000000); // Color doesn't matter for measurement

        int start = 0;
        int textLen = text.length();
        while (start < textLen) {
            StaticLayout layout = StaticLayout.Builder.obtain(text, start, textLen, paint, pageWidthPx)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0, settings.lineHeight)
                    .setIncludePad(true)
                    .build();
            int lines = layout.getLineCount();
            int endLine = 0;
            int pageBottom = pageHeightPx;
            for (int i = 0; i < lines; i++) {
                if (layout.getLineBottom(i) > pageBottom) {
                    break;
                }
                endLine = i;
            }
            int end = layout.getLineEnd(endLine);
            if (end <= start) end = Math.min(start + 500, textLen); // fallback: avoid infinite loop
            pageOffsets.add(new int[]{start, end});
            start = end;
        }
        return pageOffsets;
    }
}
