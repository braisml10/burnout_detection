package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.burnout_app.data.entity.AppEntity;
import com.example.burnout_app.data.entity.AppUsageEventEntity;

import java.util.List;

@Dao
public interface UsageDAO {

    // APP
    @Query("SELECT app_id FROM app WHERE package_name = :pkg LIMIT 1")
    Long getAppIdByPackageName(String pkg);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertApp(AppEntity app);


    // APP_USAGE_EVENT
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertUsageEvents(List<AppUsageEventEntity> events);

    @Query("SELECT * FROM app_usage_event WHERE date = :date ORDER BY timestamp ASC")
    List<AppUsageEventEntity> getUsageEventsByDate(int date);

    @Query("SELECT COUNT(DISTINCT app_id) FROM app_usage_event WHERE date = :date")
    int countUniqueAppsByDate(int date);

    @Query("SELECT COUNT(*) FROM app_usage_event WHERE date = :date")
    int countUsageEventsByDate(int date);

    @Query("DELETE FROM app_usage_event WHERE date < :cutoffDate")
    int deleteUsageEventsOlderThanDate(int cutoffDate);
}
