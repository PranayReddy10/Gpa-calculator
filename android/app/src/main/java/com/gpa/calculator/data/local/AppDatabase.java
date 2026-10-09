package com.gpa.calculator.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {HistoryEntity.class, FavouriteEntity.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract HistoryDao history();
    public abstract FavouriteDao favourites();

    public static AppDatabase get(Context c) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "gpa_calculator.db")
                            .fallbackToDestructiveMigration(false)
                            .build();
                }
            }
        }
        return instance;
    }
}
