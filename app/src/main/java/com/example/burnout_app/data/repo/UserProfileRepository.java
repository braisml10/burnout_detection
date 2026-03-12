package com.example.burnout_app.data.repo;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.dao.UserProfileDAO;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.UserProfileEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserProfileRepository {

    private final UserProfileDAO userProfileDao;
    private final LiveData<UserProfileEntity> userProfileLiveData;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public UserProfileRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        userProfileDao = db.userProfileDAO();
        userProfileLiveData = userProfileDao.observeUserProfile();
    }

    // ===================== PROFILE READ =====================

    public LiveData<UserProfileEntity> observeUserProfile() {
        return userProfileLiveData;
    }

    public UserProfileEntity getUserProfile() {
        return userProfileDao.getUserProfile();
    }

    public void userProfileExists(ProfileExistsCallback callback) {
        executorService.execute(() -> {
            UserProfileEntity profile = userProfileDao.getUserProfile();
            boolean exists = profile != null;
            callback.onResult(exists);
        });
    }

    // ===================== PROFILE WRITE =====================

    public void upsertUserProfile(UserProfileEntity profile) {
        executorService.execute(() -> userProfileDao.upsertUserProfile(profile));
    }

    public void updateUserProfile(UserProfileEntity profile) {
        executorService.execute(() -> userProfileDao.updateUserProfile(profile));
    }

    public void deleteAllUserProfiles() {
        executorService.execute(userProfileDao::deleteAllUserProfiles);
    }

    public interface ProfileExistsCallback {
        void onResult(boolean exists);
    }
}