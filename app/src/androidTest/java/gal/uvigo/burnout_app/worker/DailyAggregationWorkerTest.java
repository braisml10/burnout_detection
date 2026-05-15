package gal.uvigo.burnout_app.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.helpers.TimeKey;

@RunWith(AndroidJUnit4.class)
public class DailyAggregationWorkerTest {

    private Context context;
    private BurnoutDatabase db;

    private static final String PREFS = "burnout_runtime";

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = BurnoutDatabase.getInstance(context);

        db.clearAllTables();

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void doWork_withoutUsageAccess_returnsSuccess() {
        DailyAggregationWorker worker =
                TestListenableWorkerBuilder
                        .from(context, DailyAggregationWorker.class)
                        .build();

        ListenableWorker.Result result = worker.doWork();

        assertTrue(result instanceof ListenableWorker.Result.Success);
    }

    @Test
    public void doWork_createsDailyRowsForYesterdayTodayAndTomorrow() {
        int today = TimeKey.todayEpochDay();
        int yesterday = today - 1;
        int tomorrow = today + 1;

        DailyAggregationWorker worker =
                TestListenableWorkerBuilder
                        .from(context, DailyAggregationWorker.class)
                        .build();

        worker.doWork();

        assertEquals(1, countRows("daily_metrics", "date", yesterday));
        assertEquals(1, countRows("daily_metrics", "date", today));
        assertEquals(1, countRows("daily_metrics", "date", tomorrow));
    }

    @Test
    public void doWork_createsCommunicationRowsForYesterdayAndToday() {
        int today = TimeKey.todayEpochDay();
        int yesterday = today - 1;

        DailyAggregationWorker worker =
                TestListenableWorkerBuilder
                        .from(context, DailyAggregationWorker.class)
                        .build();

        worker.doWork();

        assertEquals(1, countRows("daily_comm_metric", "date", yesterday));
        assertEquals(1, countRows("daily_comm_metric", "date", today));
    }

    @Test
    public void doWork_computesRiskForClosedDay() {
        int yesterday = TimeKey.todayEpochDay() - 1;

        DailyAggregationWorker worker =
                TestListenableWorkerBuilder
                        .from(context, DailyAggregationWorker.class)
                        .build();

        worker.doWork();

        assertEquals(1, countRows("burnout_risk", "epochDay", yesterday));
    }

    @Test
    public void doWork_setsLastRiskComputedDayPreference() {
        int yesterday = TimeKey.todayEpochDay() - 1;

        DailyAggregationWorker worker =
                TestListenableWorkerBuilder
                        .from(context, DailyAggregationWorker.class)
                        .build();

        worker.doWork();

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int lastRiskComputedDay = prefs.getInt("last_risk_computed_day", -1);

        assertEquals(yesterday, lastRiskComputedDay);
    }

    @Test
    public void doWork_runningTwice_doesNotDuplicateDailyRows() {
        int today = TimeKey.todayEpochDay();
        int yesterday = today - 1;
        int tomorrow = today + 1;

        DailyAggregationWorker worker1 =
                TestListenableWorkerBuilder
                        .from(context, DailyAggregationWorker.class)
                        .build();

        DailyAggregationWorker worker2 =
                TestListenableWorkerBuilder
                        .from(context, DailyAggregationWorker.class)
                        .build();

        worker1.doWork();
        worker2.doWork();

        assertEquals(1, countRows("daily_metrics", "date", yesterday));
        assertEquals(1, countRows("daily_metrics", "date", today));
        assertEquals(1, countRows("daily_metrics", "date", tomorrow));
    }

    private int countRows(String table, String column, int value) {
        SupportSQLiteDatabase sqlDb = db.getOpenHelper().getReadableDatabase();

        Cursor cursor = sqlDb.query(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                new Object[]{value}
        );

        try {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }
}