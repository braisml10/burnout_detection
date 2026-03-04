package com.example.burnout_app;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.CommunicationViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
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
import java.util.Locale;

public class ActivityCommunications extends AppCompatActivity {

    private CommunicationViewModel vm;

    // KPIs
    private TextView tvCallsValue;
    private TextView tvMessagesValue;
    private TextView tvTotalCommValue;

    // Day selector
    private TextView tvDayLabel;
    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private int todayDay;
    private int selectedDay;

    // Charts
    private LineChart chartIntensity;
    private BarChart chartChannelStacked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communications);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        vm = new ViewModelProvider(this).get(CommunicationViewModel.class);

        // KPIs
        tvCallsValue = findViewById(R.id.tvCallsValue);
        tvMessagesValue = findViewById(R.id.tvMsgsValue);
        tvTotalCommValue = findViewById(R.id.tvDurValue);

        // Charts
        chartIntensity = findViewById(R.id.chartIntensity);
        if (chartIntensity == null) {
            throw new IllegalStateException("chartIntensity NULL: falta R.id.chartIntensity en el layout");
        }
        setupIntensityLineChart(chartIntensity);

        chartChannelStacked = findViewById(R.id.chartChannelDistStacked);
        if (chartChannelStacked == null) {
            throw new IllegalStateException("chartChannelDistStacked NULL: falta R.id.chartChannelDistStacked en el layout");
        }
        setupChannelStackedChart(chartChannelStacked);

        // Day selector
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

        vm.getUiState().observe(this, s -> {
            if (s == null) {
                renderEmpty();
                return;
            }

            tvCallsValue.setText(String.valueOf(Math.max(0, s.callsCount)));
            tvMessagesValue.setText(String.valueOf(Math.max(0, s.messagesCount)));

            // KPI duración total en minutos (sin unidades)
            tvTotalCommValue.setText(String.valueOf(msToMinutes(Math.max(0L, s.totalCommMs))));

            // Charts en minutos
            renderIntensityLine(chartIntensity, s.totalByHour);
            renderChannelStacked(chartChannelStacked, s.voiceByHour, s.textByHour);
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

    private void renderEmpty() {
        tvCallsValue.setText("0");
        tvMessagesValue.setText("0");
        tvTotalCommValue.setText("0");

        chartIntensity.clear();
        chartIntensity.invalidate();

        chartChannelStacked.clear();
        chartChannelStacked.invalidate();
    }

    // =========================================================
    // LINE CHART (intensidad total por hora) - MINUTOS
    // =========================================================

    private void setupIntensityLineChart(LineChart c) {
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
                if (h % 6 == 0) return String.format(Locale.getDefault(), "%02d", h);
                return "";
            }
        });

        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setGranularity(1f);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);

        // Opcional: mostrar el sufijo "m" en el eje Y
        c.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return ((int) value) + "m";
            }
        });
    }

    private void renderIntensityLine(LineChart chart, long[] totalByHourMs) {
        if (totalByHourMs == null || totalByHourMs.length != 24) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<Entry> entries = new ArrayList<>(25);
        for (int h = 0; h < 24; h++) {
            entries.add(new Entry(h, (float) msToMinutes(totalByHourMs[h])));
        }
        // cierre en 24 con el último valor
        entries.add(new Entry(24f, (float) msToMinutes(Math.max(0L, totalByHourMs[23]))));

        LineDataSet ds = new LineDataSet(entries, "Comunicación (min)");
        ds.setColor(Color.parseColor("#60A5FA"));
        ds.setLineWidth(2f);
        ds.setCircleColor(Color.parseColor("#60A5FA"));
        ds.setCircleRadius(3f);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(ds));
        chart.invalidate();
    }

    // =========================================================
    // BAR CHART (stacked: voz/texto por hora) - MINUTOS
    // EJE X IGUAL AL LINE CHART (0..24, labels cada 6h)
    // =========================================================

    private void setupChannelStackedChart(BarChart c) {
        c.getDescription().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setScaleEnabled(false);

        Legend l = c.getLegend();
        l.setEnabled(true);
        l.setTextColor(Color.parseColor("#CBD5E1"));
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);

        XAxis x = c.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);

        // MISMO rango/labels que el line
        x.setAxisMinimum(0f);
        x.setAxisMaximum(24f);
        x.setLabelCount(5, true);

        x.setTextColor(Color.parseColor("#94A3B8"));
        x.setDrawGridLines(false);
        x.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int h = Math.round(value);
                if (h == 24) return "24";
                if (h % 6 == 0) return String.format(Locale.getDefault(), "%02d", h);
                return "";
            }
        });

        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setGranularity(10f);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);

        // Opcional: sufijo minutos en el eje Y
        c.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return ((int) value) + "m";
            }
        });

        c.setFitBars(false);
        c.setExtraOffsets(4f, 2f, 6f, 6f);
    }

    private void renderChannelStacked(BarChart chart, long[] voiceByHourMs, long[] textByHourMs) {
        if (voiceByHourMs == null || textByHourMs == null
                || voiceByHourMs.length != 24 || textByHourMs.length != 24) {
            chart.clear();
            chart.invalidate();
            return;
        }

        ArrayList<BarEntry> entries = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            float v = (float) msToMinutes(voiceByHourMs[h]);
            float t = (float) msToMinutes(textByHourMs[h]);
            entries.add(new BarEntry(h, new float[]{v, t}));
        }

        BarDataSet ds = new BarDataSet(entries, "");
        ds.setDrawValues(false);
        ds.setStackLabels(new String[]{"Voz", "Texto"});
        ds.setColors(
                Color.parseColor("#60A5FA"), // voz
                Color.parseColor("#34D399")  // texto
        );

        BarData data = new BarData(ds);
        data.setBarWidth(0.70f); // un pelín menos para que no recorte en bordes

        chart.setData(data);

        // Ajusta el máximo del eje Y al contenido
        float maxY = data.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(10f, maxY * 1.2f));

        chart.invalidate();
    }

    private static long msToMinutes(long ms) {
        if (ms <= 0L) return 0L;
        return ms / 60000L;
    }
}