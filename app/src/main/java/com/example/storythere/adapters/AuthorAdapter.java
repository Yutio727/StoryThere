package com.example.storythere.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.storythere.R;
import com.example.storythere.data.Author;
import com.example.storythere.ui.AuthorDetailActivity;
import java.util.List;

public class AuthorAdapter extends RecyclerView.Adapter<AuthorAdapter.AuthorViewHolder> {
    private List<Author> authors;
    private Context context;
    
    public AuthorAdapter(Context context, List<Author> authors) {
        this.context = context;
        this.authors = authors;
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
        private ImageView authorImage;
        private TextView authorName;
        private TextView authorBooks;
        
        public AuthorViewHolder(@NonNull View itemView) {
            super(itemView);
            authorImage = itemView.findViewById(R.id.author_image);
            authorName = itemView.findViewById(R.id.author_name);
            authorBooks = itemView.findViewById(R.id.author_books);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Author author = authors.get(position);
                    Intent intent = new Intent(context, AuthorDetailActivity.class);
                    intent.putExtra("authorId", author.getAuthorId());
                    context.startActivity(intent);
                }
            });
        }
        
        public void bind(Author author) {
            authorName.setText(author.getName());
            
            // Set books count
            String booksText = author.getTotalBooks() + " " + 
                (author.getTotalBooks() == 1 ? context.getString(R.string.book) : context.getString(R.string.books));
            authorBooks.setText(booksText);
            
            // Load author image
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
        }
    }
} 