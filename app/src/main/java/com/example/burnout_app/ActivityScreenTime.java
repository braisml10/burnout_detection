package com.example.burnout_app;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DailyDetailViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
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

    private int todayDay;
    private int selectedDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_time);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // OJO: en tu XML el id es lineChart
        lineChart = findViewById(R.id.lineChart);
        if (lineChart == null) {
            throw new IllegalStateException("lineChart NULL: el layout inflado no contiene R.id.lineChart (revisa variantes en res/layout-*)");
        }
        setupChart(lineChart);

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

        // KPIs observe
        vm.getUiState().observe(this, s -> {
            tvTotal.setText(s.totalScreenTime);
            tvSessions.setText(s.sessions);
            tvNight.setText(s.night);
        });

        // Chart observe (minutos por hora 6..22)
        vm.getScreenMinutes6to22().observe(this, minutes -> {
            if (minutes == null) {
                lineChart.clear();
                lineChart.invalidate();
                return;
            }

            List<Entry> entries = new ArrayList<>(minutes.length);

            // minutes[0] corresponde a 6h, minutes[16] corresponde a 22h (si es 6..22 inclusive => 17 valores)
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

    private void setupChart(LineChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(true);

        // ---- EJE X (6h–22h) ----
        XAxis x = c.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setAxisMinimum(6f);
        x.setAxisMaximum(22f);
        x.setLabelCount(9, true);
        x.setTextColor(Color.parseColor("#94A3B8"));
        x.setDrawGridLines(false);
        x.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int h = Math.round(value);
                return h + "h";
            }
        });

        // ---- EJE Y (0–60 minutos) ----
        c.getAxisRight().setEnabled(false);

        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setAxisMaximum(60f);  // 🔥 CLAVE
        c.getAxisLeft().setGranularity(10f);  // saltos cada 10 min
        c.getAxisLeft().setLabelCount(7, true); // 0,10,20,...,60
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);


    }

}
