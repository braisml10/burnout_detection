package com.example.burnout_app.collectors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.ScreenEventEntity;
import com.example.burnout_app.helpers.TimeKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenStateReceiver";
    private static final String SOURCE = "broadcast";

    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();

    public static final int STATE_OFF = 0;
    public static final int STATE_ON = 1;
    public static final int STATE_UNLOCK = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        final String action = intent.getAction();
        final int state = resolveState(action);
        if (state < 0) return;

        final long ts = System.currentTimeMillis();
        final int day = TimeKey.epochDayLocal(ts);
        final int hour = TimeKey.hourOfDayLocal(ts);
        final Context appContext = context.getApplicationContext();

        Log.d(TAG, "onReceive action=" + action + " state=" + state + " ts=" + ts);

        DB_EXECUTOR.execute(() -> {
            BurnoutDatabase db = BurnoutDatabase.getInstance(appContext);
            db.userActivityDao().insertScreenEvent(
                    new ScreenEventEntity(ts, state, SOURCE, day, hour)
            );
        });
    }

    private int resolveState(String action) {
        if (Intent.ACTION_SCREEN_ON.equals(action)) {
            return STATE_ON;
        }
        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            return STATE_OFF;
        }
        if (Intent.ACTION_USER_PRESENT.equals(action)) {
            return STATE_UNLOCK;
        }
        return -1;
    }
}