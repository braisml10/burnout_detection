package gal.uvigo.burnout_app.data.dao;

import android.database.Cursor;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import gal.uvigo.burnout_app.data.entity.AppEntity;
import gal.uvigo.burnout_app.data.entity.AppUsageEventEntity;
import gal.uvigo.burnout_app.data.entity.DailyAppMetricsEntity;

import java.util.List;

@Dao
public interface UsageDAO {

    // ===================== APPS =====================
    @Query("SELECT appId FROM app WHERE packageName = :pkg LIMIT 1")
    Long getAppIdByPackageName(String pkg);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertApp(AppEntity app);

    @Query("SELECT category FROM app WHERE appId = :appId LIMIT 1")
    String getAppCategoryByAppId(long appId);

    @Query("SELECT name FROM app WHERE appId = :appId LIMIT 1")
    String getAppNameByAppId(long appId);

    @Query("SELECT packageName FROM app WHERE appId = :appId LIMIT 1")
    String getAppPackageNameByAppId(long appId);

    @Query("UPDATE app SET category = :category WHERE appId = :appId")
    int updateAppCategoryByAppId(long appId, String category);

    @Query("UPDATE app SET name = :name WHERE appId = :appId")
    int updateAppNameByAppId(long appId, String name);

    @Query("UPDATE app SET isIgnored = :ignored WHERE appId = :appId")
    int updateAppIgnoredByAppId(long appId, boolean ignored);

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

    @Query(
            "SELECT " +
                    "  CASE " +
                    "    WHEN a.category IS NULL OR TRIM(a.category) = '' THEN 'OTHER' " +
                    "    ELSE UPPER(a.category) " +
                    "  END AS category, " +
                    "  SUM(dam.foregroundMs) AS totalMs " +
                    "FROM daily_app_metric AS dam " +
                    "JOIN app AS a ON dam.appId = a.appId " +
                    "WHERE dam.date = :date " +
                    "  AND a.isIgnored = 0 " +
                    "GROUP BY category"
    )
    Cursor getCategoryTotalsMsForDayCursor(int date);

    @Query(
            "SELECT dam.appId AS appId, " +
                    "CASE " +
                    "  WHEN a.name IS NULL OR TRIM(a.name) = '' THEN a.packageName " +
                    "  ELSE a.name " +
                    "END AS name, " +
                    "a.packageName AS packageName, " +
                    "SUM(dam.foregroundMs) AS totalMs " +
                    "FROM daily_app_metric dam " +
                    "JOIN app a ON a.appId = dam.appId " +
                    "WHERE dam.date = :date " +
                    "  AND a.isIgnored = 0 " +
                    "GROUP BY dam.appId " +
                    "ORDER BY totalMs DESC " +
                    "LIMIT :limit"
    )
    Cursor getTopAppsForDayCursor(int date, int limit);

    @Query(
            "SELECT SUM(d.foregroundMs) AS totalMs " +
                    "FROM daily_app_metric d " +
                    "INNER JOIN app a ON d.appId = a.appId " +
                    "WHERE d.date = :date AND a.isIgnored = 0"
    )
    Cursor getTotalForegroundMsForDayCursor(int date);
}