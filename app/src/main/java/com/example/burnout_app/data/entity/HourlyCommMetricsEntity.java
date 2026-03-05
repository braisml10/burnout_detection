package com.example.burnout_app.data.entity;

import androidx.room.Entity;

@Entity(tableName = "hourly_comm_metric", primaryKeys = {"date", "hour"})
public class HourlyCommMetricsEntity {

    public int date;
    public int hour;

    public long total_value; // suma de los tiempos de llamadas y apps
    public long voice_value; // tiempo en llamadas
    public long text_value; // tiempo en apps de mensajería

    public HourlyCommMetricsEntity(int date,
                                   int hour,
                                   long total_value,
                                   long voice_value,
                                   long text_value) {
        this.date = date;
        this.hour = hour;
        this.total_value = total_value;
        this.voice_value = voice_value;
        this.text_value = text_value;
    }

}