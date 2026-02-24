package com.example.burnout_app;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.NotificationsViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

public class ActivityNotifications extends AppCompatActivity {

    private NotificationsViewModel vm;

    // KPIs
    private TextView tvTotalNotifs;
    private TextView tvAvgPerHour;
    private TextView tvMostIntrusive;

    // Day selector
    private TextView tvDayLabel;
    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private int todayDay;
    private int selectedDay;

    // Chart 1: tendencia (LINE)
    private LineChart chartNotifs;

    // ✅ Chart 2: Horas con más interrupciones (BAR 3h como MainActivity)
    private BarChart barChartInterruptions;

    // Top apps (3)
    private TextView tvApp1Name, tvApp2Name, tvApp3Name;
    private ProgressBar pbApp1, pbApp2, pbApp3;
    private TextView tvApp1Pct, tvApp2Pct, tvApp3Pct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        vm = new ViewModelProvider(this).get(NotificationsViewModel.class);

        // -------------------
        // KPIs
        // -------------------
        tvTotalNotifs = findViewById(R.id.tvTotalNotifsValue);
        tvAvgPerHour = findViewById(R.id.tvAvgPerHourValue);
        tvMostIntrusive = findViewById(R.id.tvMostIntrusiveValue);

        // -------------------
        // Chart 1 (LINE): tendencia
        // -------------------
        chartNotifs = findViewById(R.id.chartNotifs);
        if (chartNotifs == null) {
            throw new IllegalStateException("chartNotifs NULL: falta R.id.chartNotifs en el layout");
        }
        setupNotifsLineChart(chartNotifs);

        // -------------------
        // ✅ Chart 2 (BAR): interrupciones 3h
        // -------------------
        barChartInterruptions = findViewById(R.id.barChartInterruptions);
        if (barChartInterruptions == null) {
            throw new IllegalStateException("barChartInterruptions NULL: falta R.id.barChartInterruptions en el layout");
        }
        setup3hBarChart(barChartInterruptions);

        // -------------------
        // Top apps
        // -------------------
        tvApp1Name = findViewById(R.id.tvApp1Name);
        tvApp2Name = findViewById(R.id.tvApp2Name);
        tvApp3Name = findViewById(R.id.tvApp3Name);

        pbApp1 = findViewById(R.id.pbApp1);
        pbApp2 = findViewById(R.id.pbApp2);
        pbApp3 = findViewById(R.id.pbApp3);

        tvApp1Pct = findViewById(R.id.tvApp1Pct);
        tvApp2Pct = findViewById(R.id.tvApp2Pct);
        tvApp3Pct = findViewById(R.id.tvApp3Pct);

        // -------------------
        // Day selector
        // -------------------
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        tvDayLabel = findViewById(R.id.tvDayLabel);

        todayDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay = todayDay;

        btnPrevDay.setOnClickListener(v -> {
            selectedDay--;
            applyDayUi();
            vm.loadDay(selectedDay);
        });

        btnNextDay.setOnClickListener(v -> {
            if (selectedDay < todayDay) {
                selectedDay++;
                applyDayUi();
                vm.loadDay(selectedDay);
            }
        });

        // -------------------
        // Observers
        // -------------------
        vm.getUiState().observe(this, s -> {
            if (s == null) return;

            // KPIs
            tvTotalNotifs.setText(String.valueOf(s.totalDaily));
            tvAvgPerHour.setText(String.valueOf(s.avgPerHour));
            tvMostIntrusive.setText(prettyName(s.mostIntrusiveApp));

            // Chart 1: tendencia por hora (line)
            renderNotifsLineChart(chartNotifs, s.notifsByHour);

            // ✅ Chart 2: interrupciones 3h (bar igual que MainActivity)
            long[] buckets = aggregate3hBucketsFromNotifsByHour(s.notifsByHour);
            render3hBars(barChartInterruptions, buckets);

            // Top apps (3)
            renderTopApps(s.totalDaily, s.topApps);
        });

        applyDayUi();
        vm.loadDay(selectedDay);
    }

    private void applyDayUi() {
        String label;

        if (selectedDay == todayDay) label = "Hoy";
        else if (selectedDay == todayDay - 1) label = "Ayer";
        else label = TimeKey.dateLabelFromEpochDay(selectedDay);

        tvDayLabel.setText(label);

        boolean canGoNext = selectedDay < todayDay;
        btnNextDay.setEnabled(canGoNext);
        btnNextDay.setAlpha(canGoNext ? 1f : 0.35f);
    }

    // =========================================================
    // LINE CHART (tendencia)
    // =========================================================

    private void setupNotifsLineChart(LineChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(true);

        XAxis x = c.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setAxisMinimum(0f);
        x.setAxisMaximum(24f);
        x.setLabelCount(5, true);
        x.setTextColor(Color.parseColor("#94A3B8"));
        x.setDrawGridLines(false);
        x.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int h = Math.round(value);
                if (h == 24) return "24";
                if (h % 6 == 0) return String.format("%02d", h);
                return "";
            }
        });

        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setGranularity(1f);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);
    }

    private void renderNotifsLineChart(LineChart chart, int[] notifsByHour) {
        if (notifsByHour == null || notifsByHour.length != 24) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<Entry> entries = new ArrayList<>(25);
        for (int h = 0; h < 24; h++) {
            entries.add(new Entry(h, notifsByHour[h]));
        }
        entries.add(new Entry(24f, notifsByHour[23]));

        LineDataSet ds = new LineDataSet(entries, "Notificaciones");
        ds.setColor(Color.parseColor("#22D3EE"));
        ds.setLineWidth(2f);
        ds.setCircleColor(Color.parseColor("#22D3EE"));
        ds.setCircleRadius(3f);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(ds));
        chart.invalidate();
    }

    // =========================================================
    // ✅ BAR CHART 3h (Horas con más interrupciones) - igual MainActivity
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
        c.getAxisLeft().setGranularity(5f);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);

        c.setFitBars(true);
        c.setExtraOffsets(4f, 2f, 6f, 6f);
    }

    private long[] aggregate3hBucketsFromNotifsByHour(int[] notifsByHour) {
        long[] buckets = new long[8];
        if (notifsByHour == null || notifsByHour.length != 24) return buckets;

        for (int h = 0; h < 24; h++) {
            int bucket = h / 3; // 0..7
            buckets[bucket] += notifsByHour[h];
        }
        return buckets;
    }

    private void render3hBars(BarChart chart, long[] buckets) {
        if (buckets == null || buckets.length != 8) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<BarEntry> entries = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            entries.add(new BarEntry(i, (float) buckets[i]));
        }

        BarDataSet ds = new BarDataSet(entries, "Notificaciones");
        ds.setColor(Color.parseColor("#F97316")); // naranja como tu mock
        ds.setDrawValues(false);

        BarData data = new BarData(ds);
        data.setBarWidth(0.7f);

        chart.setData(data);

        float maxY = data.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(5f, maxY * 1.25f));

        chart.invalidate();
    }

    // =========================================================
    // Top apps (igual que tú)
    // =========================================================

    private void renderTopApps(int totalDaily, List<com.example.burnout_app.data.repo.NotificationRepository.TopNotifAppRow> top) {
        setTopRow(1, "--", 0, 0, totalDaily);
        setTopRow(2, "--", 0, 0, totalDaily);
        setTopRow(3, "--", 0, 0, totalDaily);

        if (top == null || top.isEmpty()) return;

        int mx = maxCount(top);
        if (top.size() >= 1) setTopRow(1, top.get(0).name, top.get(0).count, mx, totalDaily);
        if (top.size() >= 2) setTopRow(2, top.get(1).name, top.get(1).count, mx, totalDaily);
        if (top.size() >= 3) setTopRow(3, top.get(2).name, top.get(2).count, mx, totalDaily);
    }

    private int maxCount(List<com.example.burnout_app.data.repo.NotificationRepository.TopNotifAppRow> top) {
        int m = 0;
        for (com.example.burnout_app.data.repo.NotificationRepository.TopNotifAppRow r : top) {
            if (r != null) m = Math.max(m, r.count);
        }
        return m;
    }

    private void setTopRow(int idx, String name, int count, int maxCount, int totalDaily) {
        int bar = 0;
        if (maxCount > 0) bar = (int) Math.round((count * 100.0) / maxCount);

        int pct = 0;
        if (totalDaily > 0) pct = (int) Math.round((count * 100.0) / totalDaily);

        String n = prettyName(name);

        if (idx == 1) {
            tvApp1Name.setText(n);
            pbApp1.setProgress(bar);
            tvApp1Pct.setText(pct + "%");
        } else if (idx == 2) {
            tvApp2Name.setText(n);
            pbApp2.setProgress(bar);
            tvApp2Pct.setText(pct + "%");
        } else if (idx == 3) {
            tvApp3Name.setText(n);
            pbApp3.setProgress(bar);
            tvApp3Pct.setText(pct + "%");
        }
    }

    private static String prettyName(String s) {
        if (s == null) return "--";
        s = s.trim();
        if (s.isEmpty()) return "--";
        if (s.contains(".") && !s.contains(" ")) {
            String[] parts = s.split("\\.");
            return parts[parts.length - 1];
        }
        return s;
    }
}