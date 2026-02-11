package com.example.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_metrics")
public class DailyMetricsEntity {
    @PrimaryKey
    public int date; // epochDay (TimeKey)

    public long screen_ms;
    public int unlock_count;

    public long foreground_ms;
    public int app_switch_count;
    public int unique_apps_count;
    public int session_count;

    public int notification_count;

    public long night_ms;

    public DailyMetricsEntity(int date, long screen_ms, int unlock_count, long foreground_ms, int app_switch_count, int unique_apps_count, int session_count, int notification_count, long night_ms) {
        this.date = date;
        this.screen_ms = screen_ms;
        this.unlock_count = unlock_count;
        this.foreground_ms = foreground_ms;
        this.app_switch_count = app_switch_count;
        this.unique_apps_count = unique_apps_count;
        this.session_count = session_count;
        this.notification_count = notification_count;
        this.night_ms = night_ms;
    }
}
