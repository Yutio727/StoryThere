package com.example.storythere.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.storythere.R;
import com.example.storythere.api.model.ApiBook;
import com.google.android.material.card.MaterialCardView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnboardingBookAdapter extends RecyclerView.Adapter<OnboardingBookAdapter.BookViewHolder> {
    public interface OnBookClickListener {
        void onBookClick(ApiBook book);
    }

    private final List<ApiBook> books;
    private final Set<String> selectedBookIds;
    private final OnBookClickListener listener;

    public OnboardingBookAdapter(List<ApiBook> books, Set<String> selectedBookIds, OnBookClickListener listener) {
        this.books = books;
        this.selectedBookIds = selectedBookIds != null ? selectedBookIds : new HashSet<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        ApiBook book = books.get(position);
        holder.bind(book, selectedBookIds.contains(String.valueOf(book.id)));
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    class BookViewHolder extends RecyclerView.ViewHolder {
        private final ImageView cover;
        private final TextView title;
        private final TextView author;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.image_book_cover);
            title = itemView.findViewById(R.id.text_book_title);
            author = itemView.findViewById(R.id.text_book_author);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                if (listener != null) {
                    listener.onBookClick(books.get(position));
                }
                notifyItemChanged(position);
            });
        }

        void bind(ApiBook book, boolean selected) {
            title.setText(book.title);
            String authorText = book.authorName != null && !book.authorName.trim().isEmpty()
                ? book.authorName
                : book.author;
            author.setText(authorText);
            itemView.setAlpha(selected ? 1f : 0.88f);

            if (itemView instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) itemView;
                card.setStrokeColor(ContextCompat.getColor(itemView.getContext(), selected ? R.color.progress_blue : R.color.textfield_stroke));
                card.setStrokeWidth(selected ? dpToPx(2) : dpToPx(1));
                card.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), selected ? R.color.background_blue : R.color.background_activity));
            }

            Glide.with(itemView.getContext())
                .load(book.image)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .into(cover);
        }

        private int dpToPx(int dp) {
            return Math.round(dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }
    }
}
