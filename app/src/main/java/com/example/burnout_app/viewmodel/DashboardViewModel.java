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
        public final String communication;

        public UiState(String screenTime,
                       String notifications,
                       String multitask,
                       String communication) {
            this.screenTime = screenTime;
            this.notifications = notifications;
            this.multitask = multitask;
            this.communication = communication;
        }
    }

    private final UserActivityRepository userActivityRepository;
    private final CommunicationRepository communicationRepository;

    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();

    private final LiveData<List<HourlyMetricsEntity>> hourlyMetrics;
    private final MediatorLiveData<UiState> uiState = new MediatorLiveData<>();

    private LiveData<DailyMetricsEntity> dailyMetricsSource;
    private LiveData<DailyCommMetricsEntity> communicationMetricsSource;

    private DailyMetricsEntity lastDailyMetrics;
    private DailyCommMetricsEntity lastCommunicationMetrics;

    public DashboardViewModel(@NonNull Application application) {
        super(application);

        userActivityRepository = new UserActivityRepository(application);
        communicationRepository = new CommunicationRepository(application);

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);

        // ===================== DAILY METRICS =====================
        dailyMetricsSource = Transformations.switchMap(
                selectedDay,
                userActivityRepository::observeDailyMetrics
        );

        // ===================== COMMUNICATION METRICS =====================
        communicationMetricsSource = Transformations.switchMap(
                selectedDay,
                communicationRepository::observeDailyCommMetrics
        );

        uiState.addSource(dailyMetricsSource, dailyMetrics -> {
            lastDailyMetrics = dailyMetrics;
            uiState.setValue(buildUiState());
        });

        uiState.addSource(communicationMetricsSource, communicationMetrics -> {
            lastCommunicationMetrics = communicationMetrics;
            uiState.setValue(buildUiState());
        });

        // ===================== HOURLY METRICS =====================
        hourlyMetrics = Transformations.switchMap(
                selectedDay,
                userActivityRepository::observeHourlyMetrics
        );
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<List<HourlyMetricsEntity>> getHourlyMetrics() {
        return hourlyMetrics;
    }

    public void loadDay(int epochDay) {
        Integer current = selectedDay.getValue();
        if (current == null || current != epochDay) {
            selectedDay.setValue(epochDay);
        }
    }

    private UiState buildUiState() {

        long screenTimeMs =
                (lastDailyMetrics != null) ? lastDailyMetrics.screen_ms : 0L;

        int notificationCount =
                (lastDailyMetrics != null) ? lastDailyMetrics.notification_count : 0;

        int appSwitchCount =
                (lastDailyMetrics != null) ? lastDailyMetrics.app_switch_count : 0;

        int callsCount =
                (lastCommunicationMetrics != null) ? lastCommunicationMetrics.calls_count : 0;

        return new UiState(
                formatScreenTime(screenTimeMs),
                String.valueOf(Math.max(0, notificationCount)),
                String.valueOf(Math.max(0, appSwitchCount)),
                String.valueOf(Math.max(0, callsCount))
        );
    }

    private static String formatScreenTime(long ms) {

        long totalMinutes = ms / 60000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        }

        return String.format("%dm", minutes);
    }
}