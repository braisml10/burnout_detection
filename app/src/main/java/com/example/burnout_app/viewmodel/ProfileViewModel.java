package com.example.burnout_app.viewmodel;

import android.app.Application;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.entity.UserProfileEntity;
import com.example.burnout_app.data.repo.UserProfileRepository;
import com.example.burnout_app.helpers.PasswordUtils;

public class ProfileViewModel extends AndroidViewModel {

    private final UserProfileRepository userProfileRepository;
    private final LiveData<UserProfileEntity> userProfileLiveData;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        userProfileRepository = new UserProfileRepository(application);
        userProfileLiveData = userProfileRepository.observeUserProfile();
    }

    // ===================== PROFILE OBSERVATION =====================

    public LiveData<UserProfileEntity> observeUserProfile() {
        return userProfileLiveData;
    }

    // ===================== INPUT VALIDATION =====================

    public boolean isProfileInputValid(String firstName, String lastName, String email) {
        if (TextUtils.isEmpty(firstName)
                || TextUtils.isEmpty(lastName)
                || TextUtils.isEmpty(email)) {
            return false;
        }

        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public boolean isPasswordInputValid(String password, String confirmPassword) {
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

    public boolean canUpdateUserProfile(UserProfileEntity currentUserProfile,
                                        String firstName,
                                        String lastName,
                                        String email,
                                        String currentPassword,
                                        String newPassword,
                                        String confirmPassword) {

        if (currentUserProfile == null) return false;

        if (!isProfileInputValid(firstName, lastName, email)) {
            return false;
        }

        boolean wantsToChangePassword =
                !TextUtils.isEmpty(currentPassword)
                        || !TextUtils.isEmpty(newPassword)
                        || !TextUtils.isEmpty(confirmPassword);

        if (!wantsToChangePassword) {
            return true;
        }

        String hashedCurrentPassword = PasswordUtils.hashPassword(currentPassword);

        if (!currentUserProfile.passwordHash.equals(hashedCurrentPassword)) {
            return false;
        }

        return isPasswordInputValid(newPassword, confirmPassword);
    }

    // ===================== PROFILE WRITE =====================

    public void updateUserProfile(UserProfileEntity currentUserProfile,
                                  String firstName,
                                  String lastName,
                                  String email,
                                  String newPassword) {

        if (currentUserProfile == null) return;

        String finalPassword = currentUserProfile.passwordHash;
        if (!TextUtils.isEmpty(newPassword)) {
            finalPassword = PasswordUtils.hashPassword(newPassword);
        }

        UserProfileEntity updatedUserProfile = new UserProfileEntity(
                firstName.trim(),
                lastName.trim(),
                email.trim(),
                finalPassword
        );
        updatedUserProfile.id = currentUserProfile.id;

        userProfileRepository.upsertUserProfile(updatedUserProfile);
    }

    public void updateUserProfileWithPasswordCheck(UserProfileEntity currentUserProfile,
                                                   String firstName,
                                                   String lastName,
                                                   String email,
                                                   String newPassword) {

        if (currentUserProfile == null) return;

        String finalPassword = currentUserProfile.passwordHash;
        if (!TextUtils.isEmpty(newPassword)) {
            finalPassword = PasswordUtils.hashPassword(newPassword);
        }

        UserProfileEntity updatedUserProfile = new UserProfileEntity(
                firstName.trim(),
                lastName.trim(),
                email.trim(),
                finalPassword
        );
        updatedUserProfile.id = currentUserProfile.id;

        userProfileRepository.upsertUserProfile(updatedUserProfile);
    }

    public void deleteUserProfile() {
        userProfileRepository.deleteAllUserProfiles();
    }
}