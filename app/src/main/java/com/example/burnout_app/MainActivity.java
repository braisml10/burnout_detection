package com.example.burnout_app;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.burnout_app.collectors.ScreenStateReceiver;
import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DashboardViewModel;
import com.example.burnout_app.worker.DailyAggregationWorker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ScreenStateReceiver screenReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        TextView tvDate = findViewById(R.id.tvDate);
        tvDate.setText("| " + TimeKey.dateLabelFromTimestamp(System.currentTimeMillis()));

        // 0) Bind KPI TextViews (estos IDs salen de tu XML)
        TextView value1 = findViewById(R.id.value1);
        TextView value2 = findViewById(R.id.value2);
        TextView value3 = findViewById(R.id.value3);
        TextView value4 = findViewById(R.id.value4);

        // 0.1) ViewModel + observer
        DashboardViewModel vm = new ViewModelProvider(this).get(DashboardViewModel.class);
        vm.getUiState().observe(this, s -> {
            value1.setText(s.screenTime);
            value2.setText(s.notifications);
            value3.setText(s.multitask);
            value4.setText(s.communication);
        });

        // Registrar receiver de pantalla (dinámico, sin BootReceiver)
        screenReceiver = new ScreenStateReceiver();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, f);

        Log.d(TAG, "ScreenStateReceiver registered.");

        // 1) Comprobar permiso Usage Access
        if (!UsageStatsProvider.hasUsageAccess(this)) {
            Log.d(TAG, "Usage Access NOT granted -> opening settings");
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            Log.d(TAG, "Usage Access granted.");
        }

        // 2) Lanzar worker 1 vez para test
        OneTimeWorkRequest req =
                new OneTimeWorkRequest.Builder(DailyAggregationWorker.class).build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                "daily_aggregation_test",
                ExistingWorkPolicy.KEEP,
                req
        );

        Log.d(TAG, "Enqueued daily_aggregation_test (OneTimeWork).");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
                Log.d(TAG, "ScreenStateReceiver unregistered.");
            } catch (Exception ignored) {
            }
        }
    }
}
