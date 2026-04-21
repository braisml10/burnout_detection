package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "burnout_risk")
public class BurnoutRiskEntity {

    @PrimaryKey
    public long epochDay;

    public double screenTimeScore;
    public double fragmentationScore;
    public double nightUseScore;
    public double notificationPressureScore;
    public double trendDeviationScore;

    public double riskScore;
}
