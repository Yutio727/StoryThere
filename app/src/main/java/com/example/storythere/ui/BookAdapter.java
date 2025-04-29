package com.example.storythere.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.storythere.R;
import com.example.storythere.data.Book;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
    private List<Book> books;
    private OnBookClickListener clickListener;
    private OnBookLongClickListener longClickListener;
    
    public interface OnBookClickListener {
        void onBookClick(Book book);
    }
    
    public interface OnBookLongClickListener {
        void onBookLongClick(Book book);
    }
    
    public BookAdapter(OnBookClickListener clickListener, OnBookLongClickListener longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
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
        
        holder.itemView.setOnClickListener(v -> clickListener.onBookClick(book));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onBookLongClick(book);
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
    
    static class BookViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView authorTextView;
        private final TextView annotationTextView;
        private final ImageView previewImageView;
        
        public BookViewHolder(View view) {
            super(view);
            titleTextView = view.findViewById(R.id.bookTitle);
            authorTextView = view.findViewById(R.id.bookAuthor);
            annotationTextView = view.findViewById(R.id.bookAnnotation);
            previewImageView = view.findViewById(R.id.bookPreview);
        }
    }
} 