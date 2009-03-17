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

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Config;
import android.util.Log;

class ImageLoader {
    private static final String TAG = "ImageLoader";

    // queue of work to do in the worker thread
    private ArrayList<WorkItem>      mQueue = new ArrayList<WorkItem>();
    private ArrayList<WorkItem>      mInProgress = new ArrayList<WorkItem>();

    // the worker thread and a done flag so we know when to exit
    // currently we only exit from finalize
    private boolean                  mDone;
    private ArrayList<Thread>        mDecodeThreads = new ArrayList<Thread>();
    private android.os.Handler       mHandler;

    private int                      mThreadCount = 1;

    synchronized void clear(Uri uri) {
    }

    synchronized public void dump() {
        synchronized (mQueue) {
            if (Config.LOGV)
                Log.v(TAG, "Loader queue length is " + mQueue.size());
        }
    }

    public interface LoadedCallback {
        public void run(Bitmap result);
    }

    public void pushToFront(final ImageManager.IImage image) {
        synchronized (mQueue) {
            WorkItem w = new WorkItem(image, 0, null, false);

            int existing = mQueue.indexOf(w);
            if (existing >= 1) {
                WorkItem existingWorkItem = mQueue.remove(existing);
                mQueue.add(0, existingWorkItem);
                mQueue.notifyAll();
            }
        }
    }

    public boolean cancel(final ImageManager.IImage image) {
        synchronized (mQueue) {
            WorkItem w = new WorkItem(image, 0, null, false);

            int existing = mQueue.indexOf(w);
            if (existing >= 0) {
                mQueue.remove(existing);
                return true;
            }
            return false;
        }
    }

    public Bitmap getBitmap(final ImageManager.IImage image, final LoadedCallback imageLoadedRunnable, final boolean postAtFront, boolean postBack) {
        return getBitmap(image, 0, imageLoadedRunnable, postAtFront, postBack);
    }

    public Bitmap getBitmap(final ImageManager.IImage image, int tag, final LoadedCallback imageLoadedRunnable, final boolean postAtFront, boolean postBack) {
        synchronized (mDecodeThreads) {
            if (mDecodeThreads.size() == 0) {
                start();
            }
        }
        long t1 = System.currentTimeMillis();
        long t2,t3,t4;
        synchronized (mQueue) {
            t2 = System.currentTimeMillis();
            WorkItem w = new WorkItem(image, tag, imageLoadedRunnable, postBack);

            if (!mInProgress.contains(w)) {
                boolean contains = mQueue.contains(w);
                if (contains) {
                    if (postAtFront) {
                        // move this item to the front
                        mQueue.remove(w);
                        mQueue.add(0, w);
                    }
                } else {
                    if (postAtFront)
                        mQueue.add(0, w);
                    else
                        mQueue.add(w);
                    mQueue.notifyAll();
                }
            }
            if (false)
                dumpQueue("+" + (postAtFront ? "F " : "B ") + tag + ": ");
            t3 = System.currentTimeMillis();
        }
        t4 = System.currentTimeMillis();
//        Log.v(TAG, "getBitmap breakdown: tot= " + (t4-t1) + "; " + "; " + (t4-t3) + "; " + (t3-t2) + "; " + (t2-t1));
        return null;
    }

    private void dumpQueue(String s) {
        synchronized (mQueue) {
            StringBuilder sb = new StringBuilder(s);
            for (int i = 0; i < mQueue.size(); i++) {
                sb.append(mQueue.get(i).mTag + " ");
            }
            if (Config.LOGV)
                Log.v(TAG, sb.toString());
        }
    }

    long bitmapSize(Bitmap b) {
        return b.getWidth() * b.getHeight() * 4;
    }

    class WorkItem {
        ImageManager.IImage mImage;
        int mTargetX, mTargetY;
        int mTag;
        LoadedCallback mOnLoadedRunnable;
        boolean mPostBack;

        WorkItem(ImageManager.IImage image, int tag, LoadedCallback onLoadedRunnable, boolean postBack) {
            mImage = image;
            mTag = tag;
            mOnLoadedRunnable = onLoadedRunnable;
            mPostBack = postBack;
        }

        public boolean equals(Object other) {
            WorkItem otherWorkItem = (WorkItem) other;
            if (otherWorkItem.mImage != mImage)
                return false;

            return true;
        }

        public int hashCode() {
            return mImage.fullSizeImageUri().hashCode();
        }
    }

    public ImageLoader(android.os.Handler handler, int threadCount) {
        mThreadCount = threadCount;
        mHandler = handler;
        start();
    }

    synchronized private void start() {
        if (Config.LOGV)
            Log.v(TAG, "ImageLoader.start() <<<<<<<<<<<<<<<<<<<<<<<<<<<<");

        synchronized (mDecodeThreads) {
            if (mDecodeThreads.size() > 0)
                return;

            mDone = false;
            for (int i = 0;i < mThreadCount; i++) {
                Thread t = new Thread(new Runnable() {
                    // pick off items on the queue, one by one, and compute their bitmap.
                    // place the resulting bitmap in the cache.  then post a notification
                    // back to the ui so things can get updated appropriately.
                    public void run() {
                        while (!mDone) {
                            WorkItem workItem = null;
                            synchronized (mQueue) {
                                if (mQueue.size() > 0) {
                                    workItem = mQueue.remove(0);
                                    mInProgress.add(workItem);
                                }
                                else {
                                    try {
                                        mQueue.wait();
                                    } catch (InterruptedException ex) {
                                    }
                                }
                            }
                            if (workItem != null) {
                                if (false)
                                    dumpQueue("-" + workItem.mTag + ": ");
                                Bitmap b = null;
                                try {
                                    b = workItem.mImage.miniThumbBitmap();
                                } catch (Exception ex) {
                                    if (Config.LOGV) Log.v(TAG, "couldn't load miniThumbBitmap " + ex.toString());
                                    // sd card removal or sd card full
                                }
                                if (b == null) {
                                    if (Config.LOGV) Log.v(TAG, "unable to read thumbnail for " + workItem.mImage.fullSizeImageUri());
                                }

                                synchronized (mQueue) {
                                    mInProgress.remove(workItem);
                                }

                                if (workItem.mOnLoadedRunnable != null) {
                                    if (workItem.mPostBack) {
                                        final WorkItem w1 = workItem;
                                        final Bitmap bitmap = b;
                                        if (!mDone) {
                                            mHandler.post(new Runnable() {
                                                public void run() {
                                                    w1.mOnLoadedRunnable.run(bitmap);
                                                }
                                            });
                                        }
                                    } else {
                                        workItem.mOnLoadedRunnable.run(b);
                                    }
                                }
                            }
                        }
                    }
                });
                t.setName("image-loader-" + i);
                mDecodeThreads.add(t);
                t.start();
            }
        }
    }

    public static Bitmap transform(Matrix scaler, Bitmap source, int targetWidth, int targetHeight,
            boolean scaleUp) {
        int deltaX = source.getWidth() - targetWidth;
        int deltaY = source.getHeight() - targetHeight;
        if (!scaleUp && (deltaX < 0 || deltaY < 0)) {
            /*
             * In this case the bitmap is smaller, at least in one dimension, than the
             * target.  Transform it by placing as much of the image as possible into
             * the target and leaving the top/bottom or left/right (or both) black.
             */
            Bitmap b2 = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b2);

            int deltaXHalf = Math.max(0, deltaX/2);
            int deltaYHalf = Math.max(0, deltaY/2);
            Rect src = new Rect(
                    deltaXHalf,
                    deltaYHalf,
                    deltaXHalf + Math.min(targetWidth, source.getWidth()),
                    deltaYHalf + Math.min(targetHeight, source.getHeight()));
            int dstX = (targetWidth  - src.width())  / 2;
            int dstY = (targetHeight - src.height()) / 2;
            Rect dst = new Rect(
                    dstX,
                    dstY,
                    targetWidth - dstX,
                    targetHeight - dstY);
            if (Config.LOGV)
                Log.v(TAG, "draw " + src.toString() + " ==> " + dst.toString());
            c.drawBitmap(source, src, dst, null);
            return b2;
        }
        float bitmapWidthF = source.getWidth();
        float bitmapHeightF = source.getHeight();

        float bitmapAspect = bitmapWidthF / bitmapHeightF;
        float viewAspect   = (float) targetWidth / (float) targetHeight;

        if (bitmapAspect > viewAspect) {
            float scale = targetHeight / bitmapHeightF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        } else {
            float scale = targetWidth / bitmapWidthF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        }

        Bitmap b1;
        if (scaler != null) {
            // this is used for minithumb and crop, so we want to filter here.
            b1 = Bitmap.createBitmap(source, 0, 0,
                    source.getWidth(), source.getHeight(), scaler, true);
        } else {
            b1 = source;
        }

        int dx1 = Math.max(0, b1.getWidth() - targetWidth);
        int dy1 = Math.max(0, b1.getHeight() - targetHeight);

        Bitmap b2 = Bitmap.createBitmap(
                b1,
                dx1/2,
                dy1/2,
                targetWidth,
                targetHeight);

        if (b1 != source)
            b1.recycle();

        return b2;
    }

    public void stop() {
        if (Config.LOGV)
            Log.v(TAG, "ImageLoader.stop " + mDecodeThreads.size() + " threads");
        mDone = true;
        synchronized (mQueue) {
            mQueue.notifyAll();
        }
        while (mDecodeThreads.size() > 0) {
            Thread t = mDecodeThreads.get(0);
            try {
                t.join();
                mDecodeThreads.remove(0);
            } catch (InterruptedException ex) {
                // so now what?
            }
        }
    }
}
