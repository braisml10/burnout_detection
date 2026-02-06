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
import com.example.burnout_app.data.entity.DailyAppMetricsEntity;
import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.entity.ScreenEventEntity;
import com.example.burnout_app.helpers.RetentionPolicy;
import com.example.burnout_app.helpers.TimeKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class DailyAggregationWorker extends Worker {

    private static final String TAG = "DailyAggregationWorker";

    private static final String PREFS = "burnout_runtime";
    private static final String KEY_LAST_USAGE_CAPTURE_TS = "last_usage_capture_ts";

    private static final long OVERLAP_MS = 60_000L;                 // 1 min
    private static final long FIRST_LOOKBACK_MS = 2 * 60 * 60_000L; // 2h

    // Screen state (tu entity: 1=ON, 0=OFF)
    private static final int SCREEN_ON = 1;
    private static final int SCREEN_OFF = 0;

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

        // 0) Asegurar fila diaria (NO pisa unlock_count porque IGNORE)
        db.userActivityDao().insertDailyIfMissing(
                new DailyMetricsEntity(today, 0L, 0, 0L, 0, 0, 0, 0, 0L)
        );

        // 1) Captura incremental de UsageStats
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
            if (appId <= 0) continue;

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

        // 3) Agregación de uso de apps (tu MVP)
        List<AppUsageEventEntity> events = db.usageDao().getUsageEventsByDate(today);

        long foregroundMs = 0L;
        int sessionCount = 0;
        int appSwitchCount = 0;
        HashSet<Long> uniqueApps = new HashSet<>();

        Map<Long, Long> fgMsByApp = new HashMap<>();
        Map<Long, Integer> openCountByApp = new HashMap<>();

        Long currentFgAppId = null;
        long openFgTs = -1L;

        for (AppUsageEventEntity e : events) {
            uniqueApps.add(e.app_id);
            Long lastFgAppId = null;

            if (e.event_type == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {

                if (currentFgAppId != null && openFgTs > 0 && e.timestamp > openFgTs) {
                    long delta = e.timestamp - openFgTs;

                    foregroundMs += delta;
                    fgMsByApp.put(currentFgAppId, fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);
                }

                if (currentFgAppId != null && e.app_id != currentFgAppId && currentFgAppId != lastFgAppId) {
                    appSwitchCount++;
                }
                lastFgAppId = e.app_id;


                sessionCount++;
                openCountByApp.put(e.app_id, openCountByApp.getOrDefault(e.app_id, 0) + 1);

                currentFgAppId = e.app_id;
                openFgTs = e.timestamp;

            } else if (e.event_type == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND) {

                if (currentFgAppId != null && e.app_id == currentFgAppId &&
                        openFgTs > 0 && e.timestamp > openFgTs) {

                    long delta = e.timestamp - openFgTs;

                    foregroundMs += delta;
                    fgMsByApp.put(currentFgAppId,
                            fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);

                    currentFgAppId = null;
                    openFgTs = -1L;
                }
            }
        }

        if (currentFgAppId != null && openFgTs > 0 && now > openFgTs) {
            long delta = now - openFgTs;

            foregroundMs += delta;
            fgMsByApp.put(currentFgAppId,
                    fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);
        }

        // 4) Screen aggregation: calcular screen_ms desde screen_event
        List<ScreenEventEntity> screenEvents = db.userActivityDao().getScreenEventsByDate(today);

        long startOfDay = TimeKey.startOfDayMs(now);
        long endOfDay = TimeKey.endOfDayMs(now);
        long dayUpperBound = Math.min(now, endOfDay);

        long screenMs = 0L;
        long openScreenTs = -1L;

        for (ScreenEventEntity se : screenEvents) {
            if (se.state == SCREEN_ON) {
                if (openScreenTs < 0) {
                    openScreenTs = se.timestamp;
                }
            } else if (se.state == SCREEN_OFF) {
                if (openScreenTs >= 0 && se.timestamp > openScreenTs) {
                    long clampedEnd = TimeKey.clampEnd(se.timestamp, dayUpperBound);
                    long clampedStart = Math.max(openScreenTs, startOfDay);

                    if (clampedEnd > clampedStart) {
                        screenMs += (clampedEnd - clampedStart);
                    }
                    openScreenTs = -1L;
                }
            }
        }

        if (openScreenTs >= 0) {
            long clampedEnd = TimeKey.clampEnd(dayUpperBound, dayUpperBound);
            long clampedStart = Math.max(openScreenTs, startOfDay);
            if (clampedEnd > clampedStart) {
                screenMs += (clampedEnd - clampedStart);
            }
        }

        // 5) Guardar daily_metrics (NO tocar unlock_count)
        DailyMetricsEntity cur = db.userActivityDao().getDailyMetricsByDate(today);
        if (cur != null) {
            cur.screen_ms = screenMs;
            cur.foreground_ms = foregroundMs;
            cur.session_count = sessionCount;
            cur.app_switch_count = appSwitchCount;
            cur.unique_apps_count = uniqueApps.size();

            db.userActivityDao().upsertDailyMetrics(cur);
        }

        Log.d(TAG, "Aggregate(today): screen_ms=" + screenMs +
                " foreground_ms=" + foregroundMs +
                " session_count=" + sessionCount +
                " app_switch_count=" + appSwitchCount +
                " unique_apps_count=" + uniqueApps.size() +
                " screenEvents=" + screenEvents.size());

        // 6) Guardar daily_app_metric (por app)
        ArrayList<DailyAppMetricsEntity> rows = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : fgMsByApp.entrySet()) {
            long appId = entry.getKey();
            long fg = entry.getValue();
            int openCount = openCountByApp.getOrDefault(appId, 0);

            rows.add(new DailyAppMetricsEntity(today, appId, fg, openCount, 0));
        }

        if (!rows.isEmpty()) {
            db.usageDao().upsertDailyAppMetrics(rows);
        }

        Log.d(TAG, "Aggregate(today): daily_app_metric rows=" + rows.size());

        // 7) Retención
        int delUsage = db.usageDao().deleteUsageEventsOlderThanDate(cutoffDate);
        int delScreen = db.userActivityDao().deleteScreenEventsOlderThanDate(cutoffDate);
        int delNotif = db.notificationDao().deleteNotificationEventsOlderThanDate(cutoffDate);

        Log.d(TAG, "Retention: cutoffDate=" + cutoffDate +
                " deletedUsage=" + delUsage +
                " deletedScreen=" + delScreen +
                " deletedNotif=" + delNotif);

        return Result.success();
    }

    private long resolveAppId(BurnoutDatabase db, String pkg) {
        Long existing = db.usageDao().getAppIdByPackageName(pkg);
        if (existing != null) return existing;

        long inserted = db.usageDao().insertApp(new AppEntity(
                pkg,
                pkg,
                null,
                false
        ));

        if (inserted > 0) return inserted;

        Long after = db.usageDao().getAppIdByPackageName(pkg);
        return (after != null) ? after : -1L;
    }
}
