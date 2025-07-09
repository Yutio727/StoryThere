package com.example.storythere.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.storythere.R;
import com.example.storythere.data.Author;
import java.util.List;

public class AuthorSuggestionsAdapter extends RecyclerView.Adapter<AuthorSuggestionsAdapter.AuthorViewHolder> {
    private List<Author> authors;
    private Context context;
    private OnAuthorClickListener listener;
    
    public interface OnAuthorClickListener {
        void onAuthorClick(Author author);
    }
    
    public AuthorSuggestionsAdapter(List<Author> authors, OnAuthorClickListener listener) {
        this.authors = authors;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public AuthorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_author_suggestion, parent, false);
        return new AuthorViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AuthorViewHolder holder, int position) {
        Author author = authors.get(position);
        holder.bind(author);
    }
    
    @Override
    public int getItemCount() {
        return authors != null ? authors.size() : 0;
    }
    
    public void updateAuthors(List<Author> newAuthors) {
        this.authors = newAuthors;
        notifyDataSetChanged();
    }
    
    class AuthorViewHolder extends RecyclerView.ViewHolder {
        private TextView authorName;
        private TextView authorBooks;
        
        public AuthorViewHolder(@NonNull View itemView) {
            super(itemView);
            authorName = itemView.findViewById(R.id.author_name);
            authorBooks = itemView.findViewById(R.id.author_books);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAuthorClick(authors.get(position));
                }
            });
        }
        
        public void bind(Author author) {
            authorName.setText(author.getName());
            
            String booksText = author.getTotalBooks() + " " + 
                (author.getTotalBooks() == 1 ? context.getString(R.string.book) : context.getString(R.string.books));
            authorBooks.setText(booksText);
        }
    }
} 