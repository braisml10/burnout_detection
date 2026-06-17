package gal.uvigo.burnout_app.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.base.BaseActivity;
import gal.uvigo.burnout_app.helpers.ChartHelper;
import gal.uvigo.burnout_app.helpers.RetentionPolicy;
import gal.uvigo.burnout_app.helpers.TimeKey;
import gal.uvigo.burnout_app.viewmodel.DailyDetailViewModel;

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

public class ScreenTimeActivity extends BaseActivity {

    private DailyDetailViewModel dailyDetailViewModel;

    private TextView tvTotal;
    private TextView tvSessions;
    private TextView tvNight;

    private TextView tvNightTotal;
    private TextView tvNightActiveHours;

    private LineChart lineChart;
    private BarChart barChartNight;

    private int[] lastNightMinutes9;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_time);

        setupBackButton(R.id.btnBack);

        lineChart = findViewById(R.id.lineChart);
        if (lineChart == null) {
            throw new IllegalStateException(getString(R.string.error_screen_time_line_chart_missing));
        }
        setupLineChart(lineChart);

        barChartNight = findViewById(R.id.barChartNight);
        if (barChartNight == null) {
            throw new IllegalStateException(getString(R.string.error_screen_time_night_chart_missing));
        }
        setupNightTimeline(barChartNight);

        dailyDetailViewModel = new ViewModelProvider(this).get(DailyDetailViewModel.class);

        tvTotal = findViewById(R.id.tvTotalValue);
        tvSessions = findViewById(R.id.tvSessionsValue);
        tvNight = findViewById(R.id.tvNightValue);

        tvNightTotal = findViewById(R.id.tvNightTotal);
        tvNightActiveHours = findViewById(R.id.tvNightActiveHours);

        initDaySelector(
                R.id.tvDayLabel,
                R.id.btnPrevDay,
                R.id.btnNextDay,
                RetentionPolicy.DATA_RETENTION_DAYS
        );

        dailyDetailViewModel.getUiState().observe(this, uiState -> {
            if (uiState == null) return;

            tvTotal.setText(uiState.totalScreenTime);
            tvSessions.setText(uiState.sessions);
            tvNight.setText(uiState.night);

            if (tvNightTotal != null) {
                int totalMinutes = Integer.parseInt(safeText(tvNight));
                tvNightTotal.setText(getString(
                        R.string.format_screen_time_night_total,
                        TimeKey.formatDurationMinutes(totalMinutes)
                ));
            }
        });

        dailyDetailViewModel.getScreenMinutesFrom7To21().observe(this, minutes -> {
            if (minutes == null) {
                lineChart.clear();
                lineChart.invalidate();
                return;
            }

            List<Entry> entries = new ArrayList<>(minutes.length);
            for (int i = 0; i < minutes.length; i++) {
                float hour = 7f + i;
                entries.add(new Entry(hour, minutes[i]));
            }

            LineDataSet dataSet = new LineDataSet(entries, getString(R.string.unit_minutes_full));
            dataSet.setColor(Color.parseColor("#22D3EE"));
            dataSet.setLineWidth(2f);
            dataSet.setCircleColor(Color.parseColor("#22D3EE"));
            dataSet.setCircleRadius(3f);
            dataSet.setDrawValues(false);

            LineData data = new LineData(dataSet);
            lineChart.setData(data);
            lineChart.invalidate();
        });

        dailyDetailViewModel.getNightMinutesFrom22To06().observe(this, minutes -> {
            if (minutes == null || minutes.length != 9) {
                lastNightMinutes9 = null;

                barChartNight.setMarker(null);
                barChartNight.clear();
                barChartNight.invalidate();
                setNightActiveHours(null);
                return;
            }

            lastNightMinutes9 = minutes;

            NightMarkerView markerView = new NightMarkerView(this);
            markerView.setChartView(barChartNight);
            barChartNight.setMarker(markerView);

            renderNightTimeline(barChartNight, minutes);
            setNightActiveHours(minutes);
        });

        onDayChanged(selectedDay);
    }

    private static String safeText(TextView textView) {
        if (textView == null || textView.getText() == null) return "--";
        String value = textView.getText().toString().trim();
        return value.isEmpty() ? "--" : value;
    }

    @Override
    protected void onDayChanged(int selectedDay) {
        dailyDetailViewModel.loadDay(selectedDay);
    }

    private void setupLineChart(LineChart chart) {
        ChartHelper.setupBaseLineChart(chart, this, true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(7f);
        xAxis.setAxisMaximum(21f);
        xAxis.setLabelCount(8, true);
        xAxis.setTextColor(Color.parseColor("#94A3B8"));
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int hour = Math.round(value);
                return getString(R.string.format_hour_suffix, String.valueOf(hour));
            }
        });

        ChartHelper.setupDefaultLeftAxis(chart.getAxisLeft(), 0f, 10f);
        chart.getAxisLeft().setAxisMaximum(60f);
        chart.getAxisLeft().setLabelCount(7, true);
    }

    private void setupNightTimeline(BarChart chart) {
        ChartHelper.setupBaseBarChart(chart, this, false, true);

        chart.getAxisLeft().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setEnabled(false);

        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);

        float topPx = dp(28);
        chart.setViewPortOffsets(0f, topPx, 0f, 0f);
        chart.setExtraOffsets(0f, 0f, 0f, 0f);
        chart.setFitBars(true);

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
            }

            @Override
            public void onNothingSelected() {
            }
        });
    }

    private void renderNightTimeline(BarChart chart, int[] minutes9) {
        List<BarEntry> entries = new ArrayList<>(9);
        List<Integer> colors = new ArrayList<>(9);

        for (int i = 0; i < 9; i++) {
            int minutes = minutes9[i];
            entries.add(new BarEntry(i, 1f));
            colors.add(colorForNightMinutes(minutes));
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setDrawValues(false);
        dataSet.setColors(colors);
        dataSet.setBarBorderWidth(0.8f);
        dataSet.setBarBorderColor(Color.parseColor("#0B1220"));
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(Color.TRANSPARENT);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.92f);

        chart.setData(data);

        chart.getXAxis().setAxisMinimum(-0.5f);
        chart.getXAxis().setAxisMaximum(8.5f);

        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(1f);

        chart.invalidate();
    }

    private int colorForNightMinutes(int minutes) {
        final int c0 = Color.parseColor("#2A240F");
        final int c1 = Color.parseColor("#9A7B12");
        final int c2 = Color.parseColor("#F59E0B");
        final int c3 = Color.parseColor("#FDE047");

        if (minutes <= 0) return c0;
        if (minutes < 10) return c0;
        if (minutes < 30) return c1;
        if (minutes < 45) return c2;
        return c3;
    }

    public String nightSlotLabel(int index) {
        switch (index) {
            case 0:
                return "22:00–23:00";
            case 1:
                return "23:00–00:00";
            case 2:
                return "00:00–01:00";
            case 3:
                return "01:00–02:00";
            case 4:
                return "02:00–03:00";
            case 5:
                return "03:00–04:00";
            case 6:
                return "04:00–05:00";
            case 7:
                return "05:00–06:00";
            case 8:
                return "06:00–07:00";
            default:
                return getString(R.string.common_no_data);
        }
    }

    private void setNightActiveHours(int[] minutes9) {
        if (tvNightActiveHours == null) return;

        if (minutes9 == null || minutes9.length != 9) {
            tvNightActiveHours.setText(getString(
                    R.string.format_screen_time_night_active_hours,
                    getString(R.string.common_no_data)
            ));
            return;
        }

        int activeHours = 0;
        for (int minutes : minutes9) {
            if (minutes > 0) activeHours++;
        }
        tvNightActiveHours.setText(getString(
                R.string.format_screen_time_night_active_hours,
                String.valueOf(activeHours)
        ));
    }

    private class NightMarkerView extends MarkerView {

        private final TextView tv;

        public NightMarkerView(Context context) {
            super(context, R.layout.marker);
            tv = findViewById(R.id.tvMarkerText);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            int index = Math.round(e.getX());

            if (lastNightMinutes9 != null && lastNightMinutes9.length == 9 && index >= 0 && index < 9) {
                String label = nightSlotLabel(index);
                int minutes = lastNightMinutes9[index];
                tv.setText(getString(R.string.format_screen_time_night_marker, label, minutes));
            } else {
                tv.setText(getString(R.string.common_no_data));
            }

            measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            layout(0, 0, getMeasuredWidth(), getMeasuredHeight());

            super.refreshContent(e, highlight);
        }

        @Override
        public MPPointF getOffset() {
            return new MPPointF(-(getWidth() / 2f), -getHeight() - 12f);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}