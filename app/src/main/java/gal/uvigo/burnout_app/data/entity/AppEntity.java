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
    public long appId;

    @NonNull
    public String packageName;

    @NonNull
    public String name;

    public String category;
    public boolean isIgnored;

    public AppEntity(@NonNull String packageName, @NonNull String name, String category, boolean isIgnored) {
        this.packageName = packageName;
        this.name = name;
        this.category = category;
        this.isIgnored = isIgnored;
    }
}
