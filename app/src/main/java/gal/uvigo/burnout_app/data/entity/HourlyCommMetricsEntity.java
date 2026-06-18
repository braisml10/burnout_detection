package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;

@Entity(tableName = "hourly_comm_metric", primaryKeys = {"date", "hour"})
public class HourlyCommMetricsEntity {

    public int date;
    public int hour;

    public long totalValue; // suma de los tiempos de llamadas y apps
    public long voiceValue; // tiempo en llamadas
    public long textValue; // tiempo en apps de mensajería

    public HourlyCommMetricsEntity(int date,
                                   int hour,
                                   long totalValue,
                                   long voiceValue,
                                   long textValue) {
        this.date = date;
        this.hour = hour;
        this.totalValue = totalValue;
        this.voiceValue = voiceValue;
        this.textValue = textValue;
    }

}