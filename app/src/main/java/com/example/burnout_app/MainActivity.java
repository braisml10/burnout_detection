package com.example.burnout_app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.helpers.SessionManager;
import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.viewmodel.DashboardViewModel;
import com.example.burnout_app.viewmodel.ProfileViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private BarChart barChart3h;

    private DrawerLayout drawerLayout;
    private NavigationView navView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        MaterialCardView avatarCard = findViewById(R.id.avatarCard);
        TextView tvAvatarMain = findViewById(R.id.tvAvatar);

        TextView tvHeaderAvatar = null;

        if (navView != null) {
            android.view.View headerView = navView.getHeaderView(0);

            if (headerView != null) {
                tvHeaderAvatar = headerView.findViewById(R.id.tvHeaderAvatar);

                TextView finalTvHeaderAvatar = tvHeaderAvatar;
                if (finalTvHeaderAvatar != null && drawerLayout != null) {
                    finalTvHeaderAvatar.setOnClickListener(v -> {
                        drawerLayout.closeDrawer(GravityCompat.START);
                        startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                    });
                }
            }
        }

        if (avatarCard != null && drawerLayout != null) {
            avatarCard.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        TextView finalTvHeaderAvatar = tvHeaderAvatar;

        ProfileViewModel profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        profileViewModel.observeUserProfile().observe(this, userProfile -> {
            if (userProfile == null) return;

            String firstName = userProfile.nombre != null ? userProfile.nombre : "";
            String lastName = userProfile.apellidos != null ? userProfile.apellidos : "";
            String initials = getInitials(firstName, lastName);

            if (tvAvatarMain != null) {
                tvAvatarMain.setText(initials);
            }

            if (finalTvHeaderAvatar != null) {
                finalTvHeaderAvatar.setText(initials);
            }
        });

        if (navView != null && drawerLayout != null) {
            navView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();

                if (id == R.id.nav_main) {
                    // Already here
                } else if (id == R.id.nav_screen_time) {
                    startActivity(new Intent(this, ActivityScreenTime.class));
                } else if (id == R.id.nav_notifications) {
                    startActivity(new Intent(this, ActivityNotifications.class));
                } else if (id == R.id.nav_multitask) {
                    startActivity(new Intent(this, ActivityMultitask.class));
                } else if (id == R.id.nav_communication) {
                    startActivity(new Intent(this, ActivityCommunications.class));
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                } else if (id == R.id.nav_logout) {
                    new AlertDialog.Builder(this)
                            .setTitle("Cerrar sesión")
                            .setMessage("¿Está seguro de que quiere cerrar sesión?")
                            .setPositiveButton("Sí, cerrar", (dialog, which) -> {
                                SessionManager sessionManager = new SessionManager(MainActivity.this);
                                sessionManager.logout();

                                Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                            .show();
                }

                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        setupCardNavigation(R.id.cardScreenTime, ActivityScreenTime.class, "cardScreenTime");
        setupCardNavigation(R.id.cardMultitask, ActivityMultitask.class, "cardMultitask");
        setupCardNavigation(R.id.cardNotifications, ActivityNotifications.class, "cardNotifications");
        setupCardNavigation(R.id.cardCommunication, ActivityCommunications.class, "cardCommunication");

        TextView tvDate = findViewById(R.id.tvDate);
        tvDate.setText("| " + TimeKey.dateLabelFromTimestamp(System.currentTimeMillis()));

        TextView value1 = findViewById(R.id.value1);
        TextView value2 = findViewById(R.id.value2);
        TextView value3 = findViewById(R.id.value3);
        TextView value4 = findViewById(R.id.value4);

        DashboardViewModel dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        dashboardViewModel.getUiState().observe(this, uiState -> {
            if (uiState == null) return;

            if (value1 != null) value1.setText(uiState.screenTime);
            if (value2 != null) value2.setText(uiState.notifications);
            if (value3 != null) value3.setText(uiState.multitask);

            if (value4 != null) {
                int calls = 0;
                try {
                    calls = Integer.parseInt(uiState.communication.trim());
                } catch (Exception ignored) {
                }

                String callsText = getResources().getQuantityString(
                        R.plurals.kpi_calls,
                        calls,
                        calls
                );
                value4.setText(callsText);
            }
        });

        barChart3h = findViewById(R.id.barChart3h);
        if (barChart3h == null) {
            Log.e(TAG, "barChart3h is NULL -> revisa activity_main.xml: falta @+id/barChart3h");
        } else {
            setup3hBarChart(barChart3h);

            dashboardViewModel.getHourlyMetrics().observe(this, hourlyMetrics -> {
                long[] bucketsMs = aggregate3hBuckets(hourlyMetrics);
                render3hBars(barChart3h, bucketsMs);
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void setupCardNavigation(int cardId, Class<?> targetActivity, String labelForLogs) {
        MaterialCardView card = findViewById(cardId);

        if (card == null) {
            Log.e(TAG, labelForLogs + " is NULL -> revisa activity_main.xml (o variantes) y el id.");
            return;
        }

        Log.d(TAG, labelForLogs + " FOUND -> attaching click listener");
        card.setClickable(true);
        card.setFocusable(true);

        card.setOnClickListener(v -> {
            Log.d(TAG, labelForLogs + " CLICKED -> opening " + targetActivity.getSimpleName());
            startActivity(new Intent(MainActivity.this, targetActivity));
        });
    }

    private void setup3hBarChart(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText("Sin datos");

        chart.setTouchEnabled(true);
        chart.setPinchZoom(false);
        chart.setScaleEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(7.5f);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#94A3B8"));
        xAxis.setTextSize(12f);

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int i = Math.round(value);
                switch (i) {
                    case 0: return "03";
                    case 1: return "06";
                    case 2: return "09";
                    case 3: return "12";
                    case 4: return "15";
                    case 5: return "18";
                    case 6: return "21";
                    case 7: return "24";
                    default: return "";
                }
            }
        });

        chart.getAxisRight().setEnabled(false);

        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(180f);
        chart.getAxisLeft().setGranularity(30f);
        chart.getAxisLeft().setLabelCount(7, true);
        chart.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        chart.getAxisLeft().setDrawGridLines(true);

        chart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return ((int) value) + "m";
            }
        });

        chart.setFitBars(true);
        chart.setExtraOffsets(4f, 2f, 6f, 6f);
    }

    private long[] aggregate3hBuckets(List<HourlyMetricsEntity> hourlyMetrics) {
        long[] bucketsMs = new long[8];
        if (hourlyMetrics == null) return bucketsMs;

        for (HourlyMetricsEntity hourlyMetric : hourlyMetrics) {
            if (hourlyMetric == null) continue;

            int hour = hourlyMetric.hour;
            if (hour < 0 || hour > 23) continue;

            int bucket = hour / 3;
            bucketsMs[bucket] += hourlyMetric.screen_ms;
        }

        return bucketsMs;
    }

    private void render3hBars(BarChart chart, long[] bucketsMs) {
        if (bucketsMs == null || bucketsMs.length != 8) {
            chart.clear();
            chart.invalidate();
            return;
        }

        List<BarEntry> entries = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            float minutes = bucketsMs[i] / 60000f;
            entries.add(new BarEntry(i, minutes));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Minutos");
        dataSet.setColor(Color.parseColor("#22D3EE"));
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.7f);

        chart.setData(data);

        float maxY = data.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(30f, maxY * 1.2f));

        chart.invalidate();
    }

    private String getInitials(String firstName, String lastName) {
        String initials = "";

        if (firstName != null && !firstName.trim().isEmpty()) {
            initials += firstName.trim().substring(0, 1).toUpperCase();
        }

        if (lastName != null && !lastName.trim().isEmpty()) {
            initials += lastName.trim().substring(0, 1).toUpperCase();
        }

        return initials;
    }
}