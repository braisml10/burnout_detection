package gal.uvigo.burnout_app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_usage_event",
        indices = {@Index(value = {"app_id", "event_type", "timestamp"}, unique = true)}
)
public class AppUsageEventEntity {

    @PrimaryKey(autoGenerate = true)
    public long usageId;
    @NonNull
    public long appId;
    @NonNull
    public int eventType;
    @NonNull
    public long timestamp;
    public String source;
    public int date;

    public AppUsageEventEntity(long appId, int eventType, long timestamp, String source, int date) {
        this.appId = appId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.source = source;
        this.date = date;
    }
}
