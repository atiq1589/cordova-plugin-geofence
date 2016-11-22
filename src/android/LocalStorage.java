package com.appelit.geofence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

class LocalStorage {
    private final LocalStorageDBHelper localStorageDBHelper;
    private SQLiteDatabase database;

    LocalStorage(Context context) {
        localStorageDBHelper = LocalStorageDBHelper.getInstance(context);
    }

    List<String> getAllItems() {
        ArrayList<String> results = new ArrayList<String>();
        database = localStorageDBHelper.getReadableDatabase();
        Cursor cursor = database.query(LocalStorageDBHelper.LOCALSTORAGE_TABLE_NAME, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            results.add(cursor.getString(1));
        }
        cursor.close();
        database.close();
        return results;
    }

    String getItem(String key) {
        String value = null;
        if (key != null) {
            database = localStorageDBHelper.getReadableDatabase();
            Cursor cursor = database.query(LocalStorageDBHelper.LOCALSTORAGE_TABLE_NAME, null, LocalStorageDBHelper.LOCALSTORAGE_ID + " = ?", new String[] { key }, null, null, null);
            if (cursor.moveToFirst()) {
                value = cursor.getString(1);
            }
            cursor.close();
            database.close();
        }
        return value;
    }

    void setItem(String key, String value) {
        if (key != null && value != null) {
            String oldValue = getItem(key);
            database = localStorageDBHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(LocalStorageDBHelper.LOCALSTORAGE_ID, key);
            values.put(LocalStorageDBHelper.LOCALSTORAGE_VALUE, value);
            if (oldValue != null) {
                database.update(LocalStorageDBHelper.LOCALSTORAGE_TABLE_NAME, values, LocalStorageDBHelper.LOCALSTORAGE_ID + "='" + key + "'", null);
            } else {
                database.insert(LocalStorageDBHelper.LOCALSTORAGE_TABLE_NAME, null, values);
            }
            database.close();
        }
    }

    void removeItem(String key) {
        if (key != null) {
            database = localStorageDBHelper.getWritableDatabase();
            database.delete(LocalStorageDBHelper.LOCALSTORAGE_TABLE_NAME, LocalStorageDBHelper.LOCALSTORAGE_ID + "='" + key + "'", null);
            database.close();
        }
    }

    void clear() {
        database = localStorageDBHelper.getWritableDatabase();
        database.delete(LocalStorageDBHelper.LOCALSTORAGE_TABLE_NAME, null, null);
        database.close();
    }
}