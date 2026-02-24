package com.example.burnout_app.data.repo;

import android.content.Context;
import android.database.Cursor;

import com.example.burnout_app.data.db.BurnoutDatabase;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    private final BurnoutDatabase db;

    public NotificationRepository(Context ctx) {
        db = BurnoutDatabase.getInstance(ctx.getApplicationContext());
    }

    // =========================================================
    // TOTAL (daily_metrics)
    // =========================================================

    public int getTotalNotificationsForDay(int date) {
        // daily_metrics.notification_count ya lo rellena el worker
        Integer v = db.userActivityDao().getNotificationCountForDay(date);
        return (v != null) ? v : 0;
    }

    // =========================================================
    // HOURLY TREND (hourly_metric)
    // =========================================================

    /** notification_count por hora 0..23 desde hourly_metric */
    public int[] getNotificationsPerHourForDay(int date) {

        int[] out = new int[24];

        Cursor c = db.userActivityDao().getNotificationsPerHourForDay(date);
        try {
            int iHour = c.getColumnIndexOrThrow("hour");
            int iCnt  = c.getColumnIndexOrThrow("notifs");

            while (c.moveToNext()) {
                int h = c.getInt(iHour);
                int cnt = c.getInt(iCnt);
                if (h >= 0 && h <= 23) out[h] = cnt;
            }
        } finally {
            c.close();
        }
        return out;
    }

    // =========================================================
    // TOP APPS (raw notification_event + app join via usageDao)
    // =========================================================

    public static class TopNotifAppRow {
        public final long appId;
        public final String name;
        public final String packageName;
        public final int count;

        public TopNotifAppRow(long appId, String name, String packageName, int count) {
            this.appId = appId;
            this.name = name;
            this.packageName = packageName;
            this.count = count;
        }
    }

    /** Top apps por notificaciones desde notification_event (raw), con name/pkg desde app table */
    public List<TopNotifAppRow> getTopAppsByNotificationsForDay(int date, int limit) {

        List<TopNotifAppRow> out = new ArrayList<>();

        Cursor c = db.notificationDao().topAppsCursor(date, limit);
        try {
            int iAppId = c.getColumnIndexOrThrow("app_id");
            int iCnt   = c.getColumnIndexOrThrow("c");

            while (c.moveToNext()) {
                long appId = c.getLong(iAppId);
                int cnt = c.getInt(iCnt);

                String name = db.usageDao().getNameByAppId(appId);
                String pkg  = db.usageDao().getPackageNameByAppId(appId);

                if (name == null) name = (pkg != null) ? pkg : ("appId=" + appId);
                if (pkg == null) pkg = "";

                out.add(new TopNotifAppRow(appId, name, pkg, cnt));
            }
        } finally {
            c.close();
        }

        return out;
    }

    // =========================================================
    // AVERAGE/HOUR helper (opcional)
    // =========================================================

    /**
     * Horas "activas" del día (screen_ms > 0) desde hourly_metric.
     * Útil para avg/hora: totalNotifs / max(1, hoursActive).
     */
    public int getActiveHoursForDay(int date) {
        Cursor c = db.userActivityDao().getActiveHoursForDay(date);
        try {
            if (c.moveToFirst()) {
                int i = c.getColumnIndexOrThrow("active_hours");
                return c.getInt(i);
            }
            return 0;
        } finally {
            c.close();
        }
    }
}
