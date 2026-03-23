package gal.uvigo.burnout_app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import gal.uvigo.burnout_app.data.entity.DailyCommMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyCommMetricsEntity;

import java.util.List;

@Dao
public interface CommunicationDAO {

    // ===================== DAILY COMM METRICS =====================
    @Query("SELECT * FROM daily_comm_metric WHERE date = :epochDay LIMIT 1")
    LiveData<DailyCommMetricsEntity> observeDailyCommMetrics(int epochDay);

    @Query("SELECT * FROM daily_comm_metric WHERE date = :epochDay LIMIT 1")
    DailyCommMetricsEntity getDailyCommMetrics(int epochDay);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailyCommMetrics(DailyCommMetricsEntity row);

    @Query("INSERT OR IGNORE INTO daily_comm_metric(date,calls_count,messages_count,total_comm_ms,voice_ms,text_ms) " +
            "VALUES(:epochDay,0,0,0,0,0)")
    void insertDailyCommMetricsIfMissing(int epochDay);

    @Query("DELETE FROM daily_comm_metric WHERE date < :cutoffDate")
    int deleteDailyCommMetricsOlderThanDate(int cutoffDate);

    // ===================== HOURLY COMM METRICS =====================
    @Query("SELECT * FROM hourly_comm_metric WHERE date = :epochDay ORDER BY hour ASC")
    LiveData<List<HourlyCommMetricsEntity>> observeHourlyCommMetrics(int epochDay);

    @Query("SELECT * FROM hourly_comm_metric WHERE date = :epochDay ORDER BY hour ASC")
    List<HourlyCommMetricsEntity> getHourlyCommMetricsByDate(int epochDay);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHourlyCommMetrics(List<HourlyCommMetricsEntity> rows);

    @Query("DELETE FROM hourly_comm_metric WHERE date < :cutoffDate")
    int deleteHourlyCommMetricsOlderThanDate(int cutoffDate);
}