package com.example.burnout_app.worker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.AppEntity;
import com.example.burnout_app.data.entity.AppUsageEventEntity;
import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.helpers.RetentionPolicy;
import com.example.burnout_app.helpers.TimeKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class DailyAggregationWorker extends Worker {

    private static final String TAG = "DailyAggregationWorker";

    private static final String PREFS = "burnout_runtime";
    private static final String KEY_LAST_USAGE_CAPTURE_TS = "last_usage_capture_ts";

    private static final long OVERLAP_MS = 60_000L;                 // 1 min
    private static final long FIRST_LOOKBACK_MS = 2 * 60 * 60_000L; // 2h

    public DailyAggregationWorker(@NonNull Context context,
                                  @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        BurnoutDatabase db = BurnoutDatabase.getInstance(ctx);

        long now = System.currentTimeMillis();
        int today = TimeKey.epochDayLocal(now);
        int cutoffDate = today - RetentionPolicy.RAW_EVENTS_RETENTION_DAYS;

        Log.d(TAG, "doWork() started today=" + today);

        if (!UsageStatsProvider.hasUsageAccess(ctx)) {
            Log.e(TAG, "No Usage Access -> skipping");
            return Result.success();
        }

        // 1) Captura incremental
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastTs = prefs.getLong(KEY_LAST_USAGE_CAPTURE_TS, -1L);

        long start = (lastTs > 0)
                ? Math.max(0L, lastTs - OVERLAP_MS)
                : Math.max(0L, now - FIRST_LOOKBACK_MS);

        long end = now;

        List<UsageStatsProvider.RawUsageEvent> raw =
                UsageStatsProvider.collectFgBgEvents(ctx, start, end);

        // 2) Raw -> Entity(app_id)
        List<AppUsageEventEntity> toInsert = new ArrayList<>(raw.size());

        for (UsageStatsProvider.RawUsageEvent r : raw) {
            long appId = resolveAppId(db, r.pkg);
            if (appId <= 0) continue; // si algo raro pasa, saltamos ese evento

            toInsert.add(new AppUsageEventEntity(
                    appId,
                    r.type,
                    r.ts,
                    "usm",
                    r.date
            ));
        }

        if (!toInsert.isEmpty()) {
            db.usageDao().insertUsageEvents(toInsert);
        }

        prefs.edit().putLong(KEY_LAST_USAGE_CAPTURE_TS, end).apply();

        Log.d(TAG, "Usage capture: inserted=" + toInsert.size() +
                " todayCount=" + db.usageDao().countUsageEventsByDate(today));

        // 3) Agregación MVP hoy -> daily_metrics
        List<AppUsageEventEntity> events = db.usageDao().getUsageEventsByDate(today);

        long foregroundMs = 0L;
        int sessionCount = 0;
        int appSwitchCount = 0;
        HashSet<Long> uniqueApps = new HashSet<>();

        long openFgTs = -1L;
        Long lastFgAppId = null;

        for (AppUsageEventEntity e : events) {
            uniqueApps.add(e.app_id);

            if (e.event_type == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                sessionCount++;

                if (lastFgAppId != null && e.app_id != lastFgAppId) {
                    appSwitchCount++;
                }

                lastFgAppId = e.app_id;
                openFgTs = e.timestamp;

            } else if (e.event_type == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND) {
                if (openFgTs > 0 && e.timestamp > openFgTs) {
                    foregroundMs += (e.timestamp - openFgTs);
                }
                openFgTs = -1L;
            }
        }

        if (openFgTs > 0 && now > openFgTs) {
            foregroundMs += (now - openFgTs);
        }

        // asegurar fila diaria
        db.userActivityDao().insertDailyIfMissing(
                new DailyMetricsEntity(today, 0L, 0, 0L, 0, 0, 0, 0, 0L)
        );

        DailyMetricsEntity cur = db.userActivityDao().getDailyMetricsByDate(today);
        if (cur != null) {
            cur.foreground_ms = foregroundMs;
            cur.session_count = sessionCount;
            cur.app_switch_count = appSwitchCount;
            cur.unique_apps_count = uniqueApps.size();

            db.userActivityDao().upsertDailyMetrics(cur);
        }

        Log.d(TAG, "Aggregate(today): foreground_ms=" + foregroundMs +
                " session_count=" + sessionCount +
                " app_switch_count=" + appSwitchCount +
                " unique_apps_count=" + uniqueApps.size());

        // 4) Retención (7 días)
        int delUsage = db.usageDao().deleteUsageEventsOlderThanDate(cutoffDate);
        int delScreen = db.userActivityDao().deleteScreenEventsOlderThanDate(cutoffDate);
        int delNotif = db.notificationDao().deleteNotificationEventsOlderThanDate(cutoffDate);

        Log.d(TAG, "Retention: cutoffDate=" + cutoffDate +
                " deletedUsage=" + delUsage +
                " deletedScreen=" + delScreen +
                " deletedNotif=" + delNotif);

        return Result.success();
    }

    /**
     * Resuelve package_name -> app_id usando SOLO UsageDAO.
     * Si no existe, inserta un AppEntity mínimo y reintenta lookup.
     */
    private long resolveAppId(BurnoutDatabase db, String pkg) {
        Long existing = db.usageDao().getAppIdByPackageName(pkg);
        if (existing != null) return existing;

        // Inserta app mínima
        long inserted = db.usageDao().insertApp(new AppEntity(
                pkg,
                pkg,        // name provisional = package_name (luego lo mejoras)
                null,       // category desconocida
                false       // is_ignored por defecto
        ));

        if (inserted > 0) return inserted;

        // Si insert IGNORE porque otro hilo ya la insertó, volvemos a buscar
        Long after = db.usageDao().getAppIdByPackageName(pkg);
        return (after != null) ? after : -1L;
    }
}
