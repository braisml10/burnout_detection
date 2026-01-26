package com.example.burnout_app.helpers;

import java.util.Calendar;
import java.util.TimeZone;

public final class TimeKey {
    private TimeKey() {}

    // Usa hora local (más intuitivo para métricas diarias)
    public static int epochDayLocal(long timestampMs) {
        Calendar cal = Calendar.getInstance(); // local timezone
        cal.setTimeInMillis(timestampMs);

        // Normalizamos a inicio de día local
        Calendar start = (Calendar) cal.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        long startMs = start.getTimeInMillis();
        return (int) (startMs / 86_400_000L);
    }

    public static int hourOfDayLocal(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        return cal.get(Calendar.HOUR_OF_DAY);
    }
}
