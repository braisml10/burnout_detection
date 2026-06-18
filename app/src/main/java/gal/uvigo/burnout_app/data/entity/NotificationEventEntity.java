package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notification_event")
public class NotificationEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long notifId;

    public long timestamp;
    public int date;
    public int hour;

    public long appId;

    public String category;
    public boolean isOngoing;
    public String source;

    public NotificationEventEntity(long timestamp, int date, int hour, long appId, String category, boolean isOngoing, String source) {
        this.timestamp = timestamp;
        this.date = date;
        this.hour = hour;
        this.appId = appId;
        this.category = category;
        this.isOngoing = isOngoing;
        this.source = source;
    }
}
