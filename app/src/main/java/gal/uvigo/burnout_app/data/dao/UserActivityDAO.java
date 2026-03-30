package gal.uvigo.burnout_app.data.dao;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyMetricsEntity;

import java.util.List;

@Dao
public interface UserActivityDAO {

    // ===================== DAILY METRICS =====================
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertDailyMetricsIfMissing(DailyMetricsEntity dailyMetrics);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailyMetrics(DailyMetricsEntity dailyMetrics);

    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    DailyMetricsEntity getDailyMetricsByDate(int date);

    @Query("SELECT * FROM daily_metrics WHERE date = :epochDay LIMIT 1")
    LiveData<DailyMetricsEntity> observeDailyMetrics(int epochDay);

    @Query("DELETE FROM daily_metrics WHERE date < :cutoffDate")
    int deleteDailyMetricsOlderThanDate(int cutoffDate);

    @Query("SELECT notification_count FROM daily_metrics WHERE date = :date LIMIT 1")
    Integer getNotificationCountByDate(int date);

    // ===================== HOURLY METRICS =====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHourlyMetrics(List<HourlyMetricsEntity> rows);

    @Query("SELECT * FROM hourly_metric WHERE date = :date ORDER BY hour ASC")
    List<HourlyMetricsEntity> getHourlyMetricsByDate(int date);

    @Query("SELECT * FROM hourly_metric WHERE date = :date ORDER BY hour ASC")
    LiveData<List<HourlyMetricsEntity>> observeHourlyMetricsByDate(int date);

    @Query("DELETE FROM hourly_metric WHERE date < :cutoffDate")
    int deleteHourlyMetricsOlderThanDate(int cutoffDate);

    @Query("SELECT hour AS hour, " +
            "app_switch_count AS switches " +
            "FROM hourly_metric " +
            "WHERE date = :date " +
            "ORDER BY hour ASC")
    Cursor getAppSwitchCountByHourCursor(int date);

    @Query("SELECT hour, notification_count AS notifs FROM hourly_metric WHERE date = :date ORDER BY hour")
    Cursor getNotificationCountByHourCursor(int date);

    @Query("SELECT COUNT(*) FROM hourly_metric WHERE date = :date AND screen_ms > 0")
    int getActiveHourCountByDate(int date);

    // ===================== Burnout Risk =====================
    @Query("SELECT * FROM daily_metrics WHERE date BETWEEN :startDay AND :endDay ORDER BY date ASC")
    List<DailyMetricsEntity> getDailyMetricsRange(int startDay, int endDay);
}