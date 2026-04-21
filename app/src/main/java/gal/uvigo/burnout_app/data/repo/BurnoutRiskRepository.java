package gal.uvigo.burnout_app.data.repo;

import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;

import gal.uvigo.burnout_app.data.dao.BurnoutRiskDAO;
import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;

public class BurnoutRiskRepository {

    private final BurnoutRiskDAO burnoutRiskDao;

    public BurnoutRiskRepository(Context context) {
        BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
        burnoutRiskDao = db.burnoutRiskDao();
    }

    public LiveData<BurnoutRiskEntity> observeBurnoutRiskByDate(int date) {
        return burnoutRiskDao.observeBurnoutRiskByDate(date);
    }

    public LiveData<BurnoutRiskEntity> observeLatestBurnoutRisk() {
        return burnoutRiskDao.observeLatestBurnoutRisk();
    }

    public LiveData<List<BurnoutRiskEntity>> observeLatestBurnoutRiskDays(int limit) {
        return burnoutRiskDao.observeLatestBurnoutRiskDays(limit);
    }

    public LiveData<List<BurnoutRiskEntity>> observeBurnoutRiskRange(int startDay, int endDay) {
        return burnoutRiskDao.observeBurnoutRiskRange(startDay, endDay);
    }
}