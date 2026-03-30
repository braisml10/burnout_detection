package gal.uvigo.burnout_app.helpers;

import android.content.Context;
import android.graphics.Color;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.Locale;

import gal.uvigo.burnout_app.R;

public final class ChartHelper {

    private static final int COLOR_AXIS_TEXT = Color.parseColor("#94A3B8");

    private ChartHelper() {}

    public static void setupBaseLineChart(LineChart chart, Context context, boolean pinchZoom) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(context.getString(R.string.common_no_data));

        chart.setTouchEnabled(true);
        chart.setPinchZoom(pinchZoom);
        chart.getAxisRight().setEnabled(false);
    }

    public static void setupBaseBarChart(BarChart chart, Context context, boolean pinchZoom, boolean drawMarkers) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(context.getString(R.string.common_no_data));

        chart.setTouchEnabled(true);
        chart.setPinchZoom(pinchZoom);
        chart.setScaleEnabled(false);
        chart.setDrawMarkers(drawMarkers);
        chart.getAxisRight().setEnabled(false);
    }

    public static void setupBaseHorizontalBarChart(HorizontalBarChart chart, Context context, boolean drawMarkers) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(context.getString(R.string.common_no_data));

        chart.setTouchEnabled(true);
        chart.setPinchZoom(false);
        chart.setScaleEnabled(false);
        chart.setDrawMarkers(drawMarkers);
        chart.getAxisRight().setEnabled(false);
    }

    public static void setupHourXAxis24(XAxis xAxis) {
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(24f);
        xAxis.setLabelCount(5, true);
        xAxis.setTextColor(COLOR_AXIS_TEXT);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int hour = Math.round(value);
                if (hour == 24) return "24";
                if (hour % 6 == 0) return String.format(Locale.getDefault(), "%02d", hour);
                return "";
            }
        });
    }

    public static void setupMinutesLeftAxis(YAxis axis, Context context, float granularity) {
        axis.setAxisMinimum(0f);
        axis.setGranularity(granularity);
        axis.setTextColor(COLOR_AXIS_TEXT);
        axis.setDrawGridLines(true);
        axis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return ((int) value) + context.getString(R.string.unit_minutes_short);
            }
        });
    }

    public static void setupDefaultLeftAxis(YAxis axis, float min, float granularity) {
        axis.setAxisMinimum(min);
        axis.setGranularity(granularity);
        axis.setTextColor(COLOR_AXIS_TEXT);
        axis.setDrawGridLines(true);
    }
}