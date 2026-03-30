package gal.uvigo.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;
import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.repo.BurnoutRiskRepository;

public class BurnoutRiskViewModel extends AndroidViewModel {

    public static final int RISK_NONE = -1;
    public static final int RISK_LOW = 0;
    public static final int RISK_MODERATE = 1;
    public static final int RISK_HIGH = 2;

    public static final int LEVEL_NONE = -1;
    public static final int LEVEL_LOW = 0;
    public static final int LEVEL_MEDIUM = 1;
    public static final int LEVEL_HIGH = 2;

    public static final int TREND_NONE = -1;
    public static final int TREND_INCREASING = 0;
    public static final int TREND_STABLE = 1;
    public static final int TREND_DECREASING = 2;

    public static final int DRIVER_NONE = -1;
    public static final int DRIVER_FRAGMENTATION = 0;
    public static final int DRIVER_NIGHT_USE = 1;
    public static final int DRIVER_NOTIFICATIONS = 2;
    public static final int DRIVER_SCREEN_TIME = 3;
    public static final int DRIVER_TREND = 4;

    private final BurnoutRiskRepository riskRepository;
    private final BurnoutDatabase db;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final LiveData<BurnoutRiskEntity> latestRiskSource;
    private final LiveData<List<BurnoutRiskEntity>> trendSource;

    private final MediatorLiveData<BurnoutRiskUiState> uiState = new MediatorLiveData<>();

    public BurnoutRiskViewModel(@NonNull Application application) {
        super(application);
        riskRepository = new BurnoutRiskRepository(application);
        db = BurnoutDatabase.getInstance(application.getApplicationContext());

        latestRiskSource = riskRepository.observeLatestRisk();
        trendSource = riskRepository.observeLatestDays(7);

        uiState.addSource(latestRiskSource, latestRisk -> {
            if (latestRisk == null) {
                uiState.postValue(BurnoutRiskUiState.empty());
                return;
            }
            loadUiState(latestRisk);
        });
    }

    public LiveData<BurnoutRiskUiState> getUiState() {
        return uiState;
    }

    public LiveData<List<BurnoutRiskEntity>> getTrend7Days() {
        return trendSource;
    }

    private void loadUiState(BurnoutRiskEntity latestRisk) {
        ioExecutor.execute(() -> {
            int day = (int) latestRisk.epochDay;

            DailyMetricsEntity todayMetrics = db.userActivityDao().getDailyMetricsByDate(day);
            List<DailyMetricsEntity> baselineDays = db.userActivityDao().getDailyMetricsRange(day - 7, day - 1);
            if (baselineDays == null) baselineDays = new ArrayList<>();

            BurnoutRiskUiState state = buildUiState(latestRisk, todayMetrics, baselineDays);
            uiState.postValue(state);
        });
    }

    private BurnoutRiskUiState buildUiState(BurnoutRiskEntity risk,
                                            DailyMetricsEntity today,
                                            List<DailyMetricsEntity> baselineDays) {

        BurnoutRiskUiState state = new BurnoutRiskUiState();

        state.riskScore = risk.riskScore;
        state.riskLevel = riskLevel(risk.riskScore);

        List<DriverItem> drivers = buildTopDrivers(risk);
        state.driver1Type = drivers.size() > 0 ? drivers.get(0).type : DRIVER_NONE;
        state.driver1Level = drivers.size() > 0 ? drivers.get(0).level : LEVEL_NONE;
        state.driver2Type = drivers.size() > 1 ? drivers.get(1).type : DRIVER_NONE;
        state.driver2Level = drivers.size() > 1 ? drivers.get(1).level : LEVEL_NONE;
        state.driver3Type = drivers.size() > 2 ? drivers.get(2).type : DRIVER_NONE;
        state.driver3Level = drivers.size() > 2 ? drivers.get(2).level : LEVEL_NONE;

        double todayScreenHours = today != null ? millisToHours(today.screen_ms) : 0.0;
        double baselineScreenHours = avgScreenHours(baselineDays);

        double todayFragmentation = today != null ? fragmentationIndex(today) : 0.0;
        double baselineFragmentation = avgFragmentationIndex(baselineDays);

        long todayNightMs = today != null ? Math.max(0L, today.night_ms) : 0L;
        long baselineNightMs = avgNightMs(baselineDays);

        int todayNotif = today != null ? Math.max(0, today.notification_count) : 0;
        double baselineNotif = avgNotificationCount(baselineDays);

        state.fragmentation = new DimensionUi();
        state.fragmentation.level = levelFromScore(risk.fragmentationScore);
        state.fragmentation.value = todayFragmentation;
        state.fragmentation.baseline = baselineFragmentation;

        state.nightUse = new DimensionUi();
        state.nightUse.level = levelFromScore(risk.nightUseScore);
        state.nightUse.valueMinutes = todayNightMs / 60000L;
        state.nightUse.baselineMinutes = baselineNightMs / 60000L;

        state.notifications = new DimensionUi();
        state.notifications.level = levelFromScore(risk.notificationPressureScore);
        state.notifications.valueCount = todayNotif;
        state.notifications.baselineCount = baselineNotif;

        state.screenTime = new DimensionUi();
        state.screenTime.level = levelFromScore(risk.screenTimeScore);
        state.screenTime.valueHours = todayScreenHours;
        state.screenTime.baselineHours = baselineScreenHours;

        state.trend = new DimensionUi();
        state.trend.level = levelFromScore(risk.trendDeviationScore);
        state.trend.trendKind = trendKind(risk.trendDeviationScore);

        return state;
    }

    private List<DriverItem> buildTopDrivers(BurnoutRiskEntity risk) {
        List<DriverItem> items = new ArrayList<>();

        items.add(new DriverItem(DRIVER_FRAGMENTATION, levelFromScore(risk.fragmentationScore), risk.fragmentationScore));
        items.add(new DriverItem(DRIVER_NIGHT_USE, levelFromScore(risk.nightUseScore), risk.nightUseScore));
        items.add(new DriverItem(DRIVER_NOTIFICATIONS, levelFromScore(risk.notificationPressureScore), risk.notificationPressureScore));
        items.add(new DriverItem(DRIVER_SCREEN_TIME, levelFromScore(risk.screenTimeScore), risk.screenTimeScore));
        items.add(new DriverItem(DRIVER_TREND, levelFromScore(risk.trendDeviationScore), risk.trendDeviationScore));

        Collections.sort(items, (a, b) -> Double.compare(b.score, a.score));

        List<DriverItem> filtered = new ArrayList<>();
        for (DriverItem item : items) {
            if (item.score > 0.0) filtered.add(item);
        }

        return filtered;
    }

    private int riskLevel(double score) {
        if (score <= 0.7) return RISK_LOW;
        if (score <= 1.3) return RISK_MODERATE;
        return RISK_HIGH;
    }

    private int levelFromScore(double score) {
        if (score <= 0.0) return LEVEL_LOW;
        if (score < 2.0) return LEVEL_MEDIUM;
        return LEVEL_HIGH;
    }

    private int trendKind(double trendScore) {
        if (trendScore >= 2.0) return TREND_INCREASING;
        if (trendScore >= 1.0) return TREND_STABLE;
        return TREND_DECREASING;
    }

    private double fragmentationIndex(DailyMetricsEntity day) {
        if (day == null) return 0.0;
        double screenHours = millisToHours(day.screen_ms);
        if (screenHours <= 0.0) return 0.0;

        double sessionsPerHour = Math.max(0, day.session_count) / screenHours;
        double switchesPerHour = Math.max(0, day.app_switch_count) / screenHours;
        return (sessionsPerHour + switchesPerHour) / 2.0;
    }

    private double avgFragmentationIndex(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0.0;
        double sum = 0.0;
        for (DailyMetricsEntity d : days) sum += fragmentationIndex(d);
        return sum / days.size();
    }

    private double avgScreenHours(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0.0;
        double sum = 0.0;
        for (DailyMetricsEntity d : days) {
            sum += millisToHours(d.screen_ms);
        }
        return sum / days.size();
    }

    private long avgNightMs(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0L;
        long sum = 0L;
        for (DailyMetricsEntity d : days) {
            sum += Math.max(0L, d.night_ms);
        }
        return sum / days.size();
    }

    private double avgNotificationCount(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0.0;
        double sum = 0.0;
        for (DailyMetricsEntity d : days) {
            sum += Math.max(0, d.notification_count);
        }
        return sum / days.size();
    }

    private double millisToHours(long millis) {
        return Math.max(0L, millis) / 3600000.0;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdown();
    }

    private static class DriverItem {
        final int type;
        final int level;
        final double score;

        DriverItem(int type, int level, double score) {
            this.type = type;
            this.level = level;
            this.score = score;
        }
    }

    public static class DimensionUi {
        public int level = LEVEL_NONE;

        public double value = 0.0;
        public double baseline = 0.0;

        public long valueMinutes = 0L;
        public long baselineMinutes = 0L;

        public int valueCount = 0;
        public double baselineCount = 0.0;

        public double valueHours = 0.0;
        public double baselineHours = 0.0;

        public int trendKind = TREND_NONE;
    }

    public static class BurnoutRiskUiState {
        public double riskScore;
        public int riskLevel;

        public int driver1Type;
        public int driver1Level;
        public int driver2Type;
        public int driver2Level;
        public int driver3Type;
        public int driver3Level;

        public DimensionUi fragmentation;
        public DimensionUi nightUse;
        public DimensionUi notifications;
        public DimensionUi screenTime;
        public DimensionUi trend;

        public static BurnoutRiskUiState empty() {
            BurnoutRiskUiState state = new BurnoutRiskUiState();
            state.riskScore = 0.0;
            state.riskLevel = RISK_NONE;

            state.driver1Type = DRIVER_NONE;
            state.driver1Level = LEVEL_NONE;
            state.driver2Type = DRIVER_NONE;
            state.driver2Level = LEVEL_NONE;
            state.driver3Type = DRIVER_NONE;
            state.driver3Level = LEVEL_NONE;

            state.fragmentation = new DimensionUi();
            state.nightUse = new DimensionUi();
            state.notifications = new DimensionUi();
            state.screenTime = new DimensionUi();
            state.trend = new DimensionUi();

            return state;
        }
    }
}