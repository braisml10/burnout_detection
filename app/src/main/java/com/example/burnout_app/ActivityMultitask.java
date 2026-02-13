package com.example.burnout_app;

import android.os.Bundle;
import android.widget.ImageButton;
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
    private TextView tvCategorySubtitle;

    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private int todayDay;
    private int selectedDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multitask);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        vm = new ViewModelProvider(this).get(AppsUsageViewModel.class);

        // KPIs
        tvAppsTime = findViewById(R.id.tvAppsTimeValue);
        tvSwitches = findViewById(R.id.tvAppSwitchesValue);
        tvUnique = findViewById(R.id.tvUniqueAppsValue);

        // Day selector
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        tvDayLabel = findViewById(R.id.tvDayLabel);
        tvCategorySubtitle = findViewById(R.id.tvCategorySubtitle);

        todayDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay = todayDay;

        // Observa cambios del ViewModel
        vm.getUiState().observe(this, s -> {
            tvAppsTime.setText(s.appsTime);
            tvSwitches.setText(s.appSwitches);
            tvUnique.setText(s.uniqueApps);
        });

        // Flecha izquierda
        btnPrevDay.setOnClickListener(v -> {
            selectedDay--;
            applyDayUi();
            vm.loadDay(selectedDay);
        });

        // Flecha derecha (no futuro)
        btnNextDay.setOnClickListener(v -> {
            if (selectedDay < todayDay) {
                selectedDay++;
                applyDayUi();
                vm.loadDay(selectedDay);
            }
        });

        // Inicial
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
        tvCategorySubtitle.setText(label);

        boolean canGoNext = selectedDay < todayDay;
        btnNextDay.setEnabled(canGoNext);
        btnNextDay.setAlpha(canGoNext ? 1f : 0.35f);
    }
}
