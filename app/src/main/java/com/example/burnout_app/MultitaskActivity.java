package com.example.burnout_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.LanguageHelper;
import com.example.burnout_app.helpers.RetentionPolicy;
import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.AppsUsageViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

public class MultitaskActivity extends AppCompatActivity {

    private AppsUsageViewModel vm;

    private TextView tvAppsTime;
    private TextView tvSwitches;
    private TextView tvUnique;

    private TextView tvDayLabel;

    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private int todayDay;
    private int selectedDay;
    private int minAllowedDay;

    private TextView tvCatSocialTime, tvCatEntTime, tvCatMsgTime, tvCatWorkTime, tvCatOtherTime;
    private ProgressBar pbCatSocial, pbCatEnt, pbCatMsg, pbCatWork, pbCatOther;

    // Top apps
    private TextView tvApp1Name, tvApp2Name, tvApp3Name;
    private ProgressBar pbApp1, pbApp2, pbApp3;
    private TextView tvApp1Pct, tvApp2Pct, tvApp3Pct;

    // Switches chart
    private LineChart chartSwitches;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs =
                newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "es");
        super.attachBaseContext(LanguageHelper.updateContext(newBase, langCode));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multitask);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        vm = new ViewModelProvider(this).get(AppsUsageViewModel.class);

        // -------------------
        // KPIs
        // -------------------
        tvAppsTime = findViewById(R.id.tvAppsTimeValue);
        tvSwitches = findViewById(R.id.tvAppSwitchesValue);
        tvUnique   = findViewById(R.id.tvUniqueAppsValue);

        // ✅ KPI tiempo total (suma filtrada o total según tu VM)
        vm.getAppsTimeFiltered().observe(this, txt -> {
            if (txt != null) tvAppsTime.setText(txt);
        });

        // KPI switches + unique
        vm.getUiState().observe(this, s -> {
            if (s == null) return;
            tvSwitches.setText(s.appSwitches);
            tvUnique.setText(s.uniqueApps);
        });

        // -------------------
        // Categorías
        // -------------------
        tvCatSocialTime = findViewById(R.id.tvCatSocialTime);
        tvCatEntTime    = findViewById(R.id.tvCatEntTime);
        tvCatMsgTime    = findViewById(R.id.tvCatMsgTime);
        tvCatWorkTime   = findViewById(R.id.tvCatWorkTime);
        tvCatOtherTime  = findViewById(R.id.tvCatOtherTime);

        pbCatSocial = findViewById(R.id.pbCatSocial);
        pbCatEnt    = findViewById(R.id.pbCatEnt);
        pbCatMsg    = findViewById(R.id.pbCatMsg);
        pbCatWork   = findViewById(R.id.pbCatWork);
        pbCatOther  = findViewById(R.id.pbCatOther);

        vm.getCategoryState().observe(this, cs -> {
            if (cs == null) return;

            tvCatSocialTime.setText(cs.socialTxt);
            tvCatEntTime.setText(cs.entTxt);
            tvCatMsgTime.setText(cs.msgTxt);
            tvCatWorkTime.setText(cs.workTxt);
            tvCatOtherTime.setText(cs.otherTxt);

            pbCatSocial.setProgress(cs.socialPct);
            pbCatEnt.setProgress(cs.entPct);
            pbCatMsg.setProgress(cs.msgPct);
            pbCatWork.setProgress(cs.workPct);
            pbCatOther.setProgress(cs.otherPct);
        });

        // -------------------
        // Switches chart (acumulado)
        // -------------------
        chartSwitches = findViewById(R.id.chartSwitches);
        if (chartSwitches == null) {
            throw new IllegalStateException(getString(R.string.error_multitask_chart_missing));
        }
        setupSwitchesLineChart(chartSwitches);

        vm.getSwitchesChartState().observe(this, st -> {
            if (st == null || st.entries == null) {
                chartSwitches.clear();
                chartSwitches.invalidate();
                return;
            }

            LineDataSet ds = new LineDataSet(st.entries, getString(R.string.multitask_switches_dataset));
            ds.setColor(Color.parseColor("#22D3EE"));
            ds.setLineWidth(2f);
            ds.setCircleColor(Color.parseColor("#22D3EE"));
            ds.setCircleRadius(3f);
            ds.setDrawValues(false);
            ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);

            LineData data = new LineData(ds);
            chartSwitches.setData(data);
            chartSwitches.invalidate();
        });

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

        vm.getTopAppsState().observe(this, t -> {
            if (t == null) return;

            tvApp1Name.setText(t.name1);
            tvApp2Name.setText(t.name2);
            tvApp3Name.setText(t.name3);

            tvApp1Pct.setText(getString(R.string.percentage_format, t.pct1));
            tvApp2Pct.setText(getString(R.string.percentage_format, t.pct2));
            tvApp3Pct.setText(getString(R.string.percentage_format, t.pct3));

            pbApp1.setProgress(t.bar1);
            pbApp2.setProgress(t.bar2);
            pbApp3.setProgress(t.bar3);

            // Iconos opcionales: si luego metes packageName en el state, aquí los cargas.
        });

        // -------------------
        // Day selector
        // -------------------
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        tvDayLabel = findViewById(R.id.tvDayLabel);

        todayDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay = todayDay;
        minAllowedDay = todayDay - RetentionPolicy.DATA_RETENTION_DAYS;

        btnPrevDay.setOnClickListener(v -> {
            if (selectedDay > minAllowedDay) {
                selectedDay--;
                applyDayUi();
                vm.loadDay(selectedDay);
            }
        });

        btnNextDay.setOnClickListener(v -> {
            if (selectedDay < todayDay) {
                selectedDay++;
                applyDayUi();
                vm.loadDay(selectedDay);
            }
        });

        applyDayUi();
        vm.loadDay(selectedDay);
    }

    private void applyDayUi() {
        String label;

        if (selectedDay == todayDay) {
            label = getString(R.string.today);
        } else if (selectedDay == todayDay - 1) {
            label = getString(R.string.yesterday);
        } else {
            label = TimeKey.dateLabelFromEpochDay(selectedDay);
        }

        tvDayLabel.setText(label);

        boolean canGoPrev = selectedDay > minAllowedDay;
        btnPrevDay.setEnabled(canGoPrev);
        btnPrevDay.setAlpha(canGoPrev ? 1f : 0.35f);

        boolean canGoNext = selectedDay < todayDay;
        btnNextDay.setEnabled(canGoNext);
        btnNextDay.setAlpha(canGoNext ? 1f : 0.35f);
    }

    private void setupSwitchesLineChart(LineChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText(getString(R.string.no_data));

        c.setTouchEnabled(true);
        c.setPinchZoom(true);

        // X: 0..24
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

        // Y: acumulado (auto max)
        c.getAxisRight().setEnabled(false);
        c.getAxisLeft().setAxisMinimum(0f);
        c.getAxisLeft().setGranularity(5f);
        c.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        c.getAxisLeft().setDrawGridLines(true);
    }

    private String prettyName(String s) {
        if (s == null) return getString(R.string.marker_empty);
        s = s.trim();
        if (s.isEmpty()) return getString(R.string.marker_empty);

        if (s.contains(".") && !s.contains(" ")) {
            String[] parts = s.split("\\.");
            return parts[parts.length - 1];
        }
        return s;
    }
}
