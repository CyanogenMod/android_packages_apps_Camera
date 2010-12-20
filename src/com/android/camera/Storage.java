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

import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

class Storage {
    private static final String TAG = "CameraStorage";

    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;

    public static long getAvailableSpace() {
        try {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_CHECKING.equals(state)) {
                return PREPARING;
            }
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
                return UNAVAILABLE;
            }
            StatFs stat = new StatFs(
                    Environment.getExternalStorageDirectory().toString());
            return stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }
}
