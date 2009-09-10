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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CameraButtonIntentReceiver extends BroadcastReceiver {
    public CameraButtonIntentReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Try to get the camera hardware
        CameraHolder holder = CameraHolder.instance();
        if (holder.tryOpen() == null) return;

        // We are going to launch the camera, so hold the camera for later use
        holder.keep();
        holder.release();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClass(context, Camera.class);
        i.addCategory("android.intent.category.LAUNCHER");
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}
