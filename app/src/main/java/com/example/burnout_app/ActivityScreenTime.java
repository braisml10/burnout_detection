package com.example.burnout_app;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DailyDetailViewModel;
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

public class ActivityScreenTime extends AppCompatActivity {

    private DailyDetailViewModel vm;

    private TextView tvTotal;
    private TextView tvSessions;
    private TextView tvNight;

    private TextView tvDayLabel;
    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private LineChart lineChart;
    private BarChart barChartNight;

    private int todayDay;
    private int selectedDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_time);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Charts
        lineChart = findViewById(R.id.lineChart);
        if (lineChart == null) {
            throw new IllegalStateException("lineChart NULL: falta R.id.lineChart en el layout");
        }
        setupLineChart(lineChart);

        barChartNight = findViewById(R.id.barChartNight);
        if (barChartNight == null) {
            throw new IllegalStateException("barChartNight NULL: falta R.id.barChartNight en el layout");
        }
        setupNightBarChart(barChartNight);

        vm = new ViewModelProvider(this).get(DailyDetailViewModel.class);

        // KPIs
        tvTotal = findViewById(R.id.tvTotalValue);
        tvSessions = findViewById(R.id.tvSessionsValue);
        tvNight = findViewById(R.id.tvNightValue);

        // Day selector
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        tvDayLabel = findViewById(R.id.tvDayLabel);

        todayDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay = todayDay;

        // KPIs
        vm.getUiState().observe(this, s -> {
            tvTotal.setText(s.totalScreenTime);
            tvSessions.setText(s.sessions);
            tvNight.setText(s.night);
        });

        // Line chart: minutos por hora 6..22
        vm.getScreenMinutes6to22().observe(this, minutes -> {
            if (minutes == null) {
                lineChart.clear();
                lineChart.invalidate();
                return;
            }

            List<Entry> entries = new ArrayList<>(minutes.length);
            for (int i = 0; i < minutes.length; i++) {
                float hour = 6f + i;
                entries.add(new Entry(hour, minutes[i]));
            }

            LineDataSet ds = new LineDataSet(entries, "Minutos");
            ds.setColor(Color.parseColor("#22D3EE"));
            ds.setLineWidth(2f);
            ds.setCircleColor(Color.parseColor("#22D3EE"));
            ds.setCircleRadius(3f);
            ds.setDrawValues(false);

            LineData data = new LineData(ds);
            lineChart.setData(data);
            lineChart.invalidate();
        });

        // Bar chart: noche 22..06 (22,23,00,01,02,03,04,05,06)
        vm.getNightMinutes22to6().observe(this, minutes -> {
            if (minutes == null) {
                barChartNight.clear();
                barChartNight.invalidate();
                return;
            }

            List<BarEntry> entries = new ArrayList<>(minutes.length);
            for (int i = 0; i < minutes.length; i++) {
                entries.add(new BarEntry(i, minutes[i])); // X = 0..8
            }

            BarDataSet ds = new BarDataSet(entries, "Minutos");
            ds.setColor(Color.parseColor("#F59E0B")); // naranja
            ds.setDrawValues(false);

            BarData data = new BarData(ds);
            data.setBarWidth(0.7f);

            barChartNight.setData(data);
            barChartNight.invalidate();
        });

        // Flechas
        btnPrevDay.setOnClickListener(v -> {
            selectedDay = selectedDay - 1;
            applyDayUi(selectedDay, todayDay);
            vm.loadDay(selectedDay);
        });

        btnNextDay.setOnClickListener(v -> {
            if (selectedDay < todayDay) {
                selectedDay = selectedDay + 1;
                applyDayUi(selectedDay, todayDay);
                vm.loadDay(selectedDay);
            }
        });

        applyDayUi(selectedDay, todayDay);
        vm.loadDay(selectedDay);
    }

    private void applyDayUi(int day, int today) {
        if (day == today) tvDayLabel.setText("Hoy");
        else tvDayLabel.setText(TimeKey.dateLabelFromEpochDay(day));

        boolean canGoNext = day < today;
        btnNextDay.setEnabled(canGoNext);
        btnNextDay.setAlpha(canGoNext ? 1.0f : 0.35f);
    }

    private void setupLineChart(LineChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(true);

        // X: 6..22
        XAxis x = c.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setAxisMinimum(6f);
        x.setAxisMaximum(22f);
        x.setLabelCount(9, true);
        x.setTextColor(Color.parseColor("#94A3B8"));
        x.setDrawGridLines(false);
        x.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int h = Math.round(value);
                return h + "h";
            }
        });

        // Y: 0..60
        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setAxisMaximum(60f);
        c.getAxisLeft().setGranularity(10f);
        c.getAxisLeft().setLabelCount(7, true);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);
    }

    private void setupNightBarChart(BarChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setScaleEnabled(false);

        // X: índices 0..8 -> 22,23,00..06
        XAxis x = c.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setAxisMinimum(-0.5f);
        x.setAxisMaximum(8.5f);
        x.setLabelCount(9, true);
        x.setTextColor(Color.parseColor("#94A3B8"));
        x.setDrawGridLines(false);
        x.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int i = Math.round(value);
                switch (i) {
                    case 0: return "22h";
                    case 1: return "23h";
                    case 2: return "00h";
                    case 3: return "01h";
                    case 4: return "02h";
                    case 5: return "03h";
                    case 6: return "04h";
                    case 7: return "05h";
                    case 8: return "06h";
                    default: return "";
                }
            }
        });

        // Y: 0..60
        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setAxisMaximum(60f);
        c.getAxisLeft().setGranularity(10f);
        c.getAxisLeft().setLabelCount(7, true);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);

        // Ajustes visuales típicos para barras
        c.setFitBars(true);
    }
}
