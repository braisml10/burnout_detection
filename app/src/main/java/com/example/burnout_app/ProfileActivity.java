package com.example.burnout_app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.data.entity.UserProfileEntity;
import com.example.burnout_app.helpers.SessionManager;
import com.example.burnout_app.viewmodel.ProfileViewModel;
import com.google.android.material.button.MaterialButton;

public class ProfileActivity extends AppCompatActivity {

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
                    .setTitle("Eliminar cuenta")
                    .setMessage("¿Estás seguro de que quieres eliminar tu cuenta? Esta acción no se puede deshacer.")
                    .setPositiveButton("Sí, eliminar", (dialog, which) -> {
                        SessionManager sessionManager = new SessionManager(ProfileActivity.this);
                        sessionManager.logout();

                        profileViewModel.deleteUserProfile();

                        Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
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
        String firstName = safe(userProfile.nombre);
        String lastName = safe(userProfile.apellidos);
        String email = safe(userProfile.email);

        tvNombre.setText(firstName);
        tvApellidos.setText(lastName);
        tvEmailInfo.setText(email);

        String fullName = (firstName + " " + lastName).trim();
        tvFullName.setText(fullName);

        tvAvatar.setText(getInitials(firstName, lastName));
    }

    private void showEditProfileDialog() {
        if (currentUserProfile == null) {
            Toast.makeText(this, "No se pudo cargar el perfil", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);

        EditText etNombre = dialogView.findViewById(R.id.etNombre);
        EditText etApellidos = dialogView.findViewById(R.id.etApellidos);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etOldPassword = dialogView.findViewById(R.id.etOldPassword);
        EditText etNewPassword = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);

        etNombre.setText(safe(currentUserProfile.nombre));
        etApellidos.setText(safe(currentUserProfile.apellidos));
        etEmail.setText(safe(currentUserProfile.email));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar perfil")
                .setView(dialogView)
                .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                .setPositiveButton("Guardar", null)
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
                            "Contraseña antigua incorrecta o nuevas no coincidentes.",
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

                Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private String getInitials(String firstName, String lastName) {
        String firstInitial = firstName != null && !firstName.isEmpty()
                ? firstName.substring(0, 1).toUpperCase()
                : "";

        String lastInitial = lastName != null && !lastName.isEmpty()
                ? lastName.substring(0, 1).toUpperCase()
                : "";

        return firstInitial + lastInitial;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}