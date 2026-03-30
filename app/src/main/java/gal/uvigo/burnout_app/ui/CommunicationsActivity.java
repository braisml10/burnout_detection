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
import gal.uvigo.burnout_app.viewmodel.CommunicationViewModel;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.MarkerView;
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
import java.util.List;
import java.util.Locale;

public class CommunicationsActivity extends BaseActivity {

    private CommunicationViewModel vm;

    // KPIs
    private TextView tvCallsValue;
    private TextView tvMessagesValue;
    private TextView tvTotalCommValue;

    // Charts
    private LineChart chartIntensity;
    private BarChart chartChannelStacked;

    // Últimos datos (para MarkerView)
    private long[] lastVoiceByHourMs;
    private long[] lastTextByHourMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communications);

        setupBackButton(R.id.btnBack);

        vm = new ViewModelProvider(this).get(CommunicationViewModel.class);

        // KPIs
        tvCallsValue = findViewById(R.id.tvCallsValue);
        tvMessagesValue = findViewById(R.id.tvMsgsValue);
        tvTotalCommValue = findViewById(R.id.tvDurValue);

        // Charts
        chartIntensity = findViewById(R.id.chartIntensity);
        if (chartIntensity == null) {
            throw new IllegalStateException(getString(R.string.error_chart_intensity_missing));
        }
        setupIntensityLineChart(chartIntensity);

        chartChannelStacked = findViewById(R.id.chartChannelDistStacked);
        if (chartChannelStacked == null) {
            throw new IllegalStateException(getString(R.string.error_chart_channel_missing));
        }
        setupChannelStackedChart(chartChannelStacked);

        initDaySelector(
                R.id.tvDayLabel,
                R.id.btnPrevDay,
                R.id.btnNextDay,
                RetentionPolicy.DATA_RETENTION_DAYS
        );

        vm.getUiState().observe(this, s -> {
            if (s == null) {
                renderEmpty();
                return;
            }

            tvCallsValue.setText(String.valueOf(Math.max(0, s.callsCount)));
            tvMessagesValue.setText(String.valueOf(Math.max(0, s.messagesCount)));
            tvTotalCommValue.setText(String.valueOf(TimeKey.minutesFromMs(Math.max(0L, s.totalCommMs))));

            renderIntensityLine(chartIntensity, s.totalByHour);
            renderChannelStacked(chartChannelStacked, s.voiceByHour, s.textByHour);
        });

        onDayChanged(selectedDay);
    }

    @Override
    protected void onDayChanged(int selectedDay) {
        vm.loadDay(selectedDay);
    }

    private void renderEmpty() {
        tvCallsValue.setText("0");
        tvMessagesValue.setText("0");
        tvTotalCommValue.setText("0");

        lastVoiceByHourMs = null;
        lastTextByHourMs = null;

        chartIntensity.clear();
        chartIntensity.invalidate();

        chartChannelStacked.clear();
        chartChannelStacked.invalidate();
    }

    // =========================================================
    // LINE CHART
    // =========================================================

    private void setupIntensityLineChart(LineChart c) {
        ChartHelper.setupBaseLineChart(c, this, true);
        ChartHelper.setupHourXAxis24(c.getXAxis());
        ChartHelper.setupMinutesLeftAxis(c.getAxisLeft(), this, 1f);
    }

    private void renderIntensityLine(LineChart chart, long[] totalByHourMs) {
        if (totalByHourMs == null || totalByHourMs.length != 24) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<Entry> entries = new ArrayList<>(25);

        long maxMin = 0;
        for (int h = 0; h < 24; h++) {
            long m = TimeKey.minutesFromMs(totalByHourMs[h]);
            if (m > maxMin) maxMin = m;
            entries.add(new Entry(h, (float) m));
        }

        entries.add(new Entry(24f, (float) TimeKey.minutesFromMs(totalByHourMs[23])));

        LineDataSet ds = new LineDataSet(entries, getString(R.string.communications_chart_label));
        ds.setColor(Color.parseColor("#60A5FA"));
        ds.setLineWidth(2f);
        ds.setCircleColor(Color.parseColor("#60A5FA"));
        ds.setCircleRadius(3f);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(ds));
        chart.getAxisLeft().setAxisMaximum(Math.max(10f, (float) (maxMin * 1.2)));

        chart.invalidate();
    }

    // =========================================================
    // BAR CHART
    // =========================================================

    private void setupChannelStackedChart(BarChart c) {
        ChartHelper.setupBaseBarChart(c, this, false, true);
        c.setHighlightFullBarEnabled(false);

        Legend l = c.getLegend();
        l.setEnabled(true);
        l.setTextColor(Color.parseColor("#CBD5E1"));
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);

        ChartHelper.setupHourXAxis24(c.getXAxis());
        ChartHelper.setupMinutesLeftAxis(c.getAxisLeft(), this, 10f);

        c.setFitBars(false);
        c.setExtraOffsets(4f, 2f, 6f, 6f);

        ChannelMarkerView markerView = new ChannelMarkerView(this);
        markerView.setChartView(c);
        c.setMarker(markerView);

        c.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
            }

            @Override
            public void onNothingSelected() {
            }
        });
    }

    private void renderChannelStacked(BarChart chart, long[] voiceByHourMs, long[] textByHourMs) {
        if (voiceByHourMs == null || textByHourMs == null
                || voiceByHourMs.length != 24 || textByHourMs.length != 24) {

            lastVoiceByHourMs = null;
            lastTextByHourMs = null;

            chart.clear();
            chart.invalidate();
            return;
        }

        lastVoiceByHourMs = voiceByHourMs;
        lastTextByHourMs = textByHourMs;

        ArrayList<BarEntry> entries = new ArrayList<>(24);

        for (int h = 0; h < 24; h++) {
            float v = (float) TimeKey.minutesFromMs(voiceByHourMs[h]);
            float t = (float) TimeKey.minutesFromMs(textByHourMs[h]);

            if (v > 0f || t > 0f) {
                entries.add(new BarEntry(h, new float[]{v, t}));
            }
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.invalidate();
            return;
        }

        BarDataSet ds = new BarDataSet(entries, "");
        ds.setDrawValues(false);
        ds.setStackLabels(new String[]{
                getString(R.string.communications_channel_voice),
                getString(R.string.communications_channel_text)
        });
        ds.setColors(
                Color.parseColor("#60A5FA"),
                Color.parseColor("#34D399")
        );
        ds.setHighlightEnabled(true);

        BarData data = new BarData(ds);
        data.setBarWidth(0.70f);

        chart.setData(data);

        float maxY = data.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(10f, maxY * 1.2f));

        chart.invalidate();
    }

    private String hourSlotLabel(int hour) {
        return String.format(Locale.getDefault(), "%02d:00–%02d:00", hour, hour + 1);
    }

    private class ChannelMarkerView extends MarkerView {

        private final TextView tv;

        public ChannelMarkerView(Context context) {
            super(context, R.layout.marker);
            tv = findViewById(R.id.tvMarkerText);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            int hour = Math.round(e.getX());
            int stackIndex = highlight != null ? highlight.getStackIndex() : -1;

            if (lastVoiceByHourMs != null && lastTextByHourMs != null
                    && hour >= 0 && hour < 24) {

                long voiceMin = TimeKey.minutesFromMs(lastVoiceByHourMs[hour]);
                long textMin = TimeKey.minutesFromMs(lastTextByHourMs[hour]);

                String labelHour = hourSlotLabel(hour);

                if (stackIndex == 0) {
                    tv.setText(getString(
                            R.string.format_comm_marker_voice,
                            getString(R.string.communications_channel_voice),
                            labelHour,
                            voiceMin
                    ));
                } else if (stackIndex == 1) {
                    tv.setText(getString(
                            R.string.format_comm_marker_text,
                            getString(R.string.communications_channel_text),
                            labelHour,
                            textMin
                    ));
                } else {
                    tv.setText(getString(
                            R.string.format_comm_marker_both,
                            labelHour,
                            getString(R.string.communications_channel_voice),
                            voiceMin,
                            getString(R.string.communications_channel_text),
                            textMin
                    ));
                }

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
}