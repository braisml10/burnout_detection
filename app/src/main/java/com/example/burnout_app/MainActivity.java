package com.example.burnout_app;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.burnout_app.collectors.ScreenStateReceiver;
import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DashboardViewModel;
import com.example.burnout_app.worker.DailyAggregationWorker;
import com.google.android.material.card.MaterialCardView;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String UNIQUE_HOURLY = "daily_aggregation_hourly";
    private static final String UNIQUE_KICKOFF = "daily_aggregation_kickoff";

    private ScreenStateReceiver screenReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        MaterialCardView cardScreenTime = findViewById(R.id.cardScreenTime);
        MaterialCardView cardMultitask = findViewById(R.id.cardMultitask);

        setupCardNavigation(R.id.cardScreenTime, ActivityScreenTime.class, "cardScreenTime");
        setupCardNavigation(R.id.cardMultitask, ActivityMultitask.class, "cardMultitask");

        TextView tvDate = findViewById(R.id.tvDate);
        tvDate.setText("| " + TimeKey.dateLabelFromTimestamp(System.currentTimeMillis()));

        TextView value1 = findViewById(R.id.value1);
        TextView value2 = findViewById(R.id.value2);
        TextView value3 = findViewById(R.id.value3);
        TextView value4 = findViewById(R.id.value4);

        DashboardViewModel vm = new ViewModelProvider(this).get(DashboardViewModel.class);
        vm.getUiState().observe(this, s -> {
            value1.setText(s.screenTime);
            value2.setText(s.notifications);
            value3.setText(s.multitask);
            value4.setText(s.communication);
        });

        // Receiver pantalla (solo mientras la app vive)
        screenReceiver = new ScreenStateReceiver();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, f);
        Log.d(TAG, "ScreenStateReceiver registered.");

        // Permiso Usage Access
        if (!UsageStatsProvider.hasUsageAccess(this)) {
            Log.d(TAG, "Usage Access NOT granted -> opening settings");
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            Log.d(TAG, "Usage Access granted.");
        }

        WorkManager wm = WorkManager.getInstance(this);

        // 1) Programación HORARIA: idempotente (WorkManager manda)
        PeriodicWorkRequest hourlyReq =
                new PeriodicWorkRequest.Builder(DailyAggregationWorker.class, 1, TimeUnit.HOURS)
                        .build();

        wm.enqueueUniquePeriodicWork(
                UNIQUE_HOURLY,
                ExistingPeriodicWorkPolicy.KEEP,
                hourlyReq
        );
        Log.d(TAG, "enqueueUniquePeriodicWork(" + UNIQUE_HOURLY + ", KEEP) called.");

        // 2) (Opcional) Disparo inmediato 1 vez para test / primer arranque.
        //    No spamea porque es unique + KEEP.
        OneTimeWorkRequest kickoff =
                new OneTimeWorkRequest.Builder(DailyAggregationWorker.class).build();

        wm.enqueueUniqueWork(
                UNIQUE_KICKOFF,
                ExistingWorkPolicy.KEEP,
                kickoff
        );
        Log.d(TAG, "enqueueUniqueWork(" + UNIQUE_KICKOFF + ", KEEP) called.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
                Log.d(TAG, "ScreenStateReceiver unregistered.");
            } catch (Exception ignored) {}
        }
    }

    private void setupCardNavigation(int cardId, Class<?> targetActivity, String labelForLogs) {
        MaterialCardView card = findViewById(cardId);

        if (card == null) {
            Log.e(TAG, labelForLogs + " is NULL -> revisa activity_main.xml (o variantes) y el id.");
            return;
        }

        Log.d(TAG, labelForLogs + " FOUND -> attaching click listener");
        card.setClickable(true);
        card.setFocusable(true);

        card.setOnClickListener(v -> {
            Log.d(TAG, labelForLogs + " CLICKED -> opening " + targetActivity.getSimpleName());
            startActivity(new Intent(MainActivity.this, targetActivity));
        });
    }
}
