package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
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
        public final String night;      // minutos nocturnos (KPI)

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

    // Hourly del día seleccionado y del día anterior
    private final LiveData<List<HourlyMetricsEntity>> hourlyToday;
    private final LiveData<List<HourlyMetricsEntity>> hourlyPrev;

    // Serie para gráfico lineal: 06..21 (16 puntos)
    private final LiveData<int[]> screenMinutes6to22;

    // Serie para barras nocturnas: 22,23,00,01,02,03,04,05,06 (9 puntos)
    private final MediatorLiveData<int[]> nightMinutes22to6 = new MediatorLiveData<>();

    private List<HourlyMetricsEntity> cachePrev;
    private List<HourlyMetricsEntity> cacheToday;

    public DailyDetailViewModel(@NonNull Application app) {
        super(app);
        repo = new UserActivityRepository(app);

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);

        metrics = Transformations.switchMap(selectedDay, repo::observeDailyMetrics);

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

        // ✅ hourly del día seleccionado
        hourlyToday = Transformations.switchMap(selectedDay, repo::observeHourlyMetrics);

        // ✅ hourly del día anterior (para 22 y 23)
        hourlyPrev = Transformations.switchMap(selectedDay, d -> repo.observeHourlyMetrics(d - 1));

        // 06..21 (16 puntos)
        screenMinutes6to22 = Transformations.map(hourlyToday, rows -> {
            int[] out = new int[16];
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

        // ✅ combinar prev + today para la noche (sin Runnable)
        nightMinutes22to6.addSource(hourlyPrev, rows -> {
            cachePrev = rows;
            recomputeNight();
        });
        nightMinutes22to6.addSource(hourlyToday, rows -> {
            cacheToday = rows;
            recomputeNight();
        });

        // valor inicial (evita nulls)
        nightMinutes22to6.setValue(new int[8]);

    }


    private void recomputeNight() {
        int[] out = new int[8]; // 0=22, 1=23, 2=00, ..., 7=05

        // 22 y 23 del día anterior
        if (cachePrev != null) {
            for (HourlyMetricsEntity h : cachePrev) {
                if (h == null) continue;
                if (h.hour == 22) out[0] = (int) (h.screen_ms / 60000L);
                else if (h.hour == 23) out[1] = (int) (h.screen_ms / 60000L);
            }
        }

        // 00..05 del día actual
        if (cacheToday != null) {
            for (HourlyMetricsEntity h : cacheToday) {
                if (h == null) continue;
                int hour = h.hour;
                if (hour >= 0 && hour <= 5) {
                    out[2 + hour] = (int) (h.screen_ms / 60000L); // 0->2 ... 5->7
                }
            }
        }

        nightMinutes22to6.setValue(out);
    }


    // ---------------------------
    // Getters
    // ---------------------------

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<int[]> getScreenMinutes6to22() {
        return screenMinutes6to22;
    }

    public LiveData<int[]> getNightMinutes22to6() {
        return nightMinutes22to6;
    }

    // ---------------------------
    // API
    // ---------------------------

    public void loadDay(int epochDay) {
        Integer current = selectedDay.getValue();
        if (current == null || current != epochDay) {

            // ✅ evita mezclar prev/today de días distintos durante el cambio
            cachePrev = null;
            cacheToday = null;
            nightMinutes22to6.setValue(new int[8]);

            selectedDay.setValue(epochDay);
        }
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private static String formatScreenTime(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;

        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm", m);
    }
}
