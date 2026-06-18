package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_metrics")
public class DailyMetricsEntity {
    @PrimaryKey
    public int date;

    public long screenMs;
    public int unlockCount;

    public long foregroundMs;
    public int appSwitchCount;
    public int uniqueAppsCount;
    public int sessionCount;

    public int notificationCount;

    public long nightMs;

    public DailyMetricsEntity(int date, long screenMs, int unlockCount, long foregroundMs, int appSwitchCount, int uniqueAppsCount, int sessionCount, int notificationCount, long nightMs) {
        this.date = date;
        this.screenMs = screenMs;
        this.unlockCount = unlockCount;
        this.foregroundMs = foregroundMs;
        this.appSwitchCount = appSwitchCount;
        this.uniqueAppsCount = uniqueAppsCount;
        this.sessionCount = sessionCount;
        this.notificationCount = notificationCount;
        this.nightMs = nightMs;
    }
}
