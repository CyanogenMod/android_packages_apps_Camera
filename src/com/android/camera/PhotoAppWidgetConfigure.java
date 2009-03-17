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

import com.android.camera.PhotoAppWidgetProvider.PhotoDatabaseHelper;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PhotoAppWidgetConfigure extends Activity {
    static final private String TAG = "PhotoAppWidgetConfigure";
    
    static final int REQUEST_GET_PHOTO = 2;
    
    int appWidgetId = -1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // Someone is requesting that we configure the given appWidgetId, which means
        // we prompt the user to pick and crop a photo.
        
        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (appWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }

        // TODO: get these values from constants somewhere
        // TODO: Adjust the PhotoFrame's image size to avoid on the fly scaling
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 192);
        intent.putExtra("outputY", 192);
        intent.putExtra("noFaceDetection", true);
        intent.putExtra("return-data", true);
        
        startActivityForResult(intent, REQUEST_GET_PHOTO);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && appWidgetId != -1) {
            // Store the cropped photo in our database
            Bitmap bitmap = (Bitmap) data.getParcelableExtra("data");
            
            PhotoDatabaseHelper helper = new PhotoDatabaseHelper(this);
            if (helper.setPhoto(appWidgetId, bitmap)) {
                resultCode = Activity.RESULT_OK;

                // Push newly updated widget to surface
                RemoteViews views = PhotoAppWidgetProvider.buildUpdate(this, appWidgetId, helper);
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
                appWidgetManager.updateAppWidget(new int[] { appWidgetId }, views);
            }
            helper.close();
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }
        
        // Make sure we pass back the original appWidgetId
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(resultCode, resultValue);
        finish();
    }
    
}
