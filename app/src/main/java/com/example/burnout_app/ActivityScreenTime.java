package com.example.burnout_app;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DailyDetailViewModel;

public class ActivityScreenTime extends AppCompatActivity {

    private DailyDetailViewModel vm;

    private TextView tvTotal;
    private TextView tvSessions;
    private TextView tvNight;

    private TextView tvDayLabel;
    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;

    private int todayDay;
    private int selectedDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_time);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        vm = new ViewModelProvider(this).get(DailyDetailViewModel.class);

        tvTotal = findViewById(R.id.tvTotalValue);
        tvSessions = findViewById(R.id.tvSessionsValue);
        tvNight = findViewById(R.id.tvNightValue);

        // Day selector (si existe en tu XML)
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        tvDayLabel = findViewById(R.id.tvDayLabel);

        todayDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay = todayDay;

        vm.getUiState().observe(this, s -> {
            tvTotal.setText(s.totalScreenTime);
            tvSessions.setText(s.sessions);
            tvNight.setText(s.night);
        });

        // Flechas
        if (btnPrevDay != null && btnNextDay != null && tvDayLabel != null) {

            btnPrevDay.setOnClickListener(v -> {
                selectedDay = selectedDay - 1;
                applyDayUi(selectedDay, todayDay);
                vm.loadDay(selectedDay);
            });

            btnNextDay.setOnClickListener(v -> {
                if (selectedDay < todayDay) {
                    selectedDay = selectedDay + 1;
                    applyDayUi(selectedDay, todayDay);
                    vm.loadDay(selectedDay);
                }
            });

            applyDayUi(selectedDay, todayDay);
        }

        // Cargar hoy al entrar
        vm.loadDay(selectedDay);
    }

    private void applyDayUi(int day, int today) {
        if (tvDayLabel == null) return;

        if (day == today) {
            tvDayLabel.setText("Hoy");
        } else {
            // Usa tu helper si ya existe
            tvDayLabel.setText(TimeKey.dateLabelFromEpochDay(day));
        }

        boolean canGoNext = day < today;
        btnNextDay.setEnabled(canGoNext);
        btnNextDay.setAlpha(canGoNext ? 1.0f : 0.35f);
    }
}
