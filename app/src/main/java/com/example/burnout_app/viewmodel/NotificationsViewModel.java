package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.burnout_app.data.repo.NotificationRepository;
import com.example.burnout_app.helpers.TimeKey;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsViewModel extends AndroidViewModel {

    public static class UiState {
        public final int date;

        public final int totalDaily;
        public final int avgPerHour; // total / activeHours
        public final String mostIntrusiveApp;

        public final int[] notifsByHour; // 24
        public final List<NotificationRepository.TopNotifAppRow> topApps;

        public UiState(int date,
                       int totalDaily,
                       int avgPerHour,
                       String mostIntrusiveApp,
                       int[] notifsByHour,
                       List<NotificationRepository.TopNotifAppRow> topApps) {
            this.date = date;
            this.totalDaily = totalDaily;
            this.avgPerHour = avgPerHour;
            this.mostIntrusiveApp = mostIntrusiveApp;
            this.notifsByHour = notifsByHour;
            this.topApps = topApps;
        }
    }

    private final NotificationRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>();

    private int currentDay;

    public NotificationsViewModel(@NonNull Application app) {
        super(app);

        repo = new NotificationRepository(app);

        currentDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        loadDay(currentDay);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public void loadDay(int date) {
        if (date == currentDay && uiState.getValue() != null) return;
        currentDay = date;

        io.execute(() -> {
            int total = repo.getTotalNotificationsForDay(date);

            int activeHours = repo.getActiveHoursForDay(date);
            if (activeHours <= 0) activeHours = 1;
            int avg = (int) Math.round(total / (double) activeHours);

            int[] byHour = repo.getNotificationsPerHourForDay(date);

            List<NotificationRepository.TopNotifAppRow> top =
                    repo.getTopAppsByNotificationsForDay(date, 8);

            String mostIntrusive = "—";
            if (top != null && !top.isEmpty()) {
                mostIntrusive = top.get(0).name;
            }

            if (top == null) top = Collections.emptyList();

            uiState.postValue(new UiState(
                    date,
                    total,
                    avg,
                    mostIntrusive,
                    byHour,
                    top
            ));
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdown();
    }
}