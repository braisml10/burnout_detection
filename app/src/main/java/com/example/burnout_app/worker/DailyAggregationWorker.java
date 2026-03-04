package com.example.burnout_app.worker;

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

import com.example.burnout_app.collectors.UsageStatsProvider;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.AppEntity;
import com.example.burnout_app.data.entity.AppUsageEventEntity;
import com.example.burnout_app.data.entity.DailyAppMetricsEntity;
import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.entity.HourlyCommMetricsEntity;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.helpers.AppCategoryResolver;
import com.example.burnout_app.helpers.RetentionPolicy;
import com.example.burnout_app.helpers.TimeKey;

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

    // incremental screen/unlock (state)
    private static final String KEY_SCREEN_IS_ON = "screen_is_on";
    private static final String KEY_LAST_UNLOCK_EVENT_TS = "last_unlock_event_ts";

    // ✅ monotonic cursor for screen accounting (prevents double count)
    private static final String KEY_LAST_SCREEN_ACCOUNTED_TS = "last_screen_accounted_ts";

    private static final long FIRST_LOOKBACK_MS = 2 * 60 * 60_000L; // 2h
    private static final long FG_DEBOUNCE_MS = 500L;

    private static final String PKG_SELF = "com.example.burnout_app";
    private static final String PKG_LAUNCHER = "com.android.launcher";

    public DailyAggregationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        Context ctx = getApplicationContext();
        BurnoutDatabase db = BurnoutDatabase.getInstance(ctx);

        long now = System.currentTimeMillis();
        int today = TimeKey.epochDayLocal(now);
        int yesterday = today - 1;
        int tomorrow = today + 1;

        int cutoffDate = today - RetentionPolicy.RAW_EVENTS_RETENTION_DAYS;

        Log.d(TAG, "================ doWork START ================");
        Log.d(TAG, "now=" + now + " today=" + today + " y=" + yesterday + " tmr=" + tomorrow);

        if (!UsageStatsProvider.hasUsageAccess(ctx)) {
            Log.e(TAG, "No Usage Access -> skipping");
            Log.d(TAG, "================ doWork END ================");
            return Result.success();
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // ------------------------------------------------------------------
        // ✅ FIX: tras pm clear / reinstalación, NO rellenar con histórico.
        // Primera ejecución => lookback corto.
        // ------------------------------------------------------------------
        long lastTs = prefs.getLong(KEY_LAST_USAGE_CAPTURE_TS, -1L);
        boolean firstRunAfterClear = !prefs.contains(KEY_LAST_USAGE_CAPTURE_TS);
        if (firstRunAfterClear) {
            Log.d(TAG, "First run after clear -> init last_usage_capture_ts with short lookback (no backfill huge)");
            lastTs = now - 30 * 60_000L; // 30 min
            prefs.edit()
                    .putLong(KEY_LAST_USAGE_CAPTURE_TS, lastTs)
                    .putBoolean(KEY_SCREEN_IS_ON, false)
                    .putLong(KEY_LAST_UNLOCK_EVENT_TS, TimeKey.startOfDayMs(now))
                    .putLong(KEY_LAST_SCREEN_ACCOUNTED_TS, Math.max(TimeKey.startOfDayMs(now), lastTs))
                    .apply();
        }

        // 0) asegurar filas daily (ayer, hoy + mañana para nocturno)
        db.userActivityDao().insertDailyIfMissing(new DailyMetricsEntity(yesterday, 0L, 0, 0L, 0, 0, 0, 0, 0L));
        db.userActivityDao().insertDailyIfMissing(new DailyMetricsEntity(today, 0L, 0, 0L, 0, 0, 0, 0, 0L));
        db.userActivityDao().insertDailyIfMissing(new DailyMetricsEntity(tomorrow, 0L, 0, 0L, 0, 0, 0, 0, 0L));

        // 1) captura incremental SIN solape
        long start = (lastTs > 0) ? (lastTs + 1) : Math.max(0L, now - FIRST_LOOKBACK_MS);
        long end = now;

        Log.d(TAG, "Capture range: start=" + start + " end=" + end + " lastTs=" + lastTs);

        List<UsageStatsProvider.RawEvent> rawAll = UsageStatsProvider.collectEvents(ctx, start, end);
        Log.d(TAG, "Raw events total=" + (rawAll != null ? rawAll.size() : 0));

        // ✅ IMPORTANTE: NO hacemos return si rawAll está vacío.
        if (rawAll == null) rawAll = new ArrayList<>();

        // debug resumen por tipos (system vs pkg)
        int cntSys = 0, cntPkg = 0;
        Map<Integer, Integer> typeCount = new HashMap<>();
        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r == null) continue;
            if (r.pkg == null) cntSys++; else cntPkg++;
            typeCount.put(r.type, typeCount.getOrDefault(r.type, 0) + 1);
        }
        Log.d(TAG, "Raw split: pkg=" + cntPkg + " sys=" + cntSys + " typeCount=" + typeCount);

        // ------------------------------------------------------------------
        // ✅ OPT: resolver appId SOLO 1 vez por package
        // ------------------------------------------------------------------
        HashSet<String> uniquePkgs = new HashSet<>();
        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r != null && r.pkg != null) uniquePkgs.add(r.pkg);
        }

        Map<String, Long> appIdCache = new HashMap<>(Math.max(16, uniquePkgs.size() * 2));
        for (String pkg : uniquePkgs) {
            long appId = resolveAppId(db, ctx, pkg);
            if (appId > 0) appIdCache.put(pkg, appId);
        }

        // 2) insertar FG/BG en app_usage_event (solo pkg != null)
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
                + " todayCount=" + db.usageDao().countUsageEventsByDate(today));

        // 3) agregación foreground por apps (hoy) + switches
        List<AppUsageEventEntity> eventsToday = db.usageDao().getUsageEventsByDate(today);

        long foregroundMs = 0L;
        int fgSessionCount = 0;

        Map<Long, Long> fgMsByApp = new HashMap<>();
        Map<Long, Integer> openCountByApp = new HashMap<>();

        Long currentFgAppId = null;
        long openFgTs = -1L;
        long lastFgTs = -1L;

        // ================= SWITCH COUNT =================
        int appSwitchCount = 0;
        long lastSwitchTs = -1L;

        Long lastRealFgAppId = null;
        long lastRealFgTs = -1L;

        int[] switchByHour = new int[24];

        final int SWITCH_DEBUG_LIMIT = 15;
        ArrayList<String> switchDebug = new ArrayList<>();
        // =================================================

        for (AppUsageEventEntity e : eventsToday) {

            if (e.event_type == UsageEvents.Event.MOVE_TO_FOREGROUND) {

                if (currentFgAppId != null
                        && currentFgAppId.equals(e.app_id)
                        && lastFgTs > 0
                        && (e.timestamp - lastFgTs) < FG_DEBOUNCE_MS) {
                    lastFgTs = e.timestamp;
                    continue;
                }

                // -------- SWITCH --------
                String toPkg = safePkg(db, e.app_id);
                if (!isNoisePackage(toPkg)) {

                    if (lastRealFgAppId == null) {
                        lastRealFgAppId = e.app_id;
                        lastRealFgTs = e.timestamp;
                    } else if (!lastRealFgAppId.equals(e.app_id)) {

                        if (lastSwitchTs <= 0 || (e.timestamp - lastSwitchTs) >= FG_DEBOUNCE_MS) {
                            appSwitchCount++;
                            lastSwitchTs = e.timestamp;

                            int h = hourOfTsLocal(e.timestamp);
                            if (h >= 0 && h <= 23) switchByHour[h]++;

                            if (switchDebug.size() < SWITCH_DEBUG_LIMIT) {
                                String fromPkg = safePkg(db, lastRealFgAppId);
                                switchDebug.add("FG(real)->FG(real) " + fromPkg + " -> " + toPkg
                                        + " @ " + e.timestamp + " (gap=" + (e.timestamp - lastRealFgTs) + "ms)");
                            }
                        }

                        lastRealFgAppId = e.app_id;
                        lastRealFgTs = e.timestamp;
                    } else {
                        lastRealFgTs = e.timestamp;
                    }
                }
                // ------------------------

                if (currentFgAppId != null && openFgTs > 0 && e.timestamp > openFgTs) {
                    long delta = e.timestamp - openFgTs;
                    foregroundMs += delta;
                    fgMsByApp.put(currentFgAppId, fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);
                }

                fgSessionCount++;
                openCountByApp.put(e.app_id, openCountByApp.getOrDefault(e.app_id, 0) + 1);

                currentFgAppId = e.app_id;
                openFgTs = e.timestamp;
                lastFgTs = e.timestamp;

            } else if (e.event_type == UsageEvents.Event.MOVE_TO_BACKGROUND) {

                if (currentFgAppId != null
                        && e.app_id == currentFgAppId
                        && openFgTs > 0
                        && e.timestamp > openFgTs) {

                    long delta = e.timestamp - openFgTs;
                    foregroundMs += delta;
                    fgMsByApp.put(currentFgAppId, fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);

                    currentFgAppId = null;
                    openFgTs = -1L;
                }
            }
        }

        if (currentFgAppId != null && openFgTs > 0 && now > openFgTs) {
            long delta = now - openFgTs;
            foregroundMs += delta;
            fgMsByApp.put(currentFgAppId, fgMsByApp.getOrDefault(currentFgAppId, 0L) + delta);
        }

        int uniqueAppsWithRealFg = 0;
        for (long ms : fgMsByApp.values()) if (ms > 0) uniqueAppsWithRealFg++;

        Log.d(TAG, "FG agg: foregroundMs=" + foregroundMs
                + " fgSessionCount=" + fgSessionCount
                + " uniqueAppsWithRealFg=" + uniqueAppsWithRealFg
                + " eventsToday=" + eventsToday.size());

        Log.d(TAG, "SWITCH agg (real FG changes): appSwitchCount=" + appSwitchCount
                + " debugSeq=" + switchDebug);

        // 4) ventanas nocturnas (ATRIBUCIÓN A DÍA DE FIN)
        long nightStartEndingToday = atLocalHourMs(yesterday, 22, 0);
        long nightEndEndingToday   = atLocalHourMs(today, 6, 0);

        long nightStartEndingTomorrow = atLocalHourMs(today, 22, 0);
        long nightEndEndingTomorrow   = atLocalHourMs(tomorrow, 6, 0);

        // 5) SCREEN + UNLOCK incremental (cursor-based) + HOURLY
        long startOfDayToday = TimeKey.startOfDayMs(now);
        long startOfDayYesterday = TimeKey.startOfDayMsFromEpochDay(yesterday);
        long endOfDayYesterday = startOfDayToday;
        long endOfDayToday = startOfDayToday + 24L * 60L * 60_000L;

        boolean screenIsOn = prefs.getBoolean(KEY_SCREEN_IS_ON, false);

        long lastUnlockEventTs = prefs.getLong(KEY_LAST_UNLOCK_EVENT_TS, start);

        long lastAccountedTs = prefs.getLong(KEY_LAST_SCREEN_ACCOUNTED_TS, start);
        if (lastAccountedTs < start) lastAccountedTs = start;
        if (lastAccountedTs > end) lastAccountedTs = end;

        long screenMsDeltaPrev  = 0L;
        long screenMsDeltaToday = 0L;

        int unlockDelta = 0;

        long nightDeltaToday = 0L;
        long nightDeltaTomorrow = 0L;

        long[] screenMsByHourPrev = new long[24];
        long[] screenMsByHourToday = new long[24];

        int[] unlockByHourToday = new int[24];
        int[] unlockByHourPrev  = new int[24];

        ArrayList<UsageStatsProvider.RawEvent> sys = new ArrayList<>();
        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r == null) continue;
            if (r.pkg != null) continue;
            if (r.ts < start || r.ts > end) continue;
            sys.add(r);
        }
        Collections.sort(sys, new Comparator<UsageStatsProvider.RawEvent>() {
            @Override public int compare(UsageStatsProvider.RawEvent a, UsageStatsProvider.RawEvent b) {
                return Long.compare(a.ts, b.ts);
            }
        });

        long cursor = lastAccountedTs;

        for (UsageStatsProvider.RawEvent r : sys) {

            if (r.ts > cursor && screenIsOn) {
                long segStart = cursor;
                long segEnd = r.ts;

                long prevPart  = overlapMs(segStart, segEnd, startOfDayYesterday, endOfDayYesterday);
                long todayPart = overlapMs(segStart, segEnd, startOfDayToday, endOfDayToday);
                screenMsDeltaPrev  += prevPart;
                screenMsDeltaToday += todayPart;

                if (segStart < endOfDayYesterday) {
                    addScreenSegmentToHours(screenMsByHourPrev, yesterday, segStart, Math.min(segEnd, endOfDayYesterday));
                }
                if (segEnd > startOfDayToday) {
                    addScreenSegmentToHours(screenMsByHourToday, today, Math.max(segStart, startOfDayToday), segEnd);
                }

                nightDeltaToday += overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
                nightDeltaTomorrow += overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);
            }

            cursor = Math.max(cursor, r.ts);

            if (r.type == UsageEvents.Event.SCREEN_INTERACTIVE) {
                screenIsOn = true;

            } else if (r.type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                screenIsOn = false;

            } else if (r.type == UsageEvents.Event.KEYGUARD_HIDDEN) {
                if (r.ts > lastUnlockEventTs) {
                    unlockDelta++;
                    lastUnlockEventTs = Math.max(lastUnlockEventTs, r.ts);

                    int h = hourOfTsLocal(r.ts);
                    int d = TimeKey.epochDayLocal(r.ts);
                    if (h >= 0 && h <= 23) {
                        if (d == today) unlockByHourToday[h]++;
                        else if (d == yesterday) unlockByHourPrev[h]++;
                    }
                }
            }
        }

        if (end > cursor && screenIsOn) {
            long segStart = cursor;
            long segEnd = end;

            long prevPart  = overlapMs(segStart, segEnd, startOfDayYesterday, endOfDayYesterday);
            long todayPart = overlapMs(segStart, segEnd, startOfDayToday, endOfDayToday);
            screenMsDeltaPrev  += prevPart;
            screenMsDeltaToday += todayPart;

            if (segStart < endOfDayYesterday) {
                addScreenSegmentToHours(screenMsByHourPrev, yesterday, segStart, Math.min(segEnd, endOfDayYesterday));
            }
            if (segEnd > startOfDayToday) {
                addScreenSegmentToHours(screenMsByHourToday, today, Math.max(segStart, startOfDayToday), segEnd);
            }

            nightDeltaToday += overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
            nightDeltaTomorrow += overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);
        }

        prefs.edit()
                .putBoolean(KEY_SCREEN_IS_ON, screenIsOn)
                .putLong(KEY_LAST_UNLOCK_EVENT_TS, lastUnlockEventTs)
                .putLong(KEY_LAST_SCREEN_ACCOUNTED_TS, end)
                .apply();

        // ============================
        // ✅ NOTIFICATIONS
        // ============================
        int[] notifByHourToday = new int[24];
        int[] notifByHourPrev  = new int[24];

        Cursor cToday = null;
        try {
            cToday = db.notificationDao().countByHourCursor(today);
            if (cToday != null) {
                int iHour = cToday.getColumnIndex("hour");
                int iC = cToday.getColumnIndex("c");
                while (cToday.moveToNext()) {
                    int h = cToday.getInt(iHour);
                    int cnt = cToday.getInt(iC);
                    if (h >= 0 && h <= 23) notifByHourToday[h] = cnt;
                }
            }
        } finally {
            if (cToday != null) cToday.close();
        }

        Cursor cPrev = null;
        try {
            cPrev = db.notificationDao().countByHourCursor(yesterday);
            if (cPrev != null) {
                int iHour = cPrev.getColumnIndex("hour");
                int iC = cPrev.getColumnIndex("c");
                while (cPrev.moveToNext()) {
                    int h = cPrev.getInt(iHour);
                    int cnt = cPrev.getInt(iC);
                    if (h >= 0 && h <= 23) notifByHourPrev[h] = cnt;
                }
            }
        } finally {
            if (cPrev != null) cPrev.close();
        }

        int notifTotalToday = db.notificationDao().countByDate(today);
        int notifTotalPrev  = db.notificationDao().countByDate(yesterday);

        // 6) SAVE YESTERDAY (screen split)
        if (screenMsDeltaPrev > 0) {
            DailyMetricsEntity prev = db.userActivityDao().getDailyMetricsByDate(yesterday);
            if (prev != null) {
                prev.screen_ms = Math.max(0L, prev.screen_ms + screenMsDeltaPrev);
                db.userActivityDao().upsertDailyMetrics(prev);
            }
        }

        // ✅ yesterday notifications (SET)
        DailyMetricsEntity prevNotif = db.userActivityDao().getDailyMetricsByDate(yesterday);
        if (prevNotif != null) {
            prevNotif.notification_count = Math.max(0, notifTotalPrev);
            db.userActivityDao().upsertDailyMetrics(prevNotif);
        }

        // 7) SAVE TODAY
        DailyMetricsEntity cur = db.userActivityDao().getDailyMetricsByDate(today);
        if (cur != null) {
            cur.screen_ms = Math.max(0L, cur.screen_ms + screenMsDeltaToday);

            cur.unlock_count = Math.max(0, cur.unlock_count + unlockDelta);
            cur.session_count = cur.unlock_count;

            cur.foreground_ms = foregroundMs;
            cur.unique_apps_count = uniqueAppsWithRealFg;
            cur.app_switch_count = appSwitchCount;

            cur.night_ms = Math.max(0L, cur.night_ms + nightDeltaToday);

            // ✅ today notifications (SET)
            cur.notification_count = Math.max(0, notifTotalToday);

            db.userActivityDao().upsertDailyMetrics(cur);
        }

        // 8) SAVE TOMORROW (night)
        if (nightDeltaTomorrow > 0) {
            DailyMetricsEntity tm = db.userActivityDao().getDailyMetricsByDate(tomorrow);
            if (tm != null) {
                tm.night_ms = Math.max(0L, tm.night_ms + nightDeltaTomorrow);
                db.userActivityDao().upsertDailyMetrics(tm);
            }
        }

        // 9) daily_app_metrics (today)
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

        // 10) HOURLY persist: ayer + hoy (✅ con notifs)
        persistHourly(db, yesterday, screenMsByHourPrev, unlockByHourPrev, notifByHourPrev, null);
        persistHourly(db, today, screenMsByHourToday, unlockByHourToday, notifByHourToday, switchByHour);

        // ============================
        // ✅ COMMUNICATIONS (NEW)
        // ============================
        try {
            db.communicationDao().insertDailyIfMissing(yesterday);
            db.communicationDao().insertDailyIfMissing(today);

            // ✅ HOY siempre
            CommAggResult tRes = computeCommForDay(ctx, today);
            persistCommMetricsForDay(db, today, tRes);

            // ✅ AYER solo “grace window” (p.ej. hasta las 02:00)
            long startOfTodayMs = TimeKey.startOfDayMsFromEpochDay(today);
            long graceEndMs = startOfTodayMs + 2L * 60L * 60_000L; // 02:00

            if (now <= graceEndMs) {
                CommAggResult yRes = computeCommForDay(ctx, yesterday);
                persistCommMetricsForDay(db, yesterday, yRes);
                Log.d(TAG_COMM, "Saved comm metrics y=" + yesterday + " calls=" + yRes.callsTotal + " msgs=" + yRes.msgsTotal);
            } else {
                Log.d(TAG_COMM, "Skipping yesterday comm recompute (frozen) y=" + yesterday);
            }

            Log.d(TAG_COMM, "Saved comm metrics t=" + today + " calls=" + tRes.callsTotal + " msgs=" + tRes.msgsTotal);

        } catch (Exception ex) {
            Log.e(TAG_COMM, "Comm aggregation failed: " + ex.getMessage(), ex);
        }

        // 11) retención
        int delUsage = db.usageDao().deleteUsageEventsOlderThanDate(cutoffDate);
        int delScreen = db.userActivityDao().deleteScreenEventsOlderThanDate(cutoffDate);
        int delNotif = db.notificationDao().deleteNotificationEventsOlderThanDate(cutoffDate);
        int delHourly = db.userActivityDao().deleteHourlyMetricsOlderThanDate(cutoffDate);

        Log.d(TAG, "Retention: cutoffDate=" + cutoffDate
                + " deletedUsage=" + delUsage
                + " deletedScreen=" + delScreen
                + " deletedNotif=" + delNotif
                + " deletedHourly=" + delHourly);

        Log.d(TAG, "================ doWork END ================");
        return Result.success();
    }

    // ===================== HOURLY HELPERS =====================

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
                    if (h != null && h.hour >= 0 && h.hour <= 23) byHour[h.hour] = h;
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

                // ✅ notifs: SET desde raw aggregate
                int newNotif = (notifByHour != null && notifByHour.length == 24) ? notifByHour[h] : baseNotif;

                // switches: tu lógica original
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
        long dayEnd = dayStart + 24L * 60L * 60_000L;

        long s = Math.max(segStart, dayStart);
        long e = Math.min(segEnd, dayEnd);
        if (e <= s) return;

        for (int h = 0; h < 24; h++) {
            long hs = atLocalHourMs(epochDay, h, 0);
            long he = hs + 60L * 60_000L;
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
            String pkg = db.usageDao().getPackageNameByAppId(appId);
            return (pkg != null) ? pkg : ("appId=" + appId);
        } catch (Exception ex) {
            return ("appId=" + appId);
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

    // ===================== COMM METRICS =====================

    private boolean hasPermission(Context ctx, String perm) {
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private static class CommAggResult {
        int callsTotal;
        long callsDurationMs;
        int[] callsByHour = new int[24];

        int msgsTotal;          // SMS recibidos (Inbox)
        int[] msgsByHour = new int[24];
    }

    private CommAggResult computeCommForDay(Context ctx, int epochDay) {
        CommAggResult out = new CommAggResult();

        long startMs = TimeKey.startOfDayMsFromEpochDay(epochDay);
        long endMs = startMs + 24L * 60L * 60_000L;

        // ---- CALLS ----
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
                        if (h >= 0 && h <= 23) out.callsByHour[h]++;
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

        // ---- SMS RECEIVED (Inbox) ----
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
                        out.msgsTotal++;

                        int h = TimeKey.hourOfDayLocal(ts);
                        if (h >= 0 && h <= 23) out.msgsByHour[h]++;
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG_COMM, "SMS query failed: " + ex.getMessage(), ex);
            } finally {
                if (c != null) c.close();
            }
        } else {
            Log.w(TAG_COMM, "READ_SMS not granted -> messages=0");
        }

        return out;
    }

    private long getMessagingMsForDay(BurnoutDatabase db, int epochDay) {
        long ms = 0L;
        Cursor c = null;
        try {
            c = db.usageDao().getCategoryTotalsMsForDay(epochDay); // el mismo que usa UsageRepository
            int iCat = c.getColumnIndexOrThrow("category");
            int iMs  = c.getColumnIndexOrThrow("total_ms");

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
            db.communicationDao().insertDailyIfMissing(epochDay);

            // ✅ Reutiliza el tiempo de MESSAGING ya calculado en DailyAppMetric/App join
            long messagingMs = getMessagingMsForDay(db, epochDay);

            long voiceMs = Math.max(0L, r.callsDurationMs);
            long textMs  = messagingMs;
            long totalMs = voiceMs + textMs;

            db.communicationDao().upsertDaily(
                    new DailyCommMetricsEntity(
                            epochDay,
                            Math.max(0, r.callsTotal),
                            Math.max(0, r.msgsTotal), // (si ya estás sumando sms inbox + notif mensajería)
                            totalMs,
                            voiceMs,
                            textMs
                    )
            );

            // Ver comentario abajo.
            ArrayList<HourlyCommMetricsEntity> rows = new ArrayList<>(24);
            for (int h = 0; h < 24; h++) {
                long voiceVal = 0L; // aquí debería ser ms/hora, NO count
                long textVal  = 0L; // aquí debería ser ms/hora, NO count
                long totalVal = voiceVal + textVal;
                rows.add(new HourlyCommMetricsEntity(epochDay, h, totalVal, voiceVal, textVal));
            }
            db.communicationDao().upsertHourly(rows);

        } catch (Exception ex) {
            Log.e(TAG_COMM, "persistCommMetricsForDay failed: " + ex.getMessage(), ex);
        }
    }



    // ===================== APP HELPERS =====================

    private long resolveAppId(BurnoutDatabase db, Context ctx, String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return -1L;

        boolean mustIgnore = shouldIgnorePkg(pkg);

        Long existing = db.usageDao().getAppIdByPackageName(pkg);
        if (existing != null) {

            if (mustIgnore) {
                db.usageDao().updateAppIgnored(existing, true);
                return existing;
            }

            String curName = db.usageDao().getNameByAppId(existing);
            String curCat  = db.usageDao().getCategoryByAppId(existing);

            boolean needsName = (curName == null || curName.trim().isEmpty() || curName.equals(pkg) || looksLikePackage(curName));
            boolean needsCat  = (curCat == null || curCat.trim().isEmpty() || "OTHER".equalsIgnoreCase(curCat));

            if (needsName) {
                String label = AppCategoryResolver.resolveAppLabel(ctx, pkg);
                if (label != null) label = label.trim();
                if (label != null && !label.isEmpty() && !label.equals(pkg)) {
                    db.usageDao().updateAppName(existing, label);
                }
            }

            if (needsCat) {
                String cat = AppCategoryResolver.resolveCategory(ctx, pkg);
                if (cat == null) cat = "OTHER";
                db.usageDao().updateAppCategory(existing, cat);
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
}