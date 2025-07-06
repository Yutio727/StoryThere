package com.example.storythere.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Book.class, Author.class}, version = 7, exportSchema = false)
@TypeConverters({DateConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    
    public abstract BookDao bookDao(); // Using Room, which is an abstraction layer of SQLite
    public abstract AuthorDao authorDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add new columns with default values
            database.execSQL("ALTER TABLE books ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE books ADD COLUMN isAlreadyRead INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add parsedTextPath column
            database.execSQL("ALTER TABLE books ADD COLUMN parsedTextPath TEXT");
        }
    };
    
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add image column for server-side books
            database.execSQL("ALTER TABLE books ADD COLUMN image TEXT");
        }
    };
    
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add readingPosition column for storing reading progress
            database.execSQL("ALTER TABLE books ADD COLUMN readingPosition INTEGER NOT NULL DEFAULT 0");
        }
    };
    
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create authors table
            database.execSQL("CREATE TABLE IF NOT EXISTS authors (" +
                "authorId TEXT PRIMARY KEY NOT NULL, " +
                "name TEXT, " +
                "biography TEXT, " +
                "birthDate TEXT, " +
                "deathDate TEXT, " +
                "nationality TEXT, " +
                "photoUrl TEXT, " +
                "totalBooks INTEGER NOT NULL DEFAULT 0" +
                ")");
        }
    };
    
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add authorId column to books table
            database.execSQL("ALTER TABLE books ADD COLUMN authorId TEXT");
        }
    };
    
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "storythere_database"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
} 