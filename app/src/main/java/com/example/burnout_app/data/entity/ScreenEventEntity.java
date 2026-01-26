package com.example.burnout_app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "screen_event")
public class ScreenEventEntity {

    @PrimaryKey(autoGenerate = true)
    public long screen_id;
    public long timestamp;
    @NonNull
    public int state;           // 1=ON, 0=OFF
    @NonNull
    public String source;      // ej: "broadcast", "manual", etc.
    public int date;
    public int hour;

    // id autogenerado no va a constructor
    public ScreenEventEntity(long timestamp, @NonNull int state, String source, @NonNull int date,int hour) {
        this.timestamp = timestamp;
        this.state = state;
        this.source = source;
        this.date = date;
        this.hour = hour;
    }
}

