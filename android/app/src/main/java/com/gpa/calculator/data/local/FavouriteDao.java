package com.gpa.calculator.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavouriteDao {
    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    LiveData<List<FavouriteEntity>> all();

    @Query("SELECT calcId FROM favourites")
    List<String> ids();

    @Query("SELECT calcId FROM favourites")
    LiveData<List<String>> idsLive();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavouriteEntity e);

    @Query("DELETE FROM favourites WHERE calcId = :calcId")
    void delete(String calcId);

    @Query("SELECT COUNT(*) FROM favourites WHERE calcId = :calcId")
    int exists(String calcId);
}
