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
        public final String pkg;
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

    public static boolean hasUsageAccess(Context ctx) {
        AppOpsManager appOps = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;

        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static List<RawEvent> collectEvents(Context ctx, long start, long end) {
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return new ArrayList<>();

        ArrayList<RawEvent> out = new ArrayList<>();
        UsageEvents events = usm.queryEvents(start, end);
        if (events == null) return out;

        UsageEvents.Event event = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(event);

            int type = event.getEventType();
            if (!shouldKeepEventType(type)) continue;

            long ts = event.getTimeStamp();
            int date = TimeKey.epochDayLocal(ts);

            String pkg = event.getPackageName();
            if (isSystemEventType(type)) {
                pkg = null;
            }

            out.add(new RawEvent(pkg, type, ts, date));
        }

        out.sort(Comparator.comparingLong(e -> e.ts));

        Log.d(TAG, "collectEvents: start=" + start + " end=" + end + " -> " + out.size());
        return out;
    }

    // Compatibility wrapper for callers that only need foreground/background app events.
    public static List<RawUsageEvent> collectFgBgEvents(Context ctx, long start, long end) {
        List<RawEvent> all = collectEvents(ctx, start, end);
        ArrayList<RawUsageEvent> fgBg = new ArrayList<>();

        for (RawEvent r : all) {
            if (r.pkg == null) continue;
            if (!isForegroundBackgroundEventType(r.type)) continue;

            fgBg.add(new RawUsageEvent(r.pkg, r.type, r.ts, r.date));
        }

        Log.d(TAG, "collectFgBgEvents: start=" + start + " end=" + end + " -> " + fgBg.size());
        return fgBg;
    }

    private static boolean shouldKeepEventType(int type) {
        return type == UsageEvents.Event.MOVE_TO_FOREGROUND
                || type == UsageEvents.Event.MOVE_TO_BACKGROUND
                || type == UsageEvents.Event.SCREEN_INTERACTIVE
                || type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                || type == UsageEvents.Event.KEYGUARD_SHOWN
                || type == UsageEvents.Event.KEYGUARD_HIDDEN;
    }

    private static boolean isSystemEventType(int type) {
        return type == UsageEvents.Event.SCREEN_INTERACTIVE
                || type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                || type == UsageEvents.Event.KEYGUARD_SHOWN
                || type == UsageEvents.Event.KEYGUARD_HIDDEN;
    }

    private static boolean isForegroundBackgroundEventType(int type) {
        return type == UsageEvents.Event.MOVE_TO_FOREGROUND
                || type == UsageEvents.Event.MOVE_TO_BACKGROUND;
    }
}