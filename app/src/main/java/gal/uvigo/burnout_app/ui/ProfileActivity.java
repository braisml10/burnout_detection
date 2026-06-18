package gal.uvigo.burnout_app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.base.BaseActivity;
import gal.uvigo.burnout_app.data.entity.UserProfileEntity;
import gal.uvigo.burnout_app.helpers.SessionManager;
import gal.uvigo.burnout_app.helpers.UiTextUtils;
import gal.uvigo.burnout_app.viewmodel.ProfileViewModel;

public class ProfileActivity extends BaseActivity {

    private TextView tvAvatar;
    private TextView tvFullName;
    private TextView tvNombre;
    private TextView tvApellidos;
    private TextView tvEmailInfo;

    private ImageButton btnBack;
    private ImageButton btnEditProfile;
    private MaterialButton btnDeleteAccount;

    private ProfileViewModel profileViewModel;
    private UserProfileEntity currentUserProfile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();

        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        profileViewModel.observeUserProfile().observe(this, userProfile -> {
            if (userProfile != null) {
                currentUserProfile = userProfile;
                bindProfile(userProfile);
            }
        });

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        btnDeleteAccount.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.profile_delete_account_button))
                    .setMessage(getString(R.string.profile_delete_account_message))
                    .setPositiveButton(getString(R.string.profile_delete_account_confirm), (dialog, which) -> {
                        SessionManager sessionManager = new SessionManager(ProfileActivity.this);
                        sessionManager.logout();

                        profileViewModel.deleteUserProfile();

                        Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .setNegativeButton(getString(R.string.common_cancel), (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    private void initViews() {
        tvAvatar = findViewById(R.id.tvAvatar);
        tvFullName = findViewById(R.id.tvFullName);
        tvNombre = findViewById(R.id.tvNombre);
        tvApellidos = findViewById(R.id.tvApellidos);
        tvEmailInfo = findViewById(R.id.tvEmailInfo);

        btnBack = findViewById(R.id.btnBack);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
    }

    private void bindProfile(UserProfileEntity userProfile) {
        String firstName = safe(userProfile.name);
        String lastName = safe(userProfile.surname);
        String email = safe(userProfile.email);

        tvNombre.setText(firstName);
        tvApellidos.setText(lastName);
        tvEmailInfo.setText(email);

        String fullName = (firstName + " " + lastName).trim();
        tvFullName.setText(fullName);

        tvAvatar.setText(UiTextUtils.getInitials(firstName, lastName));
    }

    private void showEditProfileDialog() {
        if (currentUserProfile == null) {
            Toast.makeText(this, getString(R.string.profile_error_load), Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);

        EditText etNombre = dialogView.findViewById(R.id.etNombre);
        EditText etApellidos = dialogView.findViewById(R.id.etApellidos);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etOldPassword = dialogView.findViewById(R.id.etOldPassword);
        EditText etNewPassword = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);

        etNombre.setText(safe(currentUserProfile.name));
        etApellidos.setText(safe(currentUserProfile.surname));
        etEmail.setText(safe(currentUserProfile.email));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_edit_title))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.common_cancel), (d, which) -> d.dismiss())
                .setPositiveButton(getString(R.string.common_save), null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String firstName = etNombre.getText().toString().trim();
                String lastName = etApellidos.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String currentPassword = etOldPassword.getText().toString();
                String newPassword = etNewPassword.getText().toString();
                String confirmPassword = etConfirmPassword.getText().toString();

                boolean isValid = profileViewModel.canUpdateUserProfile(
                        currentUserProfile,
                        firstName,
                        lastName,
                        email,
                        currentPassword,
                        newPassword,
                        confirmPassword
                );

                if (!isValid) {
                    Toast.makeText(
                            this,
                            getString(R.string.profile_error_password_mismatch),
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                profileViewModel.updateUserProfileWithPasswordCheck(
                        currentUserProfile,
                        firstName,
                        lastName,
                        email,
                        newPassword
                );

                Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }


    private String safe(String value) {
        return value == null ? "" : value;
    }
}