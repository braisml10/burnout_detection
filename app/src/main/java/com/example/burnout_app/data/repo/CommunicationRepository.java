package com.example.burnout_app.data.repo;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.dao.CommunicationDAO;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.HourlyCommMetricsEntity;

import java.util.List;

public class CommunicationRepository {

    private final CommunicationDAO communicationDao;

    public CommunicationRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        communicationDao = db.communicationDao();
    }

    // ===================== DAILY COMM METRICS =====================

    public LiveData<DailyCommMetricsEntity> observeDailyCommMetrics(int epochDay) {
        return communicationDao.observeDailyCommMetrics(epochDay);
    }

    public DailyCommMetricsEntity getDailyCommMetrics(int epochDay) {
        return communicationDao.getDailyCommMetrics(epochDay);
    }

    // ===================== HOURLY COMM METRICS =====================

    public LiveData<List<HourlyCommMetricsEntity>> observeHourlyCommMetrics(int epochDay) {
        return communicationDao.observeHourlyCommMetrics(epochDay);
    }

    public List<HourlyCommMetricsEntity> getHourlyCommMetricsByDate(int epochDay) {
        return communicationDao.getHourlyCommMetricsByDate(epochDay);
    }
}