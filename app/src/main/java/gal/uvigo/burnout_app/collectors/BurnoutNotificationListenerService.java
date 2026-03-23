package gal.uvigo.burnout_app.collectors;

import android.app.Notification;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.AppEntity;
import gal.uvigo.burnout_app.data.entity.NotificationEventEntity;
import gal.uvigo.burnout_app.helpers.AppCategoryResolver;
import gal.uvigo.burnout_app.helpers.TimeKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BurnoutNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "NotifListener";
    private static final String SOURCE = "nls";

    private ExecutorService dbExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        dbExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        final String pkg = sbn.getPackageName();
        if (pkg == null || pkg.trim().isEmpty()) return;

        final Notification notification = sbn.getNotification();
        if (notification == null) return;

        // Ignore persistent notifications such as charging or USB state.
        final boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        if (ongoing) return;

        // Ignore notification group summaries.
        final boolean isGroupSummary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (isGroupSummary) return;

        final long ts = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        final int date = TimeKey.epochDayLocal(ts);
        final int hour = TimeKey.hourOfDayLocal(ts);
        final String category = (notification.category != null) ? notification.category : "unknown";

        if (dbExecutor == null) return;

        final Context ctx = getApplicationContext();
        dbExecutor.execute(() -> {
            BurnoutDatabase db = BurnoutDatabase.getInstance(ctx);

            long appId = ensureAppId(db, ctx, pkg);
            if (appId <= 0) return;

            NotificationEventEntity event = new NotificationEventEntity(
                    ts,
                    date,
                    hour,
                    appId,
                    category,
                    ongoing,
                    SOURCE
            );

            db.notificationDao().insertNotificationEvent(event);
        });
    }

    private long ensureAppId(BurnoutDatabase db, Context ctx, String pkg) {
        try {
            Long existing = db.usageDao().getAppIdByPackageName(pkg);
            if (existing != null && existing > 0) return existing;

            String label = AppCategoryResolver.resolveAppLabel(ctx, pkg);
            if (label == null || label.trim().isEmpty()) {
                label = pkg;
            }

            String cat = AppCategoryResolver.resolveCategory(ctx, pkg);
            if (cat == null) {
                cat = AppCategoryResolver.OTHER;
            }

            long inserted = db.usageDao().insertApp(new AppEntity(pkg, label, cat, false));
            if (inserted > 0) return inserted;

            Long after = db.usageDao().getAppIdByPackageName(pkg);
            return (after != null) ? after : -1L;

        } catch (Exception ex) {
            Log.e(TAG, "ensureAppId failed for pkg=" + pkg, ex);
            return -1L;
        }
    }
}