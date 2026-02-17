package com.example.burnout_app.data.repo;

import android.content.Context;
import android.database.Cursor;

import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.dao.UserActivityDAO;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;

import java.util.List;

public class UserActivityRepository {

    private final UserActivityDAO userActivityDao;

    public UserActivityRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        userActivityDao = db.userActivityDao();
    }

    // =========================================================
    // Observables (UI: Dashboard / DailyDetail)
    // =========================================================

    public LiveData<DailyMetricsEntity> observeDailyMetrics(int epochDay) {
        return userActivityDao.observeDailyMetrics(epochDay);
    }

    public LiveData<List<HourlyMetricsEntity>> observeHourlyMetrics(int epochDay) {
        return userActivityDao.observeHourlyMetricsByDate(epochDay);
    }

    // =========================================================
    // Hourly helpers (UI: Multitask -> switches per hour)
    // NOTE: Este métod corresponde a hourly_metric, así que vive aquí,
    // no en UsageRepository.
    // =========================================================

    public int[] getSwitchesPerHourForDay(int date) {

        int[] out = new int[24]; // 0..23

        Cursor c = userActivityDao.getSwitchesPerHourForDay(date);
        try {
            int iHour = c.getColumnIndexOrThrow("hour");
            int iCnt  = c.getColumnIndexOrThrow("switches");

            while (c.moveToNext()) {
                int hour = c.getInt(iHour);
                int cnt  = c.getInt(iCnt);

                if (hour >= 0 && hour <= 23) {
                    out[hour] = cnt;
                }
            }
        } finally {
            c.close();
        }

        return out;
    }
}
