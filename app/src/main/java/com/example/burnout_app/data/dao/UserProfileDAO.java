package com.example.burnout_app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.burnout_app.data.entity.UserProfileEntity;

@Dao
public interface UserProfileDAO {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    LiveData<UserProfileEntity> getProfile();

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    UserProfileEntity getProfileSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(UserProfileEntity profile);

    @Update
    void update(UserProfileEntity profile);

    @Query("DELETE FROM user_profile")
    void deleteAll();
}
