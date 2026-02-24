package com.example.burnout_app.collectors;

import android.app.Notification;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.AppEntity;
import com.example.burnout_app.data.entity.NotificationEventEntity;
import com.example.burnout_app.helpers.AppCategoryResolver;
import com.example.burnout_app.helpers.TimeKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BurnoutNotificationListenerService extends NotificationListenerService {

    private static final String SOURCE = "nls";
    private ExecutorService dbExec;

    @Override
    public void onCreate() {
        super.onCreate();
        dbExec = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbExec != null) dbExec.shutdown();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        final String pkg = sbn.getPackageName();
        if (pkg == null || pkg.trim().isEmpty()) return;

        final Notification n = sbn.getNotification();
        if (n == null) return;

        // 🔒 Ignorar notificaciones persistentes (USB, carga, etc.)
        final boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        if (ongoing) return;

        // 🔒 Ignorar group summaries
        final boolean isGroupSummary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (isGroupSummary) return;

        final long ts = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        final int date = TimeKey.epochDayLocal(ts);
        final int hour = TimeKey.hourOfDayLocal(ts);

        final String category = (n.category != null) ? n.category : "unknown";

        dbExec.execute(() -> {
            Context ctx = getApplicationContext();
            BurnoutDatabase db = BurnoutDatabase.getInstance(ctx);

            long appId = ensureAppId(db, ctx, pkg);
            if (appId <= 0) return;

            NotificationEventEntity e = new NotificationEventEntity(
                    ts, date, hour, appId, category, ongoing, SOURCE
            );

            db.notificationDao().insertNotificationEvent(e);
        });
    }

    private long ensureAppId(BurnoutDatabase db, Context ctx, String pkg) {
        try {
            Long existing = db.usageDao().getAppIdByPackageName(pkg);
            if (existing != null && existing > 0) return existing;

            String label = AppCategoryResolver.resolveAppLabel(ctx, pkg);
            if (label == null || label.trim().isEmpty()) label = pkg;

            String cat = AppCategoryResolver.resolveCategory(ctx, pkg);
            if (cat == null) cat = AppCategoryResolver.OTHER;

            long inserted = db.usageDao().insertApp(new AppEntity(pkg, label, cat, false));
            if (inserted > 0) return inserted;

            Long after = db.usageDao().getAppIdByPackageName(pkg);
            return (after != null) ? after : -1L;
        } catch (Throwable t) {
            return -1L;
        }
    }
}