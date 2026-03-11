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

    // ===================== SCREEN EVENTS =====================
    @Insert
    void insertScreenEvent(ScreenEventEntity event);

    @Query("DELETE FROM screen_event WHERE date < :cutoffDate")
    int deleteScreenEventsOlderThanDate(int cutoffDate);

    // ===================== DAILY_METRICS =====================
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertDailyIfMissing(DailyMetricsEntity dailyMetrics);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailyMetrics(DailyMetricsEntity dailyMetrics);

    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    DailyMetricsEntity getDailyMetricsByDate(int date);

    @Query("SELECT * FROM daily_metrics WHERE date = :epochDay LIMIT 1")
    LiveData<DailyMetricsEntity> observeDailyMetrics(int epochDay);

    @Query("DELETE FROM daily_metrics WHERE date < :cutoffDate")
    int deleteDailyMetricsOlderThanDate(int cutoffDate);

    // ===================== HOURLY METRICS =====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHourlyMetrics(List<HourlyMetricsEntity> rows);

    @Query("SELECT * FROM hourly_metric WHERE date = :date ORDER BY hour ASC")
    List<HourlyMetricsEntity> getHourlyMetricsByDate(int date);

    @Query("DELETE FROM hourly_metric WHERE date < :cutoffDate")
    int deleteHourlyMetricsOlderThanDate(int cutoffDate);

    @Query("SELECT * FROM hourly_metric WHERE date = :date ORDER BY hour ASC")
    LiveData<List<HourlyMetricsEntity>> observeHourlyMetricsByDate(int date);


    // for notifications
    @Query("SELECT notification_count FROM daily_metrics WHERE date = :date LIMIT 1")
    Integer getNotificationCountForDay(int date);

    // hourly_metric
    @Query("""
        SELECT hour AS hour,
               app_switch_count AS switches
        FROM hourly_metric
        WHERE date = :date
        ORDER BY hour ASC
    """)
    Cursor getSwitchesPerHourForDay(int date);

    // for notifications
    @Query("SELECT hour, notification_count AS notifs FROM hourly_metric WHERE date = :date ORDER BY hour")
    Cursor getNotificationsPerHourForDay(int date);

    @Query("SELECT COUNT(*) FROM hourly_metric WHERE date = :date AND screen_ms > 0")
    Cursor getActiveHoursForDay(int date);



}