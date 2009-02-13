/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.gadget.GadgetManager;
import android.gadget.GadgetProvider;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Instances;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Config;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.RemoteViews;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Simple gadget to show a user-selected picture.
 */
public class PhotoGadgetProvider extends GadgetProvider {
    static final String TAG = "PhotoGadgetProvider";
    static final boolean LOGD = Config.LOGD || true;
    
    @Override
    public void onUpdate(Context context, GadgetManager gadgetManager, int[] gadgetIds) {
        // Update each requested gadgetId with its unique photo
        PhotoDatabaseHelper helper = new PhotoDatabaseHelper(context);
        for (int gadgetId : gadgetIds) {
            int[] specificGadget = new int[] { gadgetId };
            RemoteViews views = buildUpdate(context, gadgetId, helper);
            if (LOGD) Log.d(TAG, "sending out views="+views+" for id="+gadgetId);
            gadgetManager.updateGadget(specificGadget, views);
        }
        helper.close();
    }
    
    @Override
    public void onDeleted(Context context, int[] gadgetIds) {
        // Clean deleted photos out of our database
        PhotoDatabaseHelper helper = new PhotoDatabaseHelper(context);
        for (int gadgetId : gadgetIds) {
            helper.deletePhoto(gadgetId);
        }
        helper.close();
    }

    /**
     * Load photo for given gadget and build {@link RemoteViews} for it.
     */
    static RemoteViews buildUpdate(Context context, int gadgetId, PhotoDatabaseHelper helper) {
        RemoteViews views = null;
        Bitmap bitmap = helper.getPhoto(gadgetId);
        if (bitmap != null) {
            views = new RemoteViews(context.getPackageName(), R.layout.photo_frame);
            views.setImageViewBitmap(R.id.photo, bitmap);
        }
        return views;
    }

    static class PhotoDatabaseHelper extends SQLiteOpenHelper {
        private final Context mContext;

        private static final String DATABASE_NAME = "launcher.db";
        
        private static final int DATABASE_VERSION = 1;

        static final String TABLE_PHOTOS = "photos";
        static final String FIELD_GADGET_ID = "gadgetId";
        static final String FIELD_PHOTO_BLOB = "photoBlob";

        PhotoDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_PHOTOS + " (" +
                    FIELD_GADGET_ID + " INTEGER PRIMARY KEY," +
                    FIELD_PHOTO_BLOB + " BLOB" +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            int version = oldVersion;
            
            if (version != DATABASE_VERSION) {
                Log.w(TAG, "Destroying all old data.");
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_PHOTOS);
                onCreate(db);
            }
        }
        
        /**
         * Store the given bitmap in this database for the given gadgetId.
         */
        public boolean setPhoto(int gadgetId, Bitmap bitmap) {
            boolean success = false;
            try {
                // Try go guesstimate how much space the icon will take when serialized
                // to avoid unnecessary allocations/copies during the write.
                int size = bitmap.getWidth() * bitmap.getHeight() * 4;
                ByteArrayOutputStream out = new ByteArrayOutputStream(size);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();

                ContentValues values = new ContentValues();
                values.put(PhotoDatabaseHelper.FIELD_GADGET_ID, gadgetId);
                values.put(PhotoDatabaseHelper.FIELD_PHOTO_BLOB, out.toByteArray());
                    
                SQLiteDatabase db = getWritableDatabase();
                db.insertOrThrow(PhotoDatabaseHelper.TABLE_PHOTOS, null, values);
                
                success = true;
            } catch (SQLiteException e) {
                Log.e(TAG, "Could not open database", e);
            } catch (IOException e) {
                Log.e(TAG, "Could not serialize photo", e);
            }
            if (LOGD) Log.d(TAG, "setPhoto success="+success);
            return success;
        }
        
        static final String[] PHOTOS_PROJECTION = {
            FIELD_PHOTO_BLOB,
        };
        
        static final int INDEX_PHOTO_BLOB = 0;
        
        /**
         * Inflate and return a bitmap for the given gadgetId.
         */
        public Bitmap getPhoto(int gadgetId) {
            Cursor c = null;
            Bitmap bitmap = null;
            try {
                SQLiteDatabase db = getReadableDatabase();
                String selection = String.format("%s=%d", FIELD_GADGET_ID, gadgetId);
                c = db.query(TABLE_PHOTOS, PHOTOS_PROJECTION, selection, null,
                        null, null, null, null);
                
                if (c != null && LOGD) Log.d(TAG, "getPhoto query count="+c.getCount());

                if (c != null && c.moveToFirst()) {
                    byte[] data = c.getBlob(INDEX_PHOTO_BLOB);
                    if (data != null) {
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    }
                }
            } catch (SQLiteException e) {
                Log.e(TAG, "Could not load photo from database", e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            return bitmap;
        }
        
        /**
         * Remove any bitmap associated with the given gadgetId.
         */
        public void deletePhoto(int gadgetId) {
            try {
                SQLiteDatabase db = getWritableDatabase();
                String whereClause = String.format("%s=%d", FIELD_GADGET_ID, gadgetId);
                db.delete(TABLE_PHOTOS, whereClause, null);
            } catch (SQLiteException e) {
                Log.e(TAG, "Could not delete photo from database", e);
            }
        }
    }
    
}

