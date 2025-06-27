package com.example.storythere;

import android.app.Activity;
import android.content.Context;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PDFPageAdapter extends RecyclerView.Adapter<PDFPageAdapter.PDFPageViewHolder> {
    public enum DocumentType { PDF, EPUB, TXT }

    private final Context context;
    private final List<PDFParser.ParsedPage> pages;
    private final DocumentType documentType;
    private final PDFParser pdfParser;
    private final List<String> txtChunks;
    private final List<String> epubTextChunks;
    private final List<List<PDFParser.ImageInfo>> epubImages;
    private PDFParser.TextSettings currentSettings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService pdfParseExecutor = Executors.newFixedThreadPool(2);
    private final ConcurrentHashMap<Integer, Boolean> pdfParsingInProgress = new ConcurrentHashMap<>();

    public PDFPageAdapter(Context context, List<PDFParser.ParsedPage> pages, DocumentType type, PDFParser pdfParser, List<String> txtChunks, List<String> epubTextChunks, List<List<PDFParser.ImageInfo>> epubImages, PDFParser.TextSettings settings) {
        this.context = context;
        this.pages = pages;
        this.documentType = type;
        this.pdfParser = pdfParser;
        this.txtChunks = txtChunks;
        this.epubTextChunks = epubTextChunks;
        this.epubImages = epubImages;
        this.currentSettings = settings;
    }

    public void setTextSettings(PDFParser.TextSettings settings) {
        this.currentSettings = settings;
    }

    @NonNull
    @Override
    public PDFPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1) { // Text-only page
            TextView textView = new TextView(context);
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            textView.setTextSize(currentSettings != null ? currentSettings.fontSize / context.getResources().getDisplayMetrics().density : 18);
            textView.setPadding(16, 16, 16, 16);
            return new PDFPageViewHolder(textView, true);
        } else {
            PDFView pdfView = new PDFView(context);
            pdfView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return new PDFPageViewHolder(pdfView, false);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (documentType == DocumentType.TXT) {
            return 1;
        } else if (documentType == DocumentType.EPUB && epubImages != null && position < epubImages.size() && (epubImages.get(position) == null || epubImages.get(position).isEmpty())) {
            return 1;
        }
        return 0;
    }

    @Override
    public void onBindViewHolder(@NonNull PDFPageViewHolder holder, int position) {
        if (holder.isTextView) {
            String text = "";
            if (documentType == DocumentType.TXT && txtChunks != null) {
                text = position < txtChunks.size() ? txtChunks.get(position) : "";
            } else if (documentType == DocumentType.EPUB && epubTextChunks != null) {
                text = position < epubTextChunks.size() ? epubTextChunks.get(position) : "";
            }
            holder.bindText(text, currentSettings);
        } else {
            PDFParser.ParsedPage page = pages.get(position);
            if (documentType == DocumentType.PDF && pdfParser != null && (page.text == null || page.text.isEmpty())) {
                holder.bind(page);
                if (pdfParsingInProgress.putIfAbsent(position, true) == null) {
                    final int bindPosition = position;
                    pdfParseExecutor.submit(() -> {
                        PDFParser.ParsedPage parsed;
                        synchronized (pdfParser) {
                            parsed = pdfParser.parsePage(bindPosition + 1, currentSettings);
                        }
                        if (parsed != null) {
                            // For the first page (position 0), center the text
                            if (bindPosition == 0) {
                                PDFParser.TextSettings centeredSettings = new PDFParser.TextSettings();
                                centeredSettings.fontSize = currentSettings.fontSize;
                                centeredSettings.letterSpacing = currentSettings.letterSpacing;
                                centeredSettings.textAlignment = Paint.Align.CENTER; // Center the first page
                                centeredSettings.lineHeight = currentSettings.lineHeight;
                                centeredSettings.paragraphSpacing = currentSettings.paragraphSpacing;
                                parsed = new PDFParser.ParsedPage(
                                    parsed.text,
                                    parsed.images,
                                    parsed.pageNumber,
                                    parsed.pageWidth,
                                    parsed.pageHeight,
                                    centeredSettings
                                );
                            }
                            pages.set(bindPosition, parsed);
                            mainHandler.post(() -> {
                                pdfParsingInProgress.remove(bindPosition);
                                notifyItemChanged(bindPosition);
                            });
                        } else {
                            mainHandler.post(() -> pdfParsingInProgress.remove(bindPosition));
                        }
                    });
                }
            } else if (documentType == DocumentType.EPUB && epubTextChunks != null && epubImages != null) {
                String text = position < epubTextChunks.size() ? epubTextChunks.get(position) : "";
                List<PDFParser.ImageInfo> images = position < epubImages.size() ? epubImages.get(position) : new java.util.ArrayList<>();
                PDFParser.ParsedPage parsed = new PDFParser.ParsedPage(
                    text, images, position + 1, 595.0f, 842.0f, currentSettings
                );
                holder.bind(parsed);
            } else if (documentType == DocumentType.TXT && txtChunks != null) {
                String text = position < txtChunks.size() ? txtChunks.get(position) : "";
                PDFParser.ParsedPage parsed = new PDFParser.ParsedPage(
                    text, new java.util.ArrayList<>(), position + 1, 612.0f, 792.0f, currentSettings
                );
                holder.bind(parsed);
            } else {
                holder.bind(page);
            }
        }
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class PDFPageViewHolder extends RecyclerView.ViewHolder {
        private final PDFView pdfView;
        private final TextView textView;
        public final boolean isTextView;

        public PDFPageViewHolder(@NonNull View itemView, boolean isTextView) {
            super(itemView);
            this.isTextView = isTextView;
            if (isTextView) {
                this.textView = (TextView) itemView;
                this.pdfView = null;
            } else {
                this.pdfView = (PDFView) itemView;
                this.textView = null;
            }
        }

        public void bind(PDFParser.ParsedPage page) {
            if (pdfView != null) {
                pdfView.setPages(java.util.Collections.singletonList(page));
                pdfView.updateThemeColors();
            }
        }

        public void bindText(String text, PDFParser.TextSettings settings) {
            if (textView != null) {
                textView.setText(text);
                if (settings != null) {
                    textView.setTextSize(settings.fontSize / textView.getResources().getDisplayMetrics().density);
                    textView.setLetterSpacing(settings.letterSpacing);
                    // Alignment
                    switch (settings.textAlignment) {
                        case CENTER:
                            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                            break;
                        case RIGHT:
                            textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                            break;
                        default:
                            textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    }
                    // Line spacing (add, mult)
                    textView.setLineSpacing(0, settings.lineHeight);
                    // Padding: 16dp all sides
                    int pad = (int) (8 * textView.getResources().getDisplayMetrics().density);
                    textView.setPadding(pad, pad, pad, pad);
                }
                // Set text color from theme
                int color = textView.getContext().getResources().getColor(R.color.text_activity_primary, textView.getContext().getTheme());
                textView.setTextColor(color);
            }
        }
    }
} 