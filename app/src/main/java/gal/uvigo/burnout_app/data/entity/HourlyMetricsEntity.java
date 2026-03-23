package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;

@Entity(
        tableName = "hourly_metric",
        primaryKeys = {"date", "hour"}
)
public class HourlyMetricsEntity {

    public int date;
    public int hour;

    public long screen_ms;
    public int unlock_count;
    public int notification_count;
    public int app_switch_count;
    public int unique_apps_count;

    public HourlyMetricsEntity(int date, int hour, long screen_ms, int unlock_count, int notification_count, int app_switch_count, int unique_apps_count) {
        this.date = date;
        this.hour = hour;
        this.screen_ms = screen_ms;
        this.unlock_count = unlock_count;
        this.notification_count = notification_count;
        this.app_switch_count = app_switch_count;
        this.unique_apps_count = unique_apps_count;
    }
}