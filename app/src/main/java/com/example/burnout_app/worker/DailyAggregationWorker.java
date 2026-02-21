package com.example.burnout_app.worker;

import android.app.usage.UsageEvents;
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
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.helpers.AppCategoryResolver;
import com.example.burnout_app.helpers.RetentionPolicy;
import com.example.burnout_app.helpers.TimeKey;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class DailyAggregationWorker extends Worker {

    private static final String TAG = "DailyAggregationWorker";

    private static final String PREFS = "burnout_runtime";
    private static final String KEY_LAST_USAGE_CAPTURE_TS = "last_usage_capture_ts";

    // incremental screen/unlock
    private static final String KEY_SCREEN_IS_ON = "screen_is_on";
    private static final String KEY_SCREEN_OPEN_TS = "screen_open_ts";
    private static final String KEY_LAST_SCREEN_EVENT_TS = "last_screen_event_ts";
    private static final String KEY_LAST_UNLOCK_EVENT_TS = "last_unlock_event_ts";

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
        // Primera ejecución => solo setear last_usage_capture_ts=now y salir.
        // ------------------------------------------------------------------
        long lastTs = prefs.getLong(KEY_LAST_USAGE_CAPTURE_TS, -1L);
        boolean firstRunAfterClear = !prefs.contains(KEY_LAST_USAGE_CAPTURE_TS);
        if (firstRunAfterClear) {
            Log.d(TAG, "First run after clear -> init last_usage_capture_ts with short lookback (no backfill huge)");
            lastTs = now - 30 * 60_000L; // 30 min
            prefs.edit()
                    .putLong(KEY_LAST_USAGE_CAPTURE_TS, lastTs)
                    .putBoolean(KEY_SCREEN_IS_ON, false)
                    .putLong(KEY_SCREEN_OPEN_TS, -1L)
                    .putLong(KEY_LAST_SCREEN_EVENT_TS, TimeKey.startOfDayMs(now))
                    .putLong(KEY_LAST_UNLOCK_EVENT_TS, TimeKey.startOfDayMs(now))
                    .apply();
        }

        // 0) asegurar filas daily (hoy + mañana para nocturno)
        db.userActivityDao().insertDailyIfMissing(new DailyMetricsEntity(today, 0L, 0, 0L, 0, 0, 0, 0, 0L));
        db.userActivityDao().insertDailyIfMissing(new DailyMetricsEntity(tomorrow, 0L, 0, 0L, 0, 0, 0, 0, 0L));

        // 1) captura incremental SIN solape
        long start = (lastTs > 0) ? (lastTs + 1) : Math.max(0L, now - FIRST_LOOKBACK_MS);
        long end = now;

        Log.d(TAG, "Capture range: start=" + start + " end=" + end + " lastTs=" + lastTs);

        List<UsageStatsProvider.RawEvent> rawAll = UsageStatsProvider.collectEvents(ctx, start, end);
        Log.d(TAG, "Raw events total=" + (rawAll != null ? rawAll.size() : 0));

        if (rawAll == null || rawAll.isEmpty()) {
            prefs.edit().putLong(KEY_LAST_USAGE_CAPTURE_TS, end).apply();
            Log.d(TAG, "No raw events -> end");
            Log.d(TAG, "================ doWork END ================");
            return Result.success();
        }

        // debug resumen por tipos (system vs pkg)
        int cntSys = 0, cntPkg = 0;
        Map<Integer, Integer> typeCount = new HashMap<>();
        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r.pkg == null) cntSys++; else cntPkg++;
            typeCount.put(r.type, typeCount.getOrDefault(r.type, 0) + 1);
        }
        Log.d(TAG, "Raw split: pkg=" + cntPkg + " sys=" + cntSys + " typeCount=" + typeCount);

        // ------------------------------------------------------------------
        // ✅ OPT: resolver appId SOLO 1 vez por package (nada de actualizar por evento)
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
        // ------------------------------------------------------------------

        // 2) insertar FG/BG en app_usage_event (solo pkg != null)
        HashSet<String> seen = new HashSet<>();
        List<AppUsageEventEntity> toInsert = new ArrayList<>();

        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r.pkg == null) continue;

            // ✅ descartar cualquier raw event fuera del rango
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

                // anti-duplicado para la misma app muy seguido
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

                // cerrar segmento anterior
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

        Log.d(TAG, "Night windows: endToday=[" + nightStartEndingToday + "," + nightEndEndingToday + "]"
                + " endTomorrow=[" + nightStartEndingTomorrow + "," + nightEndEndingTomorrow + "]");

        // 5) SCREEN + UNLOCK incremental + nocturno por solape + HOURLY
        long startOfDayToday = TimeKey.startOfDayMs(now);
        long startOfDayYesterday = TimeKey.startOfDayMsFromEpochDay(yesterday);
        long endOfDayYesterday = startOfDayToday; // inicio de hoy = fin de ayer

        boolean screenIsOn = prefs.getBoolean(KEY_SCREEN_IS_ON, false);
        long screenOpenTs  = prefs.getLong(KEY_SCREEN_OPEN_TS, -1L);

        long lastScreenEventTs = prefs.getLong(KEY_LAST_SCREEN_EVENT_TS, start);
        long lastUnlockEventTs = prefs.getLong(KEY_LAST_UNLOCK_EVENT_TS, start);

    // ✅ Si el día cambió, NO “mates” un segmento abierto.
    // Solo “arrastra” el inicio al comienzo del día actual si estaba ON.
        int openDay = TimeKey.epochDayLocal(screenOpenTs);
        if (openDay != today) {
            lastScreenEventTs = start;
            lastUnlockEventTs = start;
            prefs.edit()
                    .putLong(KEY_LAST_SCREEN_EVENT_TS, lastScreenEventTs)
                    .putLong(KEY_LAST_UNLOCK_EVENT_TS, lastUnlockEventTs)
                    .apply();
        }


        long screenMsDelta = 0L;
        int unlockDelta = 0;

        long nightDeltaToday = 0L;
        long nightDeltaTomorrow = 0L;

// ✅ dos buffers hourly: ayer y hoy
        long[] screenMsByHourPrev = new long[24];
        long[] screenMsByHourToday = new long[24];

        int[] unlockByHourToday = new int[24];   // (si quieres unlock por hora de ayer, igual: otro array)
        int[] unlockByHourPrev  = new int[24];   // opcional, por si te interesa

        Log.d(TAG, "State(before): screenIsOn=" + screenIsOn
                + " screenOpenTs=" + screenOpenTs
                + " lastScreenEventTs=" + lastScreenEventTs
                + " lastUnlockEventTs=" + lastUnlockEventTs);

        for (UsageStatsProvider.RawEvent r : rawAll) {

            if (r.pkg != null) continue; // system only
            if (screenIsOn && screenOpenTs > 0 && screenOpenTs < start) {
                screenOpenTs = start;
            }

            // Para screen events usamos lastScreenEventTs
            if (r.type != UsageEvents.Event.KEYGUARD_HIDDEN && r.ts <= lastScreenEventTs) {
                continue;
            }

            if (r.type == UsageEvents.Event.SCREEN_INTERACTIVE) {

                if (!screenIsOn) {
                    screenIsOn = true;
                    // OJO: no fuerces startOfDayToday aquí, porque el evento puede ser de ayer.
                    screenOpenTs = r.ts;
                    Log.d(TAG, "SCREEN_ON ts=" + r.ts + " screenOpenTs=" + screenOpenTs);
                }
                lastScreenEventTs = Math.max(lastScreenEventTs, r.ts);

            } else if (r.type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {

                if (screenIsOn && screenOpenTs > 0 && r.ts > screenOpenTs) {
                    long segStart = screenOpenTs;
                    long segEnd = r.ts;

                    long segMs = segEnd - segStart;
                    screenMsDelta += segMs;

                    // ✅ Atribuir a AYER y HOY según el solape
                    // Parte en ayer
                    if (segStart < endOfDayYesterday) {
                        long s = segStart;
                        long e = Math.min(segEnd, endOfDayYesterday);
                        addScreenSegmentToHours(screenMsByHourPrev, yesterday, s, e);
                    }
                    // Parte en hoy
                    if (segEnd > startOfDayToday) {
                        long s = Math.max(segStart, startOfDayToday);
                        long e = segEnd;
                        addScreenSegmentToHours(screenMsByHourToday, today, s, e);
                    }

                    // nocturno por solape (ya estaba bien)
                    long addToday = overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
                    long addTomorrow = overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);
                    nightDeltaToday += addToday;
                    nightDeltaTomorrow += addTomorrow;
                }

                screenIsOn = false;
                screenOpenTs = -1L;
                lastScreenEventTs = Math.max(lastScreenEventTs, r.ts);

            } else if (r.type == UsageEvents.Event.KEYGUARD_HIDDEN) {

                if (r.ts > lastUnlockEventTs) {
                    unlockDelta++;
                    lastUnlockEventTs = Math.max(lastUnlockEventTs, r.ts);

                    int h = hourOfTsLocal(r.ts);
                    int d = TimeKey.epochDayLocal(r.ts);
                    if (h >= 0 && h <= 23) {
                        if (d == today) unlockByHourToday[h]++;
                        else if (d == yesterday) unlockByHourPrev[h]++; // opcional
                    }
                }
            }
        }

// Cierre al final si sigue ON
        if (screenIsOn && screenOpenTs > 0 && now > screenOpenTs) {
            long segStart = screenOpenTs;
            long segEnd = now;

            long segMs = segEnd - segStart;
            screenMsDelta += segMs;

            if (segStart < endOfDayYesterday) {
                long s = segStart;
                long e = Math.min(segEnd, endOfDayYesterday);
                addScreenSegmentToHours(screenMsByHourPrev, yesterday, s, e);
            }
            if (segEnd > startOfDayToday) {
                long s = Math.max(segStart, startOfDayToday);
                long e = segEnd;
                addScreenSegmentToHours(screenMsByHourToday, today, s, e);
            }

            long addToday = overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
            long addTomorrow = overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);
            nightDeltaToday += addToday;
            nightDeltaTomorrow += addTomorrow;

            // “avanza” openTs para evitar doble conteo en la próxima run
            screenOpenTs = now;
        }

        prefs.edit()
                .putBoolean(KEY_SCREEN_IS_ON, screenIsOn)
                .putLong(KEY_SCREEN_OPEN_TS, screenOpenTs)
                .putLong(KEY_LAST_SCREEN_EVENT_TS, lastScreenEventTs)
                .putLong(KEY_LAST_UNLOCK_EVENT_TS, lastUnlockEventTs)
                .apply();


        // 6) SAVE TODAY (daily_metrics)
        DailyMetricsEntity cur = db.userActivityDao().getDailyMetricsByDate(today);
        if (cur != null) {
            cur.screen_ms = Math.max(0L, cur.screen_ms + screenMsDelta);
            cur.unlock_count = Math.max(0, cur.unlock_count + unlockDelta);

            // TU DEFINICIÓN: sesiones = desbloqueos
            cur.session_count = cur.unlock_count;

            cur.foreground_ms = foregroundMs;
            cur.unique_apps_count = uniqueAppsWithRealFg;
            cur.app_switch_count = appSwitchCount;

            cur.night_ms = Math.max(0L, cur.night_ms + nightDeltaToday);

            db.userActivityDao().upsertDailyMetrics(cur);
        }

        // 7) SAVE TOMORROW (night)
        if (nightDeltaTomorrow > 0) {
            DailyMetricsEntity tm = db.userActivityDao().getDailyMetricsByDate(tomorrow);
            if (tm != null) {
                tm.night_ms = Math.max(0L, tm.night_ms + nightDeltaTomorrow);
                db.userActivityDao().upsertDailyMetrics(tm);
            }
        }

        // 8) daily_app_metrics (today)
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

        // 9) HOURLY persist: ayer + hoy
        persistHourly(db, yesterday, screenMsByHourPrev, unlockByHourPrev, null /*switch prev si lo quieres*/);
        persistHourly(db, today, screenMsByHourToday, unlockByHourToday, switchByHour);


        // 10) retención
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
                int baseNotif = (cur != null) ? cur.notification_count : 0;
                int baseSwitch = (cur != null) ? cur.app_switch_count : 0;
                int baseUnique = (cur != null) ? cur.unique_apps_count : 0;

                long addScreen = (screenMsByHour != null && screenMsByHour.length == 24) ? screenMsByHour[h] : 0L;
                int addUnlock = (unlockByHour != null && unlockByHour.length == 24) ? unlockByHour[h] : 0;
                int addSwitch = (switchByHour != null && switchByHour.length == 24) ? switchByHour[h] : 0;

                // Nota: switches por hora aquí lo guardas como "delta de esta ejecución".
                // Si quieres acumulado por hora, usa baseSwitch + addSwitch.
                int newSwitch = (switchByHour == null) ? baseSwitch : addSwitch;

                out.add(new HourlyMetricsEntity(
                        epochDay,
                        h,
                        Math.max(0L, baseScreen + addScreen),
                        Math.max(0, baseUnlock + addUnlock),
                        Math.max(0, baseNotif),
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

    // ===================== misc helpers =====================

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

    // =========================================================
    // ✅ App resolution (sin “actualizar cada vez”; solo si falta)
    // =========================================================
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

        // no existe: crear
        String label = AppCategoryResolver.resolveAppLabel(ctx, pkg);
        if (label != null) label = label.trim();
        if (label == null || label.isEmpty()) label = pkg;

        String cat = mustIgnore ? "OTHER" : AppCategoryResolver.resolveCategory(ctx, pkg);
        if (cat == null) cat = "OTHER";

        long inserted = db.usageDao().insertApp(new AppEntity(pkg, label, cat, mustIgnore));
        if (inserted > 0) return inserted;

        // fallback (race)
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
