package com.example.burnout_app.data.repo;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.example.burnout_app.data.dao.UsageDAO;
import com.example.burnout_app.data.db.BurnoutDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageRepository {

    private final UsageDAO usageDao;

    public UsageRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        usageDao = db.usageDao();
    }

    // ===================== CATEGORY TOTALS =====================

    public Map<String, Long> getCategoryTotalsMsForDay(int date) {
        Map<String, Long> out = new HashMap<>();

        Cursor cursor = usageDao.getCategoryTotalsMsForDayCursor(date);
        try {
            int categoryIndex = cursor.getColumnIndexOrThrow("category");
            int totalMsIndex = cursor.getColumnIndexOrThrow("total_ms");

            while (cursor.moveToNext()) {
                String category = cursor.getString(categoryIndex);
                long totalMs = cursor.getLong(totalMsIndex);

                Log.d("CAT_SQL", "category=" + category + " ms=" + totalMs);
                out.put(category, totalMs);
            }
        } finally {
            cursor.close();
        }

        ensureCategory(out, "SOCIAL");
        ensureCategory(out, "ENTERTAINMENT");
        ensureCategory(out, "MESSAGING");
        ensureCategory(out, "WORK");
        ensureCategory(out, "OTHER");

        long total = 0L;
        for (Long value : out.values()) {
            total += value;
        }
        Log.d("CAT_SQL", "TOTAL SUM FROM SQL = " + total);

        return out;
    }

    // ===================== TOTAL FOREGROUND TIME =====================

    public long getTotalForegroundMsForDay(int date) {
        long total = 0L;

        Cursor cursor = usageDao.getTotalForegroundMsForDayCursor(date);
        try {
            if (cursor.moveToFirst()) {
                int totalMsIndex = cursor.getColumnIndexOrThrow("total_ms");
                if (!cursor.isNull(totalMsIndex)) {
                    total = cursor.getLong(totalMsIndex);
                }
            }
        } finally {
            cursor.close();
        }

        Log.d("TOTAL_SQL", "TOTAL FOREGROUND MS = " + total);
        return total;
    }

    // ===================== TOP APPS =====================

    public static class TopAppRow {
        public final long appId;
        public final String name;
        public final String packageName;
        public final long totalMs;

        public TopAppRow(long appId, String name, String packageName, long totalMs) {
            this.appId = appId;
            this.name = name;
            this.packageName = packageName;
            this.totalMs = totalMs;
        }
    }

    public List<TopAppRow> getTopAppsByDate(int date, int limit) {
        List<TopAppRow> out = new ArrayList<>();

        Cursor cursor = usageDao.getTopAppsForDayCursor(date, limit);
        try {
            int appIdIndex = cursor.getColumnIndexOrThrow("app_id");
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            int packageNameIndex = cursor.getColumnIndexOrThrow("package_name");
            int totalMsIndex = cursor.getColumnIndexOrThrow("total_ms");

            while (cursor.moveToNext()) {
                long appId = cursor.getLong(appIdIndex);
                String name = cursor.getString(nameIndex);
                String packageName = cursor.getString(packageNameIndex);
                long totalMs = cursor.getLong(totalMsIndex);

                out.add(new TopAppRow(appId, name, packageName, totalMs));
                Log.d("TOP_APPS_SQL", "name=" + name + " ms=" + totalMs);
            }
        } finally {
            cursor.close();
        }

        return out;
    }

    // ===================== HELPERS =====================

    private static void ensureCategory(Map<String, Long> categories, String category) {
        if (!categories.containsKey(category)) {
            categories.put(category, 0L);
        }
    }
}