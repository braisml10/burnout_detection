package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.burnout_app.data.entity.ScreenEventEntity;

import java.util.List;

@Dao
public interface UserActivityDAO {

    @Insert
    void insertScreenEvent(ScreenEventEntity event);

    @Insert
    void insertScreenEvents(List<ScreenEventEntity> events);

    @Query("SELECT COUNT(*) FROM screen_event WHERE date = :date")
    int countScreenEventsByDate(int date);

    @Query("SELECT * FROM screen_event WHERE date = :date ORDER BY date")
    List<ScreenEventEntity> getScreenEventsByDate(int date);

    @Query("DELETE FROM screen_event WHERE date < :cutoffDate")
    int deleteScreenEventsOlderThanDate(int cutoffDate);
}