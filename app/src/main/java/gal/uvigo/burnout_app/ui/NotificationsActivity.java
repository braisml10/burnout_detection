package gal.uvigo.burnout_app.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.base.BaseActivity;
import gal.uvigo.burnout_app.data.repo.NotificationRepository;
import gal.uvigo.burnout_app.helpers.ChartHelper;
import gal.uvigo.burnout_app.helpers.RetentionPolicy;
import gal.uvigo.burnout_app.viewmodel.NotificationsViewModel;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsActivity extends BaseActivity {

    private NotificationsViewModel notificationsViewModel;

    private TextView tvTotalNotifs;
    private TextView tvAvgPerHour;
    private TextView tvMostIntrusive;

    private LineChart chartNotifs;

    private HorizontalBarChart chartNotifTypesStacked;

    private LinearLayout legendNotifTypes;

    private List<NotifTypeSeg> lastTypeSegs;

    private TextView tvApp1Name;
    private TextView tvApp2Name;
    private TextView tvApp3Name;
    private ProgressBar pbApp1;
    private ProgressBar pbApp2;
    private ProgressBar pbApp3;
    private TextView tvApp1Pct;
    private TextView tvApp2Pct;
    private TextView tvApp3Pct;

    private static final String[] FIXED_CATEGORIES = new String[]{"WORK", "ENTERTAINMENT", "SOCIAL", "MESSAGING", "OTHER"};

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

        setupBackButton(R.id.btnBack);

        notificationsViewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);

        tvTotalNotifs = findViewById(R.id.tvTotalNotifsValue);
        tvAvgPerHour = findViewById(R.id.tvAvgPerHourValue);
        tvMostIntrusive = findViewById(R.id.tvMostIntrusiveValue);

        chartNotifs = findViewById(R.id.chartNotifs);
        if (chartNotifs == null) {
            throw new IllegalStateException(getString(R.string.error_notifications_chart_missing));
        }
        setupNotifsLineChart(chartNotifs);

        chartNotifTypesStacked = findViewById(R.id.chartNotifTypesStacked);
        if (chartNotifTypesStacked == null) {
            throw new IllegalStateException(getString(R.string.error_notifications_types_chart_missing));
        }
        setupTypeStrip(chartNotifTypesStacked);

        legendNotifTypes = findViewById(R.id.legendNotifTypes);
        if (legendNotifTypes == null) {
            throw new IllegalStateException(getString(R.string.error_notifications_legend_missing));
        }

        tvApp1Name = findViewById(R.id.tvApp1Name);
        tvApp2Name = findViewById(R.id.tvApp2Name);
        tvApp3Name = findViewById(R.id.tvApp3Name);

        pbApp1 = findViewById(R.id.pbApp1);
        pbApp2 = findViewById(R.id.pbApp2);
        pbApp3 = findViewById(R.id.pbApp3);

        tvApp1Pct = findViewById(R.id.tvApp1Pct);
        tvApp2Pct = findViewById(R.id.tvApp2Pct);
        tvApp3Pct = findViewById(R.id.tvApp3Pct);

        initDaySelector(R.id.tvDayLabel, R.id.btnPrevDay, R.id.btnNextDay, RetentionPolicy.DATA_RETENTION_DAYS);

        notificationsViewModel.getUiState().observe(this, uiState -> {
            if (uiState == null) return;

            tvTotalNotifs.setText(String.valueOf(uiState.totalDaily));
            tvAvgPerHour.setText(String.valueOf(uiState.avgPerHour));
            tvMostIntrusive.setText(prettyName(uiState.mostIntrusiveApp));

            renderNotifsLineChart(chartNotifs, uiState.notificationCountByHour);
            renderTypeStrip(chartNotifTypesStacked, uiState.notificationCountByCategory);
            renderTopApps(uiState.totalDaily, uiState.topNotificationApps);
        });

        onDayChanged(selectedDay);
    }

    @Override
    protected void onDayChanged(int selectedDay) {
        notificationsViewModel.loadDay(selectedDay);
    }

    private void setupNotifsLineChart(LineChart chart) {
        ChartHelper.setupBaseLineChart(chart, this, true);
        ChartHelper.setupHourXAxis24(chart.getXAxis());
        ChartHelper.setupDefaultLeftAxis(chart.getAxisLeft(), 0f, 1f);
    }

    private void renderNotifsLineChart(LineChart chart, int[] notificationCountByHour) {
        if (notificationCountByHour == null || notificationCountByHour.length != 24) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<Entry> entries = new ArrayList<>(25);
        for (int hour = 0; hour < 24; hour++) {
            entries.add(new Entry(hour, notificationCountByHour[hour]));
        }
        entries.add(new Entry(24f, notificationCountByHour[23]));

        LineDataSet dataSet = new LineDataSet(entries, getString(R.string.notifications_chart_dataset));
        dataSet.setColor(Color.parseColor("#22D3EE"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#22D3EE"));
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(dataSet));
        chart.invalidate();
    }

    private void setupTypeStrip(HorizontalBarChart chart) {
        ChartHelper.setupBaseHorizontalBarChart(chart, this, true);

        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);

        chart.setViewPortOffsets(0f, 0f, 0f, 0f);
        chart.setExtraOffsets(0f, 0f, 0f, 0f);
        chart.setFitBars(true);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setEnabled(true);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawLabels(false);
        leftAxis.setAxisMinimum(0f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setEnabled(true);
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(0.5f);

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
            }

            @Override
            public void onNothingSelected() {
            }
        });
    }

    private void renderTypeStrip(HorizontalBarChart chart, List<NotificationRepository.NotificationCategoryCountRow> rows) {

        int total = 0;
        for (String category : FIXED_CATEGORIES) {
            total += countForCategory(rows, category);
        }

        List<NotifTypeSeg> segs = new ArrayList<>(FIXED_CATEGORIES.length);
        for (String category : FIXED_CATEGORIES) {
            int count = countForCategory(rows, category);
            int pct = (total > 0) ? Math.round((count * 100f) / total) : 0;
            segs.add(new NotifTypeSeg(getCategoryDisplayLabel(category), count, pct, colorForCategory(category)));
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

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setDrawValues(false);
        dataSet.setColors(colors);
        dataSet.setStackLabels(stackLabels);
        dataSet.setBarBorderWidth(0.8f);
        dataSet.setBarBorderColor(Color.parseColor("#0B1220"));
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(Color.TRANSPARENT);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.55f);

        chart.setData(data);

        float max = (total > 0) ? (float) total : 1f;
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(max);

        TypeMarkerView markerView = new TypeMarkerView(this);
        markerView.setChartView(chart);
        chart.setMarker(markerView);

        chart.invalidate();

        renderTypeLegend(segs);
    }

    private int countForCategory(List<NotificationRepository.NotificationCategoryCountRow> rows, String category) {
        if (rows == null || rows.isEmpty()) return 0;

        String target = safeCategoryLabel(category);
        for (NotificationRepository.NotificationCategoryCountRow row : rows) {
            if (row == null) continue;
            String currentCategory = safeCategoryLabel(row.category);
            if (target.equals(currentCategory)) {
                return Math.max(0, row.count);
            }
        }
        return 0;
    }

    private void renderTypeLegend(List<NotifTypeSeg> segs) {
        if (legendNotifTypes == null) return;
        legendNotifTypes.removeAllViews();

        if (segs == null || segs.isEmpty()) return;

        List<NotifTypeSeg> nonZero = new ArrayList<>();
        for (NotifTypeSeg seg : segs) {
            if (seg != null && seg.count > 0) {
                nonZero.add(seg);
            }
        }

        if (nonZero.isEmpty()) {
            NotifTypeSeg first = segs.get(0);
            nonZero.add(new NotifTypeSeg(first.label, 0, 0, first.color));
        }

        Collections.sort(nonZero, (a, b) -> Integer.compare(b.count, a.count));

        final int maxItems = 3;
        int shown = Math.min(maxItems, nonZero.size());

        for (int i = 0; i < shown; i++) {
            legendNotifTypes.addView(makeLegendItem(nonZero.get(i)));
        }

        int remaining = nonZero.size() - shown;
        if (remaining > 0) {
            TextView more = new TextView(this);
            more.setText(getString(R.string.format_notifications_more_categories, remaining));
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

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        itemParams.rightMargin = 18;
        item.setLayoutParams(itemParams);

        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(12, 12);
        dotParams.rightMargin = 8;
        dot.setLayoutParams(dotParams);
        dot.setBackgroundColor(seg.color);

        TextView label = new TextView(this);
        label.setText(getString(R.string.format_notifications_legend_item, seg.label, seg.pct));
        label.setTextColor(Color.parseColor("#E2E8F0"));
        label.setTextSize(12f);
        label.setSingleLine(true);

        item.addView(dot);
        item.addView(label);

        return item;
    }

    private int colorForCategory(String category) {
        if (category == null) return Color.parseColor("#94A3B8");
        category = category.trim().toUpperCase();

        switch (category) {
            case "WORK":
                return Color.parseColor("#8B5CF6");
            case "ENTERTAINMENT":
                return Color.parseColor("#F59E0B");
            case "SOCIAL":
                return Color.parseColor("#EC4899");
            case "MESSAGING":
                return Color.parseColor("#60A5FA");
            case "OTHER":
            default:
                return Color.parseColor("#94A3B8");
        }
    }

    private String safeCategoryLabel(String category) {
        if (category == null) return "OTHER";
        category = category.trim();
        if (category.isEmpty()) return "OTHER";
        return category.toUpperCase();
    }

    private class TypeMarkerView extends MarkerView {

        private final TextView tv;

        public TypeMarkerView(Context context) {
            super(context, R.layout.marker);
            tv = findViewById(R.id.tvMarkerText);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            if (lastTypeSegs == null || lastTypeSegs.isEmpty() || highlight == null) {
                tv.setText(getString(R.string.common_no_data));
            } else {
                int stackIndex = highlight.getStackIndex();
                if (stackIndex >= 0 && stackIndex < lastTypeSegs.size()) {
                    NotifTypeSeg seg = lastTypeSegs.get(stackIndex);
                    tv.setText(getString(R.string.format_notifications_marker_type, seg.label, seg.pct));
                } else {
                    tv.setText(getString(R.string.common_yesterday));
                }
            }

            measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            layout(0, 0, getMeasuredWidth(), getMeasuredHeight());

            super.refreshContent(e, highlight);
        }

        @Override
        public MPPointF getOffset() {
            return new MPPointF(-(getMeasuredWidth() / 2f), -getMeasuredHeight() - dp(8));
        }

        @Override
        public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
            MPPointF base = getOffset();
            float x = base.x;
            float y = base.y;

            float width = getMeasuredWidth() > 0 ? getMeasuredWidth() : getWidth();
            float height = getMeasuredHeight() > 0 ? getMeasuredHeight() : getHeight();

            View chartView = getChartView();
            if (chartView == null) return new MPPointF(x, y);

            float chartWidth = chartView.getWidth();
            float chartHeight = chartView.getHeight();
            float padding = dp(6);

            if (posX + x < padding) {
                x = padding - posX;
            } else if (posX + x + width > chartWidth - padding) {
                x = (chartWidth - padding) - posX - width;
            }

            if (posY + y < padding) {
                y = padding - posY;
            }

            return new MPPointF(x, y);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // ===================== TOP APPS =====================

    private void renderTopApps(int totalDaily, List<NotificationRepository.TopNotificationAppRow> topNotificationApps) {
        setTopRow(1, "--", 0, 0, totalDaily);
        setTopRow(2, "--", 0, 0, totalDaily);
        setTopRow(3, "--", 0, 0, totalDaily);

        if (topNotificationApps == null || topNotificationApps.isEmpty()) return;

        int maxCount = maxCount(topNotificationApps);

        if (topNotificationApps.size() >= 1) {
            setTopRow(1, topNotificationApps.get(0).name, topNotificationApps.get(0).count, maxCount, totalDaily);
        }
        if (topNotificationApps.size() >= 2) {
            setTopRow(2, topNotificationApps.get(1).name, topNotificationApps.get(1).count, maxCount, totalDaily);
        }
        if (topNotificationApps.size() >= 3) {
            setTopRow(3, topNotificationApps.get(2).name, topNotificationApps.get(2).count, maxCount, totalDaily);
        }
    }

    private int maxCount(List<NotificationRepository.TopNotificationAppRow> topNotificationApps) {
        int max = 0;
        for (NotificationRepository.TopNotificationAppRow row : topNotificationApps) {
            if (row != null) {
                max = Math.max(max, row.count);
            }
        }
        return max;
    }

    private void setTopRow(int index, String name, int count, int maxCount, int totalDaily) {
        int bar = 0;
        if (maxCount > 0) {
            bar = (int) Math.round((count * 100.0) / maxCount);
        }

        int pct = 0;
        if (totalDaily > 0) {
            pct = (int) Math.round((count * 100.0) / totalDaily);
        }

        String prettyName = prettyName(name);

        if (index == 1) {
            tvApp1Name.setText(prettyName);
            pbApp1.setProgress(bar);
            tvApp1Pct.setText(getString(R.string.format_percentage, pct));
        } else if (index == 2) {
            tvApp2Name.setText(prettyName);
            pbApp2.setProgress(bar);
            tvApp2Pct.setText(getString(R.string.format_percentage, pct));
        } else if (index == 3) {
            tvApp3Name.setText(prettyName);
            pbApp3.setProgress(bar);
            tvApp3Pct.setText(getString(R.string.format_percentage, pct));
        }
    }

    private String getCategoryDisplayLabel(String category) {
        if (category == null) return getString(R.string.multitask_category_other);

        switch (category.trim().toUpperCase()) {
            case "WORK":
                return getString(R.string.multitask_category_work);
            case "ENTERTAINMENT":
                return getString(R.string.multitask_category_entertainment);
            case "SOCIAL":
                return getString(R.string.multitask_category_social);
            case "MESSAGING":
                return getString(R.string.main_communication_label);
            case "OTHER":
            default:
                return getString(R.string.multitask_category_other);
        }
    }

    private static String prettyName(String value) {
        if (value == null) return "--";
        value = value.trim();
        if (value.isEmpty()) return "--";

        if (value.contains(".") && !value.contains(" ")) {
            String[] parts = value.split("\\.");
            return parts[parts.length - 1];
        }

        return value;
    }
}