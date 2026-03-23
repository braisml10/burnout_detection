package gal.uvigo.burnout_app.data.dao;

import android.database.Cursor;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import gal.uvigo.burnout_app.data.entity.NotificationEventEntity;

import java.util.List;

@Dao
public interface NotificationDAO {

    // ===================== NOTIFICATION EVENTS =====================
    @Insert
    void insertNotificationEvent(NotificationEventEntity event);

    @Query("SELECT * FROM notification_event WHERE date = :date ORDER BY timestamp")
    List<NotificationEventEntity> getNotificationEventsByDate(int date);

    @Query("DELETE FROM notification_event WHERE date < :cutoffDate")
    int deleteNotificationEventsOlderThanDate(int cutoffDate);

    // ===================== NOTIFICATION COUNTS =====================
    @Query("SELECT COUNT(*) FROM notification_event WHERE date = :date")
    int getNotificationCountByDate(int date);

    @Query("SELECT hour, COUNT(*) AS c FROM notification_event WHERE date = :date GROUP BY hour ORDER BY hour")
    Cursor getNotificationCountByHourCursor(int date);

    // ===================== NOTIFICATION AGGREGATIONS =====================
    @Query("SELECT app_id, COUNT(*) AS c FROM notification_event WHERE date = :date GROUP BY app_id ORDER BY c DESC LIMIT :limit")
    Cursor getTopNotificationAppsCursor(int date, int limit);

    @Query(
            "SELECT " +
                    "  CASE " +
                    "    WHEN a.category IS NULL OR TRIM(a.category) = '' THEN 'OTHER' " +
                    "    ELSE UPPER(a.category) " +
                    "  END AS app_category, " +
                    "  COUNT(*) AS c " +
                    "FROM notification_event ne " +
                    "JOIN app a ON ne.app_id = a.app_id " +
                    "WHERE ne.date = :date " +
                    "  AND a.is_ignored = 0 " +
                    "GROUP BY " +
                    "  CASE " +
                    "    WHEN a.category IS NULL OR TRIM(a.category) = '' THEN 'OTHER' " +
                    "    ELSE UPPER(a.category) " +
                    "  END " +
                    "ORDER BY c DESC"
    )
    Cursor getNotificationCountByAppCategoryCursor(int date);

    @Query("SELECT COUNT(*) FROM notification_event WHERE date = :epochDay AND app_id IN (" +
            "SELECT app_id FROM app WHERE category = 'MESSAGING')")
    int getMessagingNotificationCountByDate(int epochDay);

    @Query("SELECT hour, COUNT(*) AS c FROM notification_event " +
            "WHERE date = :epochDay AND app_id IN (SELECT app_id FROM app WHERE category = 'MESSAGING') " +
            "GROUP BY hour " +
            "ORDER BY hour")
    Cursor getMessagingNotificationCountByHourCursor(int epochDay);
}