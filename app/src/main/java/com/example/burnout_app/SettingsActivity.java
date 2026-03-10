package com.example.burnout_app;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchNotifications;
    private Switch switchCalls;
    private Switch switchSms;
    private Switch switchUsage;

    private LinearLayout rowNotifications;
    private LinearLayout rowCalls;
    private LinearLayout rowSms;
    private LinearLayout rowUsage;
    private LinearLayout rowLanguage;

    private TextView tvSelectedLanguage;
    private ImageButton btnBack;

    private SharedPreferences prefs;

    private ActivityResultLauncher<String> callPermissionLauncher;
    private ActivityResultLauncher<String> smsPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);

        initViews();
        initLaunchers();
        setupListeners();
        refreshPermissionStates();
        loadSavedLanguage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStates();
        loadSavedLanguage();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);

        switchNotifications = findViewById(R.id.switchNotifications);
        switchCalls = findViewById(R.id.switchCalls);
        switchSms = findViewById(R.id.switchSms);
        switchUsage = findViewById(R.id.switchUsage);

        rowNotifications = findViewById(R.id.rowNotifications);
        rowCalls = findViewById(R.id.rowCalls);
        rowSms = findViewById(R.id.rowSms);
        rowUsage = findViewById(R.id.rowUsage);
        rowLanguage = findViewById(R.id.rowLanguage);

        tvSelectedLanguage = findViewById(R.id.tvSelectedLanguage);
    }

    private void initLaunchers() {
        callPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> refreshPermissionStates()
        );

        smsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> refreshPermissionStates()
        );
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> goBackToMain());

        rowNotifications.setOnClickListener(v -> openNotificationAccessSettings());
        rowCalls.setOnClickListener(v -> handleCallsPermission());
        rowSms.setOnClickListener(v -> handleSmsPermission());
        rowUsage.setOnClickListener(v -> openUsageAccessSettings());
        rowLanguage.setOnClickListener(v -> showLanguageDialog());

        switchNotifications.setOnClickListener(v -> {
            switchNotifications.setChecked(isNotificationAccessEnabled());
            openNotificationAccessSettings();
        });

        switchCalls.setOnClickListener(v -> handleCallsPermission());

        switchSms.setOnClickListener(v -> handleSmsPermission());

        switchUsage.setOnClickListener(v -> {
            switchUsage.setChecked(hasUsageAccess());
            openUsageAccessSettings();
        });
    }

    private void refreshPermissionStates() {
        switchNotifications.setChecked(isNotificationAccessEnabled());
        switchCalls.setChecked(hasCallsPermission());
        switchSms.setChecked(hasSmsPermission());
        switchUsage.setChecked(hasUsageAccess());
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

    private boolean hasUsageAccess() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;

            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        getPackageName()
                );
            } else {
                mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        getPackageName()
                );
            }

            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNotificationAccessEnabled() {
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );

        return enabledListeners != null && enabledListeners.contains(getPackageName());
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

    private void handleCallsPermission() {
        if (hasCallsPermission()) {
            Toast.makeText(this, "Permiso de llamadas ya concedido", Toast.LENGTH_SHORT).show();
            refreshPermissionStates();
            return;
        }

        callPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG);
    }

    private void handleSmsPermission() {
        if (hasSmsPermission()) {
            Toast.makeText(this, "Permiso de mensajes ya concedido", Toast.LENGTH_SHORT).show();
            refreshPermissionStates();
            return;
        }

        smsPermissionLauncher.launch(Manifest.permission.READ_SMS);
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

                    // Aquí más adelante puedes aplicar el cambio real de locale
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

    @Override
    public void onBackPressed() {
        goBackToMain();
    }
}