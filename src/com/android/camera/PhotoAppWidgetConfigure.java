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
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

public class PhotoAppWidgetConfigure extends NoSearchActivity {

    @SuppressWarnings("unused")
    private static final String TAG = "PhotoAppWidgetConfigure";
    static final int REQUEST_GET_PHOTO = 2;

    int mAppWidgetId = -1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Someone is requesting that we configure the given mAppWidgetId, which
        // means we prompt the user to pick and crop a photo.

        mAppWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (mAppWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }

        // Assume the widget will be 1/4 of the screen.
        // This will be slightly too large, but there is not a good way to know the
        // actual widget size from here. The image will be scaled to fit since the layout
        // file specifies android:scaleType="centerCrop"
        DisplayMetrics display = getResources().getDisplayMetrics();
        int maxDimension = Math.max(display.heightPixels, display.widthPixels);
        maxDimension /= 2;

        // TODO: Adjust the PhotoFrame's image size to avoid on the fly scaling
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", maxDimension);
        intent.putExtra("outputY", maxDimension);
        intent.putExtra("noFaceDetection", true);
        intent.putExtra("return-data", true);

        startActivityForResult(intent, REQUEST_GET_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK && mAppWidgetId != -1) {
            // Store the cropped photo in our database
            Bitmap bitmap = (Bitmap) data.getParcelableExtra("data");

            PhotoDatabaseHelper helper = new PhotoDatabaseHelper(this);
            if (helper.setPhoto(mAppWidgetId, bitmap)) {
                resultCode = Activity.RESULT_OK;

                // Push newly updated widget to surface
                RemoteViews views = PhotoAppWidgetProvider.buildUpdate(this,
                        mAppWidgetId, helper);
                AppWidgetManager appWidgetManager =
                        AppWidgetManager.getInstance(this);
                appWidgetManager.updateAppWidget(new int[] {mAppWidgetId},
                                                 views);
            }
            helper.close();
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }

        // Make sure we pass back the original mAppWidgetId
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(resultCode, resultValue);
        finish();
    }

}
