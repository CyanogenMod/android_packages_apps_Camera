/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Wallpaper picker for the camera application. This just redirects to the
 * standard pick action.
 */
public class Wallpaper extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "Wallpaper";
    private static final int PHOTO_PICKED = 1;
    private static final int CROP_DONE = 2;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Uri imageToUse = getIntent().getData();
        if (imageToUse != null) {
            Intent intent = new Intent();
            intent.setClassName("com.android.camera",
                                "com.android.camera.CropImage");
            intent.setData(imageToUse);
            formatIntent(intent);
            startActivityForResult(intent, CROP_DONE);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            formatIntent(intent);
            startActivityForResult(intent, PHOTO_PICKED);
        }
    }

    protected void formatIntent(Intent intent) {
        int width = getWallpaperDesiredMinimumWidth();
        int height = getWallpaperDesiredMinimumHeight();
        intent.putExtra("outputX",         width);
        intent.putExtra("outputY",         height);
        intent.putExtra("aspectX",         width);
        intent.putExtra("aspectY",         height);
        intent.putExtra("scale",           true);
        intent.putExtra("noFaceDetection", true);
        intent.putExtra("setWallpaper",    true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if ((requestCode == PHOTO_PICKED || requestCode == CROP_DONE)) {
            setResult(resultCode);
            finish();
        }
    }
}
