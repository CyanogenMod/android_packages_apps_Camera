/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

// We use a queue to store the SaveRequests that have not been completed
// yet. The main thread puts the request into the queue. The saver thread
// gets it from the queue, does the work, and removes it from the queue.
//
// The main thread needs to wait for the saver thread to finish all the work
// in the queue, when the activity's onPause() is called, we need to finish
// all the work, so other programs (like Gallery) can see all the images.
//
// If the queue becomes too long, adding a new request will block the main
// thread until the queue length drops below the threshold (QUEUE_LIMIT).
// If we don't do this, we may face several problems: (1) We may OOM
// because we are holding all the jpeg data in memory. (2) We may ANR
// when we need to wait for saver thread finishing all the work (in
// onPause() or gotoGallery()) because the time to finishing a long queue
// of work may be too long.
class MediaSaver extends Thread {
    private static final int SAVE_QUEUE_LIMIT = 3;
    private static final String TAG = "MediaSaver";

    private ArrayList<SaveRequest> mQueue;
    private boolean mStop;
    private ContentResolver mContentResolver;

    public interface OnMediaSavedListener {
        public void onMediaSaved(Uri uri);
    }

    public MediaSaver(ContentResolver resolver) {
        mContentResolver = resolver;
        mQueue = new ArrayList<SaveRequest>();
        start();
    }

    // Runs in main thread
    public synchronized boolean queueFull() {
        return (mQueue.size() >= SAVE_QUEUE_LIMIT);
    }

    // Runs in main thread
    public void addImage(final byte[] data, String title, long date, Location loc,
                         int width, int height, int orientation, OnMediaSavedListener l) {
        SaveRequest r = new SaveRequest();
        r.data = data;
        r.date = date;
        r.title = title;
        r.loc = (loc == null) ? null : new Location(loc);  // make a copy
        r.width = width;
        r.height = height;
        r.orientation = orientation;
        r.listener = l;
        synchronized (this) {
            while (mQueue.size() >= SAVE_QUEUE_LIMIT) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    // ignore.
                }
            }
            mQueue.add(r);
            notifyAll();  // Tell saver thread there is new work to do.
        }
    }

    // Runs in saver thread
    @Override
    public void run() {
        while (true) {
            SaveRequest r;
            synchronized (this) {
                if (mQueue.isEmpty()) {
                    notifyAll();  // notify main thread in waitDone

                    // Note that we can only stop after we saved all images
                    // in the queue.
                    if (mStop) break;

                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                    continue;
                }
                if (mStop) break;
                r = mQueue.remove(0);
                notifyAll();  // the main thread may wait in addImage
            }
            Uri uri = storeImage(r.data, r.title, r.date, r.loc, r.width, r.height,
                    r.orientation);
            r.listener.onMediaSaved(uri);
        }
        if (!mQueue.isEmpty()) {
            Log.e(TAG, "Media saver thread stopped with " + mQueue.size() + " images unsaved");
            mQueue.clear();
        }
    }

    // Runs in main thread
    public void finish() {
        synchronized (this) {
            mStop = true;
            notifyAll();
        }
    }

    // Runs in saver thread
    private Uri storeImage(final byte[] data, String title, long date,
                           Location loc, int width, int height, int orientation) {
        Uri uri = Storage.addImage(mContentResolver, title, date, loc,
                                   orientation, data, width, height);
        return uri;
    }

    // Each SaveRequest remembers the data needed to save an image.
    private static class SaveRequest {
        byte[] data;
        String title;
        long date;
        Location loc;
        int width, height;
        int orientation;
        OnMediaSavedListener listener;
    }
}
