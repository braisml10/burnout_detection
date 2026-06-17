package gal.uvigo.burnout_app.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.collectors.UsageStatsProvider;
import gal.uvigo.burnout_app.helpers.LanguageHelper;
import gal.uvigo.burnout_app.helpers.SessionManager;
import gal.uvigo.burnout_app.worker.DailyAggregationWorker;

import java.util.concurrent.TimeUnit;

import gal.uvigo.burnout_app.collectors.BurnoutNotificationListenerService;

public class WelcomeActivity extends AppCompatActivity {

    private static final String TAG = "WelcomeActivity";
    private static final String WORK_DAILY_AGG = "daily_aggregation_work";

    private boolean usageSettingsOpened = false;
    private boolean notifSettingsOpened = false;

    private static final int REQ_COMM_PERMS = 1001;
    private static final String[] COMM_PERMS = new String[]{
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS
    };

    private static final String PREFS = "burnout_runtime";
    private static final String KEY_ASKED_COMM_PERMS = "asked_comm_perms_once";

    private Button btnLogin;
    private Button btnCreateAccount;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs =
                newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "es");
        super.attachBaseContext(LanguageHelper.updateContext(newBase, langCode));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        btnLogin.setOnClickListener(v ->
                startActivity(new Intent(WelcomeActivity.this, LoginActivity.class)));

        btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class)));

        ensureSpecialAccessAndRuntimePerms();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureSpecialAccessAndRuntimePerms();
    }

    private void ensureSpecialAccessAndRuntimePerms() {

        if (!hasAllCommPermissions()) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean askedOnce = prefs.getBoolean(KEY_ASKED_COMM_PERMS, false);

            if (!askedOnce) {
                Log.d(TAG, "Comm runtime permissions missing -> requesting (first time)");
                prefs.edit().putBoolean(KEY_ASKED_COMM_PERMS, true).apply();
                ActivityCompat.requestPermissions(this, COMM_PERMS, REQ_COMM_PERMS);
            } else {
                Log.w(TAG, "Comm perms missing but already asked once -> not requesting again");
            }
        }

        boolean usageOk = UsageStatsProvider.hasUsageAccess(this);
        boolean notifOk = isNotificationListenerEnabled();

        Log.d(TAG, "Permissions state -> usage=" + usageOk + ", notif=" + notifOk);

        if (!usageOk && !usageSettingsOpened) {
            Log.d(TAG, "Opening Usage Access settings (once)");
            usageSettingsOpened = true;
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else if (!notifOk && !notifSettingsOpened) {
            Log.d(TAG, "Opening Notification Listener settings (once)");
            notifSettingsOpened = true;
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }

        if (usageOk && notifOk) {
            ensureAggregationWorkScheduled();
        }

        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
            finish();
        }
    }

    private boolean hasAllCommPermissions() {
        for (String p : COMM_PERMS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );

        if (TextUtils.isEmpty(enabled)) return false;

        ComponentName me = new ComponentName(
                this,
                BurnoutNotificationListenerService.class
        );

        return enabled.contains(me.flattenToString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_COMM_PERMS) {
            ensureSpecialAccessAndRuntimePerms();
        }
    }

    private void ensureAggregationWorkScheduled() {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                DailyAggregationWorker.class,
                1, TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_DAILY_AGG,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
        );
    }
}