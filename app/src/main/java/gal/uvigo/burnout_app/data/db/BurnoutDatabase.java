package gal.uvigo.burnout_app.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import gal.uvigo.burnout_app.data.dao.BurnoutRiskDAO;
import gal.uvigo.burnout_app.data.dao.CommunicationDAO;
import gal.uvigo.burnout_app.data.dao.NotificationDAO;
import gal.uvigo.burnout_app.data.dao.UsageDAO;
import gal.uvigo.burnout_app.data.dao.UserActivityDAO;
import gal.uvigo.burnout_app.data.dao.UserProfileDAO;
import gal.uvigo.burnout_app.data.entity.AppEntity;
import gal.uvigo.burnout_app.data.entity.AppUsageEventEntity;
import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;
import gal.uvigo.burnout_app.data.entity.DailyAppMetricsEntity;
import gal.uvigo.burnout_app.data.entity.DailyCommMetricsEntity;
import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyCommMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyMetricsEntity;
import gal.uvigo.burnout_app.data.entity.NotificationEventEntity;
import gal.uvigo.burnout_app.data.entity.UserProfileEntity;

@Database(
        entities = {
                AppEntity.class,
                AppUsageEventEntity.class,
                DailyAppMetricsEntity.class,
                DailyCommMetricsEntity.class,
                DailyMetricsEntity.class,
                HourlyCommMetricsEntity.class,
                HourlyMetricsEntity.class,
                NotificationEventEntity.class,
                UserProfileEntity.class,
                BurnoutRiskEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class BurnoutDatabase extends RoomDatabase {

    private static volatile BurnoutDatabase INSTANCE;

    public static BurnoutDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (BurnoutDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            BurnoutDatabase.class,
                            "Burnout.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract UserActivityDAO userActivityDao();
    public abstract NotificationDAO notificationDao();
    public abstract UsageDAO usageDao();
    public abstract CommunicationDAO communicationDao();
    public abstract UserProfileDAO userProfileDAO();
    public abstract BurnoutRiskDAO burnoutRiskDao();
}
