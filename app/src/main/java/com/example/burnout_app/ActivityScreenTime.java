package com.example.burnout_app;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DailyDetailViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.ArrayList;
import java.util.List;

public class ActivityScreenTime extends AppCompatActivity {

    private DailyDetailViewModel vm;

    private TextView tvTotal;
    private TextView tvSessions;
    private TextView tvNight; // KPI (arriba)

    // Summary abajo del strip (según tu XML)
    private TextView tvNightTotal;
    private TextView tvNightActiveHours;

    private TextView tvDayLabel;
    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private LineChart lineChart;
    private BarChart barChartNight;

    private int todayDay;
    private int selectedDay;

    // cache para marker
    private int[] lastNightMinutes8;

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
        setupNightTimeline(barChartNight);

        vm = new ViewModelProvider(this).get(DailyDetailViewModel.class);

        // KPIs
        tvTotal = findViewById(R.id.tvTotalValue);
        tvSessions = findViewById(R.id.tvSessionsValue);
        tvNight = findViewById(R.id.tvNightValue);

        // Summary (tu XML)
        tvNightTotal = findViewById(R.id.tvNightTotal);
        tvNightActiveHours = findViewById(R.id.tvNightActiveHours);

        // Day selector
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        tvDayLabel = findViewById(R.id.tvDayLabel);

        todayDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay = todayDay;

        // KPIs
        vm.getUiState().observe(this, s -> {
            if (s == null) return;

            tvTotal.setText(s.totalScreenTime);
            tvSessions.setText(s.sessions);
            tvNight.setText(s.night);

            // ✅ “Uso total” abajo usa EXACTAMENTE el mismo valor del KPI
            if (tvNightTotal != null) {
                int totalMinutes = Integer.parseInt(safeText(tvNight));  // ejemplo: "127 min" o "127"
                tvNightTotal.setText("Uso total: " + TimeKey.formatDurationMinutes(totalMinutes));
            }
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

        // Night timeline: 8 segmentos (22..05)
        vm.getNightMinutes22to6().observe(this, minutes -> {
            if (minutes == null || minutes.length != 8) {
                lastNightMinutes8 = null;

                // quitar marker si no hay datos
                barChartNight.setMarker(null);

                barChartNight.clear();
                barChartNight.invalidate();
                setNightActiveHours(null);
                return;
            }

            lastNightMinutes8 = minutes;

            NightMarkerView mv = new NightMarkerView(this);
            mv.setChartView(barChartNight);
            barChartNight.setMarker(mv);

            renderNightTimeline(barChartNight, minutes);
            setNightActiveHours(minutes);
        });

        // Flechas
        btnPrevDay.setOnClickListener(v -> {
            selectedDay -= 1;
            applyDayUi(selectedDay, todayDay);
            vm.loadDay(selectedDay);
        });

        btnNextDay.setOnClickListener(v -> {
            if (selectedDay < todayDay) {
                selectedDay += 1;
                applyDayUi(selectedDay, todayDay);
                vm.loadDay(selectedDay);
            }
        });

        applyDayUi(selectedDay, todayDay);
        vm.loadDay(selectedDay);
    }

    private static String safeText(TextView tv) {
        if (tv == null || tv.getText() == null) return "--";
        String s = tv.getText().toString().trim();
        return s.isEmpty() ? "--" : s;
    }

    private void applyDayUi(int day, int today) {
        if (day == today) {
            tvDayLabel.setText("Hoy");
        } else if (day == today - 1) {
            tvDayLabel.setText("Ayer");
        } else {
            long dayStartLocalMs = TimeKey.startOfDayMsFromEpochDay(day);
            tvDayLabel.setText(TimeKey.dateLabelFromTimestamp(dayStartLocalMs));
        }

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

    // =========================
    // Night timeline (22:00–06:00)
    // =========================

    private void setupNightTimeline(BarChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setScaleEnabled(false);

        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setEnabled(false);

        XAxis x = c.getXAxis();
        x.setEnabled(false);

        c.setDrawGridBackground(false);
        c.setDrawBorders(false);

        float topPx = dp(28); // espacio para que el marker “quepa” dentro del chart
        c.setViewPortOffsets(0f, topPx, 0f, 0f);
        c.setExtraOffsets(0f, 0f, 0f, 0f);
        c.setFitBars(true);

        // ✅ importante para mostrar MarkerView
        c.setDrawMarkers(true);

        // ✅ ya NO toast, dejamos que el Marker haga el trabajo
        c.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override public void onValueSelected(Entry e, Highlight h) {
                // NO limpiar highlight aquí
                // Si limpias highlight -> el marker desaparece al instante.
            }

            @Override public void onNothingSelected() { }
        });
    }

    private void renderNightTimeline(BarChart chart, int[] minutes8) {
        List<BarEntry> entries = new ArrayList<>(8);
        List<Integer> colors = new ArrayList<>(8);

        for (int i = 0; i < 8; i++) {
            int m = minutes8[i];
            entries.add(new BarEntry(i, 1f)); // altura fija para “barra continua”
            colors.add(colorForNightMinutes(m)); // intensidad
        }

        BarDataSet ds = new BarDataSet(entries, "");
        ds.setDrawValues(false);
        ds.setColors(colors);

        // separación sutil entre segmentos
        ds.setBarBorderWidth(0.8f);
        ds.setBarBorderColor(Color.parseColor("#0B1220"));

        ds.setHighlightEnabled(true);
        ds.setHighLightColor(Color.TRANSPARENT);

        BarData data = new BarData(ds);
        data.setBarWidth(0.92f);

        chart.setData(data);

        chart.getXAxis().setAxisMinimum(-0.5f);
        chart.getXAxis().setAxisMaximum(7.5f);

        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(1f);

        chart.invalidate();
    }

    private int colorForNightMinutes(int m) {
        // Amarillos MUY diferenciados
        final int c0 = Color.parseColor("#2A240F"); // apagado (0..9)
        final int c1 = Color.parseColor("#9A7B12"); // medio (10..29)
        final int c2 = Color.parseColor("#F59E0B"); // fuerte (30..44)
        final int c3 = Color.parseColor("#FDE047"); // muy intenso (>=45)

        if (m <= 0) return c0;
        if (m < 10) return c0;
        if (m < 30) return c1;
        if (m < 45) return c2;
        return c3;
    }

    // ✅ ahora PUBLIC para que lo use el marker interno si lo necesitas fuera
    public String nightSlotLabel(int idx) {
        switch (idx) {
            case 0: return "22:00–23:00";
            case 1: return "23:00–00:00";
            case 2: return "00:00–01:00";
            case 3: return "01:00–02:00";
            case 4: return "02:00–03:00";
            case 5: return "03:00–04:00";
            case 6: return "04:00–05:00";
            case 7: return "05:00–06:00";
            default: return "--";
        }
    }

    private void setNightActiveHours(int[] minutes8) {
        if (tvNightActiveHours == null) return;

        if (minutes8 == null || minutes8.length != 8) {
            tvNightActiveHours.setText("Horas activas: --");
            return;
        }

        int activeHours = 0;
        for (int m : minutes8) {
            if (m > 0) activeHours++;
        }
        tvNightActiveHours.setText("Horas activas: " + activeHours);
    }

    // =========================
// MarkerView interno (0 clases nuevas)
// =========================
    private class NightMarkerView extends MarkerView {

        private final TextView tv;

        public NightMarkerView(Context context) {
            super(context, R.layout.marker_night);
            tv = findViewById(R.id.tvMarkerText);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            int idx = Math.round(e.getX()); // 0..7

            if (lastNightMinutes8 != null && lastNightMinutes8.length == 8 && idx >= 0 && idx < 8) {
                String label = nightSlotLabel(idx);
                int m = lastNightMinutes8[idx];
                tv.setText(label + ": " + m + " min de uso");
            } else {
                tv.setText("--");
            }

            // ✅ asegura que getWidth()/getHeight() sean correctos en el draw del marker
            measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            layout(0, 0, getMeasuredWidth(), getMeasuredHeight());

            super.refreshContent(e, highlight);
        }

        @Override
        public MPPointF getOffset() {
            // tu diseño original: centrado y arriba
            return new MPPointF(-(getWidth() / 2f), -getHeight() - 12f);
        }
    }
    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

}