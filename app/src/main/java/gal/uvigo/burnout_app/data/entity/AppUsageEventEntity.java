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
    public long usage_id;
    @NonNull
    public long app_id;
    @NonNull
    public int event_type;
    @NonNull
    public long timestamp;
    public String source;
    public int date;

    public AppUsageEventEntity(long app_id, int event_type, long timestamp, String source, int date) {
        this.app_id = app_id;
        this.event_type = event_type;
        this.timestamp = timestamp;
        this.source = source;
        this.date = date;
    }
}
