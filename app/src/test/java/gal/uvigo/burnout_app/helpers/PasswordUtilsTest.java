package gal.uvigo.burnout_app.helpers;

import static org.junit.Assert.*;

import org.junit.Test;

public class PasswordUtilsTest {

    @Test
    public void hashPassword_sameInput_returnsSameHash() {
        String hash1 = PasswordUtils.hashPassword("password123");
        String hash2 = PasswordUtils.hashPassword("password123");

        assertEquals(hash1, hash2);
    }

    @Test
    public void hashPassword_differentInputs_returnDifferentHashes() {
        String hash1 = PasswordUtils.hashPassword("password123");
        String hash2 = PasswordUtils.hashPassword("differentPassword");

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void hashPassword_hashIsNotPlainText() {
        String password = "password123";
        String hash = PasswordUtils.hashPassword(password);

        assertNotEquals(password, hash);
    }

    @Test
    public void hashPassword_returnsSha256Length() {
        String hash = PasswordUtils.hashPassword("password123");

        assertEquals(64, hash.length());
    }

    @Test
    public void hashPassword_emptyPassword_returnsValidHash() {
        String hash = PasswordUtils.hashPassword("");

        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}