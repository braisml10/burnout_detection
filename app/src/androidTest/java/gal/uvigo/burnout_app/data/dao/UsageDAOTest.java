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

import java.util.Arrays;
import java.util.List;

import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.AppEntity;
import gal.uvigo.burnout_app.data.entity.AppUsageEventEntity;
import gal.uvigo.burnout_app.data.entity.DailyAppMetricsEntity;

@RunWith(AndroidJUnit4.class)
public class UsageDAOTest {

    private BurnoutDatabase db;
    private UsageDAO dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, BurnoutDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.usageDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void insertApp_andReadFieldsByAppId_worksCorrectly() {
        AppEntity app = new AppEntity(
                "com.whatsapp",
                "WhatsApp",
                "MESSAGING",
                false
        );

        long appId = dao.insertApp(app);

        assertNotNull(appId);
        assertEquals("MESSAGING", dao.getAppCategoryByAppId(appId));
        assertEquals("WhatsApp", dao.getAppNameByAppId(appId));
        assertEquals("com.whatsapp", dao.getAppPackageNameByAppId(appId));
        assertEquals(appId, dao.getAppIdByPackageName("com.whatsapp").longValue());
    }

    @Test
    public void updateAppFields_updatesStoredValues() {
        long appId = dao.insertApp(new AppEntity(
                "com.instagram.android",
                "Instagram",
                "SOCIAL",
                false
        ));

        int updatedCategory = dao.updateAppCategoryByAppId(appId, "ENTERTAINMENT");
        int updatedName = dao.updateAppNameByAppId(appId, "Instagram Updated");
        int updatedIgnored = dao.updateAppIgnoredByAppId(appId, true);

        assertEquals(1, updatedCategory);
        assertEquals(1, updatedName);
        assertEquals(1, updatedIgnored);

        assertEquals("ENTERTAINMENT", dao.getAppCategoryByAppId(appId));
        assertEquals("Instagram Updated", dao.getAppNameByAppId(appId));
    }

    @Test
    public void insertUsageEvents_andGetByDate_returnsOrderedEvents() {
        long appId = dao.insertApp(new AppEntity(
                "com.youtube",
                "YouTube",
                "VIDEO",
                false
        ));

        dao.insertUsageEvents(Arrays.asList(
                new AppUsageEventEntity(appId, 1, 3000L, "usage", 20520),
                new AppUsageEventEntity(appId, 1, 1000L, "usage", 20520),
                new AppUsageEventEntity(appId, 2, 2000L, "usage", 20520)
        ));

        List<AppUsageEventEntity> result = dao.getUsageEventsByDate(20520);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1000L, result.get(0).timestamp);
        assertEquals(2000L, result.get(1).timestamp);
        assertEquals(3000L, result.get(2).timestamp);
    }

    @Test
    public void getUsageEventCountByDate_andDeleteOlderThanDate_workCorrectly() {
        long appId = dao.insertApp(new AppEntity(
                "com.spotify.music",
                "Spotify",
                "MUSIC",
                false
        ));

        dao.insertUsageEvents(Arrays.asList(
                new AppUsageEventEntity(appId, 1, 1000L, "usage", 20518),
                new AppUsageEventEntity(appId, 1, 2000L, "usage", 20519),
                new AppUsageEventEntity(appId, 1, 3000L, "usage", 20520)
        ));

        assertEquals(1, dao.getUsageEventCountByDate(20518));
        assertEquals(1, dao.getUsageEventCountByDate(20519));
        assertEquals(1, dao.getUsageEventCountByDate(20520));

        int deleted = dao.deleteUsageEventsOlderThanDate(20520);

        assertEquals(2, deleted);
        assertEquals(0, dao.getUsageEventCountByDate(20518));
        assertEquals(0, dao.getUsageEventCountByDate(20519));
        assertEquals(1, dao.getUsageEventCountByDate(20520));
    }

    @Test
    public void getCategoryTotalsMsForDayCursor_groupsByCategory_andIgnoresIgnoredApps() {
        long whatsappId = dao.insertApp(new AppEntity(
                "com.whatsapp",
                "WhatsApp",
                "MESSAGING",
                false
        ));

        long instagramId = dao.insertApp(new AppEntity(
                "com.instagram.android",
                "Instagram",
                "SOCIAL",
                false
        ));

        long ignoredId = dao.insertApp(new AppEntity(
                "com.ignored.app",
                "Ignored App",
                "SOCIAL",
                true
        ));

        dao.upsertDailyAppMetrics(Arrays.asList(
                new DailyAppMetricsEntity(20520, whatsappId, 1000L, 3, 2),
                new DailyAppMetricsEntity(20520, instagramId, 2000L, 4, 1),
                new DailyAppMetricsEntity(20520, ignoredId, 5000L, 5, 0)
        ));

        Cursor cursor = dao.getCategoryTotalsMsForDayCursor(20520);
        try {
            assertNotNull(cursor);
            assertEquals(2, cursor.getCount());

            int categoryIndex = cursor.getColumnIndexOrThrow("category");
            int totalMsIndex = cursor.getColumnIndexOrThrow("total_ms");

            cursor.moveToFirst();
            String category1 = cursor.getString(categoryIndex);
            long total1 = cursor.getLong(totalMsIndex);

            cursor.moveToNext();
            String category2 = cursor.getString(categoryIndex);
            long total2 = cursor.getLong(totalMsIndex);

            // El orden del GROUP BY no conviene asumirlo, así que comprobamos ambos casos.
            if ("MESSAGING".equals(category1)) {
                assertEquals(1000L, total1);
                assertEquals("SOCIAL", category2);
                assertEquals(2000L, total2);
            } else {
                assertEquals("SOCIAL", category1);
                assertEquals(2000L, total1);
                assertEquals("MESSAGING", category2);
                assertEquals(1000L, total2);
            }
        } finally {
            cursor.close();
        }
    }

    @Test
    public void getTopAppsForDayCursor_returnsAppsOrderedByForegroundDesc() {
        long whatsappId = dao.insertApp(new AppEntity(
                "com.whatsapp",
                "WhatsApp",
                "MESSAGING",
                false
        ));

        long instagramId = dao.insertApp(new AppEntity(
                "com.instagram.android",
                "Instagram",
                "SOCIAL",
                false
        ));

        dao.upsertDailyAppMetrics(Arrays.asList(
                new DailyAppMetricsEntity(20520, whatsappId, 1500L, 3, 2),
                new DailyAppMetricsEntity(20520, instagramId, 3000L, 4, 1)
        ));

        Cursor cursor = dao.getTopAppsForDayCursor(20520, 5);
        try {
            assertNotNull(cursor);
            assertEquals(2, cursor.getCount());

            int appIdIndex = cursor.getColumnIndexOrThrow("app_id");
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            int packageIndex = cursor.getColumnIndexOrThrow("package_name");
            int totalMsIndex = cursor.getColumnIndexOrThrow("total_ms");

            cursor.moveToFirst();
            assertEquals(instagramId, cursor.getLong(appIdIndex));
            assertEquals("Instagram", cursor.getString(nameIndex));
            assertEquals("com.instagram.android", cursor.getString(packageIndex));
            assertEquals(3000L, cursor.getLong(totalMsIndex));

            cursor.moveToNext();
            assertEquals(whatsappId, cursor.getLong(appIdIndex));
            assertEquals("WhatsApp", cursor.getString(nameIndex));
            assertEquals("com.whatsapp", cursor.getString(packageIndex));
            assertEquals(1500L, cursor.getLong(totalMsIndex));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void getTotalForegroundMsForDayCursor_returnsSumIgnoringIgnoredApps() {
        long whatsappId = dao.insertApp(new AppEntity(
                "com.whatsapp",
                "WhatsApp",
                "MESSAGING",
                false
        ));

        long ignoredId = dao.insertApp(new AppEntity(
                "com.ignored.app",
                "Ignored App",
                "SOCIAL",
                true
        ));

        dao.upsertDailyAppMetrics(Arrays.asList(
                new DailyAppMetricsEntity(20520, whatsappId, 1500L, 3, 2),
                new DailyAppMetricsEntity(20520, ignoredId, 9000L, 1, 0)
        ));

        Cursor cursor = dao.getTotalForegroundMsForDayCursor(20520);
        try {
            assertNotNull(cursor);
            cursor.moveToFirst();

            int totalMsIndex = cursor.getColumnIndexOrThrow("total_ms");
            assertEquals(1500L, cursor.getLong(totalMsIndex));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void deleteDailyAppMetricsOlderThanDate_deletesOnlyOlderRows() {
        long whatsappId = dao.insertApp(new AppEntity(
                "com.whatsapp",
                "WhatsApp",
                "MESSAGING",
                false
        ));

        dao.upsertDailyAppMetrics(Arrays.asList(
                new DailyAppMetricsEntity(20518, whatsappId, 100L, 1, 0),
                new DailyAppMetricsEntity(20519, whatsappId, 200L, 1, 0),
                new DailyAppMetricsEntity(20520, whatsappId, 300L, 1, 0)
        ));

        int deleted = dao.deleteDailyAppMetricsOlderThanDate(20520);

        assertEquals(2, deleted);

        Cursor cursor = dao.getTotalForegroundMsForDayCursor(20520);
        try {
            assertNotNull(cursor);
            cursor.moveToFirst();

            int totalMsIndex = cursor.getColumnIndexOrThrow("total_ms");
            assertEquals(300L, cursor.getLong(totalMsIndex));
        } finally {
            cursor.close();
        }
    }
}