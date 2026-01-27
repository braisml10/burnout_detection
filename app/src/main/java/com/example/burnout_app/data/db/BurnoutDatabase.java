package com.example.burnout_app.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.burnout_app.data.dao.CommunicationDAO;
import com.example.burnout_app.data.dao.NotificationDAO;
import com.example.burnout_app.data.dao.UsageDAO;
import com.example.burnout_app.data.dao.UserActivityDAO;
import com.example.burnout_app.data.entity.AppEntity;
import com.example.burnout_app.data.entity.AppUsageEventEntity;
import com.example.burnout_app.data.entity.DailyAppMetricsEntity;
import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.DailyMetricsEntity;
import com.example.burnout_app.data.entity.HourlyCommMetricsEntity;
import com.example.burnout_app.data.entity.HourlyMetricsEntity;
import com.example.burnout_app.data.entity.NotificationEventEntity;
import com.example.burnout_app.data.entity.ScreenEventEntity;

@Database(entities =  {
        AppEntity.class,
        AppUsageEventEntity.class,
        DailyAppMetricsEntity.class,
        DailyCommMetricsEntity.class,
        DailyMetricsEntity.class,
        HourlyCommMetricsEntity.class,
        HourlyMetricsEntity.class,
        NotificationEventEntity.class,
        ScreenEventEntity.class },
        version = 1,
        exportSchema = false
)

public abstract class BurnoutDatabase extends RoomDatabase {

    private static volatile BurnoutDatabase INSTANCE;

    //public abstract BurnoutDao burnoutDAO();

    public static BurnoutDatabase getInstance(Context context) {
        // Comprobar si la bd existe en memoria
        if (INSTANCE == null){
            // evitar que 2 hilos creen la BD al mismo tiempo
            synchronized (BurnoutDatabase.class) {
                if (INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), BurnoutDatabase.class, "Burnout.db" ).build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract UserActivityDAO userActivityDao();
    public abstract NotificationDAO notificationDao();
    public abstract UsageDAO usageDao();
    public abstract CommunicationDAO communicationDao();

    @NonNull
    @Override
    protected InvalidationTracker createInvalidationTracker() {
        return null;
    }

    @Override
    public void clearAllTables() {

    }
}
