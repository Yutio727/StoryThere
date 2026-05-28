package com.example.storythere.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.storythere.R;
import com.example.storythere.data.Book;
import java.util.ArrayList;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
    private List<Book> books;
    private List<Book> selectedBooks = new ArrayList<>();
    private boolean isSelectionMode = false;
    private OnBookClickListener clickListener;
    private OnSelectionChangeListener selectionChangeListener;
    
    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }
    
    public BookAdapter(OnBookClickListener clickListener,
                      OnSelectionChangeListener selectionChangeListener) {
        this.clickListener = clickListener;
        this.selectionChangeListener = selectionChangeListener;
    }
    
    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_book_list, parent, false);
        return new BookViewHolder(itemView);
    }
    
    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = books.get(position);
        holder.titleTextView.setText(book.getTitle());
        holder.authorTextView.setText(book.getAuthor());
        holder.annotationTextView.setText(getBookInfoText(holder, book));
        holder.typeTextView.setText(book.isAudiobook()
            ? holder.itemView.getContext().getString(R.string.content_type_audiobook)
            : holder.itemView.getContext().getString(R.string.content_type_book));
        
        if (book.getPreviewImagePath() != null) {
            Glide.with(holder.itemView.getContext())
                .load(book.getPreviewImagePath())
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .into(holder.previewImageView);
        } else {
            holder.previewImageView.setImageResource(R.drawable.ic_book_placeholder);
        }

        // Handle selection mode
        holder.checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setChecked(selectedBooks.contains(book));
        
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleBookSelection(book);
                holder.checkBox.setChecked(selectedBooks.contains(book));
            } else {
                clickListener.onBookClick(book);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                toggleBookSelection(book);
                holder.checkBox.setChecked(true);
            }
            return true;
        });
    }
    
    @Override
    public int getItemCount() {
        return books == null ? 0 : books.size();
    }
    
    public void setBooks(List<Book> books) {
        this.books = books;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        this.isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedBooks.clear();
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedBooks.clear();
        notifyDataSetChanged();
    }

    public List<Book> getSelectedBooks() {
        return new ArrayList<>(selectedBooks);
    }

    private void toggleBookSelection(Book book) {
        if (selectedBooks.contains(book)) {
            selectedBooks.remove(book);
        } else {
            selectedBooks.add(book);
        }
        selectionChangeListener.onSelectionChanged(selectedBooks.size());
    }
    
    static class BookViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView authorTextView;
        private final TextView annotationTextView;
        private final TextView typeTextView;
        private final ImageView previewImageView;
        private final CheckBox checkBox;
        
        public BookViewHolder(View view) {
            super(view);
            titleTextView = view.findViewById(R.id.bookTitle);
            authorTextView = view.findViewById(R.id.bookAuthor);
            annotationTextView = view.findViewById(R.id.bookAnnotation);
            typeTextView = view.findViewById(R.id.bookType);
            previewImageView = view.findViewById(R.id.bookPreview);
            checkBox = view.findViewById(R.id.bookCheckBox);
        }
    }

    private String getBookInfoText(BookViewHolder holder, Book book) {
        if (book.isAudiobook()) {
            return getAudiobookInfoText(holder, book);
        }
        return getReadingInfoText(holder, book);
    }

    private String getReadingInfoText(BookViewHolder holder, Book book) {
        String readingStats = book.getReadingStats();
        if (readingStats == null || readingStats.trim().isEmpty()) {
            return "";
        }

        String result = readingStats;
        int estimatedMinutes = -1;
        try {
            int count = Integer.parseInt(readingStats.trim().split(" ")[0]);
            if ("pdf".equals(book.getFileType())) {
                estimatedMinutes = (int) Math.ceil(count * 300.0 / 250.0);
            } else {
                estimatedMinutes = (int) Math.ceil(count / 250.0);
            }
        } catch (Exception ignored) {}
        if (estimatedMinutes > 0) {
            String separator = " | ";
            String min = holder.itemView.getContext().getString(R.string.min);
            result = readingStats + separator + estimatedMinutes + min;
        }
        return result;
    }

    private String getAudiobookInfoText(BookViewHolder holder, Book book) {
        int durationSeconds = book.getDurationSeconds();
        if (durationSeconds > 0) {
            return formatAudioDuration(
                holder,
                holder.itemView.getContext().getString(R.string.book_list_audio_duration),
                durationSeconds
            );
        }

        long playbackPositionMs = book.getPlaybackPositionMs();
        if (playbackPositionMs > 0L) {
            return formatAudioDuration(
                holder,
                holder.itemView.getContext().getString(R.string.book_list_audio_listening_time),
                (int) (playbackPositionMs / 1000L)
            );
        }

        return "";
    }

    private String formatAudioDuration(BookViewHolder holder, String label, int totalSeconds) {
        int durationMinutes = (int) Math.ceil(Math.max(1, totalSeconds) / 60.0);
        return label
            + " | "
            + durationMinutes
            + holder.itemView.getContext().getString(R.string.min);
    }
} 
