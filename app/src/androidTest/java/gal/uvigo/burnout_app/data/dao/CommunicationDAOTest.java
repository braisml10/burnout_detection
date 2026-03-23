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
import gal.uvigo.burnout_app.data.entity.DailyCommMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyCommMetricsEntity;

@RunWith(AndroidJUnit4.class)
public class CommunicationDAOTest {

    private BurnoutDatabase db;
    private CommunicationDAO dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, BurnoutDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.communicationDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void upsertDailyCommMetrics_andGet_returnsInsertedRow() {
        DailyCommMetricsEntity row = new DailyCommMetricsEntity(
                20520,
                4,
                12,
                900000L,
                300000L,
                600000L
        );

        dao.upsertDailyCommMetrics(row);

        DailyCommMetricsEntity result = dao.getDailyCommMetrics(20520);

        assertNotNull(result);
        assertEquals(20520, result.date);
        assertEquals(4, result.calls_count);
        assertEquals(12, result.messages_count);
        assertEquals(900000L, result.total_comm_ms);
        assertEquals(300000L, result.voice_ms);
        assertEquals(600000L, result.text_ms);
    }

    @Test
    public void insertDailyCommMetricsIfMissing_insertsZeroRowOnlyOnce() {
        dao.insertDailyCommMetricsIfMissing(20521);

        DailyCommMetricsEntity first = dao.getDailyCommMetrics(20521);

        assertNotNull(first);
        assertEquals(20521, first.date);
        assertEquals(0, first.calls_count);
        assertEquals(0, first.messages_count);
        assertEquals(0L, first.total_comm_ms);
        assertEquals(0L, first.voice_ms);
        assertEquals(0L, first.text_ms);

        dao.insertDailyCommMetricsIfMissing(20521);

        DailyCommMetricsEntity second = dao.getDailyCommMetrics(20521);

        assertNotNull(second);
        assertEquals(20521, second.date);
        assertEquals(0, second.calls_count);
        assertEquals(0, second.messages_count);
        assertEquals(0L, second.total_comm_ms);
        assertEquals(0L, second.voice_ms);
        assertEquals(0L, second.text_ms);
    }

    @Test
    public void deleteDailyCommMetricsOlderThanDate_deletesOnlyOlderRows() {
        dao.upsertDailyCommMetrics(new DailyCommMetricsEntity(20518, 1, 2, 100L, 40L, 60L));
        dao.upsertDailyCommMetrics(new DailyCommMetricsEntity(20519, 2, 3, 200L, 80L, 120L));
        dao.upsertDailyCommMetrics(new DailyCommMetricsEntity(20520, 3, 4, 300L, 100L, 200L));

        int deleted = dao.deleteDailyCommMetricsOlderThanDate(20520);

        DailyCommMetricsEntity day20518 = dao.getDailyCommMetrics(20518);
        DailyCommMetricsEntity day20519 = dao.getDailyCommMetrics(20519);
        DailyCommMetricsEntity day20520 = dao.getDailyCommMetrics(20520);

        assertEquals(2, deleted);
        assertNull(day20518);
        assertNull(day20519);
        assertNotNull(day20520);
        assertEquals(20520, day20520.date);
    }

    @Test
    public void upsertHourlyCommMetrics_andGetByDate_returnsOrderedRows() {
        List<HourlyCommMetricsEntity> rows = Arrays.asList(
                new HourlyCommMetricsEntity(20520, 14, 100L, 40L, 60L),
                new HourlyCommMetricsEntity(20520, 9, 400L, 150L, 250L),
                new HourlyCommMetricsEntity(20520, 21, 50L, 10L, 40L)
        );

        dao.upsertHourlyCommMetrics(rows);

        List<HourlyCommMetricsEntity> result = dao.getHourlyCommMetricsByDate(20520);

        assertNotNull(result);
        assertEquals(3, result.size());

        assertEquals(9, result.get(0).hour);
        assertEquals(14, result.get(1).hour);
        assertEquals(21, result.get(2).hour);

        assertEquals(400L, result.get(0).total_value);
        assertEquals(100L, result.get(1).total_value);
        assertEquals(50L, result.get(2).total_value);
    }

    @Test
    public void deleteHourlyCommMetricsOlderThanDate_deletesOnlyOlderRows() {
        dao.upsertHourlyCommMetrics(Arrays.asList(
                new HourlyCommMetricsEntity(20518, 10, 100L, 40L, 60L),
                new HourlyCommMetricsEntity(20519, 11, 200L, 80L, 120L),
                new HourlyCommMetricsEntity(20520, 12, 300L, 100L, 200L)
        ));

        int deleted = dao.deleteHourlyCommMetricsOlderThanDate(20520);

        List<HourlyCommMetricsEntity> day20518 = dao.getHourlyCommMetricsByDate(20518);
        List<HourlyCommMetricsEntity> day20519 = dao.getHourlyCommMetricsByDate(20519);
        List<HourlyCommMetricsEntity> day20520 = dao.getHourlyCommMetricsByDate(20520);

        assertEquals(2, deleted);
        assertEquals(0, day20518.size());
        assertEquals(0, day20519.size());
        assertEquals(1, day20520.size());
        assertEquals(12, day20520.get(0).hour);
    }
}