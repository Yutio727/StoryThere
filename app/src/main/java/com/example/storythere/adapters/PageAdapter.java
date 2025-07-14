package com.example.storythere.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.storythere.R;
import com.example.storythere.parsers.PDFParser;
import com.example.storythere.viewing.PDFView;
import com.example.storythere.viewing.SelectableTextView;
import com.turingtechnologies.materialscrollbar.ICustomAdapter;

public class PageAdapter extends RecyclerView.Adapter<PageAdapter.PDFPageViewHolder> implements ICustomAdapter {
    public enum DocumentType { PDF, EPUB, TXT }

    public interface TextSelectionCallback {
        void onTextSelected(String selectedText, int pagePosition, int start, int end);
        void onSelectionCleared(int pagePosition);
    }

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
    private TextSelectionCallback textSelectionCallback;
    private RecyclerView recyclerView;

    public PageAdapter(Context context, List<PDFParser.ParsedPage> pages, DocumentType type, PDFParser pdfParser, List<String> txtChunks, List<String> epubTextChunks, List<List<PDFParser.ImageInfo>> epubImages, PDFParser.TextSettings settings) {
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
    
    public void setTextSelectionCallback(TextSelectionCallback callback) {
        this.textSelectionCallback = callback;
    }
    
    /**
     * Check if a page should use TextView based on its content
     */
    public boolean shouldUseTextView(int position) {
        if (documentType == DocumentType.TXT) {
            return true;
        } else if (documentType == DocumentType.EPUB && epubImages != null && position < epubImages.size() && (epubImages.get(position) == null || epubImages.get(position).isEmpty())) {
            return true;
        } else if (documentType == DocumentType.PDF) {
            PDFParser.ParsedPage page = pages.get(position);
            boolean shouldUse = page != null && page.text != null && !page.text.trim().isEmpty() && (page.images == null || page.images.isEmpty());
            if (shouldUse) {
                Log.d("PageAdapter", "Page " + position + " using TextView (text-only PDF page)");
            }
            return shouldUse;
        }
        return false;
    }

    @NonNull
    @Override
    public PDFPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1) { // Text-only page
            SelectableTextView textView = new SelectableTextView(context);
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
        return shouldUseTextView(position) ? 1 : 0;
    }

    @Override
    public void onBindViewHolder(@NonNull PDFPageViewHolder holder, int position) {
        if (holder.isTextView) {
            String text = "";
            PDFParser.TextSettings settings = currentSettings;
            
            if (documentType == DocumentType.TXT && txtChunks != null) {
                text = position < txtChunks.size() ? txtChunks.get(position) : "";
            } else if (documentType == DocumentType.EPUB && epubTextChunks != null) {
                text = position < epubTextChunks.size() ? epubTextChunks.get(position) : "";
            } else if (documentType == DocumentType.PDF) {
                // Handle PDF text-only pages
                PDFParser.ParsedPage page = pages.get(position);
                if (page != null && page.text != null) {
                    text = page.text;
                    settings = page.textSettings != null ? page.textSettings : currentSettings;
                }
            }
            holder.bindText(text, settings, position, textSelectionCallback);
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
                            pages.set(bindPosition, parsed);
                            mainHandler.post(() -> {
                                pdfParsingInProgress.remove(bindPosition);
                                // Check if the page should now use TextView (no images and has text)
                                if (shouldUseTextView(bindPosition)) {
                                    // Force recreation of the view holder to use TextView
                                    notifyItemChanged(bindPosition);
                                } else {
                                    // Just update the existing PDFView
                                    notifyItemChanged(bindPosition);
                                }
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
        private final SelectableTextView textView;
        public final boolean isTextView;
        private int currentPosition = -1;

        public PDFPageViewHolder(@NonNull View itemView, boolean isTextView) {
            super(itemView);
            this.isTextView = isTextView;
            if (isTextView) {
                this.textView = (SelectableTextView) itemView;
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

        public void bindText(String text, PDFParser.TextSettings settings, int position, TextSelectionCallback callback) {
            if (textView != null) {
                currentPosition = position;
                
                // First set text as Spannable to ensure selection works
                android.text.SpannableString spannableText = new android.text.SpannableString(text);
                textView.setText(spannableText);
                textView.setTextIsSelectable(false);
                textView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                
                // Then configure all other settings
                textView.setFocusable(true);
                textView.setFocusableInTouchMode(true);
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
                
                // Set up text selection listener
                textView.setTextSelectionListener(new SelectableTextView.TextSelectionListener() {
                    @Override
                    public void onTextSelected(String selectedText, int start, int end) {
                        if (callback != null) {
                            callback.onTextSelected(selectedText, currentPosition, start, end);
                        }
                    }
                    
                    @Override
                    public void onSelectionCleared() {
                        if (callback != null) {
                            callback.onSelectionCleared(currentPosition);
                        }
                    }
                });
                
                // Finally enable selection
                textView.setTextIsSelectable(true);
                
                Log.d("PageAdapter", "[BIND_TEXT] Text bound successfully - position: " + position + ", text length: " + text.length() + ", isSpannable: " + (textView.getText() instanceof android.text.Spannable));
            }
        }
        
        /**
         * Programmatically set text selection on this view holder
         */
        public void setTextSelection(int start, int end) {
            Log.d("PageAdapter", "[SELECT_TEXT] setTextSelection called - isTextView: " + isTextView + ", textView: " + (textView != null ? "not null" : "null") + ", start: " + start + ", end: " + end);
            if (isTextView && textView != null) {
                Log.d("PageAdapter", "[SELECT_TEXT] Calling textView.setSelection(" + start + ", " + end + ")");
                textView.setSelection(start, end);
                Log.d("PageAdapter", "[SELECT_TEXT] setSelection completed");
            } else {
                Log.d("PageAdapter", "[SELECT_TEXT] Cannot set selection - isTextView: " + isTextView + ", textView: " + (textView != null ? "not null" : "null"));
            }
        }
        
        public SelectableTextView getTextView() {
            return textView;
        }
    }

    // ICustomAdapter implementation for MaterialScrollBar
    @Override
    public String getCustomStringForElement(int element) {
        if (element >= 0 && element < pages.size()) {
            // Return page number as string for the scrollbar indicator
            return String.valueOf(element + 1);
        }
        return "";
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    /**
     * Programmatically select text on a given page (if it is a text view page).
     */
    public void selectTextOnPage(int pageIndex, int start, int end) {
        Log.d("PageAdapter", "[SELECT_TEXT] selectTextOnPage called - pageIndex: " + pageIndex + ", start: " + start + ", end: " + end);
        if (recyclerView == null) {
            Log.d("PageAdapter", "[SELECT_TEXT] recyclerView is null, returning");
            return;
        }
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(pageIndex);
        Log.d("PageAdapter", "[SELECT_TEXT] ViewHolder for page " + pageIndex + ": " + (vh != null ? vh.getClass().getSimpleName() : "null"));
        if (vh instanceof PDFPageViewHolder) {
            PDFPageViewHolder holder = (PDFPageViewHolder) vh;
            Log.d("PageAdapter", "[SELECT_TEXT] Holder isTextView: " + holder.isTextView);
            holder.setTextSelection(start, end);
            Log.d("PageAdapter", "[SELECT_TEXT] setTextSelection called on holder");
        } else {
            Log.d("PageAdapter", "[SELECT_TEXT] ViewHolder is not PDFPageViewHolder");
        }
    }

    public PDFParser getPdfParser() { return pdfParser; }
    public PDFParser.TextSettings getCurrentSettings() { return currentSettings; }
} 