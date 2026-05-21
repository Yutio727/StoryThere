package com.example.storythere.adapters;

import android.content.Context;
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
import com.example.storythere.data.Author;
import com.google.android.material.card.MaterialCardView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnboardingAuthorAdapter extends RecyclerView.Adapter<OnboardingAuthorAdapter.AuthorViewHolder> {
    public interface OnAuthorClickListener {
        boolean onAuthorClick(Author author);
    }

    private final Context context;
    private final List<Author> authors;
    private final Set<String> selectedAuthorIds;
    private final OnAuthorClickListener listener;

    public OnboardingAuthorAdapter(Context context, List<Author> authors, Set<String> selectedAuthorIds, OnAuthorClickListener listener) {
        this.context = context;
        this.authors = authors;
        this.selectedAuthorIds = selectedAuthorIds != null ? selectedAuthorIds : new HashSet<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public AuthorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_author, parent, false);
        return new AuthorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AuthorViewHolder holder, int position) {
        Author author = authors.get(position);
        holder.bind(author, isSelected(author));
    }

    @Override
    public int getItemCount() {
        return authors.size();
    }

    private boolean isSelected(Author author) {
        return author != null && selectedAuthorIds.contains(author.getAuthorId());
    }

    class AuthorViewHolder extends RecyclerView.ViewHolder {
        private final ImageView authorImage;
        private final TextView authorName;
        private final TextView authorBooks;

        AuthorViewHolder(@NonNull View itemView) {
            super(itemView);
            authorImage = itemView.findViewById(R.id.author_image);
            authorName = itemView.findViewById(R.id.author_name);
            authorBooks = itemView.findViewById(R.id.author_books);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listener == null) {
                    return;
                }
                if (listener.onAuthorClick(authors.get(position))) {
                    notifyItemChanged(position);
                }
            });
        }

        void bind(Author author, boolean selected) {
            authorName.setText(author.getName());
            String booksText = author.getTotalBooks() + " " +
                (author.getTotalBooks() == 1 ? context.getString(R.string.book) : context.getString(R.string.books));
            authorBooks.setText(booksText);

            if (author.getPhotoUrl() != null && !author.getPhotoUrl().isEmpty()) {
                Glide.with(context)
                    .load(author.getPhotoUrl())
                    .placeholder(R.drawable.default_author_avatar)
                    .error(R.drawable.default_author_avatar)
                    .circleCrop()
                    .into(authorImage);
            } else {
                authorImage.setImageResource(R.drawable.default_author_avatar);
            }

            if (itemView instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) itemView;
                card.setStrokeColor(ContextCompat.getColor(context, selected ? R.color.progress_blue : R.color.textfield_stroke));
                card.setStrokeWidth(selected ? dpToPx(2) : dpToPx(1));
                card.setCardBackgroundColor(ContextCompat.getColor(context, selected ? R.color.background_blue : R.color.background_activity));
            }
        }

        private int dpToPx(int dp) {
            return Math.round(dp * context.getResources().getDisplayMetrics().density);
        }
    }
}
