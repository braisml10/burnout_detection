package gal.uvigo.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfileEntity {

    private static final int SINGLETON_ID = 1;

    @PrimaryKey
    public int id = SINGLETON_ID;

    public String name;
    public String surname;
    public String email;
    public String passwordHash;

    public UserProfileEntity(String name, String surname, String email, String passwordHash) {
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
