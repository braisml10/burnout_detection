package com.example.burnout_app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DashboardViewModel;
import com.example.burnout_app.worker.DailyAggregationWorker;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String WORK_DAILY_AGG = "daily_aggregation_work";

    // Runtime perms (se piden 1 vez; Android no vuelve a mostrar el diálogo si ya están concedidos)
    private static final int REQ_COMM_PERMS = 1001;
    private static final String[] COMM_PERMS = new String[] {
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS
    };

    // Para no insistir si el usuario los deniega (evita que vuelva a “salir el mensaje”)
    private static final String PREFS = "burnout_runtime";
    private static final String KEY_ASKED_COMM_PERMS = "asked_comm_perms_once";

    private BarChart barChart3h;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ---------------------------------------------------------
        // NAVIGATION (cards grandes)
        // ---------------------------------------------------------
        setupCardNavigation(R.id.cardScreenTime, ActivityScreenTime.class, "cardScreenTime");
        setupCardNavigation(R.id.cardMultitask, ActivityMultitask.class, "cardMultitask");
        setupCardNavigation(R.id.cardNotifications, ActivityNotifications.class, "cardNotifications");
        setupCardNavigation(R.id.cardCommunication, ActivityCommunications.class, "cardCommunication");

        // ---------------------------------------------------------
        // DATE LABEL
        // ---------------------------------------------------------
        TextView tvDate = findViewById(R.id.tvDate);
        tvDate.setText("| " + TimeKey.dateLabelFromTimestamp(System.currentTimeMillis()));

        // ---------------------------------------------------------
        // KPI BINDINGS
        // ---------------------------------------------------------
        TextView value1 = findViewById(R.id.value1);
        TextView value2 = findViewById(R.id.value2);
        TextView value3 = findViewById(R.id.value3);
        TextView value4 = findViewById(R.id.value4);

        DashboardViewModel vm = new ViewModelProvider(this).get(DashboardViewModel.class);
        vm.getUiState().observe(this, s -> {
            if (s == null) return;
            if (value1 != null) value1.setText(s.screenTime);
            if (value2 != null) value2.setText(s.notifications);
            if (value3 != null) value3.setText(s.multitask);
            if (value4 != null) {
                int calls = 0;
                try {
                    // s.communication debería ser "0", "12", etc. Si ya viene con texto, esto fallará y caerá al catch.
                    calls = Integer.parseInt(s.communication.trim());
                } catch (Exception ignored) {}

                String callsText = getResources().getQuantityString(
                        R.plurals.kpi_calls,
                        calls,
                        calls
                );
                value4.setText(callsText);
            } // llamadas / comm KPI
        });

        // ---------------------------------------------------------
        // CHART (3h buckets: 00-02, 03-05, ... 21-23)
        // ---------------------------------------------------------
        barChart3h = findViewById(R.id.barChart3h);
        if (barChart3h == null) {
            Log.e(TAG, "barChart3h is NULL -> revisa activity_main.xml: falta @+id/barChart3h");
        } else {
            setup3hBarChart(barChart3h);

            vm.getHourlyMetrics().observe(this, rows -> {
                long[] bucketsMs = aggregate3hBuckets(rows);
                render3hBars(barChart3h, bucketsMs);
            });
        }

        // ---------------------------------------------------------
        // PERMISSIONS / SPECIAL ACCESS
        // ---------------------------------------------------------
        ensureSpecialAccessAndRuntimePerms();
    }

    /**
     * Flujo:
     * 1) Pide runtime perms (READ_CALL_LOG, READ_SMS) SOLO 1 vez.
     * 2) Luego gestiona special access (Usage + NotificationListener).
     * 3) Si todo OK -> schedule WorkManager.
     */
    private void ensureSpecialAccessAndRuntimePerms() {

        // 0) Runtime perms (solo 1 vez; si ya concedidos, no sale diálogo)
        if (!hasAllCommPermissions()) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean askedOnce = prefs.getBoolean(KEY_ASKED_COMM_PERMS, false);

            if (!askedOnce) {
                Log.d(TAG, "Comm runtime permissions missing -> requesting (first time)");
                prefs.edit().putBoolean(KEY_ASKED_COMM_PERMS, true).apply();
                ActivityCompat.requestPermissions(this, COMM_PERMS, REQ_COMM_PERMS);
                return;
            } else {
                // Ya se pidió una vez y el usuario lo denegó => no insistimos
                Log.w(TAG, "Comm perms missing but already asked once -> not requesting again");
                // Aun así, seguimos con special access y worker (calls/sms quedarán en 0)
            }
        }

        // 1) Usage Access (special)
        boolean usageOk = UsageStatsProvider.hasUsageAccess(this);
        if (!usageOk) {
            Log.d(TAG, "Usage Access NOT granted -> opening settings");
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }

        // 2) Notification Listener (special)
        boolean notifOk = isNotificationListenerEnabled();
        if (!notifOk) {
            Log.d(TAG, "Notification Listener NOT enabled -> opening settings");
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            return;
        }

        // 3) Todo OK -> schedule
        Log.d(TAG, "All required access OK -> scheduling aggregation worker");
        ensureAggregationWorkScheduled();
    }

    private boolean hasAllCommPermissions() {
        for (String p : COMM_PERMS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Comprueba si tu NotificationListenerService está en enabled_notification_listeners.
     */
    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );

        if (TextUtils.isEmpty(enabled)) return false;

        ComponentName me = new ComponentName(
                this,
                com.example.burnout_app.collectors.BurnoutNotificationListenerService.class
        );

        // El string suele contener "pkg/class:pkg/class:..."
        return enabled.contains(me.flattenToString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_COMM_PERMS) {
            boolean allGranted = true;

            if (grantResults.length == 0) {
                allGranted = false;
            } else {
                for (int r : grantResults) {
                    if (r != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            }

            Log.d(TAG, "Comm perms result -> allGranted=" + allGranted);

            // Continúa el flujo (si no los conceden, calls/sms quedan en 0, pero la app funciona)
            ensureSpecialAccessAndRuntimePerms();
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

    // =========================================================
    // CHART HELPERS
    // =========================================================

    private void setup3hBarChart(BarChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setScaleEnabled(false);

        XAxis x = c.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);

        x.setGranularityEnabled(true);
        x.setGranularity(1f);

        x.setAxisMinimum(-0.5f);
        x.setAxisMaximum(7.5f);

        x.setAvoidFirstLastClipping(true);
        x.setDrawGridLines(false);
        x.setTextColor(Color.parseColor("#94A3B8"));
        x.setTextSize(12f);

        x.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int i = Math.round(value);
                switch (i) {
                    case 0: return "03";
                    case 1: return "06";
                    case 2: return "09";
                    case 3: return "12";
                    case 4: return "15";
                    case 5: return "18";
                    case 6: return "21";
                    case 7: return "24";
                    default: return "";
                }
            }
        });

        c.getAxisRight().setEnabled(false);

        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setAxisMaximum(180f);
        c.getAxisLeft().setGranularity(30f);
        c.getAxisLeft().setLabelCount(7, true);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);

        c.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return ((int) value) + "m";
            }
        });

        c.setFitBars(true);
        c.setExtraOffsets(4f, 2f, 6f, 6f);
    }

    private long[] aggregate3hBuckets(List<HourlyMetricsEntity> rows) {
        long[] bucketsMs = new long[8];
        if (rows == null) return bucketsMs;

        for (HourlyMetricsEntity h : rows) {
            if (h == null) continue;
            int hour = h.hour;
            if (hour < 0 || hour > 23) continue;

            int bucket = hour / 3;
            bucketsMs[bucket] += h.screen_ms;
        }
        return bucketsMs;
    }

    private void render3hBars(BarChart chart, long[] bucketsMs) {
        if (bucketsMs == null || bucketsMs.length != 8) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<BarEntry> entries = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            float minutes = bucketsMs[i] / 60000f;
            entries.add(new BarEntry(i, minutes));
        }

        BarDataSet ds = new BarDataSet(entries, "Minutos");
        ds.setColor(Color.parseColor("#22D3EE"));
        ds.setDrawValues(false);

        BarData data = new BarData(ds);
        data.setBarWidth(0.7f);

        chart.setData(data);

        float maxY = data.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(30f, maxY * 1.2f));

        chart.invalidate();
    }

    // =========================================================
    // WORKMANAGER
    // =========================================================

    private void ensureAggregationWorkScheduled() {

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                DailyAggregationWorker.class,
                1, TimeUnit.HOURS
        ).setConstraints(constraints).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_DAILY_AGG,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
        );

        OneTimeWorkRequest now = new OneTimeWorkRequest.Builder(DailyAggregationWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                WORK_DAILY_AGG + "_kickoff",
                ExistingWorkPolicy.REPLACE,
                now
        );
    }
}