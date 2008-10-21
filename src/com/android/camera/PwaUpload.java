/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.net.Uri;

/**
 *
 */
public class PwaUpload extends Activity
{
    private static final String TAG = "camera";
    
    @Override public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ImageManager.IImageList imageList = ImageManager.instance().allImages(
                this, 
                getContentResolver(), 
                ImageManager.DataLocation.ALL, 
                ImageManager.INCLUDE_IMAGES|ImageManager.INCLUDE_VIDEOS, 
                ImageManager.SORT_ASCENDING);
        Uri uri = (Uri) getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (android.util.Config.LOGV)
            Log.v(TAG, "uri is " + uri);
        ImageManager.IImage imageObj = imageList.getImageForUri(uri);

        if (android.util.Config.LOGV)
            Log.v(TAG, "imageObj is " + imageObj);
        if (imageObj != null) {
            UploadAction.uploadImage(this, imageObj);
        }
        finish();
    }
    
    @Override public void onResume() {
        super.onResume();
    } 
}
