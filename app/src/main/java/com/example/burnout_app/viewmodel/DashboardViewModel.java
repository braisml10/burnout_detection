package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.data.repo.CommunicationRepository;
import com.example.burnout_app.data.repo.UserActivityRepository;
import com.example.burnout_app.helpers.TimeKey;

import java.util.List;

public class DashboardViewModel extends AndroidViewModel {

    public static class UiState {
        public final String screenTime;
        public final String notifications;
        public final String multitask;
        public final String communication; // ✅ nº llamadas

        public UiState(String screenTime, String notifications, String multitask, String communication) {
            this.screenTime = screenTime;
            this.notifications = notifications;
            this.multitask = multitask;
            this.communication = communication;
        }
    }

    private final UserActivityRepository userRepo;
    private final CommunicationRepository commRepo;

    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();

    private final LiveData<List<HourlyMetricsEntity>> hourlyMetrics;

    // ✅ UiState combinado sin observeForever
    private final MediatorLiveData<UiState> uiState = new MediatorLiveData<>();

    private LiveData<DailyMetricsEntity> dailySrc;
    private LiveData<DailyCommMetricsEntity> commSrc;

    private DailyMetricsEntity lastDaily;
    private DailyCommMetricsEntity lastComm;

    public DashboardViewModel(@NonNull Application app) {
        super(app);

        userRepo = new UserActivityRepository(app);
        commRepo = new CommunicationRepository(app);

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);

        // KPI 1-3 (daily_metric)
        dailySrc = Transformations.switchMap(selectedDay, userRepo::observeDailyMetrics);

        // KPI 4 (daily_comm_metric)
        commSrc = Transformations.switchMap(selectedDay, commRepo::observeDaily);

        // ✅ combiner
        uiState.addSource(dailySrc, d -> {
            lastDaily = d;
            uiState.setValue(build());
        });

        uiState.addSource(commSrc, c -> {
            lastComm = c;
            uiState.setValue(build());
        });

        // Gráfica 3h del dashboard (hourly_metric)
        hourlyMetrics = Transformations.switchMap(selectedDay, userRepo::observeHourlyMetrics);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<List<HourlyMetricsEntity>> getHourlyMetrics() {
        return hourlyMetrics;
    }

    public void loadDay(int epochDay) {
        Integer cur = selectedDay.getValue();
        if (cur == null || cur != epochDay) {
            selectedDay.setValue(epochDay);
        }
    }

    private UiState build() {
        long screenMs = (lastDaily != null) ? lastDaily.screen_ms : 0L;
        int notif = (lastDaily != null) ? lastDaily.notification_count : 0;
        int switches = (lastDaily != null) ? lastDaily.app_switch_count : 0;

        int calls = (lastComm != null) ? lastComm.calls_count : 0;

        return new UiState(
                formatScreenTime(screenMs),
                String.valueOf(Math.max(0, notif)),
                String.valueOf(Math.max(0, switches)),
                String.valueOf(Math.max(0, calls)) // ✅ KPI4 = llamadas
        );
    }

    private static String formatScreenTime(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;

        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm", m);
    }
}