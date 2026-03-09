package com.example.burnout_app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfileEntity {

    @PrimaryKey
    public int id = 1;

    public String nombre;
    public String apellidos;
    public String email;
    public String passwordHash;

    public UserProfileEntity(String nombre, String apellidos, String email, String passwordHash) {
        this.id = 1;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
