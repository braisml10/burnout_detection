package com.example.burnout_app.data.repo;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.example.burnout_app.data.db.BurnoutDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageRepository {

    private final BurnoutDatabase db;

    public UsageRepository(Context ctx) {
        Context appCtx = ctx.getApplicationContext();
        db = BurnoutDatabase.getInstance(appCtx);
    }

    // =========================================================
    // CATEGORY TOTALS (DailyAppMetric + App join) - UI: Multitask
    // =========================================================

    /**
     * category -> total_ms (solo apps no ignoradas)
     * Keys esperadas: SOCIAL, ENTERTAINMENT, MESSAGING, WORK, OTHER
     */
    public Map<String, Long> getCategoryTotalsMsForDay(int date) {

        Map<String, Long> out = new HashMap<>();

        Cursor c = db.usageDao().getCategoryTotalsMsForDay(date);
        try {
            int iCat = c.getColumnIndexOrThrow("category");
            int iMs  = c.getColumnIndexOrThrow("total_ms");

            while (c.moveToNext()) {
                String cat = c.getString(iCat);
                long ms = c.getLong(iMs);

                Log.d("CAT_SQL", "category=" + cat + " ms=" + ms);
                out.put(cat, ms);
            }
        } finally {
            c.close();
        }

        // Garantiza que existan todas las categorías en el mapa (evita NPE/keys faltantes en UI)
        ensure(out, "SOCIAL");
        ensure(out, "ENTERTAINMENT");
        ensure(out, "MESSAGING");
        ensure(out, "WORK");
        ensure(out, "OTHER");

        long sum = 0L;
        for (Long v : out.values()) sum += v;
        Log.d("CAT_SQL", "TOTAL SUM FROM SQL = " + sum);

        return out;
    }

    // =========================================================
    // TOTAL FOREGROUND (DailyAppMetric + App ignored filter)
    // =========================================================

    /**
     * Total time using apps (foreground_ms) del día, excluyendo apps ignoradas.
     */
    public long getTotalForegroundMsForDay(int date) {

        long total = 0L;

        Cursor c = db.usageDao().getTotalForegroundMsForDay(date);
        try {
            if (c.moveToFirst()) {
                int i = c.getColumnIndexOrThrow("total_ms");
                if (!c.isNull(i)) {
                    total = c.getLong(i);
                }
            }
        } finally {
            c.close();
        }

        Log.d("TOTAL_SQL", "TOTAL FOREGROUND MS = " + total);

        return total;
    }

    // =========================================================
    // TOP APPS (DailyAppMetric + App join) - UI: Multitask
    // =========================================================

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

    /**
     * Top N apps por foreground_ms del día, excluyendo apps ignoradas.
     */
    public List<TopAppRow> getTopAppsForDay(int date, int limit) {
        List<TopAppRow> out = new ArrayList<>();

        Cursor c = db.usageDao().getTopAppsForDay(date, limit);
        try {
            int iId = c.getColumnIndexOrThrow("app_id");
            int iName = c.getColumnIndexOrThrow("name");
            int iPkg = c.getColumnIndexOrThrow("package_name");
            int iMs = c.getColumnIndexOrThrow("total_ms");

            while (c.moveToNext()) {
                long id = c.getLong(iId);
                String name = c.getString(iName);
                String pkg = c.getString(iPkg);
                long ms = c.getLong(iMs);

                out.add(new TopAppRow(id, name, pkg, ms));
                Log.d("TOP_APPS_SQL", "name=" + name + " ms=" + ms);
            }
        } finally {
            c.close();
        }

        return out;
    }

    // =========================================================
    // Helpers
    // =========================================================

    private static void ensure(Map<String, Long> m, String k) {
        if (!m.containsKey(k)) m.put(k, 0L);
    }
}
