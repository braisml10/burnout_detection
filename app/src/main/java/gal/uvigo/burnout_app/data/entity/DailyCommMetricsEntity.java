package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_comm_metric")
public class DailyCommMetricsEntity {

    @PrimaryKey
    public int date;

    public int callsCount;
    public int messagesCount;

    public long totalCommMs;
    public long voiceMs;
    public long textMs;

    public DailyCommMetricsEntity(int date,
                                  int callsCount,
                                  int messagesCount,
                                  long totalCommMs,
                                  long voiceMs,
                                  long textMs) {
        this.date = date;
        this.callsCount = callsCount;
        this.messagesCount = messagesCount;
        this.totalCommMs = totalCommMs;
        this.voiceMs = voiceMs;
        this.textMs = textMs;
    }
}