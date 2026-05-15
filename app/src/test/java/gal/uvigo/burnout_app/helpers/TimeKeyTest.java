package gal.uvigo.burnout_app.helpers;

import static org.junit.Assert.*;

import org.junit.Test;

public class TimeKeyTest {

    @Test
    public void minutesFromMs_positiveValue_returnsMinutes() {
        assertEquals(5L, TimeKey.minutesFromMs(300_000L));
    }

    @Test
    public void minutesFromMs_zeroOrNegative_returnsZero() {
        assertEquals(0L, TimeKey.minutesFromMs(0L));
        assertEquals(0L, TimeKey.minutesFromMs(-1000L));
    }

    @Test
    public void formatDurationMinutes_lessThanOneHour_returnsOnlyMinutes() {
        assertEquals("45min", TimeKey.formatDurationMinutes(45));
    }

    @Test
    public void formatDurationMinutes_moreThanOneHour_returnsHoursAndMinutes() {
        assertEquals("2h 15min", TimeKey.formatDurationMinutes(135));
    }

    @Test
    public void formatDurationMinutes_negativeValue_returnsZeroMinutes() {
        assertEquals("0min", TimeKey.formatDurationMinutes(-10));
    }

    @Test
    public void startOfDayMs_removesHourMinuteSecondAndMillisecond() {
        long now = System.currentTimeMillis();

        long startOfDay = TimeKey.startOfDayMs(now);

        assertTrue(startOfDay <= now);
        assertEquals(0, TimeKey.hourOfDayLocal(startOfDay));
    }

    @Test
    public void epochDayLocal_sameDay_returnsSameEpochDay() {
        long now = System.currentTimeMillis();
        long oneHourLater = now + 60L * 60L * 1000L;

        int day1 = TimeKey.epochDayLocal(now);
        int day2 = TimeKey.epochDayLocal(oneHourLater);

        if (TimeKey.hourOfDayLocal(now) < 23) {
            assertEquals(day1, day2);
        }
    }

    @Test
    public void minAllowedEpochDay_returnsTodayMinusRetentionDays() {
        int today = TimeKey.todayEpochDay();

        assertEquals(today - 7, TimeKey.minAllowedEpochDay(7));
    }
}