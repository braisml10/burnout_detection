package gal.uvigo.burnout_app.data.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.NotificationEventEntity;

@RunWith(AndroidJUnit4.class)
public class NotificationDAOTest {

    private BurnoutDatabase db;
    private NotificationDAO dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, BurnoutDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.notificationDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void insertNotificationEvent_andGetByDate_returnsOrderedEvents() {
        dao.insertNotificationEvent(new NotificationEventEntity(
                1000L, 20520, 8, 1L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                3000L, 20520, 10, 2L, "MESSAGING", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                2000L, 20520, 9, 1L, "SOCIAL", true, "listener"
        ));

        List<NotificationEventEntity> result = dao.getNotificationEventsByDate(20520);

        assertNotNull(result);
        assertEquals(3, result.size());

        // Ordenados por timestamp ASC
        assertEquals(1000L, result.get(0).timestamp);
        assertEquals(2000L, result.get(1).timestamp);
        assertEquals(3000L, result.get(2).timestamp);

        assertEquals(8, result.get(0).hour);
        assertEquals(9, result.get(1).hour);
        assertEquals(10, result.get(2).hour);
    }

    @Test
    public void getNotificationCountByDate_returnsCorrectCount() {
        dao.insertNotificationEvent(new NotificationEventEntity(
                1000L, 20520, 8, 1L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                2000L, 20520, 9, 1L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                3000L, 20521, 10, 2L, "MESSAGING", false, "listener"
        ));

        int count20520 = dao.getNotificationCountByDate(20520);
        int count20521 = dao.getNotificationCountByDate(20521);

        assertEquals(2, count20520);
        assertEquals(1, count20521);
    }

    @Test
    public void deleteNotificationEventsOlderThanDate_deletesOnlyOlderRows() {
        dao.insertNotificationEvent(new NotificationEventEntity(
                1000L, 20518, 8, 1L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                2000L, 20519, 9, 1L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                3000L, 20520, 10, 2L, "MESSAGING", false, "listener"
        ));

        int deleted = dao.deleteNotificationEventsOlderThanDate(20520);

        assertEquals(2, deleted);
        assertEquals(0, dao.getNotificationCountByDate(20518));
        assertEquals(0, dao.getNotificationCountByDate(20519));
        assertEquals(1, dao.getNotificationCountByDate(20520));
    }

    @Test
    public void getNotificationCountByHourCursor_returnsGroupedCounts() {
        dao.insertNotificationEvent(new NotificationEventEntity(
                1000L, 20520, 8, 1L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                1500L, 20520, 8, 2L, "MESSAGING", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                2000L, 20520, 10, 1L, "SOCIAL", false, "listener"
        ));

        Cursor cursor = dao.getNotificationCountByHourCursor(20520);
        try {
            assertNotNull(cursor);
            assertEquals(2, cursor.getCount());

            int hourIndex = cursor.getColumnIndexOrThrow("hour");
            int countIndex = cursor.getColumnIndexOrThrow("c");

            cursor.moveToFirst();
            assertEquals(8, cursor.getInt(hourIndex));
            assertEquals(2, cursor.getInt(countIndex));

            cursor.moveToNext();
            assertEquals(10, cursor.getInt(hourIndex));
            assertEquals(1, cursor.getInt(countIndex));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void getTopNotificationAppsCursor_returnsAppsOrderedByCountDesc() {
        dao.insertNotificationEvent(new NotificationEventEntity(
                1000L, 20520, 8, 10L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                1100L, 20520, 8, 10L, "SOCIAL", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                1200L, 20520, 9, 20L, "MESSAGING", false, "listener"
        ));
        dao.insertNotificationEvent(new NotificationEventEntity(
                1300L, 20520, 10, 10L, "SOCIAL", false, "listener"
        ));

        Cursor cursor = dao.getTopNotificationAppsCursor(20520, 5);
        try {
            assertNotNull(cursor);
            assertEquals(2, cursor.getCount());

            int appIdIndex = cursor.getColumnIndexOrThrow("app_id");
            int countIndex = cursor.getColumnIndexOrThrow("c");

            cursor.moveToFirst();
            assertEquals(10L, cursor.getLong(appIdIndex));
            assertEquals(3, cursor.getInt(countIndex));

            cursor.moveToNext();
            assertEquals(20L, cursor.getLong(appIdIndex));
            assertEquals(1, cursor.getInt(countIndex));
        } finally {
            cursor.close();
        }
    }
}