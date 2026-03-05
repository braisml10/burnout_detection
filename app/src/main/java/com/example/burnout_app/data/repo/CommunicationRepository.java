package com.example.burnout_app.data.repo;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.burnout_app.data.dao.CommunicationDAO;
import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.HourlyCommMetricsEntity;

import java.util.List;

public class CommunicationRepository {

    private final CommunicationDAO dao;

    public CommunicationRepository(Context ctx) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(ctx.getApplicationContext());
        dao = db.communicationDao();
    }

    public LiveData<DailyCommMetricsEntity> observeDaily(int epochDay) {
        return dao.observeDailyComm(epochDay);
    }

    public LiveData<List<HourlyCommMetricsEntity>> observeHourly(int epochDay) {
        return dao.observeHourly(epochDay);
    }

    public DailyCommMetricsEntity getDailyCommForDay(int epochDay) {
        return dao.getDailyComm(epochDay);
    }

    public List<HourlyCommMetricsEntity> getHourlyCommForDay(int epochDay) {
        return dao.getHourlyCommByDate(epochDay);
    }
}