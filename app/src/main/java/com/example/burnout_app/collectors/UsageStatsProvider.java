package com.example.burnout_app.collectors;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.example.burnout_app.helpers.TimeKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UsageStatsProvider {

    private static final String TAG = "UsageStatsProvider";

    public static class RawEvent {
        public final String pkg;     // null para eventos de pantalla/keyguard
        public final int type;
        public final long ts;
        public final int date;

        public RawEvent(String pkg, int type, long ts, int date) {
            this.pkg = pkg;
            this.type = type;
            this.ts = ts;
            this.date = date;
        }
    }

    public static boolean hasUsageAccess(Context ctx) {
        AppOpsManager appOps = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Devuelve eventos UsageEvents entre [start, end], incluyendo:
     * - MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND
     * - SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE
     * - KEYGUARD_SHOWN / KEYGUARD_HIDDEN
     *
     * Nota: en algunos dispositivos, los eventos SCREEN/KEYGUARD pueden venir o no.
     * Si no vienen, haremos fallback en el Worker.
     */
    public static List<RawEvent> collectEvents(Context ctx, long start, long end) {
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return new ArrayList<>();

        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event e = new UsageEvents.Event();

        ArrayList<RawEvent> out = new ArrayList<>();

        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);

            int type = e.getEventType();
            long ts = e.getTimeStamp();
            int date = TimeKey.epochDayLocal(ts);

            boolean keep =
                    type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                            type == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                            type == UsageEvents.Event.SCREEN_INTERACTIVE ||
                            type == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                            type == UsageEvents.Event.KEYGUARD_SHOWN ||
                            type == UsageEvents.Event.KEYGUARD_HIDDEN;

            if (!keep) continue;

            String pkg = e.getPackageName();

            // Para SCREEN/KEYGUARD el package puede venir null o basura; lo anulamos para tratarlos como "system events".
            if (type == UsageEvents.Event.SCREEN_INTERACTIVE ||
                    type == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    type == UsageEvents.Event.KEYGUARD_SHOWN ||
                    type == UsageEvents.Event.KEYGUARD_HIDDEN) {
                pkg = null;
            }

            out.add(new RawEvent(pkg, type, ts, date));
        }

        out.sort(Comparator.comparingLong(a -> a.ts));

        Log.d(TAG, "collectEvents: start=" + start + " end=" + end + " -> " + out.size());
        return out;
    }

    // Si tu Worker llamaba a collectFgBgEvents, puedes mantener este wrapper:
    public static List<RawUsageEvent> collectFgBgEvents(Context ctx, long start, long end) {
        List<RawEvent> all = collectEvents(ctx, start, end);
        ArrayList<RawUsageEvent> fgBg = new ArrayList<>();
        for (RawEvent r : all) {
            if (r.pkg == null) continue;
            if (r.type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    r.type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                fgBg.add(new RawUsageEvent(r.pkg, r.type, r.ts, r.date));
            }
        }
        Log.d(TAG, "collectFgBgEvents: start=" + start + " end=" + end + " -> " + fgBg.size());
        return fgBg;
    }

    // Tu clase existente (la mantengo por compatibilidad con tu Worker actual)
    public static class RawUsageEvent {
        public final String pkg;
        public final int type;
        public final long ts;
        public final int date;

        public RawUsageEvent(String pkg, int type, long ts, int date) {
            this.pkg = pkg;
            this.type = type;
            this.ts = ts;
            this.date = date;
        }
    }
}
