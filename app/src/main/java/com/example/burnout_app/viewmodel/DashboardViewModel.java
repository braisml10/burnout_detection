package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.repo.UserActivityRepository;
import com.example.burnout_app.helpers.TimeKey;

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

    private final LiveData<UiState> uiState;

    public DashboardViewModel(@NonNull Application app) {
        super(app);

        UserActivityRepository repo = new UserActivityRepository(app);

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        LiveData<DailyMetricsEntity> src = repo.observeDailyMetrics(today);

        uiState = Transformations.map(src, m -> {
            long screenMs = (m != null) ? m.screen_ms : 0L;

            // OJO: estos campos dependen de que existan en DailyMetricsEntity
            int notif = (m != null) ? m.notification_count : 0;
            int switches = (m != null) ? m.app_switch_count : 0;

            // Comunicación: ahora mismo no la tienes integrada en daily_metrics,
            // así que de momento mostramos sesiones (o 0). Cambia cuando tengas daily_comm_metric.
            int comm = (m != null) ? m.session_count : 0;

            return new UiState(
                    formatScreenTime(screenMs),
                    String.valueOf(notif),
                    String.valueOf(switches),
                    String.valueOf(comm)
            );
        });
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    private static String formatScreenTime(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;

        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm", m);
    }

}