package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.repo.UserActivityRepository;
import com.example.burnout_app.helpers.TimeKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DailyDetailViewModel extends AndroidViewModel {

    public static class UiState {

        public final String totalScreenTime;
        public final String sessions;
        public final String night;

        public UiState (String totalScreenTime, String sessions, String night) {
            this.totalScreenTime = totalScreenTime;
            this.sessions = sessions;
            this.night = night;
        }
    }

    private final LiveData<UiState> uiState;

    public DailyDetailViewModel (@NonNull Application app){
        super(app);

        UserActivityRepository repo = new UserActivityRepository(app);

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        LiveData<DailyMetricsEntity> src = repo.observeDailyMetrics(today);

        uiState = Transformations.map(src, m -> {
            long screenMs = (m != null) ? m.screen_ms : 0L;
            long unlocks = (m != null) ? m.unlock_count : 0L;

            long nightMs = (m != null) ? m.night_ms : 0L;
            long nightMin = nightMs / 60000L;

            return new UiState(formatScreenTime(screenMs), String.valueOf(unlocks), String.valueOf(nightMin));
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
