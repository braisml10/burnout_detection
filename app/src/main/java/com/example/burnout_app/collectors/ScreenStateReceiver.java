package com.example.burnout_app.collectors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.ScreenEventEntity;
import com.example.burnout_app.helpers.TimeKey;

import java.sql.Time;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenStateReceiver";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent){
        String action = intent.getAction();
        if(action == null) return;

        final int state;

        if (Intent.ACTION_SCREEN_ON.equals(action)) {
            state = 1;
        }
        else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            state = 0;
        }
        // unlock_count
        else if (Intent.ACTION_USER_PRESENT.equals(action)) {

            final long ts = System.currentTimeMillis();
            final int date = TimeKey.epochDayLocal(ts);

            IO.execute(() -> {
                try {
                    BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
                    // asegurar fila diaria (por si aún no existe)
                    db.userActivityDao().insertDailyIfMissing(
                            new com.example.burnout_app.data.entity.DailyMetricsEntity(date, 0L, 0, 0L, 0, 0, 0, 0, 0L)
                    );
                    db.userActivityDao().incDailyUnlocks(date, 1);
                    Log.d(TAG, "incDailyUnlocks date=" + date);
                } catch (Exception e) {
                    Log.e(TAG, "Failed incDailyUnlocks", e);
                }
            });
            return;
        }
        else {
            return;
        }

        long ts = System.currentTimeMillis();
        int date = TimeKey.epochDayLocal(ts);
        int hour = TimeKey.hourOfDayLocal(ts);

        IO.execute(() -> {
            try {
                BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
                ScreenEventEntity e = new ScreenEventEntity(ts, state, "broadcast", date, hour);
                db.userActivityDao().insertScreenEvent(e);
                Log.d(TAG, "Inserted screen_event state=" + state + " date=" + date + " hour=" + hour);
            } catch (Exception ex) {
                Log.e(TAG, "Failed inserting screen_event", ex);
            }
        });

    }

}
