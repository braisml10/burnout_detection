package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
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

    @Query("SELECT * FROM screen_event WHERE date = :date ORDER BY timestamp")
    List<ScreenEventEntity> getScreenEventsByDate(int date);

    @Query("DELETE FROM screen_event WHERE date < :cutoffDate")
    int deleteScreenEventsOlderThanDate(int cutoffDate);

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





}