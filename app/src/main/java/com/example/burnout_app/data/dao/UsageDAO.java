package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.burnout_app.data.entity.AppUsageEventEntity;

import java.util.List;

@Dao
public interface UsageDAO {

    @Insert
    void insertUsageEvent(AppUsageEventEntity event);

    @Query("SELECT * FROM app_usage_event WHERE date = :date ORDER BY date")
    List<AppUsageEventEntity> getUsageEventsByDate(int date);

    @Query("DELETE FROM app_usage_event WHERE date < :cutoffDate")
    int deleteUsageEventsOlderThanDate(int cutoffDate);
}
