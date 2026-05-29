package com.example.storythere.adapters;

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
import com.example.storythere.ui.HomeActivity;
import java.util.Collections;
import java.util.List;

public class RecommendBookAdapter extends RecyclerView.Adapter<RecommendBookAdapter.BookViewHolder> {
    private List<HomeActivity.RecommendedBook> books;
    private OnBookClickListener listener;
    private List<Book> libraryBooks = Collections.emptyList();

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
                .error(R.drawable.ic_book_placeholder)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .into(holder.cover);
        LibraryState libraryState = resolveLibraryState(book);
        boolean isFavourite = book.isFavourite || libraryState.isFavourite;
        boolean isAlreadyRead = book.isAlreadyRead || libraryState.isAlreadyRead;
        holder.badges.setVisibility(isFavourite || isAlreadyRead ? View.VISIBLE : View.GONE);
        holder.favoriteBadge.setVisibility(isFavourite ? View.VISIBLE : View.GONE);
        holder.readBadge.setVisibility(isAlreadyRead ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onBookClick(book));
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    public void updateBooks(List<HomeActivity.RecommendedBook> newBooks) {
        this.books = newBooks;
        notifyDataSetChanged();
    }

    public void updateLibraryState(List<Book> newLibraryBooks) {
        this.libraryBooks = newLibraryBooks != null ? newLibraryBooks : Collections.emptyList();
        notifyDataSetChanged();
    }

    private LibraryState resolveLibraryState(HomeActivity.RecommendedBook recommendedBook) {
        if (recommendedBook == null || libraryBooks == null || libraryBooks.isEmpty()) {
            return LibraryState.EMPTY;
        }

        Book match = findLibraryMatch(recommendedBook);
        if (match == null) {
            return LibraryState.EMPTY;
        }
        return new LibraryState(match.isFavourite(), match.isAlreadyRead());
    }

    private Book findLibraryMatch(HomeActivity.RecommendedBook recommendedBook) {
        if (recommendedBook.id > 0) {
            for (Book libraryBook : libraryBooks) {
                if (libraryBook == null) {
                    continue;
                }
                if (recommendedBook.isAudiobook
                    && libraryBook.isAudiobook()
                    && libraryBook.getServerAudiobookId() == recommendedBook.id) {
                    return libraryBook;
                }
                if (!recommendedBook.isAudiobook
                    && !libraryBook.isAudiobook()
                    && libraryBook.getServerBookId() == recommendedBook.id) {
                    return libraryBook;
                }
            }
        }

        for (Book libraryBook : libraryBooks) {
            if (libraryBook == null || libraryBook.isAudiobook() != recommendedBook.isAudiobook) {
                continue;
            }
            if (sameText(libraryBook.getTitle(), recommendedBook.title)
                && sameText(libraryBook.getAuthor(), recommendedBook.author)) {
                return libraryBook;
            }
        }
        return null;
    }

    private boolean sameText(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return first.trim().equalsIgnoreCase(second.trim());
    }

    private static class LibraryState {
        static final LibraryState EMPTY = new LibraryState(false, false);

        final boolean isFavourite;
        final boolean isAlreadyRead;

        LibraryState(boolean isFavourite, boolean isAlreadyRead) {
            this.isFavourite = isFavourite;
            this.isAlreadyRead = isAlreadyRead;
        }
    }

    public static class BookViewHolder extends RecyclerView.ViewHolder {
        View badges;
        ImageView cover, favoriteBadge, readBadge;
        TextView title, author;
        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            badges = itemView.findViewById(R.id.book_status_badges);
            cover = itemView.findViewById(R.id.image_book_cover);
            favoriteBadge = itemView.findViewById(R.id.icon_book_favorite);
            readBadge = itemView.findViewById(R.id.icon_book_read);
            title = itemView.findViewById(R.id.text_book_title);
            author = itemView.findViewById(R.id.text_book_author);
        }
    }
} 
