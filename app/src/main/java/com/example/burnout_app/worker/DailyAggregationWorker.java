package com.example.burnout_app.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.burnout_app.helpers.TimeKey;
import com.example.burnout_app.helpers.RetentionPolicy;
import com.example.burnout_app.data.db.BurnoutDatabase;


public class DailyAggregationWorker extends Worker {

    public DailyAggregationWorker(@NonNull Context context,
                                  @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        BurnoutDatabase db = BurnoutDatabase.getInstance(getApplicationContext());

        int today = TimeKey.epochDayLocal(System.currentTimeMillis());
        int cutoffDate = today - RetentionPolicy.RAW_EVENTS_RETENTION_DAYS;

        db.userActivityDao().deleteScreenEventsOlderThanDate(cutoffDate);
        db.notificationDao().deleteNotificationEventsOlderThanDate(cutoffDate);
        db.usageDao().deleteUsageEventsOlderThanDate(cutoffDate);

        return Result.success();
    }
}
