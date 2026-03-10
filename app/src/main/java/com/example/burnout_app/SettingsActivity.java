package com.example.burnout_app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout rowNotifications;
    private LinearLayout rowCommunication;
    private LinearLayout rowUsage;
    private LinearLayout rowLanguage;

    private TextView tvSelectedLanguage;
    private ImageButton btnBack;

    private SharedPreferences prefs;

    private ActivityResultLauncher<String[]> communicationPermissionsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);

        initViews();
        initLaunchers();
        setupListeners();
        loadSavedLanguage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedLanguage();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);

        rowNotifications = findViewById(R.id.rowNotifications);
        rowCommunication = findViewById(R.id.rowCommunication);
        rowUsage = findViewById(R.id.rowUsage);
        rowLanguage = findViewById(R.id.rowLanguage);

        tvSelectedLanguage = findViewById(R.id.tvSelectedLanguage);
    }

    private void initLaunchers() {
        communicationPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean callGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_CALL_LOG));
                    boolean smsGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS));

                    if (callGranted && smsGranted) {
                        Toast.makeText(this, "Permisos de comunicación concedidos", Toast.LENGTH_SHORT).show();
                    } else if (callGranted || smsGranted) {
                        Toast.makeText(this, "Solo se concedió uno de los permisos", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Permisos de comunicación denegados", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> goBackToMain());

        rowNotifications.setOnClickListener(v -> openNotificationAccessSettings());
        rowCommunication.setOnClickListener(v -> handleCommunicationPermissions());
        rowUsage.setOnClickListener(v -> openUsageAccessSettings());
        rowLanguage.setOnClickListener(v -> showLanguageDialog());
    }

    private boolean hasCallsPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAllCommunicationPermissions() {
        return hasCallsPermission() && hasSmsPermission();
    }

    private void handleCommunicationPermissions() {
        if (hasAllCommunicationPermissions()) {
            openAppPermissionSettings();
            return;
        }

        communicationPermissionsLauncher.launch(new String[]{
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_SMS
        });
    }

    private void openNotificationAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir ajustes de notificaciones", Toast.LENGTH_SHORT).show();
        }
    }

    private void openUsageAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir acceso de uso", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudieron abrir los ajustes de la app", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLanguageDialog() {
        String[] languages = {"Español", "English", "Galego"};

        new AlertDialog.Builder(this)
                .setTitle("Selecciona idioma")
                .setItems(languages, (dialog, which) -> {
                    String selected = languages[which];
                    prefs.edit().putString("selected_language", selected).apply();
                    tvSelectedLanguage.setText(selected);

                    Toast.makeText(
                            this,
                            "Idioma seleccionado: " + selected,
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void loadSavedLanguage() {
        String savedLanguage = prefs.getString("selected_language", "Español");
        tvSelectedLanguage.setText(savedLanguage);
    }

    private void goBackToMain() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

}