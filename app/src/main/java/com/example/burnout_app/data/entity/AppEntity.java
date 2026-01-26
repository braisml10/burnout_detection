package com.example.burnout_app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app")
public class AppEntity {

    @PrimaryKey(autoGenerate = true)
    public long app_id;
    public String package_name;
    @NonNull
    public String name;
    public String category;
    public boolean is_ignored;

    public AppEntity(String package_name, String name, String category, boolean is_ignored) {
        this.package_name = package_name;
        this.name = name;
        this.category = category;
        this.is_ignored = is_ignored;
    }
}
