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
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import java.io.Closeable;

/**
 * A utility class to handle various kinds of menu operations.
 */
public class MenuHelper {
    private static final String TAG = "MenuHelper";

    // TODO: These should be public and added to frameworks.
    private static final int INCLUDE_IMAGES = (1 << 0);
    private static final int INCLUDE_VIDEOS = (1 << 2);

    public static final int POSITION_SWITCH_CAMERA_MODE = 1;
    public static final int POSITION_GOTO_GALLERY = 2;
    public static final int POSITION_SWITCH_CAMERA_ID = 3;

    private static final int NO_ANIMATION = 0;
    private static final String CAMERA_CLASS = "com.android.camera.Camera";
    private static final String VIDEO_CAMERA_CLASS = "com.android.camera.VideoCamera";

    public static void confirmAction(Context context, String title,
            String message, final Runnable action) {
        OnClickListener listener = new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        if (action != null) action.run();
                }
            }
        };
        new AlertDialog.Builder(context)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, listener)
            .setNegativeButton(android.R.string.cancel, listener)
            .create()
            .show();
    }

    static void addSwitchModeMenuItem(Menu menu, boolean switchToVideo,
            final Runnable r) {
        int labelId = switchToVideo
                ? R.string.switch_to_video_lable
                : R.string.switch_to_camera_lable;
        int iconId = switchToVideo
                ? R.drawable.ic_menu_camera_video_view
                : android.R.drawable.ic_menu_camera;
        MenuItem item = menu.add(Menu.NONE, Menu.NONE,
                POSITION_SWITCH_CAMERA_MODE, labelId)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                r.run();
                return true;
            }
        });
        item.setIcon(iconId);
    }

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

    public static void gotoVideoMode(Activity activity) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
        startCameraActivity(activity, intent, VIDEO_CAMERA_CLASS);
    }

    public static void gotoCameraMode(Activity activity) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        startCameraActivity(activity, intent, CAMERA_CLASS);
    }

    public static void gotoVideoMode(Activity activity, Intent intent) {
        startCameraActivity(activity, intent, VIDEO_CAMERA_CLASS);
     }

    public static void gotoCameraMode(Activity activity, Intent intent) {
        startCameraActivity(activity, intent, CAMERA_CLASS);
    }

    public static void gotoCameraImageGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_bucket_name, INCLUDE_IMAGES);
    }

    public static void gotoCameraVideoGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_videos_bucket_name, INCLUDE_VIDEOS);
    }

    private static void gotoGallery(Activity activity, int windowTitleId,
            int mediaTypes) {
        Uri target = Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                .appendQueryParameter("bucketId", Storage.BUCKET_ID).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("windowTitle", activity.getString(windowTitleId));
        intent.putExtra("mediaTypes", mediaTypes);

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not start gallery activity", e);
        }
    }
}
