package com.example.burnout_app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.worker.DailyAggregationWorker;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1) Comprobar permiso Usage Access
        if (!UsageStatsProvider.hasUsageAccess(this)) {
            Log.d(TAG, "Usage Access NOT granted -> opening settings");
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            Log.d(TAG, "Usage Access granted.");
        }

        // 2) Lanzar worker 1 vez para test (debe salir log del worker)
        OneTimeWorkRequest req =
                new OneTimeWorkRequest.Builder(DailyAggregationWorker.class).build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                "daily_aggregation_test",
                ExistingWorkPolicy.REPLACE,
                req
        );

        Log.d(TAG, "Enqueued daily_aggregation_test (OneTimeWork).");
    }
}
