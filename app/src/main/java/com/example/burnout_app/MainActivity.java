package com.example.burnout_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DashboardViewModel;
import com.google.android.material.card.MaterialCardView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private BarChart barChart3h;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // ---------------------------------------------------------
        // NAVIGATION
        // ---------------------------------------------------------
        setupCardNavigation(R.id.cardScreenTime, ActivityScreenTime.class, "cardScreenTime");
        setupCardNavigation(R.id.cardMultitask, ActivityMultitask.class, "cardMultitask");
        // (si más adelante activas Notifications/Communication, añades aquí)

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
            value1.setText(s.screenTime);
            value2.setText(s.notifications);
            value3.setText(s.multitask);
            value4.setText(s.communication);
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
        // USAGE ACCESS PERMISSION (solo abre ajustes; no WorkManager aquí)
        // ---------------------------------------------------------
        if (!UsageStatsProvider.hasUsageAccess(this)) {
            Log.d(TAG, "Usage Access NOT granted -> opening settings");
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            Log.d(TAG, "Usage Access granted.");
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
    // CHART HELPERS (estilo inspirado en tus otras activities)
    // =========================================================

    private void setup3hBarChart(BarChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setScaleEnabled(false);

        // X: 8 barras (0..7) -> 00,03,06,09,12,15,18,21
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
        c.getAxisLeft().setAxisMaximum(180f);     // ✅ 3h = 180 min
        c.getAxisLeft().setGranularity(30f);      // ✅ ticks cada 30 min
        c.getAxisLeft().setLabelCount(7, true);   // 0..180 (7 labels)
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
        long[] bucketsMs = new long[8]; // 0..7

        if (rows == null) return bucketsMs;

        for (HourlyMetricsEntity h : rows) {
            if (h == null) continue;
            int hour = h.hour;
            if (hour < 0 || hour > 23) continue;

            int bucket = hour / 3; // 0..7
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
        ds.setColor(Color.parseColor("#22D3EE")); // turquesa
        ds.setDrawValues(false);

        BarData data = new BarData(ds);
        data.setBarWidth(0.7f);

        chart.setData(data);

        // ✅ deja margen arriba para que no quede “pegado”
        float maxY = data.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(30f, maxY * 1.2f));

        chart.invalidate();
    }
}
