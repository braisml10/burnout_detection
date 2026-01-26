package com.example.burnout_app.data.room;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.burnout_app.data.entity.ScreenEventEntity;

@Database(entities =  { ScreenEventEntity.class }, version = 1)

public abstract class BurnoutDatabase extends RoomDatabase {

    private static volatile BurnoutDatabase INSTANCE;

    //public abstract BurnoutDao burnoutDAO();

    public static BurnoutDatabase getInstance(Context context) {
        // Comprobar si la bd existe en memoria
        if (INSTANCE == null){
            // evitar que 2 hilos creen la BD al mismo tiempo
            synchronized (BurnoutDatabase.class) {
                if (INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), BurnoutDatabase.class, "Burnout.db" ).build();
                }
            }
        }
        return INSTANCE;
    }
    @NonNull
    @Override
    protected InvalidationTracker createInvalidationTracker() {
        return null;
    }

    @Override
    public void clearAllTables() {

    }
}
