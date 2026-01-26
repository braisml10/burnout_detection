package com.example.burnout_app.data.entity;

import androidx.room.Entity;

@Entity(tableName = "daily_app_metric", primaryKeys = {"date", "app_id"})
public class DailyAppMetricsEntity {
    public int date;
    public long app_id;
    public long foreground_ms;
    public int open_count;
    public int notification_count;

    public DailyAppMetricsEntity(int date, long app_id, long foreground_ms, int open_count, int notification_count) {
        this.date = date;
        this.app_id = app_id;
        this.foreground_ms = foreground_ms;
        this.open_count = open_count;
        this.notification_count = notification_count;
    }

}
