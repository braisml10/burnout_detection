package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.burnout_app.data.entity.AppEntity;
import com.example.burnout_app.data.entity.AppUsageEventEntity;
import com.example.burnout_app.data.entity.DailyAppMetricsEntity;

import java.util.List;

@Dao
public interface UsageDAO {

    // =========================================================
    // APP
    // =========================================================

    @Query("SELECT app_id FROM app WHERE package_name = :pkg LIMIT 1")
    Long getAppIdByPackageName(String pkg);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertApp(AppEntity app);

    @Query("UPDATE app SET category = :category WHERE app_id = :appId")
    int updateAppCategory(long appId, String category);

    @Query("UPDATE app SET name = :name WHERE app_id = :appId")
    int updateAppName(long appId, String name);

    @Query("SELECT category FROM app WHERE app_id = :appId LIMIT 1")
    String getCategoryByAppId(long appId);

    @Query("SELECT name FROM app WHERE app_id = :appId LIMIT 1")
    String getNameByAppId(long appId);

    @Query("SELECT package_name FROM app WHERE app_id = :appId LIMIT 1")
    String getPackageNameByAppId(long appId);

    // ---- (nuevo) por package_name, más estable para el Worker ----

    @Query("SELECT category FROM app WHERE package_name = :pkg LIMIT 1")
    String getCategoryByPackageName(String pkg);

    @Query("SELECT name FROM app WHERE package_name = :pkg LIMIT 1")
    String getNameByPackageName(String pkg);

    @Query("UPDATE app SET category = :category WHERE package_name = :pkg")
    int updateAppCategoryByPackageName(String pkg, String category);

    @Query("UPDATE app SET name = :name WHERE package_name = :pkg")
    int updateAppNameByPackageName(String pkg, String name);

    // ---- (opcional pero útil) listar apps para UI/depurar ----
    @Query("SELECT * FROM app ORDER BY name COLLATE NOCASE ASC")
    List<AppEntity> getAllApps();


    // =========================================================
    // APP_USAGE_EVENT
    // =========================================================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertUsageEvents(List<AppUsageEventEntity> events);

    @Query("SELECT * FROM app_usage_event WHERE date = :date ORDER BY timestamp ASC")
    List<AppUsageEventEntity> getUsageEventsByDate(int date);

    @Query("SELECT COUNT(*) FROM app_usage_event WHERE date = :date")
    int countUsageEventsByDate(int date);

    @Query("DELETE FROM app_usage_event WHERE date < :cutoffDate")
    int deleteUsageEventsOlderThanDate(int cutoffDate);


    // =========================================================
    // DAILY_APP_METRIC
    // =========================================================

    // ✅ Esto está bien porque tu entity tiene PK (date, app_id)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailyAppMetrics(List<DailyAppMetricsEntity> rows);

    @Query("DELETE FROM daily_app_metric WHERE date < :cutoffDate")
    int deleteDailyAppMetricsOlderThanDate(int cutoffDate);
}
