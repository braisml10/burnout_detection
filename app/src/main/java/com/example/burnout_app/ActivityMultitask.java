package com.example.burnout_app;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.AppsUsageViewModel;

public class ActivityMultitask extends AppCompatActivity {

    private AppsUsageViewModel vm;

    private TextView tvAppsTime;
    private TextView tvSwitches;
    private TextView tvUnique;

    private TextView tvDayLabel;

    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private int todayDay;
    private int selectedDay;

    private TextView tvCatSocialTime, tvCatEntTime, tvCatMsgTime, tvCatWorkTime, tvCatOtherTime;
    private ProgressBar pbCatSocial, pbCatEnt, pbCatMsg, pbCatWork, pbCatOther;

    // Top apps
    private ImageView imgApp1, imgApp2, imgApp3;
    private TextView tvApp1Name, tvApp2Name, tvApp3Name;
    private ProgressBar pbApp1, pbApp2, pbApp3;
    private TextView tvApp1Pct, tvApp2Pct, tvApp3Pct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multitask);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        vm = new ViewModelProvider(this).get(AppsUsageViewModel.class);

        // KPIs
        tvAppsTime = findViewById(R.id.tvAppsTimeValue);
        tvSwitches = findViewById(R.id.tvAppSwitchesValue);
        tvUnique   = findViewById(R.id.tvUniqueAppsValue);

        // Categorías
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

        // ✅ KPI tiempo total filtrado (cuadra con categorías)
        vm.getAppsTimeFiltered().observe(this, txt -> {
            if (txt != null) tvAppsTime.setText(txt);
        });

        // KPI switches + unique
        vm.getUiState().observe(this, s -> {
            if (s == null) return;
            tvSwitches.setText(s.appSwitches);
            tvUnique.setText(s.uniqueApps);
        });

        // Top apps views
        imgApp1 = findViewById(R.id.imgApp1);
        imgApp2 = findViewById(R.id.imgApp2);
        imgApp3 = findViewById(R.id.imgApp3);

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

            tvApp1Name.setText(prettyName(t.name1));
            tvApp2Name.setText(prettyName(t.name2));
            tvApp3Name.setText(prettyName(t.name3));

            tvApp1Pct.setText(t.pct1 + "%");
            tvApp2Pct.setText(t.pct2 + "%");
            tvApp3Pct.setText(t.pct3 + "%");

            pbApp1.setProgress(t.bar1);
            pbApp2.setProgress(t.bar2);
            pbApp3.setProgress(t.bar3);

            // Iconos opcionales: cuando tengas packageName en el state, aquí los cargas.
        });

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

        applyDayUi();
        vm.loadDay(selectedDay);
    }

    private void applyDayUi() {
        String label;

        if (selectedDay == todayDay) {
            label = "Hoy";
        } else if (selectedDay == todayDay - 1) {
            label = "Ayer";
        } else {
            label = TimeKey.dateLabelFromEpochDay(selectedDay);
        }

        tvDayLabel.setText(label);

        boolean canGoNext = selectedDay < todayDay;
        btnNextDay.setEnabled(canGoNext);
        btnNextDay.setAlpha(canGoNext ? 1f : 0.35f);
    }

    private static String prettyName(String s) {
        if (s == null) return "--";
        s = s.trim();
        if (s.isEmpty()) return "--";

        // si es package, coge el último segmento
        if (s.contains(".") && !s.contains(" ")) {
            String[] parts = s.split("\\.");
            return parts[parts.length - 1];
        }
        return s;
    }

}
