package gal.uvigo.burnout_app.helpers;

import java.util.List;

import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;
import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;

public class BurnoutRiskEngine {

    private static final double WEIGHT_SCREEN_TIME = 0.20;
    private static final double WEIGHT_FRAGMENTATION = 0.25;
    private static final double WEIGHT_NIGHT_USE = 0.25;
    private static final double WEIGHT_NOTIFICATION_PRESSURE = 0.20;
    private static final double WEIGHT_TREND_DEVIATION = 0.10;

    private static final long FOUR_HOURS_MS = 4L * 60L * 60L * 1000L;
    private static final long SEVEN_HOURS_MS = 7L * 60L * 60L * 1000L;

    public BurnoutRiskEntity evaluate(
            long epochDay,
            DailyMetricsEntity today,
            List<DailyMetricsEntity> baselineDays,
            int notificationCount,
            double reactiveOpenRatio
    ) {

        BurnoutRiskEntity out = new BurnoutRiskEntity();
        out.epochDay = epochDay;

        boolean hasBaseline = baselineDays != null && baselineDays.size() >= 3;

        double avgScreenMs7d = avgScreenMs(baselineDays);
        double avgFragmentation7d = avgFragmentationIndex(baselineDays);
        double avgNightRatio7d = avgNightRatio(baselineDays);
        double avgNotificationPressure7d = avgNotificationPressure(baselineDays);
        double avgTrendRef7d = avgTrendReference(baselineDays);

        double todayScreenMs = safeLong(today.screenMs);
        double todayFragmentation = fragmentationIndex(today);
        double todayNightRatio = nightRatio(today);
        double todayNotificationPressure = notificationPressure(notificationCount, reactiveOpenRatio);

        out.screenTimeScore = scoreScreenTime(todayScreenMs, avgScreenMs7d, hasBaseline);
        out.fragmentationScore = scoreFragmentation(todayFragmentation, avgFragmentation7d, hasBaseline);
        out.nightUseScore = scoreNightUse(todayNightRatio, avgNightRatio7d, hasBaseline);
        out.notificationPressureScore = scoreNotificationPressure(todayNotificationPressure, avgNotificationPressure7d, hasBaseline);
        out.trendDeviationScore = scoreTrendDeviation(
                todayScreenMs,
                avgScreenMs7d,
                todayFragmentation,
                avgFragmentation7d,
                todayNightRatio,
                avgNightRatio7d,
                todayNotificationPressure,
                avgNotificationPressure7d,
                avgTrendRef7d,
                hasBaseline
        );

        out.riskScore =
                WEIGHT_SCREEN_TIME * out.screenTimeScore +
                        WEIGHT_FRAGMENTATION * out.fragmentationScore +
                        WEIGHT_NIGHT_USE * out.nightUseScore +
                        WEIGHT_NOTIFICATION_PRESSURE * out.notificationPressureScore +
                        WEIGHT_TREND_DEVIATION * out.trendDeviationScore;

        return out;
    }

    // ===================== DIMENSIONS =====================

    private double scoreScreenTime(double todayScreenMs, double avgScreenMs7d, boolean hasBaseline) {

        if (!hasBaseline) {
            if (todayScreenMs > SEVEN_HOURS_MS) return 2.0;
            if (todayScreenMs >= FOUR_HOURS_MS) return 1.0;
            return 0.0;
        }

        boolean highAbs = todayScreenMs > SEVEN_HOURS_MS;
        boolean highBaseline = ratio(todayScreenMs, avgScreenMs7d) > 1.5;

        if (highAbs && highBaseline) return 2.0;

        boolean mediumAbs = todayScreenMs >= FOUR_HOURS_MS && todayScreenMs <= SEVEN_HOURS_MS;
        boolean mediumBaseline = ratio(todayScreenMs, avgScreenMs7d) > 1.3;

        if (mediumAbs || mediumBaseline) return 1.0;

        return 0.0;
    }

    private double scoreFragmentation(double todayFragmentation, double avgFragmentation7d, boolean hasBaseline) {

        if (!hasBaseline) {
            if (todayFragmentation >= 20.0) return 2.0;
            if (todayFragmentation >= 12.0) return 1.0;
            return 0.0;
        }

        double r = ratio(todayFragmentation, avgFragmentation7d);

        if (todayFragmentation >= 20.0 && r > 1.5) return 2.0;
        if (todayFragmentation >= 12.0 || r > 1.3) return 1.0;
        return 0.0;
    }

    private double scoreNightUse(double todayNightRatio, double avgNightRatio7d, boolean hasBaseline) {

        if (!hasBaseline) {
            if (todayNightRatio > 0.25) return 2.0;
            if (todayNightRatio >= 0.10) return 1.0;
            return 0.0;
        }

        double r = ratio(todayNightRatio, avgNightRatio7d);

        if (todayNightRatio > 0.25 && r > 1.5) return 2.0;
        if ((todayNightRatio >= 0.10 && todayNightRatio <= 0.25) || r > 1.3) return 1.0;
        return 0.0;
    }

    private double scoreNotificationPressure(double todayPressure, double avgPressure7d, boolean hasBaseline) {

        if (!hasBaseline) {
            if (todayPressure >= 0.75) return 2.0;
            if (todayPressure >= 0.40) return 1.0;
            return 0.0;
        }

        double r = ratio(todayPressure, avgPressure7d);

        if (todayPressure >= 0.75 && r > 1.5) return 2.0;
        if (todayPressure >= 0.40 || r > 1.3) return 1.0;
        return 0.0;
    }

    private double scoreTrendDeviation(
            double todayScreenMs,
            double avgScreenMs7d,
            double todayFragmentation,
            double avgFragmentation7d,
            double todayNightRatio,
            double avgNightRatio7d,
            double todayNotificationPressure,
            double avgNotificationPressure7d,
            double avgTrendRef7d,
            boolean hasBaseline
    ) {

        if (!hasBaseline) {
            return 0.0;
        }

        double screenDev = ratio(todayScreenMs, avgScreenMs7d);
        double fragDev = ratio(todayFragmentation, avgFragmentation7d);
        double nightDev = ratio(todayNightRatio, avgNightRatio7d);
        double notifDev = ratio(todayNotificationPressure, avgNotificationPressure7d);

        double trendIndex = mean(screenDev, fragDev, nightDev, notifDev);
        double trendRatio = ratio(trendIndex, avgTrendRef7d);

        if (trendIndex > 1.5 && trendRatio > 1.5) return 2.0;
        if (trendIndex > 1.3 || trendRatio > 1.3) return 1.0;
        return 0.0;
    }

    // ===================== INDEXES =====================

    public static double fragmentationIndex(DailyMetricsEntity day) {
        if (day == null) {
            return 0.0;
        }

        double screenHours = Math.max(0L, day.screenMs) / 3_600_000.0;

        if (screenHours <= 0.0) {
            return 0.0;
        }

        double sessionsPerHour =
                Math.max(0, day.sessionCount) / screenHours;

        double switchesPerHour =
                Math.max(0, day.appSwitchCount) / screenHours;

        return (sessionsPerHour + switchesPerHour) / 2.0;
    }

    private double nightRatio(DailyMetricsEntity day) {
        double screenMs = safeLong(day.screenMs);
        if (screenMs <= 0.0) return 0.0;
        return safeLong(day.nightMs) / screenMs;
    }

    private double notificationPressure(int notificationCount, double reactiveOpenRatio) {
        double normalizedCount;
        if (notificationCount >= 120) normalizedCount = 1.0;
        else if (notificationCount >= 60) normalizedCount = 0.7;
        else if (notificationCount >= 25) normalizedCount = 0.4;
        else normalizedCount = 0.1;

        double reactive = clamp(reactiveOpenRatio, 0.0, 1.0);
        return (normalizedCount + reactive) / 2.0;
    }

    // ===================== BASELINES =====================

    private double avgScreenMs(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0.0;
        double sum = 0.0;
        for (DailyMetricsEntity d : days) sum += safeLong(d.screenMs);
        return sum / days.size();
    }

    public static double avgFragmentationIndex(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0.0;

        double sum = 0.0;
        for (DailyMetricsEntity d : days) sum += fragmentationIndex(d);
        return sum / days.size();
    }

    private double avgNightRatio(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0.0;
        double sum = 0.0;
        for (DailyMetricsEntity d : days) sum += nightRatio(d);
        return sum / days.size();
    }

    private double avgNotificationPressure(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 0.0;

        double sum = 0.0;
        for (DailyMetricsEntity d : days) {
            int notificationCount = d.notificationCount;
            sum += notificationPressure(notificationCount, 0.0);
        }
        return sum / days.size();
    }

    private double avgTrendReference(List<DailyMetricsEntity> days) {
        if (days == null || days.isEmpty()) return 1.0;

        double avgScreen = avgScreenMs(days);
        double avgFrag = avgFragmentationIndex(days);
        double avgNight = avgNightRatio(days);
        double avgNotif = avgNotificationPressure(days);

        double sum = 0.0;
        int count = 0;

        for (DailyMetricsEntity d : days) {
            double idx = mean(
                    ratio(safeLong(d.screenMs), avgScreen),
                    ratio(fragmentationIndex(d), avgFrag),
                    ratio(nightRatio(d), avgNight),
                    ratio(notificationPressure(d.notificationCount, 0.0), avgNotif)
            );
            sum += idx;
            count++;
        }

        return count > 0 ? (sum / count) : 1.0;
    }

    // ===================== UTILS =====================

    private double ratio(double current, double baseline) {
        if (baseline <= 0.0) return current > 0.0 ? 1.0 : 0.0;
        return current / baseline;
    }

    private double mean(double... values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private long safeLong(long value) {
        return Math.max(value, 0L);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}