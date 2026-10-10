package com.gpa.calculator.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {
    @Insert
    long insert(HistoryEntity e);

    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    LiveData<List<HistoryEntity>> all();

    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT :limit")
    List<HistoryEntity> recent(int limit);

    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT :limit")
    LiveData<List<HistoryEntity>> recentLive(int limit);

    @Query("SELECT * FROM history WHERE calcId = :calcId ORDER BY createdAt DESC LIMIT :limit")
    LiveData<List<HistoryEntity>> byCalc(String calcId, int limit);

    @Query("SELECT * FROM history WHERE synced = 0")
    List<HistoryEntity> unsynced();

    @Query("UPDATE history SET synced = 1, remoteId = :remoteId WHERE id = :id")
    void markSynced(long id, long remoteId);

    @Query("SELECT COUNT(*) FROM history WHERE remoteId = :remoteId")
    int countByRemoteId(long remoteId);

    @Delete
    void delete(HistoryEntity e);

    @Query("DELETE FROM history")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM history")
    int count();
}
