package com.gpa.calculator.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "history")
public class HistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String calcId;
    public String title;
    public String inputsJson;
    public String resultsJson;
    /** Display string of the primary result, e.g. "90%". */
    public String summary;
    public long createdAt;
    public boolean synced;
    public long remoteId;
}
