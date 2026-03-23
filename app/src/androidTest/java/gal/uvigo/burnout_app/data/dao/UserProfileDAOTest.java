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

import gal.uvigo.burnout_app.data.db.BurnoutDatabase;
import gal.uvigo.burnout_app.data.entity.UserProfileEntity;

@RunWith(AndroidJUnit4.class)
public class UserProfileDAOTest {

    private BurnoutDatabase db;
    private UserProfileDAO dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, BurnoutDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.userProfileDAO();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void upsertUserProfile_andGet_returnsInsertedProfile() {
        UserProfileEntity profile = new UserProfileEntity(
                "Brais",
                "Mondragón",
                "brais@email.com",
                "hash123"
        );

        dao.upsertUserProfile(profile);

        UserProfileEntity result = dao.getUserProfile();

        assertNotNull(result);
        assertEquals(1, result.id);
        assertEquals("Brais", result.nombre);
        assertEquals("Mondragón", result.apellidos);
        assertEquals("brais@email.com", result.email);
        assertEquals("hash123", result.passwordHash);
    }

    @Test
    public void upsertUserProfile_replacesExistingProfile() {
        UserProfileEntity profile1 = new UserProfileEntity(
                "Old",
                "User",
                "old@email.com",
                "oldhash"
        );

        dao.upsertUserProfile(profile1);

        UserProfileEntity profile2 = new UserProfileEntity(
                "New",
                "User",
                "new@email.com",
                "newhash"
        );

        dao.upsertUserProfile(profile2);

        UserProfileEntity result = dao.getUserProfile();

        assertNotNull(result);
        assertEquals("New", result.nombre);
        assertEquals("User", result.apellidos);
        assertEquals("new@email.com", result.email);
        assertEquals("newhash", result.passwordHash);
    }

    @Test
    public void updateUserProfile_updatesFieldsCorrectly() {
        UserProfileEntity profile = new UserProfileEntity(
                "Initial",
                "User",
                "init@email.com",
                "hash1"
        );

        dao.upsertUserProfile(profile);

        profile.nombre = "Updated";
        profile.email = "updated@email.com";

        dao.updateUserProfile(profile);

        UserProfileEntity result = dao.getUserProfile();

        assertNotNull(result);
        assertEquals("Updated", result.nombre);
        assertEquals("updated@email.com", result.email);
    }

    @Test
    public void deleteAllUserProfiles_removesProfile() {
        UserProfileEntity profile = new UserProfileEntity(
                "Test",
                "User",
                "test@email.com",
                "hash"
        );

        dao.upsertUserProfile(profile);

        dao.deleteAllUserProfiles();

        UserProfileEntity result = dao.getUserProfile();

        assertNull(result);
    }
}