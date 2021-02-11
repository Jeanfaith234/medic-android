package org.medicmobile.webapp.mobile.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {FormDataEntity.class}, version = 1)
public abstract class MedicDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "medicdb";
    private static MedicDatabase INSTANCE;

    public static MedicDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context, MedicDatabase.class, DATABASE_NAME).build();
        }
        return INSTANCE;
    }

    public abstract FormDataDao getFormDataDao();

}
