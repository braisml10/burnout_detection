package gal.uvigo.burnout_app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import gal.uvigo.burnout_app.data.entity.UserProfileEntity;

@Dao
public interface UserProfileDAO {

    // ===================== PROFILE READ =====================
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    LiveData<UserProfileEntity> observeUserProfile();

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    UserProfileEntity getUserProfile();

    // ===================== PROFILE WRITE =====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertUserProfile(UserProfileEntity profile);

    @Update
    void updateUserProfile(UserProfileEntity profile);

    @Query("DELETE FROM user_profile")
    void deleteAllUserProfiles();
}