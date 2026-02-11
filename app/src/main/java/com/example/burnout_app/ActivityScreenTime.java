package com.example.burnout_app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.viewmodel.DailyDetailViewModel;

public class ActivityScreenTime extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_time);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        DailyDetailViewModel vm = new ViewModelProvider(this).get(DailyDetailViewModel.class);

        TextView tvTotal = findViewById(R.id.tvTotalValue);
        TextView tvSessions = findViewById(R.id.tvSessionsValue);
        TextView tvNight = findViewById(R.id.tvNightValue);

        vm.getUiState().observe(this, s -> {
            tvTotal.setText(s.totalScreenTime);
            tvSessions.setText(s.sessions);
            tvNight.setText(s.night);
        });

    }


}
