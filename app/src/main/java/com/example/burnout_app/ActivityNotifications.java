package com.example.burnout_app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.data.repo.NotificationRepository;
import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.NotificationsViewModel;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;

import java.util.ArrayList;
import java.util.Collections;
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

    // Chart 2: una barra horizontal segmentada por tipo
    private HorizontalBarChart chartNotifTypesStacked;

    // Leyenda custom
    private LinearLayout legendNotifTypes;

    // cache para tooltip por segmento (stackIndex)
    private List<NotifTypeSeg> lastTypeSegs;

    // Top apps (3)
    private TextView tvApp1Name, tvApp2Name, tvApp3Name;
    private ProgressBar pbApp1, pbApp2, pbApp3;
    private TextView tvApp1Pct, tvApp2Pct, tvApp3Pct;

    // ✅ Categorías fijas (siempre aparecen aunque estén a 0)
    private static final String[] FIXED_CATEGORIES = new String[]{
            "WORK", "ENTERTAINMENT", "SOCIAL", "COMMUNICATION", "OTHER"
    };

    // -------------------------
    // Modelo interno para leyenda/tooltip
    // -------------------------
    private static class NotifTypeSeg {
        final String label;
        final int count;
        final int pct;
        final int color;

        NotifTypeSeg(String label, int count, int pct, int color) {
            this.label = label;
            this.count = count;
            this.pct = pct;
            this.color = color;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        vm = new ViewModelProvider(this).get(NotificationsViewModel.class);

        // KPIs
        tvTotalNotifs = findViewById(R.id.tvTotalNotifsValue);
        tvAvgPerHour = findViewById(R.id.tvAvgPerHourValue);
        tvMostIntrusive = findViewById(R.id.tvMostIntrusiveValue);

        // Chart 1 (LINE)
        chartNotifs = findViewById(R.id.chartNotifs);
        if (chartNotifs == null) {
            throw new IllegalStateException("chartNotifs NULL: falta R.id.chartNotifs en el layout");
        }
        setupNotifsLineChart(chartNotifs);

        // Chart 2 (STACKED)
        chartNotifTypesStacked = findViewById(R.id.chartNotifTypesStacked);
        if (chartNotifTypesStacked == null) {
            throw new IllegalStateException("chartNotifTypesStacked NULL: falta R.id.chartNotifTypesStacked en el layout");
        }
        setupTypeStrip(chartNotifTypesStacked);

        // Leyenda
        legendNotifTypes = findViewById(R.id.legendNotifTypes);
        if (legendNotifTypes == null) {
            throw new IllegalStateException("legendNotifTypes NULL: falta R.id.legendNotifTypes en el layout");
        }

        // Top apps
        tvApp1Name = findViewById(R.id.tvApp1Name);
        tvApp2Name = findViewById(R.id.tvApp2Name);
        tvApp3Name = findViewById(R.id.tvApp3Name);

        pbApp1 = findViewById(R.id.pbApp1);
        pbApp2 = findViewById(R.id.pbApp2);
        pbApp3 = findViewById(R.id.pbApp3);

        tvApp1Pct = findViewById(R.id.tvApp1Pct);
        tvApp2Pct = findViewById(R.id.tvApp2Pct);
        tvApp3Pct = findViewById(R.id.tvApp3Pct);

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

        // Observers
        vm.getUiState().observe(this, s -> {
            if (s == null) return;

            tvTotalNotifs.setText(String.valueOf(s.totalDaily));
            tvAvgPerHour.setText(String.valueOf(s.avgPerHour));
            tvMostIntrusive.setText(prettyName(s.mostIntrusiveApp));

            renderNotifsLineChart(chartNotifs, s.notifsByHour);

            // ✅ barra segmentada + leyenda + tooltip
            renderTypeStrip(chartNotifTypesStacked, s.byCategory);

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
        for (int h = 0; h < 24; h++) entries.add(new Entry(h, notifsByHour[h]));
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
    // TYPE STRIP (1 barra segmentada proporcional + tooltip + leyenda)
    // =========================================================

    private void setupTypeStrip(HorizontalBarChart c) {
        c.getDescription().setEnabled(false);
        c.getLegend().setEnabled(false);
        c.setNoDataText("Sin datos");

        c.setTouchEnabled(true);
        c.setPinchZoom(false);
        c.setScaleEnabled(false);

        c.setDrawGridBackground(false);
        c.setDrawBorders(false);

        // Barra “full width”
        c.setViewPortOffsets(0f, 0f, 0f, 0f);
        c.setExtraOffsets(0f, 0f, 0f, 0f);
        c.setFitBars(true);

        // Ejes invisibles
        c.getAxisRight().setEnabled(false);

        YAxis left = c.getAxisLeft();
        left.setEnabled(true);
        left.setDrawAxisLine(false);
        left.setDrawGridLines(false);
        left.setDrawLabels(false);
        left.setAxisMinimum(0f);

        XAxis x = c.getXAxis();
        x.setEnabled(true);
        x.setDrawAxisLine(false);
        x.setDrawGridLines(false);
        x.setDrawLabels(false);
        x.setAxisMinimum(-0.5f);
        x.setAxisMaximum(0.5f); // 1 barra

        // ✅ Tooltip por segmento usando stackIndex
        c.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override public void onValueSelected(Entry e, Highlight h) {
                if (!(e instanceof BarEntry)) return;
                if (lastTypeSegs == null || lastTypeSegs.isEmpty()) return;

                int stackIdx = h.getStackIndex();
                if (stackIdx < 0 || stackIdx >= lastTypeSegs.size()) return;

                NotifTypeSeg seg = lastTypeSegs.get(stackIdx);

                // ✅ SOLO label + porcentaje (sin el count)
                Toast.makeText(
                        ActivityNotifications.this,
                        seg.label + " (" + seg.pct + "%)",
                        Toast.LENGTH_SHORT
                ).show();

                chartNotifTypesStacked.highlightValues(null);
                chartNotifTypesStacked.invalidate();
            }

            @Override public void onNothingSelected() { }
        });
    }

    private void renderTypeStrip(HorizontalBarChart chart,
                                 List<NotificationRepository.CategoryCountRow> rows) {

        int total = 0;
        for (String cat : FIXED_CATEGORIES) {
            total += countForCat(rows, cat);
        }

        List<NotifTypeSeg> segs = new ArrayList<>(FIXED_CATEGORIES.length);
        for (String cat : FIXED_CATEGORIES) {
            int cnt = countForCat(rows, cat);
            int pct = (total > 0) ? Math.round((cnt * 100f) / total) : 0;
            segs.add(new NotifTypeSeg(cat, cnt, pct, colorForCategory(cat)));
        }
        lastTypeSegs = segs;

        float[] stack = new float[segs.size()];
        List<Integer> colors = new ArrayList<>(segs.size());
        String[] stackLabels = new String[segs.size()];

        if (total <= 0) {
            for (int i = 0; i < segs.size(); i++) {
                stack[i] = (i == 0) ? 1f : 0f;
                colors.add(Color.TRANSPARENT);
                stackLabels[i] = segs.get(i).label;
            }
            colors.set(0, Color.parseColor("#1E2A44"));
        } else {
            for (int i = 0; i < segs.size(); i++) {
                stack[i] = segs.get(i).count;
                colors.add(segs.get(i).color);
                stackLabels[i] = segs.get(i).label;
            }
        }

        List<BarEntry> entries = new ArrayList<>(1);
        entries.add(new BarEntry(0f, stack));

        BarDataSet ds = new BarDataSet(entries, "");
        ds.setDrawValues(false);
        ds.setColors(colors);
        ds.setStackLabels(stackLabels);

        ds.setBarBorderWidth(0.8f);
        ds.setBarBorderColor(Color.parseColor("#0B1220"));
        ds.setHighlightEnabled(true);
        ds.setHighLightColor(Color.TRANSPARENT);

        BarData data = new BarData(ds);

        // ✅ más gruesa (0.45–0.65 es buen rango con una sola barra)
        data.setBarWidth(0.55f);

        chart.setData(data);

        float max = (total > 0) ? (float) total : 1f;
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(max);

        chart.invalidate();

        renderTypeLegend(segs);
    }

    private int countForCat(List<NotificationRepository.CategoryCountRow> rows, String cat) {
        if (rows == null || rows.isEmpty()) return 0;
        String target = safeCatLabel(cat);
        for (NotificationRepository.CategoryCountRow r : rows) {
            if (r == null) continue;
            String c = safeCatLabel(r.category);
            if (target.equals(c)) return Math.max(0, r.count);
        }
        return 0;
    }

    private void renderTypeLegend(List<NotifTypeSeg> segs) {
        if (legendNotifTypes == null) return;
        legendNotifTypes.removeAllViews();

        if (segs == null || segs.isEmpty()) return;

        List<NotifTypeSeg> nonZero = new ArrayList<>();
        for (NotifTypeSeg s : segs) {
            if (s != null && s.count > 0) nonZero.add(s);
        }

        if (nonZero.isEmpty()) {
            NotifTypeSeg first = segs.get(0);
            nonZero.add(new NotifTypeSeg(first.label, 0, 0, first.color));
        }

        Collections.sort(nonZero, (a, b) -> Integer.compare(b.count, a.count));

        final int MAX_ITEMS = 3;

        int shown = Math.min(MAX_ITEMS, nonZero.size());
        for (int i = 0; i < shown; i++) {
            legendNotifTypes.addView(makeLegendItem(nonZero.get(i)));
        }

        int remaining = nonZero.size() - shown;
        if (remaining > 0) {
            TextView more = new TextView(this);
            more.setText("+" + remaining);
            more.setTextColor(Color.parseColor("#94A3B8"));
            more.setTextSize(12f);
            more.setPadding(10, 2, 0, 0);
            more.setSingleLine(true);
            legendNotifTypes.addView(more);
        }
    }

    private View makeLegendItem(NotifTypeSeg seg) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = 18;
        item.setLayoutParams(lp);

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(12, 12);
        dotLp.rightMargin = 8;
        dot.setLayoutParams(dotLp);
        dot.setBackgroundColor(seg.color);

        TextView tv = new TextView(this);
        tv.setText(seg.label + " " + seg.pct + "%");
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setTextSize(12f);
        tv.setSingleLine(true);

        item.addView(dot);
        item.addView(tv);

        return item;
    }

    // ✅ Paleta diferente a la superior (evitamos cyan/verde similares a top apps)
    private int colorForCategory(String cat) {
        if (cat == null) return Color.parseColor("#94A3B8");
        cat = cat.trim().toUpperCase();

        switch (cat) {
            case "WORK":
                return Color.parseColor("#8B5CF6"); // purple
            case "ENTERTAINMENT":
                return Color.parseColor("#F59E0B"); // amber
            case "SOCIAL":
                return Color.parseColor("#EC4899"); // pink
            case "COMMUNICATION":
                return Color.parseColor("#60A5FA"); // blue
            case "OTHER":
            default:
                return Color.parseColor("#94A3B8"); // gray-blue
        }
    }

    private String safeCatLabel(String cat) {
        if (cat == null) return "OTHER";
        cat = cat.trim();
        if (cat.isEmpty()) return "OTHER";
        return cat.toUpperCase();
    }

    // =========================================================
    // Top apps
    // =========================================================

    private void renderTopApps(int totalDaily, List<NotificationRepository.TopNotifAppRow> top) {
        setTopRow(1, "--", 0, 0, totalDaily);
        setTopRow(2, "--", 0, 0, totalDaily);
        setTopRow(3, "--", 0, 0, totalDaily);

        if (top == null || top.isEmpty()) return;

        int mx = maxCount(top);
        if (top.size() >= 1) setTopRow(1, top.get(0).name, top.get(0).count, mx, totalDaily);
        if (top.size() >= 2) setTopRow(2, top.get(1).name, top.get(1).count, mx, totalDaily);
        if (top.size() >= 3) setTopRow(3, top.get(2).name, top.get(2).count, mx, totalDaily);
    }

    private int maxCount(List<NotificationRepository.TopNotifAppRow> top) {
        int m = 0;
        for (NotificationRepository.TopNotifAppRow r : top) {
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