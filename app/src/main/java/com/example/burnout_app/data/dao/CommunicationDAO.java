package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.HourlyCommMetricsEntity;

import java.util.List;

@Dao
public interface CommunicationDAO {

    //DAILY
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailyCommMetric(DailyCommMetricsEntity m);

    @Query("SELECT * FROM daily_comm_metric WHERE date = :date LIMIT 1")
    DailyCommMetricsEntity getDailyCommMetric(int date);

    //HOURLY
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHourlyCommMetric(HourlyCommMetricsEntity m);

    @Query("SELECT * FROM hourly_comm_metric WHERE date = :date ORDER BY date")
    List<HourlyCommMetricsEntity> getHourlyCommMetricsByDate(int date);
}
