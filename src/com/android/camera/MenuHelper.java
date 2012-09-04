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
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.util.LightCycleHelper;

/**
 * A utility class to handle various kinds of menu operations.
 */
public class MenuHelper {
    private static final String TAG = "MenuHelper";
    private static final String CAMERA_CLASS = "com.android.camera.Camera";
    private static final String PANORAMA_CLASS = "com.android.camera.PanoramaActivity";
    private static final String VIDEO_CAMERA_CLASS = "com.android.camera.VideoCamera";

    private static void startCameraActivity(Activity activity, Intent intent,
                boolean keepCamera) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Keep the camera instance for a while.
        // This avoids re-opening the camera and saves time.
        if (keepCamera) CameraHolder.instance().keep();

        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void gotoMode(int mode, Activity activity, boolean secure) {
        String packageName = activity.getPackageName();
        boolean keepCamera = true;
        Intent it = new Intent();
        switch (mode) {
            case ModePicker.MODE_PANORAMA:
                it.setAction(Intent.ACTION_MAIN);
                if (LightCycleHelper.hasLightCycleCapture(activity)) {
                    keepCamera = false;
                    LightCycleHelper.setupCaptureIntent(it, Storage.DIRECTORY);
                } else {
                    it.setClassName(packageName, PANORAMA_CLASS);
                }
                break;
            case ModePicker.MODE_VIDEO:
                it.setAction(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
                it.setClassName(packageName, VIDEO_CAMERA_CLASS);
                break;
            case ModePicker.MODE_CAMERA:
                it.setAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                it.setClassName(packageName, CAMERA_CLASS);
                break;
            default:
                Log.e(TAG, "unknown camera mode:" + mode);
                return;
        }
        if (secure) {
            it.putExtra(ActivityBase.SECURE_CAMERA_EXTRA, secure);
        }
        startCameraActivity(activity, it, keepCamera);
    }
}
