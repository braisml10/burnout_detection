package gal.uvigo.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyMetricsEntity;
import gal.uvigo.burnout_app.data.repo.UserActivityRepository;
import gal.uvigo.burnout_app.helpers.TimeKey;

import java.util.List;

public class DailyDetailViewModel extends AndroidViewModel {

    public static class UiState {
        public final String totalScreenTime;
        public final String sessions;
        public final String night;

        public UiState(String totalScreenTime, String sessions, String night) {
            this.totalScreenTime = totalScreenTime;
            this.sessions = sessions;
            this.night = night;
        }
    }

    private static final int NIGHT_START_HOUR = 22;
    private static final int NIGHT_END_HOUR = 6;
    private static final int DAY_START_HOUR = 7;
    private static final int DAY_END_HOUR = 21;

    private final UserActivityRepository userActivityRepository;

    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();

    private final LiveData<DailyMetricsEntity> dailyMetrics;
    private final LiveData<UiState> uiState;

    private final LiveData<List<HourlyMetricsEntity>> hourlyMetricsToday;
    private final LiveData<List<HourlyMetricsEntity>> hourlyMetricsPreviousDay;

    private final LiveData<int[]> screenMinutesFrom7To21;

    private final MediatorLiveData<int[]> nightMinutesFrom22To06 = new MediatorLiveData<>();

    private List<HourlyMetricsEntity> cachedPreviousDayHourlyMetrics;
    private List<HourlyMetricsEntity> cachedTodayHourlyMetrics;

    public DailyDetailViewModel(@NonNull Application application) {
        super(application);
        userActivityRepository = new UserActivityRepository(application);

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);

        dailyMetrics = Transformations.switchMap(
                selectedDay,
                userActivityRepository::observeDailyMetrics
        );

        uiState = Transformations.map(dailyMetrics, metrics -> {
            long screenTimeMs = (metrics != null) ? metrics.screen_ms : 0L;
            int unlockCount = (metrics != null) ? metrics.unlock_count : 0;
            long nightTimeMs = (metrics != null) ? metrics.night_ms : 0L;

            long nightMinutes = nightTimeMs / 60000L;

            return new UiState(
                    formatScreenTime(screenTimeMs),
                    String.valueOf(unlockCount),
                    String.valueOf(nightMinutes)
            );
        });

        hourlyMetricsToday = Transformations.switchMap(
                selectedDay,
                userActivityRepository::observeHourlyMetrics
        );

        hourlyMetricsPreviousDay = Transformations.switchMap(
                selectedDay,
                day -> userActivityRepository.observeHourlyMetrics(day - 1)
        );

        screenMinutesFrom7To21 = Transformations.map(hourlyMetricsToday, hourlyRows -> {
            int[] out = new int[15];
            if (hourlyRows == null) return out;

            for (HourlyMetricsEntity hourlyRow : hourlyRows) {
                if (hourlyRow == null) continue;

                int hour = hourlyRow.hour;
                if (hour >= DAY_START_HOUR && hour <= DAY_END_HOUR) {
                    out[hour - DAY_START_HOUR] = (int) (hourlyRow.screen_ms / 60000L);
                }
            }

            return out;
        });

        nightMinutesFrom22To06.addSource(hourlyMetricsPreviousDay, hourlyRows -> {
            cachedPreviousDayHourlyMetrics = hourlyRows;
            recomputeNightMinutesFrom22To06();
        });

        nightMinutesFrom22To06.addSource(hourlyMetricsToday, hourlyRows -> {
            cachedTodayHourlyMetrics = hourlyRows;
            recomputeNightMinutesFrom22To06();
        });

        nightMinutesFrom22To06.setValue(new int[9]);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<int[]> getScreenMinutesFrom7To21() {
        return screenMinutesFrom7To21;
    }

    public LiveData<int[]> getNightMinutesFrom22To06() {
        return nightMinutesFrom22To06;
    }

    public void loadDay(int epochDay) {
        Integer currentDay = selectedDay.getValue();
        if (currentDay == null || currentDay != epochDay) {
            cachedPreviousDayHourlyMetrics = null;
            cachedTodayHourlyMetrics = null;
            nightMinutesFrom22To06.setValue(new int[9]);

            selectedDay.setValue(epochDay);
        }
    }

    private void recomputeNightMinutesFrom22To06() {
        int[] out = new int[9];

        if (cachedPreviousDayHourlyMetrics != null) {
            for (HourlyMetricsEntity hourlyRow : cachedPreviousDayHourlyMetrics) {
                if (hourlyRow == null) continue;

                if (hourlyRow.hour == 22) {
                    out[0] = (int) (hourlyRow.screen_ms / 60000L);
                } else if (hourlyRow.hour == 23) {
                    out[1] = (int) (hourlyRow.screen_ms / 60000L);
                }
            }
        }

        if (cachedTodayHourlyMetrics != null) {
            for (HourlyMetricsEntity hourlyRow : cachedTodayHourlyMetrics) {
                if (hourlyRow == null) continue;

                int hour = hourlyRow.hour;
                if (hour >= 0 && hour <= NIGHT_END_HOUR) {
                    out[2 + hour] = (int) (hourlyRow.screen_ms / 60000L);
                }
            }
        }

        nightMinutesFrom22To06.setValue(out);
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