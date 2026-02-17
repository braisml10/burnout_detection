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
        setupNightHeatStrip(barChartNight);

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

        // ✅ Heat strip nocturno: 8 celdas (22..05)
        vm.getNightMinutes22to6().observe(this, minutes -> {
            if (minutes == null || minutes.length == 0) {
                barChartNight.clear();
                barChartNight.invalidate();
                return;
            }

            int[] mins8 = new int[8];
            for (int i = 0; i < 8 && i < minutes.length; i++) {
                mins8[i] = minutes[i];
            }

            renderNightHeatStrip(barChartNight, mins8);
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

        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setAxisMaximum(60f);
        c.getAxisLeft().setGranularity(10f);
        c.getAxisLeft().setLabelCount(7, true);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);
    }

    // ✅ Strip
    private void setupNightHeatStrip(BarChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);

        c.setTouchEnabled(false);
        c.setPinchZoom(false);
        c.setScaleEnabled(false);

        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setEnabled(false);

        XAxis x = c.getXAxis();
        x.setEnabled(false);

        c.setDrawGridBackground(false);
        c.setDrawBorders(false);

        c.setViewPortOffsets(0f, 0f, 0f, 0f);
        c.setExtraOffsets(0f, 0f, 0f, 0f);
        c.setFitBars(true);
    }

    private void renderNightHeatStrip(BarChart chart, int[] minutes8) {
        final int ON  = Color.parseColor("#FACC15"); // amarillo vivo
        final int OFF = Color.parseColor("#6B5A1A"); // amarillo apagado

        final float ON_V  = 1.0f;
        final float OFF_V = 1.0f; // ✅ clave: si es 0 no se dibuja

        List<BarEntry> entries = new ArrayList<>(8);
        List<Integer> colors = new ArrayList<>(8);

        for (int i = 0; i < 8; i++) {
            int m = (minutes8 != null && minutes8.length > i) ? minutes8[i] : 0;
            boolean used = m > 0;

            entries.add(new BarEntry(i, used ? ON_V : OFF_V));
            colors.add(used ? ON : OFF);
        }

        BarDataSet ds = new BarDataSet(entries, "");
        ds.setDrawValues(false);
        ds.setColors(colors);
        ds.setHighlightEnabled(false);

        ds.setBarBorderWidth(1f);
        ds.setBarBorderColor(Color.parseColor("#0E1729"));

        BarData data = new BarData(ds);
        data.setBarWidth(1.0f);

        chart.setData(data);

        chart.getXAxis().setAxisMinimum(-0.5f);
        chart.getXAxis().setAxisMaximum(7.5f);

        // ✅ fija el rango para que OFF_V se vea “bajito” y ON sea alto
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(1f);

        chart.invalidate();
    }

}
