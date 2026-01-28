package com.example.burnout_app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.helpers.TimeKey;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        new Thread(() -> {
            BurnoutDatabase db = BurnoutDatabase.getInstance(getApplicationContext());
            db.usageDao().countUsageEventsByDate( TimeKey.epochDayLocal(System.currentTimeMillis()) );
        }).start();

        ensureUsageAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Cuando vuelves de Ajustes, comprobamos otra vez
        if (UsageStatsProvider.hasUsageAccess(this)) {
            Log.d(TAG, "Usage Access granted.");
        } else {
            Log.d(TAG, "Usage Access NOT granted.");
        }
    }

    private void ensureUsageAccess() {
        if (!UsageStatsProvider.hasUsageAccess(this)) {
            Log.d(TAG, "Requesting Usage Access -> opening settings");
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        } else {
            Log.d(TAG, "Usage Access already granted.");
        }
    }
}
