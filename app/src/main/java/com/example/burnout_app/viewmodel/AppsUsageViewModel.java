package com.example.burnout_app.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.repo.UsageRepository;
import com.example.burnout_app.data.repo.UserActivityRepository;
import com.example.burnout_app.helpers.TimeKey;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppsUsageViewModel extends AndroidViewModel {

    // ---------------------------
    // KPI SUPERIORES (switches + unique)
    // ---------------------------
    public static class UiState {
        public final String appSwitches;
        public final String uniqueApps;

        public UiState(String appSwitches, String uniqueApps) {
            this.appSwitches = appSwitches;
            this.uniqueApps = uniqueApps;
        }
    }

    // ---------------------------
    // TIEMPO POR CATEGORÍA
    // ---------------------------
    public static class CategoryState {
        public final String socialTxt, entTxt, msgTxt, workTxt, otherTxt;
        public final int socialPct, entPct, msgPct, workPct, otherPct;

        public CategoryState(
                String socialTxt,
                String entTxt,
                String msgTxt,
                String workTxt,
                String otherTxt,
                int socialPct,
                int entPct,
                int msgPct,
                int workPct,
                int otherPct
        ) {
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

    // ---------------------------
    // TOP APPS (Top 3)
    // ---------------------------
    public static class TopAppsState {
        public final String name1, name2, name3;
        public final int pct1, pct2, pct3;
        public final int bar1, bar2, bar3;

        public TopAppsState(String name1, String name2, String name3,
                            int pct1, int pct2, int pct3,
                            int bar1, int bar2, int bar3) {
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

    // ---------------------------
    // SWITCHES CHART (acumulado 0..24)
    // ---------------------------
    public static class SwitchesChartState {
        public final List<Entry> entries;
        public final int maxY;

        public SwitchesChartState(List<Entry> entries, int maxY) {
            this.entries = entries;
            this.maxY = maxY;
        }
    }

    private final UserActivityRepository userRepo;
    private final UsageRepository usageRepo;

    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();

    private final LiveData<UiState> uiState;

    // KPI tiempo total (suma foreground_ms de todas las apps no ignoradas)
    private final MutableLiveData<String> appsTimeFiltered = new MutableLiveData<>();

    // Categorías
    private final MutableLiveData<CategoryState> categoryState = new MutableLiveData<>();

    // Top Apps
    private final MutableLiveData<TopAppsState> topAppsState = new MutableLiveData<>();

    // Switches chart
    private final MutableLiveData<SwitchesChartState> switchesChartState = new MutableLiveData<>();

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public AppsUsageViewModel(@NonNull Application app) {
        super(app);

        userRepo = new UserActivityRepository(app);
        usageRepo = new UsageRepository(app);

        // KPI superiores (switches + uniqueApps) salen de daily_metrics (agregado global)
        LiveData<DailyMetricsEntity> src = Transformations.switchMap(selectedDay, userRepo::observeDailyMetrics);

        uiState = Transformations.map(src, m -> {
            long fgMsAll   = (m != null) ? m.foreground_ms : 0L;       // debug: puede incluir ignoradas
            int switches  = (m != null) ? m.app_switch_count : 0;
            int uniqueApps = (m != null) ? m.unique_apps_count : 0;

            Log.d("KPI_TOTAL", "DailyMetrics.foreground_ms (ALL, incl ignored) = " + fgMsAll);

            return new UiState(
                    String.valueOf(switches),
                    String.valueOf(uniqueApps)
            );
        });

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        selectedDay.setValue(today);
        loadAll(today);
    }

    // ---------------------------
    // Getters
    // ---------------------------
    public LiveData<UiState> getUiState() { return uiState; }

    public LiveData<CategoryState> getCategoryState() { return categoryState; }

    public LiveData<String> getAppsTimeFiltered() { return appsTimeFiltered; }

    public LiveData<TopAppsState> getTopAppsState() { return topAppsState; }

    public LiveData<SwitchesChartState> getSwitchesChartState() { return switchesChartState; }

    // ---------------------------
    // Loading
    // ---------------------------
    public void loadDay(int day) {
        Integer cur = selectedDay.getValue();
        if (cur == null || cur != day) {
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
        io.execute(() -> {
            long totalMs = usageRepo.getTotalForegroundMsForDay(day);
            appsTimeFiltered.postValue(formatTimeKpi(totalMs));

            Map<String, Long> ms = usageRepo.getCategoryTotalsMsForDay(day);

            long social = get(ms, "SOCIAL");
            long ent    = get(ms, "ENTERTAINMENT");
            long msg    = get(ms, "MESSAGING");
            long work   = get(ms, "WORK");
            long other  = get(ms, "OTHER");

            long total = social + ent + msg + work + other;

            int pSocial = 0, pEnt = 0, pMsg = 0, pWork = 0, pOther = 0;

            if (total > 0L) {
                pSocial = pct(social, total);
                pEnt    = pct(ent, total);
                pMsg    = pct(msg, total);
                pWork   = pct(work, total);

                pOther  = 100 - (pSocial + pEnt + pMsg + pWork);
                pOther  = clampPct(pOther);
            }

            CategoryState out = new CategoryState(
                    formatTimeShort(social),
                    formatTimeShort(ent),
                    formatTimeShort(msg),
                    formatTimeShort(work),
                    formatTimeShort(other),
                    clampPct(pSocial),
                    clampPct(pEnt),
                    clampPct(pMsg),
                    clampPct(pWork),
                    pOther
            );

            Log.d("CAT_VM", "DAY " + day + " total=" + total + " pOther=" + pOther);

            categoryState.postValue(out);
        });
    }

    private void loadTopApps(int day) {
        io.execute(() -> {
            long totalFiltered = 0L;
            Map<String, Long> cat = usageRepo.getCategoryTotalsMsForDay(day);
            for (Long v : cat.values()) totalFiltered += (v != null) ? v : 0L;
            if (totalFiltered <= 0L) totalFiltered = 1L;

            List<UsageRepository.TopAppRow> rows = usageRepo.getTopAppsForDay(day, 3);

            String n1 = "--", n2 = "--", n3 = "--";
            int p1 = 0, p2 = 0, p3 = 0;

            if (rows.size() > 0) { n1 = safe(rows.get(0).name); p1 = pct(rows.get(0).totalMs, totalFiltered); }
            if (rows.size() > 1) { n2 = safe(rows.get(1).name); p2 = pct(rows.get(1).totalMs, totalFiltered); }
            if (rows.size() > 2) { n3 = safe(rows.get(2).name); p3 = pct(rows.get(2).totalMs, totalFiltered); }

            int b1 = clampPct(p1);
            int b2 = clampPct(p2);
            int b3 = clampPct(p3);

            topAppsState.postValue(new TopAppsState(n1, n2, n3, p1, p2, p3, b1, b2, b3));
        });
    }

    // ✅ switches acumulados 0..24
    private void loadSwitchesChart(int day) {
        io.execute(() -> {

            // ✅ hourly_metric -> UserActivityRepository (no UsageRepository)
            int[] perHour = userRepo.getSwitchesPerHourForDay(day);

            if (perHour == null || perHour.length != 24) {
                Log.d("SW_CHART", "perHour inválido (null o != 24). day=" + day);
                switchesChartState.postValue(new SwitchesChartState(new ArrayList<>(), 0));
                return;
            }

            List<Entry> entries = new ArrayList<>(25);

            int acc = 0;
            int max = 0;

            for (int h = 0; h < 24; h++) {
                acc += perHour[h];
                if (acc > max) max = acc;
                entries.add(new Entry((float) h, (float) acc));
            }

            entries.add(new Entry(24f, (float) acc));

            switchesChartState.postValue(new SwitchesChartState(entries, max));
        });
    }

    // ---------------------------
    // Helpers
    // ---------------------------
    private static String safe(String s) {
        if (s == null) return "--";
        String t = s.trim();
        return t.isEmpty() ? "--" : t;
    }

    private static long get(Map<String, Long> map, String key) {
        Long v = (map != null) ? map.get(key) : null;
        return (v != null) ? v : 0L;
    }

    private static int pct(long value, long total) {
        return (int) Math.round(100.0 * value / (double) total);
    }

    private static int clampPct(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static String formatTimeKpi(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;
        if (h > 0) return String.format(Locale.ROOT, "%dh %02dm", h, m);
        return String.format(Locale.ROOT, "%dm", m);
    }

    private static String formatTimeShort(long ms) {
        if (ms <= 0L) return "0m";
        long totalSec = ms / 1000L;
        if (totalSec < 60L) return "<1m";

        long totalMin = (totalSec + 59L) / 60L; // ceil
        long h = totalMin / 60L;
        long m = totalMin % 60L;

        if (h > 0) return String.format(Locale.ROOT, "%dh %02dm", h, m);
        return String.format(Locale.ROOT, "%dm", m);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdownNow();
    }
}
