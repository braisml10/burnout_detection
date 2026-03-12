package com.example.burnout_app.viewmodel;

import android.app.Application;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.entity.UserProfileEntity;
import com.example.burnout_app.data.repo.UserProfileRepository;

public class OnboardingViewModel extends AndroidViewModel {

    private final UserProfileRepository userProfileRepository;
    private final LiveData<UserProfileEntity> userProfileLiveData;

    public OnboardingViewModel(@NonNull Application application) {
        super(application);
        userProfileRepository = new UserProfileRepository(application);
        userProfileLiveData = userProfileRepository.observeUserProfile();
    }

    // ===================== PROFILE OBSERVATION =====================

    public LiveData<UserProfileEntity> observeUserProfile() {
        return userProfileLiveData;
    }

    // ===================== PROFILE CREATION =====================

    public void createUserProfile(String firstName,
                                  String lastName,
                                  String email,
                                  String password) {
        UserProfileEntity userProfile = new UserProfileEntity(
                firstName.trim(),
                lastName.trim(),
                email.trim(),
                password
        );

        userProfileRepository.upsertUserProfile(userProfile);
    }

    // ===================== INPUT VALIDATION =====================

    public boolean isInputValid(String firstName,
                                String lastName,
                                String email,
                                String password,
                                String confirmPassword) {
        if (TextUtils.isEmpty(firstName)
                || TextUtils.isEmpty(lastName)
                || TextUtils.isEmpty(email)
                || TextUtils.isEmpty(password)
                || TextUtils.isEmpty(confirmPassword)) {
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return false;
        }

        if (password.length() < 6) {
            return false;
        }

        return password.equals(confirmPassword);
    }

    // ===================== LOGIN =====================

    public interface LoginCallback {
        void onResult(boolean success);
    }

    public void login(String email, String password, LoginCallback callback) {
        new Thread(() -> {
            UserProfileEntity userProfile = userProfileRepository.getUserProfile();

            boolean success = false;
            if (userProfile != null) {
                success = email.equals(userProfile.email)
                        && password.equals(userProfile.passwordHash);
            }

            callback.onResult(success);
        }).start();
    }
}