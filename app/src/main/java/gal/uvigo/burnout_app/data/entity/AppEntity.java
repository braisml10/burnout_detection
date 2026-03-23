package gal.uvigo.burnout_app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "app",
        indices = {@Index(value = {"package_name"}, unique = true)}
)
public class AppEntity {

    @PrimaryKey(autoGenerate = true)
    public long app_id;

    @NonNull
    public String package_name;

    @NonNull
    public String name;

    public String category;
    public boolean is_ignored;

    public AppEntity(@NonNull String package_name, @NonNull String name, String category, boolean is_ignored) {
        this.package_name = package_name;
        this.name = name;
        this.category = category;
        this.is_ignored = is_ignored;
    }
}
