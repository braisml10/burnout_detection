package com.example.burnout_app.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.repo.UserActivityRepository;
import com.example.burnout_app.helpers.TimeKey;

import java.util.Calendar;

public class DailyDetailViewModel extends AndroidViewModel {

    public static class UiState {
        public final String totalScreenTime;
        public final String sessions;   // desbloqueos
        public final String night;      // minutos nocturnos
        public final String debugDay;   // opcional, para debug

        public UiState(String totalScreenTime, String sessions, String night, String debugDay) {
            this.totalScreenTime = totalScreenTime;
            this.sessions = sessions;
            this.night = night;
            this.debugDay = debugDay;
        }
    }

    private final UserActivityRepository repo;

    // qué día estamos observando (hoy o mañana, según la hora)
    private final MutableLiveData<Integer> observedDay = new MutableLiveData<>();

    private final LiveData<DailyMetricsEntity> metrics;
    private final LiveData<UiState> uiState;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable boundaryRunnable = this::recomputeObservedDayAndReschedule;

    public DailyDetailViewModel(@NonNull Application app) {
        super(app);
        repo = new UserActivityRepository(app);

        metrics = Transformations.switchMap(observedDay, repo::observeDailyMetrics);

        uiState = Transformations.map(metrics, m -> {
            long screenMs = (m != null) ? m.screen_ms : 0L;
            long unlocks  = (m != null) ? m.unlock_count : 0L;
            long nightMs  = (m != null) ? m.night_ms : 0L;

            long nightMin = nightMs / 60000L;

            Integer day = observedDay.getValue();
            String dbg = (day == null) ? "-" : String.valueOf(day);

            return new UiState(
                    formatScreenTime(screenMs),
                    String.valueOf(unlocks),         // sesiones = desbloqueos
                    String.valueOf(nightMin),
                    dbg
            );
        });

        // inicializar y programar cambio automático
        recomputeObservedDayAndReschedule();
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    private void recomputeObservedDayAndReschedule() {
        long now = System.currentTimeMillis();

        int today = TimeKey.epochDayLocal(now);
        int hour = TimeKey.hourOfDayLocal(now);

        // Regla KPI nocturno:
        // - antes de las 22:00: mostramos la noche que terminó hoy => guardada en TODAY
        // - desde las 22:00: mostramos la noche en curso que termina mañana => guardada en TODAY+1
        int dayToObserve = (hour >= 22) ? (today + 1) : today;

        Integer prev = observedDay.getValue();
        if (prev == null || prev != dayToObserve) {
            observedDay.setValue(dayToObserve);
        }

        // Reprogramar al siguiente "punto de cambio" (22:00 o medianoche)
        long nextBoundary = Math.min(nextLocalTimeMs(22, 0), nextMidnightMs());
        long delay = Math.max(5_000L, nextBoundary - now); // mínimo 5s para evitar 0ms raros

        handler.removeCallbacks(boundaryRunnable);
        handler.postDelayed(boundaryRunnable, delay);
    }

    private long nextLocalTimeMs(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long t = cal.getTimeInMillis();
        if (t <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            t = cal.getTimeInMillis();
        }
        return t;
    }

    private long nextMidnightMs() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacks(boundaryRunnable);
        super.onCleared();
    }

    private static String formatScreenTime(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;

        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm", m);
    }
}
