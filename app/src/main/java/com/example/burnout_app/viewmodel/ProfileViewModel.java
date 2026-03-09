package com.example.burnout_app.viewmodel;

import android.app.Application;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.entity.UserProfileEntity;
import com.example.burnout_app.data.repo.UserProfileRepository;

public class ProfileViewModel extends AndroidViewModel {

    private final UserProfileRepository repository;
    private final LiveData<UserProfileEntity> profileLiveData;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new UserProfileRepository(application);
        profileLiveData = repository.getProfile();
    }

    public LiveData<UserProfileEntity> getProfile() {
        return profileLiveData;
    }

    public boolean isInputValid(String nombre, String apellidos, String email) {

        if (TextUtils.isEmpty(nombre) ||
                TextUtils.isEmpty(apellidos) ||
                TextUtils.isEmpty(email)) {
            return false;
        }

        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public boolean isPasswordValid(String password, String confirmPassword) {

        if (TextUtils.isEmpty(password) && TextUtils.isEmpty(confirmPassword)) {
            return true;
        }

        if (TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            return false;
        }

        if (password.length() < 6) {
            return false;
        }

        return password.equals(confirmPassword);
    }

    public void updateProfile(UserProfileEntity currentProfile,
                              String nombre,
                              String apellidos,
                              String email,
                              String newPassword) {

        if (currentProfile == null) return;

        String finalPassword = currentProfile.passwordHash;

        if (!TextUtils.isEmpty(newPassword)) {
            finalPassword = newPassword;
        }

        UserProfileEntity updatedProfile = new UserProfileEntity(
                nombre.trim(),
                apellidos.trim(),
                email.trim(),
                finalPassword
        );

        updatedProfile.id = currentProfile.id;

        repository.insertOrReplace(updatedProfile);
    }

    public void deleteAccount() {
        repository.deleteAll();
    }

    public boolean canUpdateProfile(UserProfileEntity currentProfile,
                                    String nombre,
                                    String apellidos,
                                    String email,
                                    String oldPassword,
                                    String newPassword,
                                    String confirmPassword) {

        if (currentProfile == null) return false;

        if (!isInputValid(nombre, apellidos, email)) {
            return false;
        }

        boolean wantsToChangePassword =
                !TextUtils.isEmpty(oldPassword) ||
                        !TextUtils.isEmpty(newPassword) ||
                        !TextUtils.isEmpty(confirmPassword);

        if (!wantsToChangePassword) {
            return true;
        }

        if (!currentProfile.passwordHash.equals(oldPassword)) {
            return false;
        }

        return isPasswordValid(newPassword, confirmPassword);
    }

    public void updateProfileWithPasswordCheck(UserProfileEntity currentProfile,
                                               String nombre,
                                               String apellidos,
                                               String email,
                                               String newPassword) {

        if (currentProfile == null) return;
        String finalPassword = currentProfile.passwordHash;
        if (!TextUtils.isEmpty(newPassword)) {
            finalPassword = newPassword;
        }
        UserProfileEntity updatedProfile = new UserProfileEntity(
                nombre.trim(),
                apellidos.trim(),
                email.trim(),
                finalPassword
        );
        updatedProfile.id = currentProfile.id;

        repository.insertOrReplace(updatedProfile);
    }


}