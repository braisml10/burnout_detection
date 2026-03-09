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

    private final UserProfileRepository repository;
    private final LiveData<UserProfileEntity> profileLiveData;

    public OnboardingViewModel(@NonNull Application application) {
        super(application);
        repository = new UserProfileRepository(application);
        profileLiveData = repository.getProfile();
    }

    public LiveData<UserProfileEntity> getProfile() {
        return profileLiveData;
    }

    public void createProfile(String nombre, String apellidos, String email, String password) {
        UserProfileEntity profile = new UserProfileEntity(
                nombre.trim(),
                apellidos.trim(),
                email.trim(),
                password
        );

        repository.insertOrReplace(profile);
    }

    public boolean isInputValid(String nombre, String apellidos, String email, String password, String confirmPassword) {
        if (TextUtils.isEmpty(nombre) ||
                TextUtils.isEmpty(apellidos) ||
                TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(password) ||
                TextUtils.isEmpty(confirmPassword)) {
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

    public interface LoginCallback {
        void onResult(boolean success);
    }

    public void login(String email, String password, LoginCallback callback) {
        new Thread(() -> {
            UserProfileEntity profile = repository.getProfileSync();

            boolean success = false;

            if (profile != null) {
                success = email.equals(profile.email)
                        && password.equals(profile.passwordHash);
            }

            callback.onResult(success);
        }).start();
    }
}