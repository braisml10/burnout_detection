package com.example.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_comm_metric")
public class DailyCommMetricsEntity {

    @PrimaryKey
    public int date;

    public int calls_count;
    public int messages_count;

    public long total_comm_ms;
    public long voice_ms;
    public long text_ms;
    public long video_ms;

    // total_comm_ms se calculará en el worker
    public DailyCommMetricsEntity(int date, int calls_count, int messages_count, long total_comm_ms, long voice_ms, long text_ms, long video_ms) {
        this.date = date;
        this.calls_count = calls_count;
        this.messages_count = messages_count;
        this.total_comm_ms = total_comm_ms;
        this.voice_ms = voice_ms;
        this.text_ms = text_ms;
        this.video_ms = video_ms;
    }
}
