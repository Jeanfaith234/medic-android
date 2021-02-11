package org.medicmobile.webapp.mobile.db;

import android.database.Cursor;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface FormDataDao {

    /**
     * Insert a person data into the table
     *
     * @return row ID for newly inserted data
     */
    @Insert
    long insert(FormDataEntity formDataEntity);


    /**
     * select all person
     *
     * @return A {@link Cursor} of all person in the table
     */
    @Query("SELECT * FROM form_data LIMIT 2")
    Cursor findAll();

    /**
     * Delete a person by ID
     *
     * @return A number of persons deleted
     */
    @Query("DELETE FROM form_data WHERE id = :id ")
    int delete(long id);

    /**
     * Update the person
     *
     * @return A number of persons updated
     */
    @Update
    int update(FormDataEntity formedata);

}
