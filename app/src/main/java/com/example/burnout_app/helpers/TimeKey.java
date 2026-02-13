package com.example.burnout_app.helpers;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class TimeKey {
    private TimeKey() {}

    public static int epochDayLocal(long timestampMs) {
        Calendar cal = Calendar.getInstance(); // local timezone
        cal.setTimeInMillis(timestampMs);

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

    /** Inicio del día local (00:00:00.000) para un timestamp dado. */
    public static long startOfDayMs(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Fin del día local (23:59:59.999) para un timestamp dado. */
    public static long endOfDayMs(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.MILLISECOND, -1);
        return cal.getTimeInMillis();
    }

    /** Clamp genérico para no pasarte de un límite superior (p.ej. now o fin de día). */
    public static long clampEnd(long endCandidate, long upperBound) {
        return Math.min(endCandidate, upperBound);
    }

    public static String formatEpochDay(int epochDay) {
        long startOfDayMs = epochDay * 86_400_000L;

        Calendar cal = Calendar.getInstance(); // local timezone
        cal.setTimeInMillis(startOfDayMs);

        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM", new Locale("es", "ES"));
        return sdf.format(cal.getTime());
    }

    public static String dateLabelFromTimestamp(long timestampMs) {
        Calendar cal = Calendar.getInstance(); // timezone local del dispositivo
        cal.setTimeInMillis(timestampMs);

        // Normalizar a 00:00 LOCAL del mismo día
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("d 'de' MMMM", new java.util.Locale("es", "ES"));

        // Asegura que formatea en la TZ local
        sdf.setTimeZone(cal.getTimeZone());

        return sdf.format(cal.getTime());
    }

    /** Inicio del día local (00:00:00.000) a partir de epochDay. */

    public static long startOfDayMsFromEpochDay(int epochDay) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        int today = epochDayLocal(System.currentTimeMillis());
        int delta = epochDay - today;

        cal.add(Calendar.DAY_OF_YEAR, delta);
        return cal.getTimeInMillis();
    }

    public static int epochDayFromTimestamp(long ts) {
        // días desde 1970-01-01
        return (int) (ts / 86400000L);
    }

    public static String dateLabelFromEpochDay(int epochDay) {
        long ts = epochDay * 86400000L;
        return dateLabelFromTimestamp(ts); // reutiliza tu méodo existente
    }



}
