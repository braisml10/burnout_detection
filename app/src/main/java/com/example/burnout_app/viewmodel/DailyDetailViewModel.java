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

    // Hourly + serie para gráfica (06..21)
    private final LiveData<List<HourlyMetricsEntity>> hourly;
    private final LiveData<int[]> screenMinutes6to22;

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

        // Hourly del día seleccionado
        hourly = Transformations.switchMap(selectedDay, day -> repo.observeHourlyMetrics(day));

        // Serie minutos/hora para gráfica: 06..21 (16 puntos)
        screenMinutes6to22 = Transformations.map(hourly, rows -> {
            int[] out = new int[16]; // idx 0 = 06:00, idx 15 = 21:00
            if (rows == null) return out;

            for (HourlyMetricsEntity h : rows) {
                if (h == null) continue;
                int hour = h.hour;
                if (hour >= 6 && hour <= 21) {
                    out[hour - 6] = (int) (h.screen_ms / 60000L);
                }
            }
            return out;
        });
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<int[]> getScreenMinutes6to22() {
        return screenMinutes6to22;
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
