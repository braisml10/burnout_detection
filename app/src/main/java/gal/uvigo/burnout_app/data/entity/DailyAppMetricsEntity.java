package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;

@Entity(tableName = "daily_app_metric", primaryKeys = {"date", "appId"})
public class DailyAppMetricsEntity {
    public int date;
    public long appId;
    public long foregroundMs;
    public int openCount;
    public int notificationCount;

    public DailyAppMetricsEntity(int date, long appId, long foregroundMs, int openCount, int notificationCount) {
        this.date = date;
        this.appId = appId;
        this.foregroundMs = foregroundMs;
        this.openCount = openCount;
        this.notificationCount = notificationCount;
    }

}
