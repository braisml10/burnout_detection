package gal.uvigo.burnout_app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.base.BaseActivity;
import gal.uvigo.burnout_app.helpers.ChartHelper;
import gal.uvigo.burnout_app.helpers.RetentionPolicy;
import gal.uvigo.burnout_app.viewmodel.AppsUsageViewModel;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

public class MultitaskActivity extends BaseActivity {

    private AppsUsageViewModel vm;

    private TextView tvAppsTime;
    private TextView tvSwitches;
    private TextView tvUnique;

    private TextView tvCatSocialTime, tvCatEntTime, tvCatMsgTime, tvCatWorkTime, tvCatOtherTime;
    private ProgressBar pbCatSocial, pbCatEnt, pbCatMsg, pbCatWork, pbCatOther;

    private TextView tvApp1Name, tvApp2Name, tvApp3Name;
    private ProgressBar pbApp1, pbApp2, pbApp3;
    private TextView tvApp1Pct, tvApp2Pct, tvApp3Pct;

    private LineChart chartSwitches;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multitask);

        setupBackButton(R.id.btnBack);

        vm = new ViewModelProvider(this).get(AppsUsageViewModel.class);

        tvAppsTime = findViewById(R.id.tvAppsTimeValue);
        tvSwitches = findViewById(R.id.tvAppSwitchesValue);
        tvUnique = findViewById(R.id.tvUniqueAppsValue);

        vm.getAppsTimeFiltered().observe(this, txt -> {
            if (txt != null) tvAppsTime.setText(txt);
        });

        vm.getUiState().observe(this, s -> {
            if (s == null) return;
            tvSwitches.setText(s.appSwitches);
            tvUnique.setText(s.uniqueApps);
        });

        tvCatSocialTime = findViewById(R.id.tvCatSocialTime);
        tvCatEntTime = findViewById(R.id.tvCatEntTime);
        tvCatMsgTime = findViewById(R.id.tvCatMsgTime);
        tvCatWorkTime = findViewById(R.id.tvCatWorkTime);
        tvCatOtherTime = findViewById(R.id.tvCatOtherTime);

        pbCatSocial = findViewById(R.id.pbCatSocial);
        pbCatEnt = findViewById(R.id.pbCatEnt);
        pbCatMsg = findViewById(R.id.pbCatMsg);
        pbCatWork = findViewById(R.id.pbCatWork);
        pbCatOther = findViewById(R.id.pbCatOther);

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

            tvApp1Pct.setText(getString(R.string.format_percentage, t.pct1));
            tvApp2Pct.setText(getString(R.string.format_percentage, t.pct2));
            tvApp3Pct.setText(getString(R.string.format_percentage, t.pct3));

            pbApp1.setProgress(t.bar1);
            pbApp2.setProgress(t.bar2);
            pbApp3.setProgress(t.bar3);
        });

        initDaySelector(
                R.id.tvDayLabel,
                R.id.btnPrevDay,
                R.id.btnNextDay,
                RetentionPolicy.DATA_RETENTION_DAYS
        );

        onDayChanged(selectedDay);
    }

    @Override
    protected void onDayChanged(int selectedDay) {
        vm.loadDay(selectedDay);
    }


    private void setupSwitchesLineChart(LineChart c) {
        ChartHelper.setupBaseLineChart(c, this, true);
        ChartHelper.setupHourXAxis24(c.getXAxis());
        ChartHelper.setupDefaultLeftAxis(c.getAxisLeft(), 0f, 5f);
    }

    private String prettyName(String s) {
        if (s == null) return getString(R.string.common_no_data);
        s = s.trim();
        if (s.isEmpty()) return getString(R.string.common_no_data);

        if (s.contains(".") && !s.contains(" ")) {
            String[] parts = s.split("\\.");
            return parts[parts.length - 1];
        }
        return s;
    }
}
