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

    private ProfileViewModel viewModel;
    private UserProfileEntity currentProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        viewModel.getProfile().observe(this, profile -> {
            if (profile != null) {
                currentProfile = profile;
                bindProfile(profile);
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

                        viewModel.deleteAccount();

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

    private void bindProfile(UserProfileEntity profile) {
        String nombre = safe(profile.nombre);
        String apellidos = safe(profile.apellidos);
        String email = safe(profile.email);

        tvNombre.setText(nombre);
        tvApellidos.setText(apellidos);
        tvEmailInfo.setText(email);

        String fullName = (nombre + " " + apellidos).trim();
        tvFullName.setText(fullName);

        tvAvatar.setText(getInitials(nombre, apellidos));
    }

    private void showEditProfileDialog() {
        if (currentProfile == null) {
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

        etNombre.setText(safe(currentProfile.nombre));
        etApellidos.setText(safe(currentProfile.apellidos));
        etEmail.setText(safe(currentProfile.email));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar perfil")
                .setView(dialogView)
                .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                .setPositiveButton("Guardar", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String nombre = etNombre.getText().toString().trim();
                String apellidos = etApellidos.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String oldPassword = etOldPassword.getText().toString();
                String newPassword = etNewPassword.getText().toString();
                String confirmPassword = etConfirmPassword.getText().toString();

                boolean valid = viewModel.canUpdateProfile(
                        currentProfile,
                        nombre,
                        apellidos,
                        email,
                        oldPassword,
                        newPassword,
                        confirmPassword
                );

                if (!valid) {
                    Toast.makeText(
                            this,
                            "Contraseña antigua incorrecta o nuevas no coincidentes.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                viewModel.updateProfileWithPasswordCheck(
                        currentProfile,
                        nombre,
                        apellidos,
                        email,
                        newPassword
                );

                Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private String getInitials(String nombre, String apellidos) {
        String n = nombre != null && !nombre.isEmpty() ? nombre.substring(0, 1).toUpperCase() : "";
        String a = apellidos != null && !apellidos.isEmpty() ? apellidos.substring(0, 1).toUpperCase() : "";
        return n + a;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}