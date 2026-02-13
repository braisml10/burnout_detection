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

    // Ventana para detectar "A->B" cuando el sistema emite BG(A) y luego FG(B)
    // (ajústalo si quieres, 1000-2000ms suele ir bien)
    private static final long SWITCH_GAP_MS = 1500L;

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

        // 0) asegurar filas
        db.userActivityDao().insertDailyIfMissing(new DailyMetricsEntity(today, 0L, 0, 0L, 0, 0, 0, 0, 0L));
        db.userActivityDao().insertDailyIfMissing(new DailyMetricsEntity(tomorrow, 0L, 0, 0L, 0, 0, 0, 0, 0L));

        // 1) captura incremental SIN solape
        long lastTs = prefs.getLong(KEY_LAST_USAGE_CAPTURE_TS, -1L);
        long start = (lastTs > 0) ? (lastTs + 1) : Math.max(0L, now - FIRST_LOOKBACK_MS);
        long end = now;

        Log.d(TAG, "Capture range: start=" + start + " end=" + end + " lastTs=" + lastTs);

        List<UsageStatsProvider.RawEvent> rawAll = UsageStatsProvider.collectEvents(ctx, start, end);

        Log.d(TAG, "Raw events total=" + rawAll.size());

        // debug resumen por tipos (system vs pkg)
        int cntSys = 0, cntPkg = 0;
        Map<Integer, Integer> typeCount = new HashMap<>();
        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r.pkg == null) cntSys++; else cntPkg++;
            typeCount.put(r.type, typeCount.getOrDefault(r.type, 0) + 1);
        }
        Log.d(TAG, "Raw split: pkg=" + cntPkg + " sys=" + cntSys + " typeCount=" + typeCount);

        // 2) insertar FG/BG en app_usage_event (solo pkg != null)
        HashSet<String> seen = new HashSet<>();
        List<AppUsageEventEntity> toInsert = new ArrayList<>();

        for (UsageStatsProvider.RawEvent r : rawAll) {
            if (r.pkg == null) continue; // system events fuera

            long appId = resolveAppId(db, r.pkg);
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

        // 3) agregación foreground por apps (hoy) + app_switch_count
        List<AppUsageEventEntity> eventsToday = db.usageDao().getUsageEventsByDate(today);

        long foregroundMs = 0L;
        int fgSessionCount = 0;

        Map<Long, Long> fgMsByApp = new HashMap<>();
        Map<Long, Integer> openCountByApp = new HashMap<>();

        Long currentFgAppId = null;
        long openFgTs = -1L;
        long lastFgTs = -1L;

        // ---- KPI switches robusto (BG->FG cercano) + fallback FG->FG ----
        int appSwitchCount = 0;
        long lastSwitchTs = -1L;

        Long pendingBgAppId = null;
        long pendingBgTs = -1L;

        final int SWITCH_DEBUG_LIMIT = 15;
        ArrayList<String> switchDebug = new ArrayList<>();
        // ---------------------------------------------------------------

        for (AppUsageEventEntity e : eventsToday) {

            if (e.event_type == UsageEvents.Event.MOVE_TO_FOREGROUND) {

                // anti-duplicado para la misma app muy seguido (tu lógica)
                if (currentFgAppId != null
                        && currentFgAppId.equals(e.app_id)
                        && lastFgTs > 0
                        && (e.timestamp - lastFgTs) < FG_DEBOUNCE_MS) {
                    lastFgTs = e.timestamp;
                    continue;
                }

                // ========== SWITCH COUNT ==========
                // Caso típico: BG(A) -> (gap pequeño) -> FG(B)
                Long fromApp = null;
                long fromTs = -1L;

                boolean hasPending = (pendingBgAppId != null && pendingBgTs > 0
                        && (e.timestamp - pendingBgTs) <= SWITCH_GAP_MS);

                if (hasPending) {
                    fromApp = pendingBgAppId;
                    fromTs = pendingBgTs;
                } else if (currentFgAppId != null) {
                    // fallback: algunos dispositivos pueden hacer FG(B) sin BG(A) antes
                    fromApp = currentFgAppId;
                    fromTs = lastFgTs;
                }

                if (fromApp != null && !fromApp.equals(e.app_id)) {

                    String fromPkg = safePkg(db, fromApp);
                    String toPkg = safePkg(db, e.app_id);

                    // filtrar “cerrar/ir a home”: launcher y systemui (y similares)
                    boolean noise = isNoisePackage(fromPkg) || isNoisePackage(toPkg);

                    if (!noise) {
                        if (lastSwitchTs <= 0 || (e.timestamp - lastSwitchTs) >= FG_DEBOUNCE_MS) {
                            appSwitchCount++;
                            lastSwitchTs = e.timestamp;

                            if (switchDebug.size() < SWITCH_DEBUG_LIMIT) {
                                String how = hasPending ? "BG->FG" : "FG->FG";
                                switchDebug.add(how + " " + fromPkg + " -> " + toPkg
                                        + " @ " + e.timestamp + " (gap=" + (e.timestamp - fromTs) + "ms)");
                            }
                        }
                    }
                }

                // consumimos el pending (si lo había); si era viejo, lo limpiamos igual
                pendingBgAppId = null;
                pendingBgTs = -1L;
                // =================================

                // cerrar segmento anterior (tu lógica)
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

                    // marcar “posible switch” (si a continuación viene FG de otra app, lo contamos)
                    pendingBgAppId = currentFgAppId;
                    pendingBgTs = e.timestamp;

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

        Log.d(TAG, "SWITCH agg (A->B, no-home): appSwitchCount=" + appSwitchCount
                + " debugSeq=" + switchDebug);

        // 4) ventanas nocturnas (ATRIBUCIÓN A DÍA DE FIN)
        long nightStartEndingToday = atLocalHourMs(yesterday, 22, 0);
        long nightEndEndingToday   = atLocalHourMs(today, 6, 0);

        long nightStartEndingTomorrow = atLocalHourMs(today, 22, 0);
        long nightEndEndingTomorrow   = atLocalHourMs(tomorrow, 6, 0);

        Log.d(TAG, "Night windows: endToday=[" + nightStartEndingToday + "," + nightEndEndingToday + "]"
                + " endTomorrow=[" + nightStartEndingTomorrow + "," + nightEndEndingTomorrow + "]");

        // 5) SCREEN + UNLOCK incremental + nocturno por solape
        long startOfDay = TimeKey.startOfDayMs(now);

        // reset por cambio de día
        if (TimeKey.epochDayLocal(prefs.getLong(KEY_SCREEN_OPEN_TS, -1L)) != today) {
            Log.d(TAG, "Day changed -> resetting screen/unlock incremental state");
            prefs.edit()
                    .putBoolean(KEY_SCREEN_IS_ON, false)
                    .putLong(KEY_SCREEN_OPEN_TS, -1L)
                    .putLong(KEY_LAST_SCREEN_EVENT_TS, startOfDay)
                    .putLong(KEY_LAST_UNLOCK_EVENT_TS, startOfDay)
                    .apply();
        }

        boolean screenIsOn = prefs.getBoolean(KEY_SCREEN_IS_ON, false);
        long screenOpenTs = prefs.getLong(KEY_SCREEN_OPEN_TS, -1L);

        long lastScreenEventTs = prefs.getLong(KEY_LAST_SCREEN_EVENT_TS, startOfDay);
        long lastUnlockEventTs = prefs.getLong(KEY_LAST_UNLOCK_EVENT_TS, startOfDay);

        long screenMsDelta = 0L;
        int unlockDelta = 0;

        long nightDeltaToday = 0L;
        long nightDeltaTomorrow = 0L;

        boolean sawScreenEvents = false;
        boolean sawUnlockEvents = false;

        Log.d(TAG, "State(before): screenIsOn=" + screenIsOn
                + " screenOpenTs=" + screenOpenTs
                + " lastScreenEventTs=" + lastScreenEventTs
                + " lastUnlockEventTs=" + lastUnlockEventTs);

        for (UsageStatsProvider.RawEvent r : rawAll) {

            if (r.pkg != null) continue; // system only

            // incremental de pantalla (excepto unlock)
            if (r.type != UsageEvents.Event.KEYGUARD_HIDDEN && r.ts <= lastScreenEventTs) {
                continue;
            }

            if (r.type == UsageEvents.Event.SCREEN_INTERACTIVE) {
                sawScreenEvents = true;

                if (!screenIsOn) {
                    screenIsOn = true;
                    screenOpenTs = Math.max(r.ts, startOfDay);
                    Log.d(TAG, "SCREEN_ON ts=" + r.ts + " screenOpenTs=" + screenOpenTs);
                }
                lastScreenEventTs = Math.max(lastScreenEventTs, r.ts);

            } else if (r.type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                sawScreenEvents = true;

                if (screenIsOn && screenOpenTs > 0 && r.ts > screenOpenTs) {
                    long segStart = screenOpenTs;
                    long segEnd = r.ts;
                    long segMs = segEnd - segStart;

                    screenMsDelta += segMs;

                    long addToday = overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
                    long addTomorrow = overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);

                    nightDeltaToday += addToday;
                    nightDeltaTomorrow += addTomorrow;

                    Log.d(TAG, "SCREEN_OFF ts=" + r.ts
                            + " seg=[" + segStart + "," + segEnd + "] segMs=" + segMs
                            + " nightAddToday=" + addToday
                            + " nightAddTomorrow=" + addTomorrow);
                }

                screenIsOn = false;
                screenOpenTs = -1L;
                lastScreenEventTs = Math.max(lastScreenEventTs, r.ts);

            } else if (r.type == UsageEvents.Event.KEYGUARD_HIDDEN) {

                // KPI sesiones = desbloqueos (incremental anti-duplicados)
                if (r.ts > lastUnlockEventTs) {
                    sawUnlockEvents = true;
                    unlockDelta++;
                    lastUnlockEventTs = Math.max(lastUnlockEventTs, r.ts);
                    Log.d(TAG, "UNLOCK ts=" + r.ts + " unlockDelta=" + unlockDelta);
                }
            }
        }

        // si pantalla sigue ON, cerramos hasta now
        if (screenIsOn && screenOpenTs > 0 && now > screenOpenTs) {
            long segStart = screenOpenTs;
            long segEnd = now;
            long segMs = segEnd - segStart;

            screenMsDelta += segMs;

            long addToday = overlapMs(segStart, segEnd, nightStartEndingToday, nightEndEndingToday);
            long addTomorrow = overlapMs(segStart, segEnd, nightStartEndingTomorrow, nightEndEndingTomorrow);

            nightDeltaToday += addToday;
            nightDeltaTomorrow += addTomorrow;

            // mover open para evitar doble conteo
            screenOpenTs = now;

            Log.d(TAG, "SCREEN_STILL_ON closeToNow seg=[" + segStart + "," + segEnd + "] segMs=" + segMs
                    + " nightAddToday=" + addToday
                    + " nightAddTomorrow=" + addTomorrow);
        }

        prefs.edit()
                .putBoolean(KEY_SCREEN_IS_ON, screenIsOn)
                .putLong(KEY_SCREEN_OPEN_TS, screenOpenTs)
                .putLong(KEY_LAST_SCREEN_EVENT_TS, lastScreenEventTs)
                .putLong(KEY_LAST_UNLOCK_EVENT_TS, lastUnlockEventTs)
                .apply();

        Log.d(TAG, "State(after): screenIsOn=" + screenIsOn
                + " screenOpenTs=" + screenOpenTs
                + " lastScreenEventTs=" + lastScreenEventTs
                + " lastUnlockEventTs=" + lastUnlockEventTs);

        Log.d(TAG, "Screen/unlock delta: screenMsDelta=" + screenMsDelta
                + " unlockDelta=" + unlockDelta
                + " sawScreenEvents=" + sawScreenEvents
                + " sawUnlockEvents=" + sawUnlockEvents);

        Log.d(TAG, "Night deltas (attribution): today+=" + nightDeltaToday + " tomorrow+=" + nightDeltaTomorrow);

        // 6) SAVE TODAY
        DailyMetricsEntity cur = db.userActivityDao().getDailyMetricsByDate(today);
        if (cur == null) {
            Log.e(TAG, "SAVE TODAY: cur==null (unexpected)");
        } else {

            cur.screen_ms = Math.max(0L, cur.screen_ms + screenMsDelta);
            cur.unlock_count = Math.max(0, cur.unlock_count + unlockDelta);

            // TU DEFINICIÓN: sesiones = desbloqueos
            cur.session_count = cur.unlock_count;

            cur.foreground_ms = foregroundMs;
            cur.unique_apps_count = uniqueAppsWithRealFg;
            cur.app_switch_count = appSwitchCount;

            cur.night_ms = Math.max(0L, cur.night_ms + nightDeltaToday);

            db.userActivityDao().upsertDailyMetrics(cur);

            Log.d(TAG, "SAVE TODAY: screen_ms=" + cur.screen_ms
                    + " unlock_count=" + cur.unlock_count
                    + " session_count=" + cur.session_count
                    + " foreground_ms=" + cur.foreground_ms
                    + " app_switch_count=" + cur.app_switch_count
                    + " unique_apps=" + cur.unique_apps_count
                    + " night_ms(today)=" + cur.night_ms);
        }

        // 7) SAVE TOMORROW (night)
        if (nightDeltaTomorrow > 0) {
            DailyMetricsEntity tm = db.userActivityDao().getDailyMetricsByDate(tomorrow);
            if (tm == null) {
                Log.e(TAG, "SAVE TOMORROW: tm==null (unexpected)");
            } else {
                tm.night_ms = Math.max(0L, tm.night_ms + nightDeltaTomorrow);
                db.userActivityDao().upsertDailyMetrics(tm);
                Log.d(TAG, "SAVE TOMORROW: night_ms+=" + nightDeltaTomorrow + " -> night_ms=" + tm.night_ms);
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
        Log.d(TAG, "daily_app_metric rows=" + rows.size());

        // 9) retención
        int delUsage = db.usageDao().deleteUsageEventsOlderThanDate(cutoffDate);
        int delScreen = db.userActivityDao().deleteScreenEventsOlderThanDate(cutoffDate);
        int delNotif = db.notificationDao().deleteNotificationEventsOlderThanDate(cutoffDate);

        Log.d(TAG, "Retention: cutoffDate=" + cutoffDate
                + " deletedUsage=" + delUsage
                + " deletedScreen=" + delScreen
                + " deletedNotif=" + delNotif);

        Log.d(TAG, "================ doWork END ================");

        return Result.success();
    }

    private String safePkg(BurnoutDatabase db, long appId) {
        try {
            String pkg = db.usageDao().getPackageNameByAppId(appId);
            return (pkg != null) ? pkg : ("appId=" + appId);
        } catch (Exception ex) {
            return ("appId=" + appId);
        }
    }

    /**
     * Filtra "no-apps" típicas: launcher/home, system ui, etc.
     * (puedes ampliar esta lista según tu móvil)
     */
    private boolean isNoisePackage(String pkg) {
        if (pkg == null) return true;

        // launcher/home (varía por fabricante)
        if (pkg.contains("launcher")) return true;
        if (pkg.contains("quickstep")) return true;

        // system UI
        if (pkg.equals("com.android.systemui")) return true;

        // si quieres ser aún más estricto:
        // if (pkg.startsWith("com.android.")) return true;

        return false;
    }

    /**
     * Timestamp local para epochDay a hora:minuto. (respeta TZ/DST)
     * Requiere que TimeKey.startOfDayMsFromEpochDay(epochDay) esté bien implementado.
     */
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

    private long resolveAppId(BurnoutDatabase db, String pkg) {
        Long existing = db.usageDao().getAppIdByPackageName(pkg);
        if (existing != null) return existing;

        long inserted = db.usageDao().insertApp(new AppEntity(pkg, pkg, null, false));
        if (inserted > 0) return inserted;

        Long after = db.usageDao().getAppIdByPackageName(pkg);
        return (after != null) ? after : -1L;
    }
}
