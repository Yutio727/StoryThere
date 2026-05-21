package com.example.storythere.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.storythere.R;
import com.google.android.material.card.MaterialCardView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnboardingGenreAdapter extends RecyclerView.Adapter<OnboardingGenreAdapter.GenreViewHolder> {
    public interface OnGenreClickListener {
        void onGenreClick(String genre);
    }

    private final List<String> genres;
    private final Set<String> selectedGenres;
    private final OnGenreClickListener listener;

    public OnboardingGenreAdapter(List<String> genres, Set<String> selectedGenres, OnGenreClickListener listener) {
        this.genres = genres;
        this.selectedGenres = selectedGenres != null ? selectedGenres : new HashSet<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public GenreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_genre, parent, false);
        return new GenreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GenreViewHolder holder, int position) {
        String genre = genres.get(position);
        holder.bind(genre, selectedGenres.contains(genre));
    }

    @Override
    public int getItemCount() {
        return genres.size();
    }

    class GenreViewHolder extends RecyclerView.ViewHolder {
        private final TextView genreName;

        GenreViewHolder(@NonNull View itemView) {
            super(itemView);
            genreName = itemView.findViewById(R.id.genreName);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                if (listener != null) {
                    listener.onGenreClick(genres.get(position));
                }
                notifyItemChanged(position);
            });
        }

        void bind(String genre, boolean selected) {
            genreName.setText(genre);
            if (itemView instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) itemView;
                card.setStrokeColor(ContextCompat.getColor(itemView.getContext(), selected ? R.color.progress_blue : R.color.textfield_stroke));
                card.setStrokeWidth(selected ? dpToPx(2) : dpToPx(1));
                card.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), selected ? R.color.background_blue : R.color.background_activity));
            }
        }

        private int dpToPx(int dp) {
            return Math.round(dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }
    }
}
