package gal.uvigo.burnout_app.data.repo;

import android.content.Context;

import androidx.lifecycle.LiveData;

import gal.uvigo.burnout_app.data.dao.UserProfileDAO;
import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.UserProfileEntity;

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


    // ===================== PROFILE WRITE =====================

    public void upsertUserProfile(UserProfileEntity profile) {
        executorService.execute(() -> userProfileDao.upsertUserProfile(profile));
    }

    public void deleteAllUserProfiles() {
        executorService.execute(userProfileDao::deleteAllUserProfiles);
    }
}