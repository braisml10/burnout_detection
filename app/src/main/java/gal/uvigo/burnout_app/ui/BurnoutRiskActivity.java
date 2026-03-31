package gal.uvigo.burnout_app.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.base.BaseActivity;
import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;
import gal.uvigo.burnout_app.helpers.ChartHelper;
import gal.uvigo.burnout_app.helpers.TimeKey;
import gal.uvigo.burnout_app.viewmodel.BurnoutRiskViewModel;

public class BurnoutRiskActivity extends BaseActivity {

    private BurnoutRiskViewModel viewModel;

    private TextView tvRiskLevel;
    private TextView tvRiskScore;
    private TextView tvDriver1;
    private TextView tvDriver2;
    private TextView tvDriver3;

    private TextView tvFragLevel;
    private TextView tvFragValue;
    private TextView tvFragBaseline;

    private TextView tvNightLevel;
    private TextView tvNightValue;
    private TextView tvNightBaseline;

    private TextView tvNotifLevel;
    private TextView tvNotifValue;
    private TextView tvNotifBaseline;

    private TextView tvScreenLevel;
    private TextView tvScreenValue;
    private TextView tvScreenBaseline;

    private TextView tvTrendDimLevel;
    private TextView tvTrendDimValue;
    private TextView tvTrendDimBaseline;

    private LineChart lineChartRiskTrend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_burnout_risk);

        bindViews();
        setupBackButton(R.id.btnBack);
        setupChart();

        viewModel = new ViewModelProvider(this).get(BurnoutRiskViewModel.class);

        viewModel.getUiState().observe(this, this::renderState);
        viewModel.getTrend7Days().observe(this, this::renderTrendChart);
    }

    private void bindViews() {
        tvRiskLevel = findViewById(R.id.tvRiskLevel);
        tvRiskScore = findViewById(R.id.tvRiskScore);
        tvDriver1 = findViewById(R.id.tvDriver1);
        tvDriver2 = findViewById(R.id.tvDriver2);
        tvDriver3 = findViewById(R.id.tvDriver3);

        tvFragLevel = findViewById(R.id.tvFragLevel);
        tvFragValue = findViewById(R.id.tvFragValue);
        tvFragBaseline = findViewById(R.id.tvFragBaseline);

        tvNightLevel = findViewById(R.id.tvNightLevel);
        tvNightValue = findViewById(R.id.tvNightValue);
        tvNightBaseline = findViewById(R.id.tvNightBaseline);

        tvNotifLevel = findViewById(R.id.tvNotifLevel);
        tvNotifValue = findViewById(R.id.tvNotifValue);
        tvNotifBaseline = findViewById(R.id.tvNotifBaseline);

        tvScreenLevel = findViewById(R.id.tvScreenLevel);
        tvScreenValue = findViewById(R.id.tvScreenValue);
        tvScreenBaseline = findViewById(R.id.tvScreenBaseline);

        tvTrendDimLevel = findViewById(R.id.tvTrendDimLevel);
        tvTrendDimValue = findViewById(R.id.tvTrendDimValue);
        tvTrendDimBaseline = findViewById(R.id.tvTrendDimBaseline);

        lineChartRiskTrend = findViewById(R.id.lineChartRiskTrend);
    }

    private void renderState(BurnoutRiskViewModel.BurnoutRiskUiState state) {
        if (state == null) return;

        tvRiskLevel.setText(getRiskLabel(state.riskLevel));
        tvRiskScore.setText(getString(R.string.format_burnout_score, state.riskScore));

        tvDriver1.setText(formatDriver(state.driver1Type, state.driver1Level));
        tvDriver2.setText(formatDriver(state.driver2Type, state.driver2Level));
        tvDriver3.setText(formatDriver(state.driver3Type, state.driver3Level));

        tvFragLevel.setText(getLevelLabel(state.fragmentation.level));
        tvFragValue.setText(formatFragmentationValue(state.fragmentation.value));
        tvFragBaseline.setText(getString(
                R.string.format_burnout_baseline,
                formatFragmentationValue(state.fragmentation.baseline)
        ));

        tvNightLevel.setText(getLevelLabel(state.nightUse.level));
        tvNightValue.setText(TimeKey.formatDurationMinutes((int) state.nightUse.valueMinutes));
        tvNightBaseline.setText(getString(
                R.string.format_burnout_baseline,
                TimeKey.formatDurationMinutes((int) state.nightUse.baselineMinutes)
        ));

        tvNotifLevel.setText(getLevelLabel(state.notifications.level));
        tvNotifValue.setText(formatYesterdayValue(String.valueOf(state.notifications.valueCount)));
        tvNotifBaseline.setText(getString(
                R.string.format_burnout_baseline,
                formatOneDecimal(state.notifications.baselineCount)
        ));

        tvScreenLevel.setText(getLevelLabel(state.screenTime.level));
        tvScreenValue.setText(
                formatYesterdayValue(formatOneDecimal(state.screenTime.valueHours) + getString(R.string.unit_hours_short))
        );
        tvScreenBaseline.setText(getString(
                R.string.format_burnout_baseline,
                formatOneDecimal(state.screenTime.baselineHours) + getString(R.string.unit_hours_short)
        ));

        tvTrendDimLevel.setText(getLevelLabel(state.trend.level));
        tvTrendDimValue.setText(getTrendLabel(state.trend.trendKind));
        tvTrendDimBaseline.setText(getString(R.string.burnout_weekly_comparison));

        String riskLabel = getRiskLabel(state.riskLevel);
        tvRiskLevel.setTextColor(getRiskColor(riskLabel));

        applySectionRiskStyle(tvFragLevel, getLevelLabel(state.fragmentation.level));
        applySectionRiskStyle(tvNightLevel, getLevelLabel(state.nightUse.level));
        applySectionRiskStyle(tvNotifLevel, getLevelLabel(state.notifications.level));
        applySectionRiskStyle(tvScreenLevel, getLevelLabel(state.screenTime.level));
        applySectionRiskStyle(tvTrendDimLevel, getLevelLabel(state.trend.level));
    }

    private String getRiskLabel(int riskLevel) {
        switch (riskLevel) {
            case BurnoutRiskViewModel.RISK_LOW:
                return getString(R.string.burnout_risk_low);
            case BurnoutRiskViewModel.RISK_MODERATE:
                return getString(R.string.burnout_risk_moderate);
            case BurnoutRiskViewModel.RISK_HIGH:
                return getString(R.string.burnout_risk_high);
            default:
                return getString(R.string.common_no_data);
        }
    }

    private String getLevelLabel(int level) {
        switch (level) {
            case BurnoutRiskViewModel.LEVEL_LOW:
                return getString(R.string.burnout_level_low);
            case BurnoutRiskViewModel.LEVEL_MEDIUM:
                return getString(R.string.burnout_level_medium);
            case BurnoutRiskViewModel.LEVEL_HIGH:
                return getString(R.string.burnout_level_high);
            default:
                return getString(R.string.common_no_data);
        }
    }

    private String getTrendLabel(int trendKind) {
        switch (trendKind) {
            case BurnoutRiskViewModel.TREND_INCREASING:
                return getString(R.string.burnout_trend_increasing);
            case BurnoutRiskViewModel.TREND_STABLE:
                return getString(R.string.burnout_trend_stable);
            case BurnoutRiskViewModel.TREND_DECREASING:
                return getString(R.string.burnout_trend_decreasing);
            default:
                return getString(R.string.common_no_data);
        }
    }

    private String formatDriver(int driverType, int level) {
        if (driverType == BurnoutRiskViewModel.DRIVER_NONE || level == BurnoutRiskViewModel.LEVEL_NONE) {
            return getString(R.string.common_no_data);
        }

        String levelText = getLevelLabel(level);

        switch (driverType) {
            case BurnoutRiskViewModel.DRIVER_FRAGMENTATION:
                return getString(R.string.format_burnout_driver_fragmentation, levelText);
            case BurnoutRiskViewModel.DRIVER_NIGHT_USE:
                return getString(R.string.format_burnout_driver_night_use, levelText);
            case BurnoutRiskViewModel.DRIVER_NOTIFICATIONS:
                return getString(R.string.format_burnout_driver_notifications, levelText);
            case BurnoutRiskViewModel.DRIVER_SCREEN_TIME:
                return getString(R.string.format_burnout_driver_screen_time, levelText);
            case BurnoutRiskViewModel.DRIVER_TREND:
                return getString(R.string.format_burnout_driver_trend, levelText);
            default:
                return levelText;
        }
    }

    private String formatOneDecimal(double value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String formatFragmentationValue(double value) {
        return formatOneDecimal(value) + " " + getString(R.string.unit_changes_per_hour);
    }

    private String formatYesterdayValue(String value) {
        return value + " " + getString(R.string.common_yesterday).toLowerCase(Locale.getDefault());
    }

    private int getRiskColor(String level) {
        if (level == null) {
            return ContextCompat.getColor(this, R.color.risk_medium);
        }

        String normalized = level.trim().toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "bajo":
            case "baixo":
            case "low":
                return ContextCompat.getColor(this, R.color.risk_low);

            case "medio":
            case "moderado":
            case "medium":
                return ContextCompat.getColor(this, R.color.risk_medium);

            case "alto":
            case "high":
                return ContextCompat.getColor(this, R.color.risk_high);

            default:
                return ContextCompat.getColor(this, R.color.risk_medium);
        }
    }

    private void applySectionRiskStyle(TextView levelView, String level) {
        levelView.setTextColor(getRiskColor(level));
    }

    private void setupChart() {
        ChartHelper.setupBaseLineChart(lineChartRiskTrend, this, false);

        lineChartRiskTrend.setTouchEnabled(true);
        lineChartRiskTrend.setHighlightPerTapEnabled(true);
        lineChartRiskTrend.setDragEnabled(false);
        lineChartRiskTrend.setScaleEnabled(false);
        lineChartRiskTrend.setPinchZoom(false);

        XAxis xAxis = lineChartRiskTrend.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#94A3B8"));

        ChartHelper.setupDefaultLeftAxis(lineChartRiskTrend.getAxisLeft(), 0f, 1f);
        lineChartRiskTrend.getAxisLeft().setAxisMaximum(2f);

        RiskMarkerView markerView = new RiskMarkerView(this);
        markerView.setChartView(lineChartRiskTrend);
        lineChartRiskTrend.setMarker(markerView);
    }

    private class RiskMarkerView extends MarkerView {

        private final TextView tv;

        public RiskMarkerView(Context context) {
            super(context, R.layout.marker);
            tv = findViewById(R.id.tvMarkerText);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            if (e != null) {
                tv.setText(getString(
                        R.string.format_burnout_score,
                        (double) e.getY()
                ));
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

    private void renderTrendChart(List<BurnoutRiskEntity> items) {
        if (items == null || items.isEmpty()) {
            lineChartRiskTrend.clear();
            return;
        }

        List<BurnoutRiskEntity> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(a -> a.epochDay));

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            BurnoutRiskEntity item = sorted.get(i);
            entries.add(new Entry(i, (float) item.riskScore));

            String dateLabel = TimeKey.dateLabelFromEpochDay((int) item.epochDay);
            String shortLabel = dateLabel.split(" ")[0];
            labels.add(shortLabel);
        }

        LineDataSet dataSet = new LineDataSet(entries, "Risk");
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setColor(Color.parseColor("#60A5FA"));
        dataSet.setCircleColor(Color.parseColor("#60A5FA"));
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        lineChartRiskTrend.setData(new LineData(dataSet));

        XAxis xAxis = lineChartRiskTrend.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(labels.size(), true);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = Math.round(value);
                if (index < 0 || index >= labels.size()) return "";
                return labels.get(index);
            }
        });

        lineChartRiskTrend.invalidate();
    }
}