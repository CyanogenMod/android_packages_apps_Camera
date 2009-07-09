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

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import static com.android.camera.Util.Assert;

/**
 * A dedicated decoding thread used by ImageGallery.
 */
public class ImageLoader {
    @SuppressWarnings("unused")
    private static final String TAG = "ImageLoader";

    // Queue of work to do in the worker thread. The work is done in order.
    private final ArrayList<WorkItem> mQueue = new ArrayList<WorkItem>();

    // the worker thread and a done flag so we know when to exit
    private boolean mDone;
    private Thread mDecodeThread;

    // Thumbnail checking will be done when there is no getBitmap requests
    // need to be processed.
    private ThumbnailChecker mThumbnailChecker;

    /**
     * Notify interface of how many thumbnails are processed.
     */
    public interface ThumbCheckCallback {
        public boolean checking(int current, int count);
        public void done();
    }

    public interface LoadedCallback {
        public void run(Bitmap result);
    }

    public void getBitmap(IImage image,
                          LoadedCallback imageLoadedRunnable,
                          int tag) {
        if (mDecodeThread == null) {
            start();
        }
        synchronized (mQueue) {
            WorkItem w = new WorkItem(image, imageLoadedRunnable, tag);
            mQueue.add(w);
            mQueue.notifyAll();
        }
    }

    public boolean cancel(final IImage image) {
        synchronized (mQueue) {
            int index = findItem(image);
            if (index >= 0) {
                mQueue.remove(index);
                return true;
            } else {
                return false;
            }
        }
    }

    // The caller should hold mQueue lock.
    private int findItem(IImage image) {
        for (int i = 0; i < mQueue.size(); i++) {
            if (mQueue.get(i).mImage == image) {
                return i;
            }
        }
        return -1;
    }

    // Clear the queue. Returns an array of tags that were in the queue.
    public int[] clearQueue() {
        synchronized (mQueue) {
            int n = mQueue.size();
            int[] tags = new int[n];
            for (int i = 0; i < n; i++) {
                tags[i] = mQueue.get(i).mTag;
            }
            mQueue.clear();
            return tags;
        }
    }

    private static class WorkItem {
        IImage mImage;
        LoadedCallback mOnLoadedRunnable;
        int mTag;

        WorkItem(IImage image, LoadedCallback onLoadedRunnable, int tag) {
            mImage = image;
            mOnLoadedRunnable = onLoadedRunnable;
            mTag = tag;
        }
    }

    public ImageLoader(Handler handler) {
        mThumbnailChecker = new ThumbnailChecker();
        start();
    }

    private class WorkerThread implements Runnable {

        // IDLE_TIME is the time we wait before we start checking thumbnail.
        // This gives the thumbnail generation work priority because there
        // may be a short period of time when the queue is empty while
        // ImageBlockManager is calculating what to load next.
        private static final long IDLE_TIME = 1000000000;  // in nanoseconds.
        private long mLastWorkTime = System.nanoTime();

        // Pick off items on the queue, one by one, and compute their bitmap.
        // Place the resulting bitmap in the cache, then call back by executing
        // the given runnable so things can get updated appropriately.
        public void run() {
            while (true) {
                WorkItem workItem = null;
                synchronized (mQueue) {
                    if (mDone) {
                        break;
                    }
                    if (!mQueue.isEmpty()) {
                        workItem = mQueue.remove(0);
                    } else {
                        if (!mThumbnailChecker.hasMoreThumbnailsToCheck()) {
                            try {
                                mQueue.wait();
                            } catch (InterruptedException ex) {
                                // ignore the exception
                            }
                            continue;
                        } else {
                            // Calculate the time we need to be idle before we
                            // start checking thumbnail.
                            long t = IDLE_TIME -
                                    (System.nanoTime() - mLastWorkTime);
                            t = t / 1000000;  // convert to milliseconds.
                            if (t > 0) {
                                try {
                                    mQueue.wait(t);
                                } catch (InterruptedException ex) {
                                    // ignore the exception
                                }
                                continue;
                            }
                        }
                    }
                }

                // This holds if and only if the above
                // hasMoreThumbnailsToCheck() returns true. (We put the call
                // here because we want to release the lock on mQueue.
                if (workItem == null) {
                    mThumbnailChecker.checkNextThumbnail();
                    continue;
                }

                final Bitmap b = workItem.mImage.miniThumbBitmap();

                if (workItem.mOnLoadedRunnable != null) {
                    workItem.mOnLoadedRunnable.run(b);
                }

                mLastWorkTime = System.nanoTime();
            }
        }
    }

    private void start() {
        if (mDecodeThread != null) {
            return;
        }

        mDone = false;
        Thread t = new Thread(new WorkerThread());
        t.setName("image-loader");
        mDecodeThread = t;
        t.start();
    }

    public void stop() {
        synchronized (mQueue) {
            mDone = true;
            mQueue.notifyAll();
        }
        if (mDecodeThread != null) {
            try {
                Thread t = mDecodeThread;
                BitmapManager.instance().cancelThreadDecoding(t);
                t.join();
                mDecodeThread = null;
            } catch (InterruptedException ex) {
                // so now what?
            }
        }
        stopCheckingThumbnails();
    }

    // Passthrough to ThumbnailChecker.
    public void startCheckingThumbnails(IImageList imageList,
            ThumbCheckCallback cb) {
        mThumbnailChecker.startCheckingThumbnails(imageList, cb);
        // Kick WorkerThread to start working.
        synchronized (mQueue) {
            mQueue.notifyAll();
        }
    }

    public void stopCheckingThumbnails() {
        mThumbnailChecker.stopCheckingThumbnails();
    }
}

// This is part of ImageLoader which is responsible for checking thumbnails.
//
// The methods of ThumbnailChecker need to be synchronized because the data
// will also be accessed by the WorkerThread. The methods of ThumbnailChecker
// is only called by ImageLoader.
class ThumbnailChecker {
    private static final String TAG = "ThumbnailChecker";

    private IImageList mImageListToCheck;  // The image list we will check.
    private int mTotalToCheck;  // total number of thumbnails to check.
    private int mNextToCheck;  // next thumbnail to check,
                               // -1 if no further checking is needed.
    private ImageLoader.ThumbCheckCallback mThumbCheckCallback;

    ThumbnailChecker() {
        mNextToCheck = -1;
    }

    // Both imageList and cb must be non-null.
    synchronized void startCheckingThumbnails(IImageList imageList,
            ImageLoader.ThumbCheckCallback cb) {
        Assert(imageList != null);
        Assert(cb != null);
        mImageListToCheck = imageList;
        mTotalToCheck = imageList.getCount();
        mNextToCheck = 0;
        mThumbCheckCallback = cb;

        if (!ImageManager.hasStorage()) {
            Log.v(TAG, "bailing from the image checker -- no storage");
            stopCheckingThumbnails();
        }
    }

    synchronized void stopCheckingThumbnails() {
        if (mThumbCheckCallback == null) return;  // already stopped.
        mThumbCheckCallback.done();
        mImageListToCheck = null;
        mTotalToCheck = 0;
        mNextToCheck = -1;
        mThumbCheckCallback = null;
    }

    synchronized boolean hasMoreThumbnailsToCheck() {
        return mNextToCheck != -1;
    }

    synchronized void checkNextThumbnail() {
        if (mNextToCheck == -1) {
            return;
        }

        if (mNextToCheck >= mTotalToCheck) {
            stopCheckingThumbnails();
            return;
        }

        try {
            mImageListToCheck.checkThumbnail(mNextToCheck);
        } catch (IOException ex) {
            Log.e(TAG, "Failed to check thumbnail..."
                    + " was the sd card removed? - " + ex.getMessage());
            stopCheckingThumbnails();
            return;
        }

        if (!mThumbCheckCallback.checking(mNextToCheck, mTotalToCheck)) {
            stopCheckingThumbnails();
            return;
        }

        mNextToCheck++;
    }
}
