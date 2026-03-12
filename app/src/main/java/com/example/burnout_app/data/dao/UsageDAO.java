package com.example.burnout_app.data.dao;

import android.database.Cursor;

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

    // ===================== APPS =====================
    @Query("SELECT app_id FROM app WHERE package_name = :pkg LIMIT 1")
    Long getAppIdByPackageName(String pkg);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertApp(AppEntity app);

    @Query("SELECT * FROM app ORDER BY name COLLATE NOCASE ASC")
    List<AppEntity> getAllApps();

    @Query("SELECT category FROM app WHERE app_id = :appId LIMIT 1")
    String getAppCategoryByAppId(long appId);

    @Query("SELECT name FROM app WHERE app_id = :appId LIMIT 1")
    String getAppNameByAppId(long appId);

    @Query("SELECT package_name FROM app WHERE app_id = :appId LIMIT 1")
    String getAppPackageNameByAppId(long appId);

    @Query("SELECT category FROM app WHERE package_name = :pkg LIMIT 1")
    String getAppCategoryByPackageName(String pkg);

    @Query("SELECT name FROM app WHERE package_name = :pkg LIMIT 1")
    String getAppNameByPackageName(String pkg);

    @Query("UPDATE app SET category = :category WHERE app_id = :appId")
    int updateAppCategoryByAppId(long appId, String category);

    @Query("UPDATE app SET name = :name WHERE app_id = :appId")
    int updateAppNameByAppId(long appId, String name);

    @Query("UPDATE app SET category = :category WHERE package_name = :pkg")
    int updateAppCategoryByPackageName(String pkg, String category);

    @Query("UPDATE app SET name = :name WHERE package_name = :pkg")
    int updateAppNameByPackageName(String pkg, String name);

    @Query("UPDATE app SET is_ignored = :ignored WHERE app_id = :appId")
    int updateAppIgnoredByAppId(long appId, boolean ignored);

    @Query("UPDATE app SET is_ignored = :ignored WHERE package_name = :pkg")
    int updateAppIgnoredByPackageName(String pkg, boolean ignored);

    @Query("SELECT app_id, category, is_ignored FROM app")
    Cursor getAppCategoryMapCursor();

    // ===================== APP USAGE EVENTS =====================
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertUsageEvents(List<AppUsageEventEntity> events);

    @Query("SELECT * FROM app_usage_event WHERE date = :date ORDER BY timestamp ASC")
    List<AppUsageEventEntity> getUsageEventsByDate(int date);

    @Query("SELECT COUNT(*) FROM app_usage_event WHERE date = :date")
    int getUsageEventCountByDate(int date);

    @Query("DELETE FROM app_usage_event WHERE date < :cutoffDate")
    int deleteUsageEventsOlderThanDate(int cutoffDate);

    // ===================== DAILY APP METRICS =====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailyAppMetrics(List<DailyAppMetricsEntity> rows);

    @Query("DELETE FROM daily_app_metric WHERE date < :cutoffDate")
    int deleteDailyAppMetricsOlderThanDate(int cutoffDate);

    @Query("SELECT app_id, SUM(foreground_ms) AS total_ms " +
            "FROM daily_app_metric " +
            "WHERE date = :date " +
            "GROUP BY app_id")
    Cursor getForegroundMsByAppIdForDayCursor(int date);

    @Query(
            "SELECT " +
                    "  CASE " +
                    "    WHEN a.category IS NULL OR TRIM(a.category) = '' THEN 'OTHER' " +
                    "    ELSE UPPER(a.category) " +
                    "  END AS category, " +
                    "  SUM(dam.foreground_ms) AS total_ms " +
                    "FROM daily_app_metric AS dam " +
                    "JOIN app AS a ON dam.app_id = a.app_id " +
                    "WHERE dam.date = :date " +
                    "  AND a.is_ignored = 0 " +
                    "GROUP BY category"
    )
    Cursor getCategoryTotalsMsForDayCursor(int date);

    @Query(
            "SELECT dam.app_id AS app_id, " +
                    "CASE " +
                    "  WHEN a.name IS NULL OR TRIM(a.name) = '' THEN a.package_name " +
                    "  ELSE a.name " +
                    "END AS name, " +
                    "a.package_name AS package_name, " +
                    "SUM(dam.foreground_ms) AS total_ms " +
                    "FROM daily_app_metric dam " +
                    "JOIN app a ON a.app_id = dam.app_id " +
                    "WHERE dam.date = :date " +
                    "  AND a.is_ignored = 0 " +
                    "GROUP BY dam.app_id " +
                    "ORDER BY total_ms DESC " +
                    "LIMIT :limit"
    )
    Cursor getTopAppsForDayCursor(int date, int limit);

    @Query(
            "SELECT SUM(d.foreground_ms) AS total_ms " +
                    "FROM daily_app_metric d " +
                    "INNER JOIN app a ON d.app_id = a.app_id " +
                    "WHERE d.date = :date AND a.is_ignored = 0"
    )
    Cursor getTotalForegroundMsForDayCursor(int date);
}