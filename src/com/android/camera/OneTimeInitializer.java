/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

/**
 * A class that performs one-time initialization after installation.
 *
 * <p>Android doesn't offer any mechanism to trigger an app right after installation, so we use the
 * BOOT_COMPLETED broadcast intent instead.  This means, when the app is upgraded, the
 * initialization code here won't run until the device reboots.
 *
 * <p>This is adapted from the Email app.
 */
public class OneTimeInitializer extends BroadcastReceiver {

    private static final String TAG = "camera";
    private ArrayList<Entry> mMappingTable;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "OneTimeInitializer.onReceive");
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            initialize(context);
        }
    }

    /**
     * Perform the one-time initialization.
     */
    private void initialize(Context context) {
        // If in the future we have more than one thing to do, we may also
        // store the progress in a preference like the Email app does.
        updateShortcut(context);

        // Disable itself.
        setComponentEnabled(context, getClass(), false);
    }

    private void setComponentEnabled(Context context, Class<?> clazz, boolean enabled) {
        final ComponentName c = new ComponentName(context, clazz.getName());
        context.getPackageManager().setComponentEnabledSetting(c,
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    // This is the content uri for Launcher content provider. See
    // LauncherSettings and LauncherProvider in the Launcher app for details.
    private static final Uri LAUNCHER_CONTENT_URI =
            Uri.parse("content://com.android.launcher2.settings/favorites" +
            "?notify=true");


    private static class Entry {
        ComponentName mOldComp, mNewComp;
        Entry(ComponentName oldComp, ComponentName newComp) {
            mOldComp = oldComp;
            mNewComp = newComp;
        }
    }

    private void prepareTable(Context context) {
        mMappingTable = new ArrayList<Entry>();
        // We have two names for each of the old component.
        String oldPkg = "com.android.camera";
        String newPkg = context.getPackageName();

        Log.v(TAG, "oldPkg = " + oldPkg + ", newPkg = " + newPkg);

        ComponentName oldCamera = new ComponentName(
                oldPkg, "com.android.camera.Camera");
        ComponentName oldCameraShort = new ComponentName(
                oldPkg, ".Camera");
        ComponentName oldVideoCamera = new ComponentName(
                oldPkg, "com.android.camera.VideoCamera");
        ComponentName oldVideoCameraShort = new ComponentName(
                oldPkg, ".VideoCamera");

        // The new names.
        ComponentName newCamera = new ComponentName(
                newPkg, "com.android.camera.Camera");
        ComponentName newVideoCamera = new ComponentName(
                newPkg, "com.android.camera.VideoCamera");

        mMappingTable.add(new Entry(oldCamera, newCamera));
        mMappingTable.add(new Entry(oldCameraShort, newCamera));
        mMappingTable.add(new Entry(oldVideoCamera, newVideoCamera));
        mMappingTable.add(new Entry(oldVideoCameraShort, newVideoCamera));
    }

    private void updateShortcut(Context context) {

        prepareTable(context);

        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(LAUNCHER_CONTENT_URI,
                new String[] { "_id", "intent" }, null, null, null);
        if (c == null) return;

        ContentValues values = new ContentValues(1);

        try {
            while (c.moveToNext()) {
                try {
                    long id = c.getLong(0);
                    String intentUri = c.getString(1);
                    if (intentUri == null) continue;
                    Intent shortcut = Intent.parseUri(intentUri, 0);
                    ComponentName comp = shortcut.getComponent();
                    for (Entry e : mMappingTable) {
                        if (comp.equals(e.mOldComp)) {
                            Log.v(TAG, "fix shortcut id " + id + " from " +
                                    comp + " to " + e.mNewComp);
                            shortcut.setComponent(e.mNewComp);
                            values.put("intent", shortcut.toUri(0));
                            cr.update(LAUNCHER_CONTENT_URI, values, "_id = ?",
                                    new String[] { String.valueOf(id) });
                            break;
                        }
                    }
                } catch (Throwable t) {
                    // Move to the next one if there is any problem.
                    Log.w(TAG, "can't parse a shortcut entry", t);
                    continue;
                }
            }
        } finally {
            c.close();
        }
    }
}
