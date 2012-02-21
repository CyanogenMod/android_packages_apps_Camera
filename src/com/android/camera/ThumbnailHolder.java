/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.ContentResolver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public class ThumbnailHolder {
    private static final int CLEAN_THUMBNAIL = 1;

    private static Thumbnail sLastThumbnail;

    private static class LazyHandlerHolder {
        private static final HandlerThread sHandlerThread = new HandlerThread("ClearThumbnail");
        static {
            sHandlerThread.start();
        }
        public static final Handler sHandler =
                new Handler(sHandlerThread.getLooper(), new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        switch(msg.what) {
                            case CLEAN_THUMBNAIL:
                                cleanLastThumbnail();
                                break;
                        }
                        return true;
                    }
                });
    }

    private ThumbnailHolder() {
    }

    public static synchronized Thumbnail getLastThumbnail(ContentResolver resolver) {
        if (sLastThumbnail != null) {  // Thumbnail exists. Checks validity.
            LazyHandlerHolder.sHandler.removeMessages(CLEAN_THUMBNAIL);
            Thumbnail t = sLastThumbnail;
            sLastThumbnail = null;
            if (Util.isUriValid(t.getUri(), resolver)) {
                return t;
            }
        }

        return null;
    }

    private static synchronized void cleanLastThumbnail() {
        sLastThumbnail = null;
    }

    public static synchronized void keep(Thumbnail t) {
        sLastThumbnail = t;
        LazyHandlerHolder.sHandler.removeMessages(CLEAN_THUMBNAIL);
        LazyHandlerHolder.sHandler.sendEmptyMessageDelayed(CLEAN_THUMBNAIL, 3000);
    }
}
