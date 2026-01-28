package com.example.burnout_app.collectors;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.example.burnout_app.helpers.TimeKey;

import java.util.ArrayList;
import java.util.List;

public class UsageStatsProvider {

    private static final String TAG = "UsageStatsProvider";

    public static class RawUsageEvent {
        public final long ts;
        public final int type;
        public final String pkg;
        public final int date;

        public RawUsageEvent(long ts, int type, String pkg, int date) {
            this.ts = ts;
            this.type = type;
            this.pkg = pkg;
            this.date = date;
        }
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static List<RawUsageEvent> collectFgBgEvents(Context context, long start, long end) {
        List<RawUsageEvent> out = new ArrayList<>();

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            Log.e(TAG, "UsageStatsManager is null");
            return out;
        }

        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event e = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(e);

            int type = e.getEventType();
            if (type != UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    type != UsageEvents.Event.MOVE_TO_BACKGROUND) {
                continue;
            }

            long ts = e.getTimeStamp();
            String pkg = e.getPackageName();
            if (pkg == null) pkg = "unknown";

            int date = TimeKey.epochDayLocal(ts);
            out.add(new RawUsageEvent(ts, type, pkg, date));
        }

        Log.d(TAG, "collectFgBgEvents: start=" + start + " end=" + end + " -> " + out.size());
        return out;
    }
}
