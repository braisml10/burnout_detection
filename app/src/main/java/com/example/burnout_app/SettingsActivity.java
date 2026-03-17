package com.example.burnout_app;

import android.Manifest;
import android.content.Context;
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

import com.example.burnout_app.helpers.LanguageHelper;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_SELECTED_LANGUAGE = "selected_language";

    private static final String LANG_ES = "es";
    private static final String LANG_EN = "en";
    private static final String LANG_GL = "gl";

    private LinearLayout rowNotifications;
    private LinearLayout rowCommunication;
    private LinearLayout rowUsage;
    private LinearLayout rowLanguage;

    private TextView tvSelectedLanguage;
    private ImageButton btnBack;

    private SharedPreferences prefs;
    private ActivityResultLauncher<String[]> communicationPermissionsLauncher;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences languagePrefs =
                newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String langCode = languagePrefs.getString(KEY_SELECTED_LANGUAGE, LANG_ES);
        super.attachBaseContext(LanguageHelper.updateContext(newBase, langCode));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

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
                    boolean callGranted =
                            Boolean.TRUE.equals(result.get(Manifest.permission.READ_CALL_LOG));
                    boolean smsGranted =
                            Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS));

                    if (callGranted && smsGranted) {
                        Toast.makeText(
                                this,
                                getString(R.string.settings_comm_permissions_granted),
                                Toast.LENGTH_SHORT
                        ).show();
                    } else if (callGranted || smsGranted) {
                        Toast.makeText(
                                this,
                                getString(R.string.settings_comm_permissions_partial),
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                this,
                                getString(R.string.settings_comm_permissions_denied),
                                Toast.LENGTH_SHORT
                        ).show();
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
            Toast.makeText(
                    this,
                    getString(R.string.settings_error_open_notifications),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void openUsageAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    getString(R.string.settings_error_open_usage),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void openAppPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    getString(R.string.settings_error_open_app_settings),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showLanguageDialog() {
        String[] languageLabels = {
                getString(R.string.language_spanish),
                getString(R.string.language_english),
                getString(R.string.language_galician)
        };

        String[] languageCodes = {
                LANG_ES,
                LANG_EN,
                LANG_GL
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_language_dialog_title))
                .setItems(languageLabels, (dialog, which) -> {
                    String selectedCode = languageCodes[which];
                    String selectedLabel = languageLabels[which];

                    prefs.edit()
                            .putString(KEY_SELECTED_LANGUAGE, selectedCode)
                            .apply();

                    Toast.makeText(
                            this,
                            getString(R.string.settings_language_selected, selectedLabel),
                            Toast.LENGTH_SHORT
                    ).show();

                    restartAppAtMain();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void loadSavedLanguage() {
        String savedCode = prefs.getString(KEY_SELECTED_LANGUAGE, LANG_ES);
        tvSelectedLanguage.setText(getLanguageLabel(savedCode));
    }

    private String getLanguageLabel(String langCode) {
        if (LANG_EN.equals(langCode)) {
            return getString(R.string.language_english);
        }
        if (LANG_GL.equals(langCode)) {
            return getString(R.string.language_galician);
        }
        return getString(R.string.language_spanish);
    }

    private void goBackToMain() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void restartAppAtMain() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}