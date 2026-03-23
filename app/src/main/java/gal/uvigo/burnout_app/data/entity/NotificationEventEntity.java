package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notification_event")
public class NotificationEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long notif_id;

    public long timestamp;
    public int date;
    public int hour;

    public long app_id;

    public String category;
    public boolean is_ongoing;
    public String source;

    public NotificationEventEntity(long timestamp, int date, int hour, long app_id, String category, boolean is_ongoing, String source) {
        this.timestamp = timestamp;
        this.date = date;
        this.hour = hour;
        this.app_id = app_id;
        this.category = category;
        this.is_ongoing = is_ongoing;
        this.source = source;
    }
}
