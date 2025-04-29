package com.example.storythere.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.storythere.data.Book;
import com.example.storythere.data.BookRepository;
import java.util.List;

public class BookListViewModel extends AndroidViewModel {
    private final BookRepository repository;
    private final LiveData<List<Book>> allBooks;
    
    public BookListViewModel(Application application) {
        super(application);
        repository = new BookRepository(application);
        allBooks = repository.getAllBooks();
    }
    
    public LiveData<List<Book>> getAllBooks() {
        return allBooks;
    }
    
    public void insert(Book book) {
        repository.insert(book);
    }
    
    public void update(Book book) {
        repository.update(book);
    }

    public void delete(Book book) {
        repository.delete(book);
    }
} 