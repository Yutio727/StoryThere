package com.example.storythere.ui;

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
            .inflate(R.layout.book_list_item, parent, false);
        return new BookViewHolder(itemView);
    }
    
    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = books.get(position);
        holder.titleTextView.setText(book.getTitle());
        holder.authorTextView.setText(book.getAuthor());
        holder.annotationTextView.setText(book.getAnnotation());
        
        if (book.getPreviewImagePath() != null) {
            Glide.with(holder.itemView.getContext())
                .load(book.getPreviewImagePath())
                .placeholder(R.drawable.ic_book_placeholder)
                .into(holder.previewImageView);
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
        private final ImageView previewImageView;
        private final CheckBox checkBox;
        
        public BookViewHolder(View view) {
            super(view);
            titleTextView = view.findViewById(R.id.bookTitle);
            authorTextView = view.findViewById(R.id.bookAuthor);
            annotationTextView = view.findViewById(R.id.bookAnnotation);
            previewImageView = view.findViewById(R.id.bookPreview);
            checkBox = view.findViewById(R.id.bookCheckBox);
        }
    }
} 