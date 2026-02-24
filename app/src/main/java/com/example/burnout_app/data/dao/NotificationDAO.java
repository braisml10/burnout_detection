package com.example.burnout_app.data.dao;

import android.database.Cursor;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.burnout_app.data.entity.NotificationEventEntity;

import java.util.List;

@Dao
public interface NotificationDAO {

    @Insert
    void insertNotificationEvent(NotificationEventEntity event);

    @Query("SELECT * FROM notification_event WHERE date = :date ORDER BY timestamp")
    List<NotificationEventEntity> getNotificationEventsByDate(int date);

    @Query("DELETE FROM notification_event WHERE date < :cutoffDate")
    int deleteNotificationEventsOlderThanDate(int cutoffDate);

    @Query("SELECT COUNT(*) FROM notification_event WHERE date = :date")
    int countByDate(int date);

    @Query("SELECT hour, COUNT(*) AS c FROM notification_event WHERE date = :date GROUP BY hour ORDER BY hour")
    Cursor countByHourCursor(int date);

    @Query("SELECT app_id, COUNT(*) AS c FROM notification_event WHERE date = :date GROUP BY app_id ORDER BY c DESC LIMIT :limit")
    Cursor topAppsCursor(int date, int limit);
}
