package gal.uvigo.burnout_app.data.repo;

import android.content.Context;
import android.database.Cursor;

import androidx.lifecycle.LiveData;

import gal.uvigo.burnout_app.data.dao.UserActivityDAO;
import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyMetricsEntity;

import java.util.List;

public class UserActivityRepository {

    private final UserActivityDAO userActivityDao;

    public UserActivityRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        userActivityDao = db.userActivityDao();
    }

    // ===================== DAILY METRICS =====================

    public LiveData<DailyMetricsEntity> observeDailyMetrics(int epochDay) {
        return userActivityDao.observeDailyMetrics(epochDay);
    }

    // ===================== HOURLY METRICS =====================

    public LiveData<List<HourlyMetricsEntity>> observeHourlyMetrics(int epochDay) {
        return userActivityDao.observeHourlyMetricsByDate(epochDay);
    }

    public int[] getAppSwitchCountByHour(int date) {
        int[] out = new int[24];

        Cursor cursor = userActivityDao.getAppSwitchCountByHourCursor(date);
        try {
            int hourIndex = cursor.getColumnIndexOrThrow("hour");
            int countIndex = cursor.getColumnIndexOrThrow("switches");

            while (cursor.moveToNext()) {
                int hour = cursor.getInt(hourIndex);
                int count = cursor.getInt(countIndex);

                if (hour >= 0 && hour <= 23) {
                    out[hour] = count;
                }
            }
        } finally {
            cursor.close();
        }

        return out;
    }
}