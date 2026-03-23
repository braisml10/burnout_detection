package gal.uvigo.burnout_app.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.repo.UsageRepository;
import gal.uvigo.burnout_app.data.repo.UserActivityRepository;
import gal.uvigo.burnout_app.helpers.AppCategoryResolver;
import gal.uvigo.burnout_app.helpers.TimeKey;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppsUsageViewModel extends AndroidViewModel {

    // ===================== TOP KPIS =====================
    public static class UiState {
        public final String appSwitches;
        public final String uniqueApps;

        public UiState(String appSwitches, String uniqueApps) {
            this.appSwitches = appSwitches;
            this.uniqueApps = uniqueApps;
        }
    }

    // ===================== CATEGORY TIME =====================
    public static class CategoryState {
        public final String socialTxt;
        public final String entTxt;
        public final String msgTxt;
        public final String workTxt;
        public final String otherTxt;

        public final int socialPct;
        public final int entPct;
        public final int msgPct;
        public final int workPct;
        public final int otherPct;

        public CategoryState(String socialTxt,
                             String entTxt,
                             String msgTxt,
                             String workTxt,
                             String otherTxt,
                             int socialPct,
                             int entPct,
                             int msgPct,
                             int workPct,
                             int otherPct) {
            this.socialTxt = socialTxt;
            this.entTxt = entTxt;
            this.msgTxt = msgTxt;
            this.workTxt = workTxt;
            this.otherTxt = otherTxt;
            this.socialPct = socialPct;
            this.entPct = entPct;
            this.msgPct = msgPct;
            this.workPct = workPct;
            this.otherPct = otherPct;
        }
    }

    // ===================== TOP APPS =====================
    public static class TopAppsState {
        public final String name1;
        public final String name2;
        public final String name3;
        public final int pct1;
        public final int pct2;
        public final int pct3;
        public final int bar1;
        public final int bar2;
        public final int bar3;

        public TopAppsState(String name1,
                            String name2,
                            String name3,
                            int pct1,
                            int pct2,
                            int pct3,
                            int bar1,
                            int bar2,
                            int bar3) {
            this.name1 = name1;
            this.name2 = name2;
            this.name3 = name3;
            this.pct1 = pct1;
            this.pct2 = pct2;
            this.pct3 = pct3;
            this.bar1 = bar1;
            this.bar2 = bar2;
            this.bar3 = bar3;
        }
    }

    // ===================== SWITCHES CHART =====================
    public static class SwitchesChartState {
        public final List<Entry> entries;
        public final int maxY;

        public SwitchesChartState(List<Entry> entries, int maxY) {
            this.entries = entries;
            this.maxY = maxY;
        }
    }

    private final UserActivityRepository userActivityRepository;
    private final UsageRepository usageRepository;

    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();

    private final LiveData<UiState> uiState;
    private final MutableLiveData<String> appsTimeFiltered = new MutableLiveData<>();
    private final MutableLiveData<CategoryState> categoryState = new MutableLiveData<>();
    private final MutableLiveData<TopAppsState> topAppsState = new MutableLiveData<>();
    private final MutableLiveData<SwitchesChartState> switchesChartState = new MutableLiveData<>();

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public AppsUsageViewModel(@NonNull Application application) {
        super(application);

        userActivityRepository = new UserActivityRepository(application);
        usageRepository = new UsageRepository(application);

        LiveData<DailyMetricsEntity> dailyMetricsSource =
                Transformations.switchMap(selectedDay, userActivityRepository::observeDailyMetrics);

        uiState = Transformations.map(dailyMetricsSource, dailyMetrics -> {
            int appSwitchCount = (dailyMetrics != null) ? dailyMetrics.app_switch_count : 0;
            int uniqueAppsCount = (dailyMetrics != null) ? dailyMetrics.unique_apps_count : 0;

            return new UiState(
                    String.valueOf(appSwitchCount),
                    String.valueOf(uniqueAppsCount)
            );
        });

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);
        loadAll(today);
    }

    // ===================== GETTERS =====================

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<CategoryState> getCategoryState() {
        return categoryState;
    }

    public LiveData<String> getAppsTimeFiltered() {
        return appsTimeFiltered;
    }

    public LiveData<TopAppsState> getTopAppsState() {
        return topAppsState;
    }

    public LiveData<SwitchesChartState> getSwitchesChartState() {
        return switchesChartState;
    }

    // ===================== LOADING =====================

    public void loadDay(int day) {
        Integer currentDay = selectedDay.getValue();
        if (currentDay == null || currentDay != day) {
            selectedDay.setValue(day);
        }
        loadAll(day);
    }

    private void loadAll(int day) {
        loadCategories(day);
        loadTopApps(day);
        loadSwitchesChart(day);
    }

    private void loadCategories(int day) {
        ioExecutor.execute(() -> {
            long totalForegroundMs = usageRepository.getTotalForegroundMsForDay(day);
            appsTimeFiltered.postValue(formatTimeKpi(totalForegroundMs));

            Map<String, Long> categoryTotalsMs = usageRepository.getCategoryTotalsMsForDay(day);

            long socialMs = get(categoryTotalsMs, "SOCIAL");
            long entertainmentMs = get(categoryTotalsMs, "ENTERTAINMENT");
            long messagingMs = get(categoryTotalsMs, "MESSAGING");
            long workMs = get(categoryTotalsMs, "WORK");
            long otherMs = get(categoryTotalsMs, "OTHER");

            long totalMs = socialMs + entertainmentMs + messagingMs + workMs + otherMs;

            int socialPct = 0;
            int entertainmentPct = 0;
            int messagingPct = 0;
            int workPct = 0;
            int otherPct = 0;

            if (totalMs > 0L) {
                socialPct = pct(socialMs, totalMs);
                entertainmentPct = pct(entertainmentMs, totalMs);
                messagingPct = pct(messagingMs, totalMs);
                workPct = pct(workMs, totalMs);

                otherPct = 100 - (socialPct + entertainmentPct + messagingPct + workPct);
                otherPct = clampPct(otherPct);
            }

            CategoryState out = new CategoryState(
                    formatTimeShort(socialMs),
                    formatTimeShort(entertainmentMs),
                    formatTimeShort(messagingMs),
                    formatTimeShort(workMs),
                    formatTimeShort(otherMs),
                    clampPct(socialPct),
                    clampPct(entertainmentPct),
                    clampPct(messagingPct),
                    clampPct(workPct),
                    otherPct
            );

            categoryState.postValue(out);
        });
    }

    private void loadTopApps(int day) {
        ioExecutor.execute(() -> {
            long totalFilteredMs = 0L;
            Map<String, Long> categoryTotalsMs = usageRepository.getCategoryTotalsMsForDay(day);
            for (Long value : categoryTotalsMs.values()) {
                totalFilteredMs += (value != null) ? value : 0L;
            }
            if (totalFilteredMs <= 0L) {
                totalFilteredMs = 1L;
            }

            List<UsageRepository.TopAppRow> topApps = usageRepository.getTopAppsByDate(day, 3);

            String name1 = "--";
            String name2 = "--";
            String name3 = "--";
            int pct1 = 0;
            int pct2 = 0;
            int pct3 = 0;

            Context context = getApplication();

            if (topApps.size() > 0) {
                name1 = resolveDisplayName(context, topApps.get(0).name);
                pct1 = pct(topApps.get(0).totalMs, totalFilteredMs);
            }
            if (topApps.size() > 1) {
                name2 = resolveDisplayName(context, topApps.get(1).name);
                pct2 = pct(topApps.get(1).totalMs, totalFilteredMs);
            }
            if (topApps.size() > 2) {
                name3 = resolveDisplayName(context, topApps.get(2).name);
                pct3 = pct(topApps.get(2).totalMs, totalFilteredMs);
            }

            int bar1 = clampPct(pct1);
            int bar2 = clampPct(pct2);
            int bar3 = clampPct(pct3);

            topAppsState.postValue(new TopAppsState(
                    name1, name2, name3,
                    pct1, pct2, pct3,
                    bar1, bar2, bar3
            ));
        });
    }

    private void loadSwitchesChart(int day) {
        ioExecutor.execute(() -> {
            int[] appSwitchCountByHour = userActivityRepository.getAppSwitchCountByHour(day);

            if (appSwitchCountByHour == null || appSwitchCountByHour.length != 24) {
                switchesChartState.postValue(new SwitchesChartState(new ArrayList<>(), 0));
                return;
            }

            List<Entry> entries = new ArrayList<>(25);

            int accumulated = 0;
            int maxY = 0;

            entries.add(new Entry(0f, 0f));

            for (int hour = 0; hour < 24; hour++) {
                accumulated += appSwitchCountByHour[hour];
                if (accumulated > maxY) {
                    maxY = accumulated;
                }

                entries.add(new Entry((float) (hour + 1), (float) accumulated));
            }

            switchesChartState.postValue(new SwitchesChartState(entries, maxY));
        });
    }

    // ===================== HELPERS =====================

    private static long get(Map<String, Long> map, String key) {
        Long value = (map != null) ? map.get(key) : null;
        return (value != null) ? value : 0L;
    }

    private static int pct(long value, long total) {
        return (int) Math.round(100.0 * value / (double) total);
    }

    private static int clampPct(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String formatTimeKpi(long ms) {
        long totalMin = ms / 60000L;
        long hours = totalMin / 60L;
        long minutes = totalMin % 60L;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.ROOT, "%dm", minutes);
    }

    private static String formatTimeShort(long ms) {
        if (ms <= 0L) return "0m";

        long totalSec = ms / 1000L;
        if (totalSec < 60L) return "<1m";

        long totalMin = (totalSec + 59L) / 60L;
        long hours = totalMin / 60L;
        long minutes = totalMin % 60L;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.ROOT, "%dm", minutes);
    }

    private static String resolveDisplayName(Context context, String rawName) {
        if (rawName == null) return "--";

        String value = rawName.trim();
        if (value.isEmpty()) return "--";

        if ("android".equalsIgnoreCase(value)) return "Sistema";

        boolean looksLikePackage = value.contains(".") && !value.contains(" ");
        if (looksLikePackage) {
            String label = AppCategoryResolver.resolveAppLabel(context, value);
            if (label != null) {
                label = label.trim();
                if (!label.isEmpty() && !label.equalsIgnoreCase(value)) {
                    return label;
                }
            }

            String[] parts = value.split("\\.");
            return parts[parts.length - 1];
        }

        return value;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdownNow();
    }
}