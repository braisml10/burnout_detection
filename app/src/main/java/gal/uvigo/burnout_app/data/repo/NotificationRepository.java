package gal.uvigo.burnout_app.data.repo;

import android.content.Context;
import android.database.Cursor;

import gal.uvigo.burnout_app.data.dao.NotificationDAO;
import gal.uvigo.burnout_app.data.dao.UsageDAO;
import gal.uvigo.burnout_app.data.dao.UserActivityDAO;
import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.helpers.TimeKey;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    private final NotificationDAO notificationDao;
    private final UsageDAO usageDao;
    private final UserActivityDAO userActivityDao;

    public NotificationRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        notificationDao = db.notificationDao();
        usageDao = db.usageDao();
        userActivityDao = db.userActivityDao();
    }

    // ===================== DAILY NOTIFICATION COUNTS =====================

    public int getNotificationCountByDate(int date) {
        Integer value = userActivityDao.getNotificationCountByDate(date);
        return (value != null) ? value : 0;
    }

    // ===================== HOURLY NOTIFICATION COUNTS =====================

    public int[] getNotificationCountByHour(int date) {
        int[] out = new int[24];

        Cursor cursor = userActivityDao.getNotificationCountByHourCursor(date);
        try {
            int hourIndex = cursor.getColumnIndexOrThrow("hour");
            int countIndex = cursor.getColumnIndexOrThrow("notifs");

            while (cursor.moveToNext()) {
                int hour = cursor.getInt(hourIndex);
                int count = cursor.getInt(countIndex);

                if (hour >= TimeKey.MIN_HOUR_OF_DAY && hour <= TimeKey.MAX_HOUR_OF_DAY) {
                    out[hour] = count;
                }
            }
        } finally {
            cursor.close();
        }

        return out;
    }

    // ===================== TOP NOTIFICATION APPS =====================

    public static class TopNotificationAppRow {
        public final long appId;
        public final String name;
        public final String packageName;
        public final int count;

        public TopNotificationAppRow(long appId, String name, String packageName, int count) {
            this.appId = appId;
            this.name = name;
            this.packageName = packageName;
            this.count = count;
        }
    }

    public List<TopNotificationAppRow> getTopNotificationAppsByDate(int date, int limit) {
        List<TopNotificationAppRow> out = new ArrayList<>();

        Cursor cursor = notificationDao.getTopNotificationAppsCursor(date, limit);
        try {
            int appIdIndex = cursor.getColumnIndexOrThrow("app_id");
            int countIndex = cursor.getColumnIndexOrThrow("c");

            while (cursor.moveToNext()) {
                long appId = cursor.getLong(appIdIndex);
                int count = cursor.getInt(countIndex);

                String name = usageDao.getAppNameByAppId(appId);
                String packageName = usageDao.getAppPackageNameByAppId(appId);

                if (name == null) {
                    name = (packageName != null) ? packageName : ("appId=" + appId);
                }
                if (packageName == null) {
                    packageName = "";
                }

                out.add(new TopNotificationAppRow(appId, name, packageName, count));
            }
        } finally {
            cursor.close();
        }

        return out;
    }

    // ===================== NOTIFICATION COUNTS BY CATEGORY =====================

    public static class NotificationCategoryCountRow {
        public final String category;
        public final int count;

        public NotificationCategoryCountRow(String category, int count) {
            this.category = category;
            this.count = count;
        }
    }

    public List<NotificationCategoryCountRow> getNotificationCountByCategory(int date) {
        List<NotificationCategoryCountRow> out = new ArrayList<>();

        Cursor cursor = notificationDao.getNotificationCountByAppCategoryCursor(date);
        try {
            int categoryIndex = cursor.getColumnIndexOrThrow("app_category");
            int countIndex = cursor.getColumnIndexOrThrow("c");

            while (cursor.moveToNext()) {
                String category = cursor.getString(categoryIndex);
                int count = cursor.getInt(countIndex);

                if (category == null || category.trim().isEmpty()) {
                    category = "OTHER";
                }

                out.add(new NotificationCategoryCountRow(category, count));
            }
        } finally {
            cursor.close();
        }

        return out;
    }

    // ===================== ACTIVE HOURS =====================

    public int getActiveHourCountByDate(int date) {
        return userActivityDao.getActiveHourCountByDate(date);
    }
}