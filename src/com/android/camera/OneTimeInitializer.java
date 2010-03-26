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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.util.Log;

import android.content.ContentResolver;
import android.net.Uri;

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

    static final String TAG = "camera";

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
        // Disable itself.
        setComponentEnabled(context, getClass(), false);
        // If in the future we have more than one thing to do, we may also
        // store the progress in a preference like the Email app does.
        updateCameraShortcut(context);
    }

    private void setComponentEnabled(Context context, Class<?> clazz, boolean enabled) {
        final ComponentName c = new ComponentName(context, clazz.getName());
        context.getPackageManager().setComponentEnabledSetting(c,
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    // This is the content uri for Launcher content provider, see
    // LauncherSettings and LauncherProvider in the Launcher app for details.
    static final Uri LAUNCHER_CONTENT_URI =
            Uri.parse("content://com.android.launcher2.settings/favorites" +
            "?notify=true");

    // We have two names for the old component.
    static final ComponentName oldComp = new ComponentName(
            "com.android.camera", "com.android.camera.Camera");
    static final ComponentName oldCompShort = new ComponentName(
            "com.android.camera", ".Camera");

    void updateCameraShortcut(Context context) {
        ComponentName newComp = new ComponentName(
                context, "com.android.camera.Camera");

        Log.v(TAG, "newComp = " + newComp);

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
                    if (comp.equals(oldComp) || comp.equals(oldCompShort)) {
                        Log.v(TAG, "fix shortcut id " + id + " from " + comp +
                                " to " + newComp);
                        shortcut.setComponent(newComp);
                        values.put("intent", shortcut.toUri(0));
                        cr.update(LAUNCHER_CONTENT_URI, values, "_id = ?",
                                new String[] { String.valueOf(id) });
                    }
                } catch (Throwable t) {
                    // Move to the next one if there is any problem.
                    continue;
                }
            }
        } finally {
            c.close();
        }
    }
}
