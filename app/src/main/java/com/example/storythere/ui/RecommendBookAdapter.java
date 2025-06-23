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
import com.example.storythere.HomeActivity;
import java.util.List;

public class RecommendBookAdapter extends RecyclerView.Adapter<RecommendBookAdapter.BookViewHolder> {
    private List<HomeActivity.RecommendedBook> books;
    private OnBookClickListener listener;

    public interface OnBookClickListener {
        void onBookClick(HomeActivity.RecommendedBook book);
    }

    public RecommendBookAdapter(List<HomeActivity.RecommendedBook> books, OnBookClickListener listener) {
        this.books = books;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommend_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        HomeActivity.RecommendedBook book = books.get(position);
        holder.title.setText(book.title);
        holder.author.setText(book.author);
        Glide.with(holder.itemView.getContext())
                .load(book.image)
                .placeholder(R.drawable.ic_book_placeholder)
                .into(holder.cover);
        holder.itemView.setOnClickListener(v -> listener.onBookClick(book));
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    public static class BookViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, author;
        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.image_book_cover);
            title = itemView.findViewById(R.id.text_book_title);
            author = itemView.findViewById(R.id.text_book_author);
        }
    }
} 