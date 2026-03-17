package com.example.burnout_app.helpers;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class TimeKey {
    private TimeKey() {}

    public static int epochDayLocal(long timestampMs) {
        Calendar cal = Calendar.getInstance();
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

    public static long startOfDayMs(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

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

    public static long clampEnd(long endCandidate, long upperBound) {
        return Math.min(endCandidate, upperBound);
    }

    public static String formatEpochDay(int epochDay) {
        long startOfDayMs = startOfDayMsFromEpochDay(epochDay);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startOfDayMs);

        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM", Locale.getDefault());
        sdf.setTimeZone(cal.getTimeZone());
        return sdf.format(cal.getTime());
    }

    public static String dateLabelFromTimestamp(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Locale locale = Locale.getDefault();
        String pattern = usesPrepositionDe(locale) ? "d 'de' MMMM" : "d MMMM";

        SimpleDateFormat sdf = new SimpleDateFormat(pattern, locale);
        sdf.setTimeZone(cal.getTimeZone());

        return sdf.format(cal.getTime());
    }

    private static boolean usesPrepositionDe(Locale locale) {
        String lang = locale.getLanguage();
        return "es".equals(lang) || "gl".equals(lang);
    }

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
        return epochDayLocal(ts);
    }

    public static String dateLabelFromEpochDay(int epochDay) {
        long tsLocal = startOfDayMsFromEpochDay(epochDay);
        return dateLabelFromTimestamp(tsLocal);
    }

    public static String formatDurationMinutes(int totalMin) {
        if (totalMin < 0) totalMin = 0;

        int h = totalMin / 60;
        int m = totalMin % 60;

        if (h > 0) return h + "h " + m + "min";
        return m + "min";
    }
}