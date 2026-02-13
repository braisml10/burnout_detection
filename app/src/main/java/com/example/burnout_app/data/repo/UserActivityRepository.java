package com.example.burnout_app.data.repo;

import android.content.Context;

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

    public LiveData<DailyMetricsEntity> observeDailyMetrics(int epochDay) {
        return userActivityDao.observeDailyMetrics(epochDay);
    }

    public LiveData<List<HourlyMetricsEntity>> observeHourlyMetrics(int epochDay) {
        return userActivityDao.observeHourlyMetricsByDate(epochDay);
    }

}

