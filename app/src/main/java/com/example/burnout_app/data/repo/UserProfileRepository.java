package com.example.burnout_app.data.repo;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.dao.UserProfileDAO;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.UserProfileEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserProfileRepository {

    private final UserProfileDAO dao;
    private final LiveData<UserProfileEntity> profileLiveData;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public UserProfileRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        dao = db.userProfileDAO();
        profileLiveData = dao.getProfile();
    }

    public LiveData<UserProfileEntity> getProfile() {
        return profileLiveData;
    }

    public UserProfileEntity getProfileSync() {
        return dao.getProfileSync();
    }

    public void insertOrReplace(UserProfileEntity profile) {
        executorService.execute(() -> dao.insertOrReplace(profile));
    }

    public void update(UserProfileEntity profile) {
        executorService.execute(() -> dao.update(profile));
    }

    public void deleteAll() {
        executorService.execute(dao::deleteAll);
    }

    public interface ProfileExistsCallback {
        void onResult(boolean exists);
    }

    public void profileExists(ProfileExistsCallback callback) {
        executorService.execute(() -> {
            UserProfileEntity profile = dao.getProfileSync();
            boolean exists = profile != null;
            callback.onResult(exists);
        });
    }
}