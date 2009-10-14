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
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.ArrayList;

class PhotoAppWidgetBind extends NoSearchActivity {
    private static final String TAG = "PhotoAppWidgetBind";
    private static final String EXTRA_APPWIDGET_BITMAPS =
            "com.android.camera.appwidgetbitmaps";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        finish();

        // The caller has requested that we bind a given bitmap to a specific
        // appWidgetId, which probably is happening during a Launcher upgrade.
        // This is dangerous because the caller could set bitmaps on
        // appWidgetIds they don't own, so we guard this call at the manifest
        // level by requiring the BIND_APPWIDGET permission.

        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();

        final int[] appWidgetIds =
                extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        final ArrayList<Bitmap> bitmaps =
                extras.getParcelableArrayList(EXTRA_APPWIDGET_BITMAPS);

        if (appWidgetIds == null || bitmaps == null
                || appWidgetIds.length != bitmaps.size()) {
            Log.e(TAG, "Problem parsing photo widget bind request");
            return;
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        PhotoDatabaseHelper helper = new PhotoDatabaseHelper(this);
        for (int i = 0; i < appWidgetIds.length; i++) {
            // Store the cropped photo in our database
            int appWidgetId = appWidgetIds[i];
            helper.setPhoto(appWidgetId, bitmaps.get(i));

            // Push newly updated widget to surface
            RemoteViews views =
                    PhotoAppWidgetProvider.buildUpdate(this, appWidgetId,
                    helper);
            appWidgetManager.updateAppWidget(new int[] { appWidgetId }, views);
        }
        helper.close();
    }
}
