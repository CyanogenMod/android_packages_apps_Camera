/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;


/**
 * A utility class to handle various kinds of menu operations.
 */
public class MenuHelper {
    private static final String TAG = "MenuHelper";

    // TODO: These should be public and added to frameworks.
    private static final int INCLUDE_IMAGES = (1 << 0);
    private static final int INCLUDE_VIDEOS = (1 << 2);

    private static final String CAMERA_CLASS = "com.android.camera.Camera";
    private static final String PANORAMA_CLASS = "com.android.camera.PanoramaActivity";
    private static final String VIDEO_CAMERA_CLASS = "com.android.camera.VideoCamera";

    private static void startCameraActivity(Activity activity, Intent intent,
            String className) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.setClassName(activity.getPackageName(), className);

        // Keep the camera instance for a while.
        // This avoids re-opening the camera and saves time.
        CameraHolder.instance().keep();

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            intent.setComponent(null);
            activity.startActivity(intent);
        }
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void gotoMode(int mode, Activity activity) {
        String action, className;
        switch (mode) {
            case ModePicker.MODE_PANORAMA:
                action = PANORAMA_CLASS;
                className = PANORAMA_CLASS;
                break;
            case ModePicker.MODE_VIDEO:
                action = MediaStore.INTENT_ACTION_VIDEO_CAMERA;
                className = VIDEO_CAMERA_CLASS;
                break;
            case ModePicker.MODE_CAMERA:
                action = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
                className = CAMERA_CLASS;
                break;
            default:
                Log.e(TAG, "unknown camera mode:" + mode);
                return;
        }
        Intent it = new Intent(action);
        startCameraActivity(activity, it, className);
    }
}
