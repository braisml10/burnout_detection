package gal.uvigo.burnout_app.worker;

import android.Manifest;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import gal.uvigo.burnout_app.collectors.UsageStatsProvider;
import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.AppEntity;
import gal.uvigo.burnout_app.data.entity.AppUsageEventEntity;
import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;
import gal.uvigo.burnout_app.data.entity.DailyAppMetricsEntity;
import gal.uvigo.burnout_app.data.entity.DailyCommMetricsEntity;
import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyCommMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyMetricsEntity;
import gal.uvigo.burnout_app.helpers.AppCategoryResolver;
import gal.uvigo.burnout_app.helpers.BurnoutRiskEngine;
import gal.uvigo.burnout_app.helpers.RetentionPolicy;
import gal.uvigo.burnout_app.helpers.TimeKey;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class DailyAggregationWorker extends Worker {

    private static final String TAG = "DailyAggregationWorker";
    private static final String TAG_COMM = "CommAgg";

    private static final String PREFS = "burnout_runtime";
    private static final String KEY_LAST_USAGE_CAPTURE_TS = "last_usage_capture_ts";
    private static final String KEY_SCREEN_IS_ON = "screen_is_on";
    private static final String KEY_LAST_UNLOCK_EVENT_TS = "last_unlock_event_ts";
    private static final String KEY_LAST_SCREEN_ACCOUNTED_TS = "last_screen_accounted_ts";
    private static final String KEY_LAST_RISK_COMPUTED_DAY = "last_risk_computed_day";

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;

    private static final long FIRST_LOOKBACK_MS = 2L * HOUR_MS;
    private static final long FIRST_RUN_BACKFILL_MS = 30L * MINUTE_MS;
    private static final long FG_DEBOUNCE_MS = 500L;
    private static final long COMM_YESTERDAY_GRACE_MS = 2L * HOUR_MS;

    private static final String PKG_SELF = "com.example.burnout_app";
    private static final String PKG_LAUNCHER = "com.android.launcher";

    public DailyAggregationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        try {

            Context ctx = getApplicationContext();
            BurnoutDatabase db = BurnoutDatabase.getInstance(ctx);
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            long now = System.currentTimeMillis();
            int today = TimeKey.epochDayLocal(now);
            int yesterday = today - 1;
            int tomorrow = today + 1;

            ensureDailyRows(db, yesterday, today, tomorrow);

            boolean hasUsage = UsageStatsProvider.hasUsageAccess(ctx);
            if (hasUsage) {
                runUsageAggregation(ctx, db, prefs, now, yesterday, today, tomorrow);
            } else {
                Log.w(TAG,
                        "No Usage Access -> skipping USAGE/SCREEN/NOTIF/HOURLY/RETENTION(usage), but COMM will run");
            }

            runCommunicationAggregation(ctx, db, now, yesterday, today);

            computeBurnoutRiskForClosedDay(db, prefs, yesterday);

            long durationMs = System.currentTimeMillis() - start;

            Log.d(TAG,
                    "================ doWork END ================ (" +
                            durationMs + " ms)");

            return Result.success();

        } catch (Exception e) {

            long durationMs = System.currentTimeMillis() - start;

            Log.e(TAG,
                    "Worker failed after " + durationMs + " ms",
                    e);

            return Result.failure();
        }
    }

    private void ensureDailyRows(BurnoutDatabase db, int yesterday, int today, int tomorrow) {
        db.userActivityDao().insertDailyMetricsIfMissing(new DailyMetricsEntity(yesterday, 0L, 0, 0L, 0, 0, 0, 0, 0L));
        db.userActivityDao().insertDailyMetricsIfMissing(new DailyMetricsEntity(today, 0L, 0, 0L, 0, 0, 0, 0, 0L));
        db.userActivityDao().insertDailyMetricsIfMissing(new DailyMetricsEntity(tomorrow, 0L, 0, 0L, 0, 0, 0, 0, 0L));
    }

    private void runUsageAggregation(Context ctx,
                                     BurnoutDatabase db,
                                     SharedPreferences prefs,
                                     long now,
                                     int yesterday,
                                     int today,
                                     int tomorrow) {

        int cutoffDate = today - RetentionPolicy.DATA_RETENTION_DAYS;

        UsageWindow usageWindow = initUsageWindow(prefs, now);
        List<UsageStatsProvider.RawEvent> rawAll = collectRawEvents(ctx, usageWindow.start, usageWindow.end);

        Map<String, Long> appIdCache = buildAppIdCache(db, ctx, rawAll);
        insertUsageEvents(db, prefs, rawAll, appIdCache, usageWindow.start, usageWindow.end, today);

        ForegroundAggResult fgResult = aggregateForegroundMetrics(db, today, now);
        ScreenAggResult screenResult = aggregateScreenUnlockAndNightMetrics(
                prefs,
                rawAll,
                usageWindow.start,
                usageWindow.end,
                yesterday,
                today,
                tomorrow,
                now
        );

        NotificationAggResult notifResult = loadNotificationMetrics(db, yesterday, today);

        db.runInTransaction(new Runnable() {
            @Override
            public void run() {
                saveDailyMetrics(db, yesterday, today, tomorrow, fgResult, screenResult, notifResult);
                saveDailyAppMetrics(db, today, fgResult.fgMsByApp, fgResult.openCountByApp);
                saveHourlyMetrics(db, yesterday, today, fgResult.switchByHour, screenResult, notifResult);
            }
        });

        runUsageRetention(db, cutoffDate);
    }

    private void runCommunicationAggregation(Context ctx,
                                             BurnoutDatabase db,
                                             long now,
                                             int yesterday,
                                             int today) {
        try {
            db.communicationDao().insertDailyCommMetricsIfMissing(yesterday);
            db.communicationDao().insertDailyCommMetricsIfMissing(today);

            CommAggResult todayResult = computeCommForDay(ctx, today);
            persistCommMetricsForDay(db, today, todayResult);

            long startOfTodayMs = TimeKey.startOfDayMsFromEpochDay(today);
            long graceEndMs = startOfTodayMs + COMM_YESTERDAY_GRACE_MS;

            if (now <= graceEndMs) {
                CommAggResult yesterdayResult = computeCommForDay(ctx, yesterday);
                persistCommMetricsForDay(db, yesterday, yesterdayResult);
                Log.d(TAG_COMM, "Saved comm metrics y=" + yesterday
                        + " calls=" + yesterdayResult.callsTotal
                        + " msgs=" + yesterdayResult.msgsTotal);
            } else {
                Log.d(TAG_COMM, "Skipping yesterday comm recompute (frozen) y=" + yesterday);
            }

            Log.d(TAG_COMM, "Saved comm metrics t=" + today
                    + " calls=" + todayResult.callsTotal
                    + " msgs=" + todayResult.msgsTotal);

        } catch (Exception ex) {
            Log.e(TAG_COMM, "Comm aggregation failed: " + ex.getMessage(), ex);
        }
    }

    private static class UsageWindow {
        long start;
        long end;
    }

    private UsageWindow initUsageWindow(SharedPreferences prefs, long now) {
        long lastTs = prefs.getLong(KEY_LAST_USAGE_CAPTURE_TS, -1L);
        boolean firstRunAfterClear = !prefs.contains(KEY_LAST_USAGE_CAPTURE_TS);

        if (firstRunAfterClear) {
            lastTs = now - FIRST_RUN_BACKFILL_MS;
            prefs.edit()
                    .putLong(KEY_LAST_USAGE_CAPTURE_TS, lastTs)
                    .putBoolean(KEY_SCREEN_IS_ON, false)
                    .putLong(KEY_LAST_UNLOCK_EVENT_TS, TimeKey.startOfDayMs(now))
                    .putLong(KEY_LAST_SCREEN_ACCOUNTED_TS, Math.max(TimeKey.startOfDayMs(now), lastTs))
                    .apply();
        }

        UsageWindow out = new UsageWindow();
        out.start = (lastTs > 0) ? (lastTs + 1) : Math.max(0L, now - FIRST_LOOKBACK_MS);
        out.end = now;
        return out;
    }

    private List<UsageStatsProvider.RawEvent> collectRawEvents(Context ctx, long start, long end) {
        List<UsageStatsProvider.RawEvent> rawAll = UsageStatsProvider.collectEvents(ctx, start, end);
        Log.d(TAG, "Raw events total=" + (rawAll != null ? rawAll.size() : 0));
        return (rawAll != null) ? rawAll : new ArrayList<UsageStatsProvider.RawEvent>();
    }

    private Map<String, Long> buildAppIdCache(BurnoutDatabase db,
                                              Context ctx,
                                              List<UsageStatsProvider.RawEvent> rawAll) {
        HashSet<String> uniquePkgs = new HashSet<>();
        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r != null && r.pkg != null) {
                uniquePkgs.add(r.pkg);
            }
        }

        Map<String, Long> appIdCache = new HashMap<>(Math.max(16, uniquePkgs.size() * 2));
        for (String pkg : uniquePkgs) {
            long appId = resolveAppId(db, ctx, pkg);
            if (appId > 0) {
                appIdCache.put(pkg, appId);
            }
        }

        return appIdCache;
    }

    private void insertUsageEvents(BurnoutDatabase db,
                                   SharedPreferences prefs,
                                   List<UsageStatsProvider.RawEvent> rawAll,
                                   Map<String, Long> appIdCache,
                                   long start,
                                   long end,
                                   int today) {

        HashSet<String> seen = new HashSet<>();
        List<AppUsageEventEntity> toInsert = new ArrayList<>();

        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r == null) continue;
            if (r.pkg == null) continue;
            if (r.ts < start || r.ts > end) continue;

            Long appIdObj = appIdCache.get(r.pkg);
            long appId = (appIdObj != null) ? appIdObj : -1L;
            if (appId <= 0) continue;

            String key = appId + "|" + r.type + "|" + r.ts;
            if (!seen.add(key)) continue;

            toInsert.add(new AppUsageEventEntity(appId, r.type, r.ts, "usm", r.date));
        }

        if (!toInsert.isEmpty()) {
            db.usageDao().insertUsageEvents(toInsert);
        }

        prefs.edit().putLong(KEY_LAST_USAGE_CAPTURE_TS, end).apply();

        Log.d(TAG, "Usage insert: inserted=" + toInsert.size()
                + " todayCount=" + db.usageDao().getUsageEventCountByDate(today));
    }

    private static class ForegroundAggResult {
        long foregroundMs;
        int fgSessionCount;
        int uniqueAppsWithRealFg;
        int appSwitchCount;

        Map<Long, Long> fgMsByApp = new HashMap<>();
        Map<Long, Integer> openCountByApp = new HashMap<>();
        int[] switchByHour = new int[24];
        ArrayList<String> switchDebug = new ArrayList<>();
        int eventsTodayCount;
    }

    private ForegroundAggResult aggregateForegroundMetrics(BurnoutDatabase db, int today, long now) {
        List<AppUsageEventEntity> eventsToday = db.usageDao().getUsageEventsByDate(today);

        ForegroundAggResult out = new ForegroundAggResult();
        out.eventsTodayCount = eventsToday.size();

        Long currentFgAppId = null;
        long openFgTs = -1L;
        long lastFgTs = -1L;

        long lastSwitchTs = -1L;
        Long lastRealFgAppId = null;
        long lastRealFgTs = -1L;

        final int SWITCH_DEBUG_LIMIT = 15;

        for (AppUsageEventEntity e : eventsToday) {

            if (e.event_type == UsageEvents.Event.MOVE_TO_FOREGROUND) {

                if (currentFgAppId != null
                        && currentFgAppId.equals(e.app_id)
                        && lastFgTs > 0
                        && (e.timestamp - lastFgTs) < FG_DEBOUNCE_MS) {
                    lastFgTs = e.timestamp;
                    continue;
                }

                String toPkg = safePkg(db, e.app_id);
                if (!isNoisePackage(toPkg)) {
                    if (lastRealFgAppId == null) {
                        lastRealFgAppId = e.app_id;
                        lastRealFgTs = e.timestamp;
                    } else if (!lastRealFgAppId.equals(e.app_id)) {

                        if (lastSwitchTs <= 0 || (e.timestamp - lastSwitchTs) >= FG_DEBOUNCE_MS) {
                            out.appSwitchCount++;
                            lastSwitchTs = e.timestamp;

                            int h = hourOfTsLocal(e.timestamp);
                            if (h >= 0 && h <= 23) {
                                out.switchByHour[h]++;
                            }

                            if (out.switchDebug.size() < SWITCH_DEBUG_LIMIT) {
                                String fromPkg = safePkg(db, lastRealFgAppId);
                                out.switchDebug.add("FG(real)->FG(real) " + fromPkg + " -> " + toPkg
                                        + " @ " + e.timestamp + " (gap=" + (e.timestamp - lastRealFgTs) + "ms)");
                            }
                        }

                        lastRealFgAppId = e.app_id;
                        lastRealFgTs = e.timestamp;
                    } else {
                        lastRealFgTs = e.timestamp;
                    }
                }

                if (currentFgAppId != null && openFgTs > 0 && e.timestamp > openFgTs) {
                    long delta = e.timestamp - openFgTs;
                    out.foregroundMs += delta;
                    out.fgMsByApp.put(currentFgAppId, out.fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);
                }

                out.fgSessionCount++;
                out.openCountByApp.put(e.app_id, out.openCountByApp.getOrDefault(e.app_id, 0) + 1);

                currentFgAppId = e.app_id;
                openFgTs = e.timestamp;
                lastFgTs = e.timestamp;

            } else if (e.event_type == UsageEvents.Event.MOVE_TO_BACKGROUND) {

                if (currentFgAppId != null
                        && e.app_id == currentFgAppId
                        && openFgTs > 0
                        && e.timestamp > openFgTs) {

                    long delta = e.timestamp - openFgTs;
                    out.foregroundMs += delta;
                    out.fgMsByApp.put(currentFgAppId, out.fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);

                    currentFgAppId = null;
                    openFgTs = -1L;
                }
            }
        }

        if (currentFgAppId != null && openFgTs > 0 && now > openFgTs) {
            long delta = now - openFgTs;
            out.foregroundMs += delta;
            out.fgMsByApp.put(currentFgAppId, out.fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);
        }

        int uniqueAppsWithRealFg = 0;
        for (long ms : out.fgMsByApp.values()) {
            if (ms > 0) uniqueAppsWithRealFg++;
        }
        out.uniqueAppsWithRealFg = uniqueAppsWithRealFg;

        Log.d(TAG, "FG agg: foregroundMs=" + out.foregroundMs
                + " fgSessionCount=" + out.fgSessionCount
                + " uniqueAppsWithRealFg=" + out.uniqueAppsWithRealFg
                + " eventsToday=" + out.eventsTodayCount);

        Log.d(TAG, "SWITCH agg (real FG changes): appSwitchCount=" + out.appSwitchCount
                + " debugSeq=" + out.switchDebug);

        return out;
    }

    private static class ScreenAggResult {
        long screenMsDeltaPrev;
        long screenMsDeltaToday;

        int unlockDelta;

        long nightDeltaToday;
        long nightDeltaTomorrow;

        long[] screenMsByHourPrev = new long[24];
        long[] screenMsByHourToday = new long[24];

        int[] unlockByHourToday = new int[24];
        int[] unlockByHourPrev = new int[24];
    }

    private ScreenAggResult aggregateScreenUnlockAndNightMetrics(SharedPreferences prefs,
                                                                 List<UsageStatsProvider.RawEvent> rawAll,
                                                                 long start,
                                                                 long end,
                                                                 int yesterday,
                                                                 int today,
                                                                 int tomorrow,
                                                                 long now) {

        long nightStartEndingToday = atLocalHourMs(yesterday, 22, 0);
        long nightEndEndingToday = atLocalHourMs(today, 6, 0);

        long nightStartEndingTomorrow = atLocalHourMs(today, 22, 0);
        long nightEndEndingTomorrow = atLocalHourMs(tomorrow, 6, 0);

        long startOfDayToday = TimeKey.startOfDayMs(now);
        long startOfDayYesterday = TimeKey.startOfDayMsFromEpochDay(yesterday);
        long endOfDayYesterday = startOfDayToday;
        long endOfDayToday = startOfDayToday + DAY_MS;

        boolean screenIsOn = prefs.getBoolean(KEY_SCREEN_IS_ON, false);
        long lastUnlockEventTs = prefs.getLong(KEY_LAST_UNLOCK_EVENT_TS, start);

        long lastAccountedTs = prefs.getLong(KEY_LAST_SCREEN_ACCOUNTED_TS, start);
        if (lastAccountedTs < start) lastAccountedTs = start;
        if (lastAccountedTs > end) lastAccountedTs = end;

        ArrayList<UsageStatsProvider.RawEvent> sys = new ArrayList<>();
        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r == null) continue;
            if (r.pkg != null) continue;
            if (r.ts < start || r.ts > end) continue;
            sys.add(r);
        }

        Collections.sort(sys, new Comparator<UsageStatsProvider.RawEvent>() {
            @Override
            public int compare(UsageStatsProvider.RawEvent a, UsageStatsProvider.RawEvent b) {
                return Long.compare(a.ts, b.ts);
            }
        });

        ScreenAggResult out = new ScreenAggResult();
        long cursor = lastAccountedTs;

        for (UsageStatsProvider.RawEvent r : sys) {

            if (r.ts > cursor && screenIsOn) {
                long segStart = cursor;
                long segEnd = r.ts;

                long prevPart = overlapMs(segStart, segEnd, startOfDayYesterday, endOfDayYesterday);
                long todayPart = overlapMs(segStart, segEnd, startOfDayToday, endOfDayToday);

                out.screenMsDeltaPrev += prevPart;
                out.screenMsDeltaToday += todayPart;

                if (segStart < endOfDayYesterday) {
                    addScreenSegmentToHours(out.screenMsByHourPrev, yesterday, segStart, Math.min(segEnd, endOfDayYesterday));
                }
                if (segEnd > startOfDayToday) {
                    addScreenSegmentToHours(out.screenMsByHourToday, today, Math.max(segStart, startOfDayToday), segEnd);
                }

                out.nightDeltaToday += overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
                out.nightDeltaTomorrow += overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);
            }

            cursor = Math.max(cursor, r.ts);

            if (r.type == UsageEvents.Event.SCREEN_INTERACTIVE) {
                screenIsOn = true;

            } else if (r.type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                screenIsOn = false;

            } else if (r.type == UsageEvents.Event.KEYGUARD_HIDDEN) {
                if (r.ts > lastUnlockEventTs) {
                    out.unlockDelta++;
                    lastUnlockEventTs = Math.max(lastUnlockEventTs, r.ts);

                    int h = hourOfTsLocal(r.ts);
                    int d = TimeKey.epochDayLocal(r.ts);
                    if (h >= 0 && h <= 23) {
                        if (d == today) out.unlockByHourToday[h]++;
                        else if (d == yesterday) out.unlockByHourPrev[h]++;
                    }
                }
            }
        }

        if (end > cursor && screenIsOn) {
            long segStart = cursor;
            long segEnd = end;

            long prevPart = overlapMs(segStart, segEnd, startOfDayYesterday, endOfDayYesterday);
            long todayPart = overlapMs(segStart, segEnd, startOfDayToday, endOfDayToday);

            out.screenMsDeltaPrev += prevPart;
            out.screenMsDeltaToday += todayPart;

            if (segStart < endOfDayYesterday) {
                addScreenSegmentToHours(out.screenMsByHourPrev, yesterday, segStart, Math.min(segEnd, endOfDayYesterday));
            }
            if (segEnd > startOfDayToday) {
                addScreenSegmentToHours(out.screenMsByHourToday, today, Math.max(segStart, startOfDayToday), segEnd);
            }

            out.nightDeltaToday += overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
            out.nightDeltaTomorrow += overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);
        }

        prefs.edit()
                .putBoolean(KEY_SCREEN_IS_ON, screenIsOn)
                .putLong(KEY_LAST_UNLOCK_EVENT_TS, lastUnlockEventTs)
                .putLong(KEY_LAST_SCREEN_ACCOUNTED_TS, end)
                .apply();

        return out;
    }

    private static class NotificationAggResult {
        int[] notifByHourToday = new int[24];
        int[] notifByHourPrev = new int[24];
        int notifTotalToday;
        int notifTotalPrev;
    }

    private NotificationAggResult loadNotificationMetrics(BurnoutDatabase db, int yesterday, int today) {
        NotificationAggResult out = new NotificationAggResult();

        Cursor cToday = null;
        try {
            cToday = db.notificationDao().getNotificationCountByHourCursor(today);
            if (cToday != null) {
                int iHour = cToday.getColumnIndex("hour");
                int iC = cToday.getColumnIndex("c");
                while (cToday.moveToNext()) {
                    int h = cToday.getInt(iHour);
                    int cnt = cToday.getInt(iC);
                    if (h >= 0 && h <= 23) {
                        out.notifByHourToday[h] = cnt;
                    }
                }
            }
        } finally {
            if (cToday != null) cToday.close();
        }

        Cursor cPrev = null;
        try {
            cPrev = db.notificationDao().getMessagingNotificationCountByHourCursor(yesterday);
            if (cPrev != null) {
                int iHour = cPrev.getColumnIndex("hour");
                int iC = cPrev.getColumnIndex("c");
                while (cPrev.moveToNext()) {
                    int h = cPrev.getInt(iHour);
                    int cnt = cPrev.getInt(iC);
                    if (h >= 0 && h <= 23) {
                        out.notifByHourPrev[h] = cnt;
                    }
                }
            }
        } finally {
            if (cPrev != null) cPrev.close();
        }

        out.notifTotalToday = db.notificationDao().getNotificationCountByDate(today);
        out.notifTotalPrev = db.notificationDao().getNotificationCountByDate(yesterday);

        return out;
    }

    private void saveDailyMetrics(BurnoutDatabase db,
                                  int yesterday,
                                  int today,
                                  int tomorrow,
                                  ForegroundAggResult fgResult,
                                  ScreenAggResult screenResult,
                                  NotificationAggResult notifResult) {

        DailyMetricsEntity yesterdayMetrics = db.userActivityDao().getDailyMetricsByDate(yesterday);
        DailyMetricsEntity todayMetrics = db.userActivityDao().getDailyMetricsByDate(today);
        DailyMetricsEntity tomorrowMetrics = db.userActivityDao().getDailyMetricsByDate(tomorrow);

        if (yesterdayMetrics != null) {
            if (screenResult.screenMsDeltaPrev > 0) {
                yesterdayMetrics.screen_ms = Math.max(0L, yesterdayMetrics.screen_ms + screenResult.screenMsDeltaPrev);
            }
            yesterdayMetrics.notification_count = Math.max(0, notifResult.notifTotalPrev);
            db.userActivityDao().upsertDailyMetrics(yesterdayMetrics);
        }

        if (todayMetrics != null) {
            todayMetrics.screen_ms = Math.max(0L, todayMetrics.screen_ms + screenResult.screenMsDeltaToday);

            todayMetrics.unlock_count = Math.max(0, todayMetrics.unlock_count + screenResult.unlockDelta);
            todayMetrics.session_count = todayMetrics.unlock_count;

            todayMetrics.foreground_ms = fgResult.foregroundMs;
            todayMetrics.unique_apps_count = fgResult.uniqueAppsWithRealFg;
            todayMetrics.app_switch_count = fgResult.appSwitchCount;

            todayMetrics.night_ms = Math.max(0L, todayMetrics.night_ms + screenResult.nightDeltaToday);
            todayMetrics.notification_count = Math.max(0, notifResult.notifTotalToday);

            db.userActivityDao().upsertDailyMetrics(todayMetrics);
        }

        if (screenResult.nightDeltaTomorrow > 0 && tomorrowMetrics != null) {
            tomorrowMetrics.night_ms = Math.max(0L, tomorrowMetrics.night_ms + screenResult.nightDeltaTomorrow);
            db.userActivityDao().upsertDailyMetrics(tomorrowMetrics);
        }
    }

    private void saveDailyAppMetrics(BurnoutDatabase db,
                                     int today,
                                     Map<Long, Long> fgMsByApp,
                                     Map<Long, Integer> openCountByApp) {
        ArrayList<DailyAppMetricsEntity> rows = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : fgMsByApp.entrySet()) {
            long appId = entry.getKey();
            long fg = entry.getValue();
            int openCount = openCountByApp.getOrDefault(appId, 0);

            if (fg > 0) {
                rows.add(new DailyAppMetricsEntity(today, appId, fg, openCount, 0));
            }
        }

        if (!rows.isEmpty()) {
            db.usageDao().upsertDailyAppMetrics(rows);
        }
    }

    private void saveHourlyMetrics(BurnoutDatabase db,
                                   int yesterday,
                                   int today,
                                   int[] switchByHour,
                                   ScreenAggResult screenResult,
                                   NotificationAggResult notifResult) {
        persistHourly(db, yesterday,
                screenResult.screenMsByHourPrev,
                screenResult.unlockByHourPrev,
                notifResult.notifByHourPrev,
                null);

        persistHourly(db, today,
                screenResult.screenMsByHourToday,
                screenResult.unlockByHourToday,
                notifResult.notifByHourToday,
                switchByHour);
    }

    private void runUsageRetention(BurnoutDatabase db, int cutoffDate) {
        int delUsage = db.usageDao().deleteUsageEventsOlderThanDate(cutoffDate);
        int delDailyApp = db.usageDao().deleteDailyAppMetricsOlderThanDate(cutoffDate);

        int delDailyMetrics = db.userActivityDao().deleteDailyMetricsOlderThanDate(cutoffDate);
        int delHourly = db.userActivityDao().deleteHourlyMetricsOlderThanDate(cutoffDate);

        int delNotif = db.notificationDao().deleteNotificationEventsOlderThanDate(cutoffDate);

        int delDailyComm = db.communicationDao().deleteDailyCommMetricsOlderThanDate(cutoffDate);
        int delHourlyComm = db.communicationDao().deleteHourlyCommMetricsOlderThanDate(cutoffDate);

        int delRisk = db.burnoutRiskDao().deleteBurnoutRiskOlderThanDate(cutoffDate);

        Log.d(TAG, "Retention: cutoffDate=" + cutoffDate
                + " deletedUsage=" + delUsage
                + " deletedDailyApp=" + delDailyApp
                + " deletedDailyMetrics=" + delDailyMetrics
                + " deletedHourly=" + delHourly
                + " deletedNotif=" + delNotif
                + " deletedDailyComm=" + delDailyComm
                + " deletedHourlyComm=" + delHourlyComm
                + " deletedRisk=" + delRisk);
    }

    private void persistHourly(BurnoutDatabase db,
                               int epochDay,
                               long[] screenMsByHour,
                               int[] unlockByHour,
                               int[] notifByHour,
                               int[] switchByHour) {
        try {
            List<HourlyMetricsEntity> existing = db.userActivityDao().getHourlyMetricsByDate(epochDay);
            HourlyMetricsEntity[] byHour = new HourlyMetricsEntity[24];

            if (existing != null) {
                for (HourlyMetricsEntity h : existing) {
                    if (h != null && h.hour >= 0 && h.hour <= 23) {
                        byHour[h.hour] = h;
                    }
                }
            }

            ArrayList<HourlyMetricsEntity> out = new ArrayList<>(24);

            for (int h = 0; h < 24; h++) {
                HourlyMetricsEntity cur = byHour[h];

                long baseScreen = (cur != null) ? cur.screen_ms : 0L;
                int baseUnlock = (cur != null) ? cur.unlock_count : 0;
                int baseSwitch = (cur != null) ? cur.app_switch_count : 0;
                int baseUnique = (cur != null) ? cur.unique_apps_count : 0;
                int baseNotif = (cur != null) ? cur.notification_count : 0;

                long addScreen = (screenMsByHour != null && screenMsByHour.length == 24) ? screenMsByHour[h] : 0L;
                int addUnlock = (unlockByHour != null && unlockByHour.length == 24) ? unlockByHour[h] : 0;

                int newNotif = (notifByHour != null && notifByHour.length == 24) ? notifByHour[h] : baseNotif;
                int addSwitch = (switchByHour != null && switchByHour.length == 24) ? switchByHour[h] : 0;
                int newSwitch = (switchByHour == null) ? baseSwitch : addSwitch;

                out.add(new HourlyMetricsEntity(
                        epochDay,
                        h,
                        Math.max(0L, baseScreen + addScreen),
                        Math.max(0, baseUnlock + addUnlock),
                        Math.max(0, newNotif),
                        Math.max(0, newSwitch),
                        Math.max(0, baseUnique)
                ));
            }

            db.userActivityDao().upsertHourlyMetrics(out);

        } catch (Exception ex) {
            Log.e(TAG, "[HOURLY] persist failed: " + ex.getMessage(), ex);
        }
    }

    private void addScreenSegmentToHours(long[] out24, int epochDay, long segStart, long segEnd) {
        if (out24 == null || out24.length != 24) return;
        if (segEnd <= segStart) return;

        long dayStart = TimeKey.startOfDayMsFromEpochDay(epochDay);
        long dayEnd = dayStart + DAY_MS;

        long s = Math.max(segStart, dayStart);
        long e = Math.min(segEnd, dayEnd);
        if (e <= s) return;

        for (int h = 0; h < 24; h++) {
            long hs = atLocalHourMs(epochDay, h, 0);
            long he = hs + HOUR_MS;
            long add = overlapMs(s, e, hs, he);
            if (add > 0) out24[h] += add;
        }
    }

    private int hourOfTsLocal(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        return cal.get(Calendar.HOUR_OF_DAY);
    }

    private String safePkg(BurnoutDatabase db, long appId) {
        try {
            String pkg = db.usageDao().getAppPackageNameByAppId(appId);
            return (pkg != null) ? pkg : ("appId=" + appId);
        } catch (Exception ex) {
            return "appId=" + appId;
        }
    }

    private boolean isNoisePackage(String pkg) {
        if (pkg == null) return true;

        if (pkg.equals(PKG_SELF)) return true;

        if (pkg.contains("launcher")) return true;
        if (pkg.contains("quickstep")) return true;

        if (pkg.equals("com.android.systemui")) return true;

        if (pkg.startsWith("com.android.")) return true;

        if (pkg.startsWith("com.google.android.gms")) return true;
        if (pkg.contains("dynamite")) return true;

        if (pkg.contains("tachyon")) return true;
        if (pkg.contains("googlequicksearchbox")) return true;

        return false;
    }

    private long atLocalHourMs(int epochDay, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(TimeKey.startOfDayMsFromEpochDay(epochDay));

        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTimeInMillis();
    }

    private static long overlapMs(long aStart, long aEnd, long bStart, long bEnd) {
        long s = Math.max(aStart, bStart);
        long e = Math.min(aEnd, bEnd);
        return Math.max(0L, e - s);
    }

    private boolean hasPermission(Context ctx, String perm) {
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private static class CommAggResult {
        int callsTotal;
        long callsDurationMs;

        int[] callsByHour = new int[24];
        long[] callsDurationByHourMs = new long[24];

        int smsTotal;
        int[] smsByHour = new int[24];

        int msgNotifsTotal;
        int[] msgNotifsByHour = new int[24];

        int msgsTotal;
        int[] msgsByHour = new int[24];
    }

    private CommAggResult computeCommForDay(Context ctx, int epochDay) {
        CommAggResult out = new CommAggResult();

        long startMs = TimeKey.startOfDayMsFromEpochDay(epochDay);
        long endMs = startMs + DAY_MS;

        if (hasPermission(ctx, Manifest.permission.READ_CALL_LOG)) {
            Cursor c = null;
            try {
                String[] proj = new String[]{
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION
                };
                String sel = CallLog.Calls.DATE + ">=? AND " + CallLog.Calls.DATE + "<?";
                String[] args = new String[]{String.valueOf(startMs), String.valueOf(endMs)};

                c = ctx.getContentResolver().query(CallLog.Calls.CONTENT_URI, proj, sel, args, null);
                if (c != null) {
                    int iDate = c.getColumnIndex(CallLog.Calls.DATE);
                    int iDur = c.getColumnIndex(CallLog.Calls.DURATION);

                    while (c.moveToNext()) {
                        long ts = c.getLong(iDate);
                        long durSec = c.getLong(iDur);
                        long durMs = Math.max(0L, durSec) * 1000L;

                        out.callsTotal++;
                        out.callsDurationMs += durMs;

                        int h = TimeKey.hourOfDayLocal(ts);
                        if (h >= 0 && h <= 23) {
                            out.callsByHour[h]++;
                            out.callsDurationByHourMs[h] += durMs;
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG_COMM, "CallLog query failed: " + ex.getMessage(), ex);
            } finally {
                if (c != null) c.close();
            }
        } else {
            Log.w(TAG_COMM, "READ_CALL_LOG not granted -> calls=0");
        }

        if (hasPermission(ctx, Manifest.permission.READ_SMS)) {
            Cursor c = null;
            try {
                String[] proj = new String[]{Telephony.Sms.DATE};
                String sel = Telephony.Sms.DATE + ">=? AND " + Telephony.Sms.DATE + "<?";
                String[] args = new String[]{String.valueOf(startMs), String.valueOf(endMs)};

                c = ctx.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI, proj, sel, args, null);
                if (c != null) {
                    int iDate = c.getColumnIndex(Telephony.Sms.DATE);
                    while (c.moveToNext()) {
                        long ts = c.getLong(iDate);
                        out.smsTotal++;

                        int h = TimeKey.hourOfDayLocal(ts);
                        if (h >= 0 && h <= 23) {
                            out.smsByHour[h]++;
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG_COMM, "SMS query failed: " + ex.getMessage(), ex);
            } finally {
                if (c != null) c.close();
            }
        } else {
            Log.w(TAG_COMM, "READ_SMS not granted -> sms=0");
        }

        return out;
    }

    private long getMessagingMsForDay(BurnoutDatabase db, int epochDay) {
        long ms = 0L;
        Cursor c = null;
        try {
            c = db.usageDao().getCategoryTotalsMsForDayCursor(epochDay);
            if (c == null) return 0L;

            int iCat = c.getColumnIndexOrThrow("category");
            int iMs = c.getColumnIndexOrThrow("total_ms");

            while (c.moveToNext()) {
                String cat = c.getString(iCat);
                if ("MESSAGING".equalsIgnoreCase(cat)) {
                    ms = c.getLong(iMs);
                    break;
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return Math.max(0L, ms);
    }

    private void persistCommMetricsForDay(BurnoutDatabase db, int epochDay, CommAggResult r) {
        try {
            db.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    db.communicationDao().insertDailyCommMetricsIfMissing(epochDay);

                    long messagingMs = getMessagingMsForDay(db, epochDay);

                    int[] msgNotifsByHour = new int[24];
                    int msgNotifsTotal = 0;

                    Cursor c = null;
                    try {
                        c = db.notificationDao().getMessagingNotificationCountByHourCursor(epochDay);
                        if (c != null) {
                            int iHour = c.getColumnIndex("hour");
                            int iC = c.getColumnIndex("c");

                            while (c.moveToNext()) {
                                int h = c.getInt(iHour);
                                int cnt = c.getInt(iC);
                                if (h >= 0 && h <= 23) {
                                    msgNotifsByHour[h] = cnt;
                                    msgNotifsTotal += cnt;
                                }
                            }
                        }
                    } finally {
                        if (c != null) c.close();
                    }

                    int msgNotifsTotalCheck = db.notificationDao().getMessagingNotificationCountByDate(epochDay);
                    msgNotifsTotal = Math.max(msgNotifsTotal, msgNotifsTotalCheck);

                    r.msgNotifsTotal = msgNotifsTotal;
                    r.msgNotifsByHour = msgNotifsByHour;

                    int msgsTotal = Math.max(0, r.smsTotal) + Math.max(0, r.msgNotifsTotal);
                    int[] msgsByHour = new int[24];
                    for (int h = 0; h < 24; h++) {
                        msgsByHour[h] = Math.max(0, r.smsByHour[h]) + Math.max(0, r.msgNotifsByHour[h]);
                    }

                    r.msgsTotal = msgsTotal;
                    r.msgsByHour = msgsByHour;

                    long voiceMs = Math.max(0L, r.callsDurationMs);
                    long textMs = Math.max(0L, messagingMs);
                    long totalMs = voiceMs + textMs;

                    DailyCommMetricsEntity daily = new DailyCommMetricsEntity(
                            epochDay,
                            Math.max(0, r.callsTotal),
                            Math.max(0, r.msgsTotal),
                            totalMs,
                            voiceMs,
                            textMs
                    );
                    db.communicationDao().upsertDailyCommMetrics(daily);

                    long sumMsgs = 0L;
                    for (int h = 0; h < 24; h++) {
                        sumMsgs += Math.max(0, r.msgsByHour[h]);
                    }

                    ArrayList<HourlyCommMetricsEntity> rows = new ArrayList<>(24);
                    for (int h = 0; h < 24; h++) {
                        long voiceVal = (r.callsDurationByHourMs != null && r.callsDurationByHourMs.length == 24)
                                ? Math.max(0L, r.callsDurationByHourMs[h])
                                : 0L;

                        long textVal = 0L;
                        if (sumMsgs > 0) {
                            textVal = (messagingMs * Math.max(0, r.msgsByHour[h])) / sumMsgs;
                        }

                        long totalVal = voiceVal + textVal;
                        rows.add(new HourlyCommMetricsEntity(epochDay, h, totalVal, voiceVal, textVal));
                    }

                    db.communicationDao().upsertHourlyCommMetrics(rows);

                    Log.d(TAG_COMM, "COMM day=" + epochDay
                            + " voiceMs=" + voiceMs
                            + " textMs=" + textMs
                            + " msgsTotal=" + msgsTotal
                            + " (sms=" + r.smsTotal + " + msgNotifs=" + r.msgNotifsTotal + ")");
                }
            });

        } catch (Exception ex) {
            Log.e(TAG_COMM, "persistCommMetricsForDay failed: " + ex.getMessage(), ex);
        }
    }

    private long resolveAppId(BurnoutDatabase db, Context ctx, String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return -1L;

        boolean mustIgnore = shouldIgnorePkg(pkg);

        Long existing = db.usageDao().getAppIdByPackageName(pkg);
        if (existing != null) {

            if (mustIgnore) {
                db.usageDao().updateAppIgnoredByAppId(existing, true);
                return existing;
            }

            String curName = db.usageDao().getAppNameByAppId(existing);
            String curCat = db.usageDao().getAppCategoryByAppId(existing);

            boolean needsName = (curName == null || curName.trim().isEmpty() || curName.equals(pkg) || looksLikePackage(curName));
            boolean needsCat = (curCat == null || curCat.trim().isEmpty() || "OTHER".equalsIgnoreCase(curCat));

            if (needsName) {
                String label = AppCategoryResolver.resolveAppLabel(ctx, pkg);
                if (label != null) label = label.trim();
                if (label != null && !label.isEmpty() && !label.equals(pkg)) {
                    db.usageDao().updateAppNameByAppId(existing, label);
                }
            }

            if (needsCat) {
                String cat = AppCategoryResolver.resolveCategory(ctx, pkg);
                if (cat == null) cat = "OTHER";
                db.usageDao().updateAppCategoryByAppId(existing, cat);
            }

            return existing;
        }

        String label = AppCategoryResolver.resolveAppLabel(ctx, pkg);
        if (label != null) label = label.trim();
        if (label == null || label.isEmpty()) label = pkg;

        String cat = mustIgnore ? "OTHER" : AppCategoryResolver.resolveCategory(ctx, pkg);
        if (cat == null) cat = "OTHER";

        long inserted = db.usageDao().insertApp(new AppEntity(pkg, label, cat, mustIgnore));
        if (inserted > 0) return inserted;

        Long after = db.usageDao().getAppIdByPackageName(pkg);
        return (after != null) ? after : -1L;
    }

    private static boolean looksLikePackage(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;

        if (t.contains(" ")) return false;
        if (!t.contains(".")) return false;

        return t.startsWith("com.")
                || t.startsWith("org.")
                || t.startsWith("net.")
                || t.startsWith("io.")
                || t.startsWith("android")
                || t.startsWith("com.android")
                || t.startsWith("com.google");
    }

    private static boolean shouldIgnorePkg(String pkg) {
        if (pkg == null) return false;
        return PKG_SELF.equals(pkg) || PKG_LAUNCHER.equals(pkg);
    }

    private void computeBurnoutRiskForClosedDay(BurnoutDatabase db,
                                                SharedPreferences prefs,
                                                int targetDay) {
        try {
            int lastComputedDay = prefs.getInt(KEY_LAST_RISK_COMPUTED_DAY, -1);

            if (lastComputedDay >= targetDay) {
                Log.d(TAG, "Risk skip: already computed for day=" + targetDay);
                return;
            }

            DailyMetricsEntity targetMetrics = db.userActivityDao().getDailyMetricsByDate(targetDay);

            if (targetMetrics == null) {
                Log.d(TAG, "Risk skip: no DailyMetrics for day=" + targetDay);
                return;
            }

            List<DailyMetricsEntity> baselineDays =
                    db.userActivityDao().getDailyMetricsRange(targetDay - 7, targetDay - 1);

            if (baselineDays == null) {
                baselineDays = new ArrayList<>();
            }

            BurnoutRiskEngine engine = new BurnoutRiskEngine();

            BurnoutRiskEntity risk = engine.evaluate(
                    targetDay,
                    targetMetrics,
                    baselineDays,
                    targetMetrics.notification_count,
                    0.0
            );

            db.burnoutRiskDao().upsertBurnoutRisk(risk);

            prefs.edit()
                    .putInt(KEY_LAST_RISK_COMPUTED_DAY, targetDay)
                    .apply();

            Log.d(TAG, "Risk saved: day=" + targetDay
                    + " riskScore=" + risk.riskScore);

        } catch (Exception ex) {
            Log.e(TAG, "Risk computation failed: " + ex.getMessage(), ex);
        }
    }
}