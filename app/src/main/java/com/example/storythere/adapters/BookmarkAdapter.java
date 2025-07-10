package com.example.storythere.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.storythere.R;
import java.util.List;

public class BookmarkAdapter extends ArrayAdapter<BookmarkAdapter.BookmarkItem> {
    private List<BookmarkAdapter.BookmarkItem> bookmarks;
    private OnBookmarkClickListener clickListener;
    private OnBookmarkDeleteListener deleteListener;
    private boolean isDeleteMode = false;

    public interface OnBookmarkClickListener {
        void onBookmarkClick(BookmarkAdapter.BookmarkItem bookmark);
    }

    public interface OnBookmarkDeleteListener {
        void onBookmarkDelete(BookmarkAdapter.BookmarkItem bookmark, int position);
    }

    public static class BookmarkItem {
        public String displayText;
        public int position;
        public long timestamp;
        public String label;

        public BookmarkItem(String displayText, int position, long timestamp, String label) {
            this.displayText = displayText;
            this.position = position;
            this.timestamp = timestamp;
            this.label = label;
        }
    }

    public BookmarkAdapter(Context context, List<BookmarkAdapter.BookmarkItem> bookmarks,
                          OnBookmarkClickListener clickListener,
                          OnBookmarkDeleteListener deleteListener) {
        super(context, 0, bookmarks);
        this.bookmarks = bookmarks;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_bookmark, parent, false);
        }

        BookmarkAdapter.BookmarkItem bookmark = getItem(position);
        if (bookmark == null) return convertView;

        TextView bookmarkText = convertView.findViewById(R.id.bookmark_text);
        Button deleteButton = convertView.findViewById(R.id.delete_button);

        bookmarkText.setText(bookmark.displayText);

        // Set up click listener for the entire item
        convertView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onBookmarkClick(bookmark);
            }
        });

        // Set up long press listener to toggle delete mode
        convertView.setOnLongClickListener(v -> {
            toggleDeleteMode();
            return true;
        });

        // Set up delete button
        deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onBookmarkDelete(bookmark, position);
            }
        });

        // Show/hide delete button based on delete mode
        deleteButton.setVisibility(isDeleteMode ? View.VISIBLE : View.GONE);

        return convertView;
    }

    public void toggleDeleteMode() {
        isDeleteMode = !isDeleteMode;
        notifyDataSetChanged();
    }

    public void setDeleteMode(boolean deleteMode) {
        this.isDeleteMode = deleteMode;
        notifyDataSetChanged();
    }

    public boolean isDeleteMode() {
        return isDeleteMode;
    }
} 