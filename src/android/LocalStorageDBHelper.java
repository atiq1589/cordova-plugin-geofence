package com.appelit.geofence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class LocalStorageDBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "geonotifications.db";
    static final String LOCALSTORAGE_TABLE_NAME = "geonotifications";
    static final String LOCALSTORAGE_ID = "_id";
    static final String LOCALSTORAGE_VALUE = "value";
    private static final String DICTIONARY_TABLE_CREATE = "CREATE TABLE " + LOCALSTORAGE_TABLE_NAME + " (" + LOCALSTORAGE_ID + " TEXT PRIMARY KEY, " + LOCALSTORAGE_VALUE + " TEXT NOT NULL);";
    private static LocalStorageDBHelper mInstance;

    private LocalStorageDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    static LocalStorageDBHelper getInstance(Context ctx) {
        if (mInstance == null) {
            mInstance = new LocalStorageDBHelper(ctx);
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DICTIONARY_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(LocalStorageDBHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + LOCALSTORAGE_TABLE_NAME);
        onCreate(db);
    }
}