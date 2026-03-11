package com.example.burnout_app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.HourlyCommMetricsEntity;

import java.util.List;

@Dao
public interface CommunicationDAO {

    // ===================== DAILY_COMM_METRICS =====================
    @Query("SELECT * FROM daily_comm_metric WHERE date = :epochDay LIMIT 1")
    LiveData<DailyCommMetricsEntity> observeDailyComm(int epochDay);

    @Query("SELECT * FROM daily_comm_metric WHERE date = :epochDay LIMIT 1")
    DailyCommMetricsEntity getDailyComm(int epochDay);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDaily(DailyCommMetricsEntity row);

    @Query("INSERT OR IGNORE INTO daily_comm_metric(date,calls_count,messages_count,total_comm_ms,voice_ms,text_ms) " +
            "VALUES(:epochDay,0,0,0,0,0)")
    void insertDailyIfMissing(int epochDay);

    @Query("DELETE FROM daily_comm_metric WHERE date < :cutoffDate")
    int deleteDailyCommOlderThanDate(int cutoffDate);

    // ===================== HOURLY_COMM_METRIC =====================
    @Query("SELECT * FROM hourly_comm_metric WHERE date = :epochDay ORDER BY hour ASC")
    LiveData<List<HourlyCommMetricsEntity>> observeHourly(int epochDay);

    @Query("SELECT * FROM hourly_comm_metric WHERE date = :epochDay ORDER BY hour ASC")
    List<HourlyCommMetricsEntity> getHourlyCommByDate(int epochDay);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHourly(List<HourlyCommMetricsEntity> rows);

    @Query("DELETE FROM hourly_comm_metric WHERE date < :cutoffDate")
    int deleteHourlyCommOlderThanDate(int cutoffDate);
}