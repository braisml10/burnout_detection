package gal.uvigo.burnout_app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;

@Dao
public interface BurnoutRiskDAO {

    // ===================== DAILY BURNOUT RISK =====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertBurnoutRisk(BurnoutRiskEntity burnoutRisk);

    @Query("SELECT * FROM burnout_risk WHERE epochDay = :epochDay LIMIT 1")
    BurnoutRiskEntity getBurnoutRiskByDate(int epochDay);

    @Query("SELECT * FROM burnout_risk WHERE epochDay = :epochDay LIMIT 1")
    LiveData<BurnoutRiskEntity> observeBurnoutRiskByDate(int epochDay);

    @Query("SELECT * FROM burnout_risk ORDER BY epochDay DESC LIMIT 1")
    LiveData<BurnoutRiskEntity> observeLatestBurnoutRisk();

    @Query("SELECT * FROM burnout_risk ORDER BY epochDay DESC LIMIT :limit")
    LiveData<List<BurnoutRiskEntity>> observeLatestBurnoutRiskDays(int limit);

    @Query("SELECT * FROM burnout_risk WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC")
    LiveData<List<BurnoutRiskEntity>> observeBurnoutRiskRange(int startDay, int endDay);

    @Query("DELETE FROM burnout_risk WHERE epochDay < :cutoffDate")
    int deleteBurnoutRiskOlderThanDate(int cutoffDate);
}