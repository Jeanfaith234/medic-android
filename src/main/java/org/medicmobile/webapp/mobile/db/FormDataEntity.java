package org.medicmobile.webapp.mobile.db;

import android.content.ContentValues;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import static org.medicmobile.webapp.mobile.db.DBCONSTANTS.FORMDATA_ID;
import static org.medicmobile.webapp.mobile.db.DBCONSTANTS.FORM_DATA;
import static org.medicmobile.webapp.mobile.db.DBCONSTANTS.FORM_DATA_TIMESTAMP;

@Entity(tableName = "form_data")
public class FormDataEntity {

    @PrimaryKey(autoGenerate = true)
    private Integer id;

    @ColumnInfo(name = "actual_data")
    private String data;

    @ColumnInfo(name = "timestamp")
    private Long timstamp = System.currentTimeMillis();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Long getTimstamp() {
        return timstamp;
    }

    public void setTimstamp(Long timstamp) {
        this.timstamp = timstamp;
    }

    public static FormDataEntity fromContentValues
            (ContentValues contentValues) {

        FormDataEntity data = new FormDataEntity();
        if (contentValues.containsKey(FORMDATA_ID)) {
            data.setId(contentValues.getAsInteger(FORMDATA_ID));
        }
        if (contentValues.containsKey(FORM_DATA)) {
            data.setData(contentValues.getAsString(FORM_DATA));
        }
        if (contentValues.containsKey(FORM_DATA_TIMESTAMP)) {
            data.setTimstamp(contentValues.getAsLong(FORM_DATA_TIMESTAMP));
        }
        return data;
    }

}
