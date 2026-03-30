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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(BurnoutRiskEntity entity);

    @Query("SELECT * FROM burnout_risk WHERE epochDay = :epochDay LIMIT 1")
    BurnoutRiskEntity getByDay(long epochDay);

    @Query("SELECT * FROM burnout_risk WHERE epochDay = :epochDay LIMIT 1")
    LiveData<BurnoutRiskEntity> observeByDay(long epochDay);

    @Query("SELECT * FROM burnout_risk ORDER BY epochDay DESC LIMIT 1")
    LiveData<BurnoutRiskEntity> observeLatest();

    @Query("SELECT * FROM burnout_risk ORDER BY epochDay DESC LIMIT :limit")
    LiveData<List<BurnoutRiskEntity>> observeLatestDays(int limit);

    @Query("SELECT * FROM burnout_risk WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC")
    LiveData<List<BurnoutRiskEntity>> observeRange(long startDay, long endDay);

    @Query("DELETE FROM burnout_risk WHERE epochDay < :minEpochDay")
    int deleteOlderThan(long minEpochDay);
}
