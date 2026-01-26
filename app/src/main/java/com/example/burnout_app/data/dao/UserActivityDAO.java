package com.example.burnout_app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.burnout_app.data.entity.ScreenEventEntity;

@Dao
public interface UserActivityDAO {

    @Insert
    void insertScreenEvent(ScreenEventEntity event);

    @Query("SELECT COUNT(*) FROM screen_event WHERE date = :date")
    int countScreenEventsByDate(int date);
}