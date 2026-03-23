package gal.uvigo.burnout_app.data.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyMetricsEntity;

@RunWith(AndroidJUnit4.class)
public class UserActivityDAOTest {

    private BurnoutDatabase db;
    private UserActivityDAO dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, BurnoutDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.userActivityDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void upsertDailyMetrics_andGetByDate_returnsInsertedRow() {
        DailyMetricsEntity row = new DailyMetricsEntity(
                20520,
                3600000L,
                15,
                2800000L,
                42,
                12,
                8,
                25,
                600000L
        );

        dao.upsertDailyMetrics(row);

        DailyMetricsEntity result = dao.getDailyMetricsByDate(20520);

        assertNotNull(result);
        assertEquals(20520, result.date);
        assertEquals(3600000L, result.screen_ms);
        assertEquals(15, result.unlock_count);
        assertEquals(2800000L, result.foreground_ms);
        assertEquals(42, result.app_switch_count);
        assertEquals(12, result.unique_apps_count);
        assertEquals(8, result.session_count);
        assertEquals(25, result.notification_count);
        assertEquals(600000L, result.night_ms);
    }

    @Test
    public void upsertHourlyMetrics_andGetByDate_returnsOrderedRows() {
        List<HourlyMetricsEntity> rows = Arrays.asList(
                new HourlyMetricsEntity(20520, 10, 120000L, 2, 3, 4, 2),
                new HourlyMetricsEntity(20520, 8, 300000L, 5, 7, 9, 4),
                new HourlyMetricsEntity(20520, 15, 60000L, 1, 0, 2, 1)
        );

        dao.upsertHourlyMetrics(rows);

        List<HourlyMetricsEntity> result = dao.getHourlyMetricsByDate(20520);

        assertNotNull(result);
        assertEquals(3, result.size());

        // Deben venir ordenadas por hour ASC
        assertEquals(8, result.get(0).hour);
        assertEquals(10, result.get(1).hour);
        assertEquals(15, result.get(2).hour);

        assertEquals(300000L, result.get(0).screen_ms);
        assertEquals(120000L, result.get(1).screen_ms);
        assertEquals(60000L, result.get(2).screen_ms);
    }

    @Test
    public void deleteDailyMetricsOlderThanDate_deletesOnlyOlderRows() {
        dao.upsertDailyMetrics(new DailyMetricsEntity(20518, 1000L, 1, 1000L, 1, 1, 1, 1, 0L));
        dao.upsertDailyMetrics(new DailyMetricsEntity(20519, 2000L, 2, 2000L, 2, 2, 2, 2, 0L));
        dao.upsertDailyMetrics(new DailyMetricsEntity(20520, 3000L, 3, 3000L, 3, 3, 3, 3, 0L));

        int deleted = dao.deleteDailyMetricsOlderThanDate(20520);

        DailyMetricsEntity day20518 = dao.getDailyMetricsByDate(20518);
        DailyMetricsEntity day20519 = dao.getDailyMetricsByDate(20519);
        DailyMetricsEntity day20520 = dao.getDailyMetricsByDate(20520);

        assertEquals(2, deleted);
        assertNull(day20518);
        assertNull(day20519);
        assertNotNull(day20520);
        assertEquals(20520, day20520.date);
    }

    @Test
    public void getActiveHourCountByDate_countsOnlyHoursWithScreenTime() {
        List<HourlyMetricsEntity> rows = Arrays.asList(
                new HourlyMetricsEntity(20520, 8, 300000L, 5, 7, 9, 4),
                new HourlyMetricsEntity(20520, 9, 0L, 1, 1, 1, 1),
                new HourlyMetricsEntity(20520, 10, 120000L, 2, 3, 4, 2)
        );

        dao.upsertHourlyMetrics(rows);

        int activeHours = dao.getActiveHourCountByDate(20520);

        assertEquals(2, activeHours);
    }
}