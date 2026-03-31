package gal.uvigo.burnout_app.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.base.BaseActivity;
import gal.uvigo.burnout_app.data.entity.HourlyMetricsEntity;
import gal.uvigo.burnout_app.helpers.ChartHelper;
import gal.uvigo.burnout_app.helpers.SessionManager;
import gal.uvigo.burnout_app.helpers.TimeKey;
import gal.uvigo.burnout_app.viewmodel.DashboardViewModel;
import gal.uvigo.burnout_app.viewmodel.ProfileViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointF;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";

    private BarChart barChart3h;
    private long[] lastBucketsMs;

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
                    startActivity(new Intent(this, ScreenTimeActivity.class));
                } else if (id == R.id.nav_notifications) {
                    startActivity(new Intent(this, NotificationsActivity.class));
                } else if (id == R.id.nav_multitask) {
                    startActivity(new Intent(this, MultitaskActivity.class));
                } else if (id == R.id.nav_communication) {
                    startActivity(new Intent(this, CommunicationsActivity.class));
                } else if (id == R.id.nav_risk) {
                    startActivity(new Intent(this, BurnoutRiskActivity.class));
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                } else if (id == R.id.nav_logout) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.main_logout_title))
                            .setMessage(getString(R.string.main_logout_message))
                            .setPositiveButton(getString(R.string.main_logout_confirm), (dialog, which) -> {
                                SessionManager sessionManager = new SessionManager(MainActivity.this);
                                sessionManager.logout();

                                Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .setNegativeButton(getString(R.string.common_cancel), (dialog, which) -> dialog.dismiss())
                            .show();
                }

                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        setupCardNavigation(R.id.cardScreenTime, ScreenTimeActivity.class);
        setupCardNavigation(R.id.cardMultitask, MultitaskActivity.class);
        setupCardNavigation(R.id.cardNotifications, NotificationsActivity.class);
        setupCardNavigation(R.id.cardCommunication, CommunicationsActivity.class);
        setupCardNavigation(R.id.cardBurnoutRisk, BurnoutRiskActivity.class);

        TextView tvDate = findViewById(R.id.tvDate);
        tvDate.setText(getString(
                R.string.format_date_prefix,
                TimeKey.dateLabelFromTimestamp(System.currentTimeMillis())
        ));

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
            Log.e(TAG, getString(R.string.error_main_chart_missing));
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

    private void setupCardNavigation(int cardId, Class<?> targetActivity) {
        MaterialCardView card = findViewById(cardId);

        if (card == null) {
            return;
        }

        card.setClickable(true);
        card.setFocusable(true);

        card.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, targetActivity));
        });
    }

    private void setup3hBarChart(BarChart chart) {
        ChartHelper.setupBaseBarChart(chart, this, false, true);
        chart.setHighlightFullBarEnabled(true);

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

        ChartHelper.setupMinutesLeftAxis(chart.getAxisLeft(), this, 30f);
        chart.getAxisLeft().setAxisMaximum(180f);
        chart.getAxisLeft().setLabelCount(7, true);

        chart.setFitBars(true);
        chart.setExtraOffsets(4f, 2f, 6f, 6f);

        MainBarMarkerView markerView = new MainBarMarkerView(this);
        markerView.setChartView(chart);
        chart.setMarker(markerView);

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
            }

            @Override
            public void onNothingSelected() {
            }
        });
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
            lastBucketsMs = null;
            chart.clear();
            chart.invalidate();
            return;
        }

        lastBucketsMs = bucketsMs;

        List<BarEntry> entries = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            float minutes = bucketsMs[i] / 60000f;
            entries.add(new BarEntry(i, minutes));
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.unit_minutes_full));
        dataSet.setColor(Color.parseColor("#22D3EE"));
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.7f);

        chart.setData(data);

        float maxY = data.getYMax();
        chart.getAxisLeft().setAxisMaximum(Math.max(30f, maxY * 1.2f));

        chart.invalidate();
    }

    private String[] bucketRange(int index) {
        int start = index * 3;
        int end = start + 3;

        String startStr = String.format(Locale.getDefault(), "%02d:00", start);
        String endStr = String.format(Locale.getDefault(), "%02d:00", end);

        return new String[]{startStr, endStr};
    }


    private class MainBarMarkerView extends MarkerView {

        private final TextView tv;

        public MainBarMarkerView(Context context) {
            super(context, R.layout.marker);
            tv = findViewById(R.id.tvMarkerText);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            int index = Math.round(e.getX());

            if (lastBucketsMs != null && index >= 0 && index < lastBucketsMs.length) {
                String[] range = bucketRange(index);
                long minutes = TimeKey.minutesFromMs(lastBucketsMs[index]);

                tv.setText(getString(
                        R.string.format_chart_usage_marker,
                        range[0],
                        range[1],
                        minutes,
                        getString(R.string.unit_minutes_short)
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