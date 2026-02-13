package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.repo.UserActivityRepository;
import com.example.burnout_app.helpers.TimeKey;

public class AppsUsageViewModel extends AndroidViewModel {

    public static class UiState {
        public final String appsTime;
        public final String appSwitches;
        public final String uniqueApps;

        public UiState(String appsTime, String appSwitches, String uniqueApps) {
            this.appsTime = appsTime;
            this.appSwitches = appSwitches;
            this.uniqueApps = uniqueApps;
        }
    }

    private final UserActivityRepository repo;

    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();
    private final LiveData<UiState> uiState;

    public AppsUsageViewModel(@NonNull Application app) {
        super(app);

        repo = new UserActivityRepository(app);

        // Fuente dinámica: cambia según selectedDay
        LiveData<DailyMetricsEntity> src = Transformations.switchMap(selectedDay, day ->
                repo.observeDailyMetrics(day)
        );

        uiState = Transformations.map(src, m -> {
            long fgMs = (m != null) ? m.foreground_ms : 0L;
            int switches = (m != null) ? m.app_switch_count : 0;
            int uniqueApps = (m != null) ? m.unique_apps_count : 0;

            return new UiState(
                    formatTime(fgMs),
                    String.valueOf(switches),
                    String.valueOf(uniqueApps)
            );
        });

        // Inicial: HOY
        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    // Esto es lo que te faltaba: cambiar el día cambia el LiveData observado
    public void loadDay(int day) {
        Integer cur = selectedDay.getValue();
        if (cur == null || cur != day) {
            selectedDay.setValue(day);
        }
    }

    private static String formatTime(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;

        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm", m);
    }
}
