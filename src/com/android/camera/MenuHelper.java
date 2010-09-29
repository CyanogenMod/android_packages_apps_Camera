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
import android.os.Environment;
import android.os.StatFs;
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

    public static final int INCLUDE_ALL           = 0xFFFFFFFF;
    public static final int INCLUDE_VIEWPLAY_MENU = (1 << 0);
    public static final int INCLUDE_SHARE_MENU    = (1 << 1);
    public static final int INCLUDE_SET_MENU      = (1 << 2);
    public static final int INCLUDE_CROP_MENU     = (1 << 3);
    public static final int INCLUDE_DELETE_MENU   = (1 << 4);
    public static final int INCLUDE_ROTATE_MENU   = (1 << 5);
    public static final int INCLUDE_DETAILS_MENU  = (1 << 6);
    public static final int INCLUDE_SHOWMAP_MENU  = (1 << 7);

    public static final int MENU_IMAGE_SHARE = 1;
    public static final int MENU_IMAGE_SHOWMAP = 2;

    public static final int POSITION_SWITCH_CAMERA_MODE = 1;
    public static final int POSITION_GOTO_GALLERY = 2;
    public static final int POSITION_SWITCH_CAMERA_ID = 3;

    public static final int NO_STORAGE_ERROR = -1;
    public static final int CANNOT_STAT_ERROR = -2;
    public static final String EMPTY_STRING = "";
    public static final String JPEG_MIME_TYPE = "image/jpeg";
    // valid range is -180f to +180f
    public static final float INVALID_LATLNG = 255f;

    /** Activity result code used to report crop results.
     */
    public static final int RESULT_COMMON_MENU_CROP = 490;

    private static final int NO_ANIMATION = 0;
    private static final String CAMERA_CLASS = "com.android.camera.Camera";
    private static final String VIDEO_CAMERA_CLASS = "com.android.camera.VideoCamera";

    public static void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }

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
            .setIcon(android.R.drawable.ic_dialog_alert)
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

    private static void startCameraActivity(Activity activity, String action,
            String className) {
        Intent intent = new Intent(action);
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
        activity.overridePendingTransition(NO_ANIMATION, NO_ANIMATION);
    }

    public static void gotoVideoMode(Activity activity) {
        startCameraActivity(activity, MediaStore.INTENT_ACTION_VIDEO_CAMERA,
                VIDEO_CAMERA_CLASS);
    }

    public static void gotoCameraMode(Activity activity) {
        startCameraActivity(activity,
                MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, CAMERA_CLASS);
    }

    public static void gotoCameraImageGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_bucket_name,
                ImageManager.INCLUDE_IMAGES);
    }

    public static void gotoCameraVideoGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_videos_bucket_name,
                ImageManager.INCLUDE_VIDEOS);
    }

    private static void gotoGallery(Activity activity, int windowTitleId,
            int mediaTypes) {
        Uri target = Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                .appendQueryParameter("bucketId",
                ImageManager.CAMERA_IMAGE_BUCKET_ID).build();
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

    public static int calculatePicturesRemaining() {
        try {
            if (!ImageManager.hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {
                String storageDirectory =
                        Environment.getExternalStorageDirectory().toString();
                StatFs stat = new StatFs(storageDirectory);
                final int PICTURE_BYTES = 1500000;
                float remaining = ((float) stat.getAvailableBlocks()
                        * (float) stat.getBlockSize()) / PICTURE_BYTES;
                return (int) remaining;
            }
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many
            // pictures are remaining.  it might be zero but just leave it
            // blank since we really don't know.
            Log.e(TAG, "Fail to access sdcard", ex);
            return CANNOT_STAT_ERROR;
        }
    }
}
