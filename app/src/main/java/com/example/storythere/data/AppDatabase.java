package com.example.storythere.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {Book.class}, version = 1, exportSchema = false)
@TypeConverters({DateConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    
    public abstract BookDao bookDao(); // Using Room, which is an abstraction layer of SQLite
    
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "storythere_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
} 