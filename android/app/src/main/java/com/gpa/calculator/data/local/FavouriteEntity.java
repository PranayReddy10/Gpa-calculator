package com.gpa.calculator.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favourites")
public class FavouriteEntity {
    @PrimaryKey
    @NonNull
    public String calcId = "";
    public long addedAt;
}
