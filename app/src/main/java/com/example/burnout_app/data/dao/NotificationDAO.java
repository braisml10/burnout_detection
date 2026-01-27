package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.burnout_app.data.entity.NotificationEventEntity;

import java.util.List;

@Dao
public interface NotificationDAO {

    @Insert
    void insertNotificationEvent(NotificationEventEntity event);

    @Query("SELECT * FROM notification_event WHERE date = :date ORDER BY date")
    List<NotificationEventEntity> getNotificationEventsByDate(int date);

    @Query("DELETE FROM notification_event WHERE date < :cutoffDate")
    int deleteNotificationEventsOlderThanDate(long cutoffDate);
}
