package com.example.burnout_app.collectors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.burnout_app.data.db.BurnoutDatabase;
import com.example.burnout_app.data.entity.ScreenEventEntity;
import com.example.burnout_app.helpers.TimeKey;

import java.util.concurrent.Executors;

public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenStateReceiver";

    public static final int STATE_OFF = 0;
    public static final int STATE_ON = 1;
    public static final int STATE_UNLOCK = 2; // ACTION_USER_PRESENT

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        final String action = intent.getAction();
        final long ts = System.currentTimeMillis();
        final int day = TimeKey.epochDayLocal(ts);
        final int hour = TimeKey.hourOfDayLocal(ts);

        final int state;
        if (Intent.ACTION_SCREEN_ON.equals(action)) {
            state = STATE_ON;
        } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            state = STATE_OFF;
        } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
            state = STATE_UNLOCK;
        } else {
            return;
        }

        Log.d(TAG, "onReceive action=" + action + " state=" + state + " ts=" + ts);

        Executors.newSingleThreadExecutor().execute(() -> {
            BurnoutDatabase db = BurnoutDatabase.getInstance(context.getApplicationContext());
            db.userActivityDao().insertScreenEvent(
                    new ScreenEventEntity(ts, state, "broadcast", day, hour)
            );
        });
    }
}
