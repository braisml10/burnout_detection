package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.data.repo.UserActivityRepository;
import com.example.burnout_app.helpers.TimeKey;

import java.util.List;

public class DashboardViewModel extends AndroidViewModel {

    public static class UiState {
        public final String screenTime;
        public final String notifications;
        public final String multitask;
        public final String communication;

        public UiState(String screenTime, String notifications, String multitask, String communication) {
            this.screenTime = screenTime;
            this.notifications = notifications;
            this.multitask = multitask;
            this.communication = communication;
        }
    }

    private final UserActivityRepository repo;

    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();

    private final LiveData<UiState> uiState;

    // ✅ Para la gráfica del dashboard (MainActivity): hourly_metric del día
    private final LiveData<List<HourlyMetricsEntity>> hourlyMetrics;

    public DashboardViewModel(@NonNull Application app) {
        super(app);

        repo = new UserActivityRepository(app);

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);

        // Daily metrics del día seleccionado (para KPIs)
        LiveData<DailyMetricsEntity> src = Transformations.switchMap(selectedDay, repo::observeDailyMetrics);

        uiState = Transformations.map(src, m -> {
            long screenMs = (m != null) ? m.screen_ms : 0L;

            int notif = (m != null) ? m.notification_count : 0;
            int switches = (m != null) ? m.app_switch_count : 0;

            // Comunicación (placeholder temporal)
            int comm = (m != null) ? m.session_count : 0;

            return new UiState(
                    formatScreenTime(screenMs),
                    String.valueOf(notif),
                    String.valueOf(switches),
                    String.valueOf(comm)
            );
        });

        // Hourly metrics del día seleccionado (para el BarChart 3h en MainActivity)
        hourlyMetrics = Transformations.switchMap(selectedDay, repo::observeHourlyMetrics);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    // ✅ MainActivity observará esto para pintar la gráfica (y agrupar 3h)
    public LiveData<List<HourlyMetricsEntity>> getHourlyMetrics() {
        return hourlyMetrics;
    }

    // (Opcional) por si más adelante metes selector de día en dashboard
    public void loadDay(int epochDay) {
        Integer cur = selectedDay.getValue();
        if (cur == null || cur != epochDay) {
            selectedDay.setValue(epochDay);
        }
    }

    private static String formatScreenTime(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;

        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm", m);
    }
}
