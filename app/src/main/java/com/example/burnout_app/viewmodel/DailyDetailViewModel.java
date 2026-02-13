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

public class DailyDetailViewModel extends AndroidViewModel {

    public static class UiState {
        public final String totalScreenTime;
        public final String sessions;   // desbloqueos
        public final String night;      // minutos nocturnos

        public UiState(String totalScreenTime, String sessions, String night) {
            this.totalScreenTime = totalScreenTime;
            this.sessions = sessions;
            this.night = night;
        }
    }

    private final UserActivityRepository repo;

    // Día seleccionado desde la UI
    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();

    private final LiveData<DailyMetricsEntity> metrics;
    private final LiveData<UiState> uiState;

    public DailyDetailViewModel(@NonNull Application app) {
        super(app);
        repo = new UserActivityRepository(app);

        // Por defecto: hoy
        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);

        // Re-observar Room cada vez que cambie el día
        metrics = Transformations.switchMap(selectedDay, day -> repo.observeDailyMetrics(day));

        // Mapear entidad -> UiState
        uiState = Transformations.map(metrics, m -> {
            long screenMs = (m != null) ? m.screen_ms : 0L;
            int unlocks   = (m != null) ? m.unlock_count : 0;
            long nightMs  = (m != null) ? m.night_ms : 0L;

            long nightMin = nightMs / 60000L;

            return new UiState(
                    formatScreenTime(screenMs),
                    String.valueOf(unlocks),
                    String.valueOf(nightMin)
            );
        });
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public void loadDay(int epochDay) {
        Integer current = selectedDay.getValue();
        if (current == null || current != epochDay) {
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
