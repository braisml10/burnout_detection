package com.example.burnout_app.data.dao;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.data.entity.ScreenEventEntity;

import java.util.List;

@Dao
public interface UserActivityDAO {

    // SCREEN_EVENT
    @Insert
    void insertScreenEvent(ScreenEventEntity event);

    @Insert
    void insertScreenEvents(List<ScreenEventEntity> events);

    @Query("SELECT COUNT(*) FROM screen_event WHERE date = :date")
    int countScreenEventsByDate(int date);

    @Query("SELECT * FROM screen_event WHERE date = :date ORDER BY timestamp ASC")
    List<ScreenEventEntity> getScreenEventsByDate(int date);

    @Query("DELETE FROM screen_event WHERE date < :cutoffDate")
    int deleteScreenEventsOlderThanDate(int cutoffDate);

    @Query("SELECT * FROM screen_event WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    List<ScreenEventEntity> getScreenEventsBetween(long start, long end);

    @Query("SELECT * FROM screen_event WHERE timestamp < :ts ORDER BY timestamp DESC LIMIT 1")
    ScreenEventEntity getLastScreenEventBefore(long ts);

    @Query("SELECT COUNT(*) FROM screen_event WHERE timestamp >= :start AND timestamp < :end AND state = :type")
    int countScreenEventsOfType(long start, long end, int type);

    // DAILY_METRIC
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertDailyIfMissing(DailyMetricsEntity dailyMetrics);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailyMetrics(DailyMetricsEntity dailyMetrics);

    @Query("UPDATE daily_metrics SET unlock_count = unlock_count + :inc WHERE date = :date")
    int incDailyUnlocks(int date, int inc);

    @Query("UPDATE daily_metrics SET screen_ms = screen_ms + :delta WHERE date = :date")
    int addDailyScreenMs(int date, long delta);

    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    DailyMetricsEntity getDailyMetricsByDate(int date);

    @Query("SELECT * FROM daily_metrics WHERE date = :epochDay")
    LiveData<DailyMetricsEntity> observeDailyMetrics(int epochDay);

    // HOURLY_METRIC
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHourlyMetrics(List<HourlyMetricsEntity> rows);

    @Query("SELECT * FROM hourly_metric WHERE date = :date ORDER BY hour ASC")
    List<HourlyMetricsEntity> getHourlyMetricsByDate(int date);

    @Query("DELETE FROM hourly_metric WHERE date < :cutoffDate")
    int deleteHourlyMetricsOlderThanDate(int cutoffDate);

    @Query("SELECT * FROM hourly_metric WHERE date = :date ORDER BY hour ASC")
    LiveData<List<HourlyMetricsEntity>> observeHourlyMetricsByDate(int date);

    // hourly_metric
    @Query("""
        SELECT hour AS hour,
               app_switch_count AS switches
        FROM hourly_metric
        WHERE date = :date
        ORDER BY hour ASC
    """)
    Cursor getSwitchesPerHourForDay(int date);

}