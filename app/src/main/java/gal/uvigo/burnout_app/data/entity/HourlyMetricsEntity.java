package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;

@Entity(
        tableName = "hourly_metric",
        primaryKeys = {"date", "hour"}
)
public class HourlyMetricsEntity {

    public int date;
    public int hour;

    public long screenMs;
    public int unlockCount;
    public int notificationCount;
    public int appSwitchCount;
    public int uniqueAppsCount;

    public HourlyMetricsEntity(int date, int hour, long screenMs, int unlockCount, int notificationCount, int appSwitchCount, int uniqueAppsCount) {
        this.date = date;
        this.hour = hour;
        this.screenMs = screenMs;
        this.unlockCount = unlockCount;
        this.notificationCount = notificationCount;
        this.appSwitchCount = appSwitchCount;
        this.uniqueAppsCount = uniqueAppsCount;
    }
}