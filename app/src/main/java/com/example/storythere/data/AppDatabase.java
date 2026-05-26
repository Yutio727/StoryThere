package com.example.storythere.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Book.class, Author.class, RemoteBook.class}, version = 12, exportSchema = false)
@TypeConverters({DateConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    
    public abstract BookDao bookDao(); // Using Room, which is an abstraction layer of SQLite
    public abstract AuthorDao authorDao();
    public abstract RemoteBookDao remoteBookDao();

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
            // Add readingPosition column for storing viewing progress
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
    
    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Rename column timeOfListen to readingStats
            database.execSQL("ALTER TABLE books RENAME COLUMN timeOfListen TO readingStats");
        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS remote_books (" +
                "id INTEGER PRIMARY KEY NOT NULL, " +
                "title TEXT, " +
                "author TEXT, " +
                "fileUrl TEXT, " +
                "fileType TEXT, " +
                "image TEXT, " +
                "annotation TEXT" +
                ")");
        }
    };

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE remote_books ADD COLUMN recommendationRank INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE remote_books ADD COLUMN cachedAtMillis INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE remote_books ADD COLUMN recommendationSource TEXT");
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN serverBookId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE books ADD COLUMN remoteFileUrl TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN serverProgress REAL NOT NULL DEFAULT 0.0");
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_books_serverBookId ON books(serverBookId)");
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
} 
