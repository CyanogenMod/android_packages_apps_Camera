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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileDescriptor;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This class provides several utilities to cancel/prevent potential heavy
 * loading of CPU in bitmap decoding.
 *
 * To use this class, be sure to call setAllowDecoding(true) in onResume
 * and turn it off by cancelAllDecoding() in onPause, and replace all
 * BitmapFactory.decodeFileDescriptor with BitmapManager.decodeFileDescriptor.
 *
 * Individual thread can be controlled by {allow/cancel}ThreadDecoding.
 */
public class BitmapManager {
    private static final String TAG = "BitmapManager";
    private static enum State {RUNNING, CANCEL, WAIT}
    private static class ThreadStatus {
        public State state = State.WAIT;
        public BitmapFactory.Options options;

        @Override
        public String toString() {
            String s;
            if (state == State.RUNNING) {
                s = "Running";
            } else if (state == State.CANCEL) {
                s = "Cancel";
            } else {
                s = "Wait";
            }
            s = "thread state = " + s + ", options = " + options;
            return s;
        }
    }
    private final WeakHashMap<Thread, ThreadStatus> mThreadStatus =
            new WeakHashMap<Thread, ThreadStatus>();
    private boolean mAllowDecoding = false;
    private boolean mLocked = false;
    private boolean mCheckResourceLock = false;
    private static BitmapManager sManager;

    public static BitmapManager instance() {
        if (sManager == null) {
            sManager = new BitmapManager();
        }
        return sManager;
    }

    private BitmapManager() {
    }

    public synchronized void setCheckResourceLock(boolean b) {
        mCheckResourceLock = b;
    }

    /**
     * Get thread status and create one if specified.
     */
    private synchronized ThreadStatus getThreadStatus(Thread t,
            boolean createNew) {
        ThreadStatus status = mThreadStatus.get(t);
        if (status == null && createNew) {
            status = new ThreadStatus();
            mThreadStatus.put(t, status);
        }
        return status;
    }

    /**
     * Since Bitmap related operations are resource-intensive (CPU/Memory),
     * we can't affort too many threads running at the same time, so only
     * thread that acquire the lock can proceed, others have to wait in the
     * queue. It can also be canceled and removed by other thread to avoid
     * starvation.
     */
    public synchronized boolean acquireResourceLock() {
        Thread t = Thread.currentThread();
        ThreadStatus status = getThreadStatus(t, true);

        while (mLocked) {
            try {
                wait();
                // remove canceled thread
                if (status.state == State.CANCEL) {
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, ex.toString());
            }

        }
        status.state = State.RUNNING;
        mLocked = true;
        return true;
    }

    /**
     * Make sure "acquire/release" are pairing correctly
     */
    public synchronized void releaseResourceLock() {
        Thread t = Thread.currentThread();
        mLocked = false;
        notifyAll();
    }

    /**
     * The following three methods are used to keep track of
     * BitmapFaction.Options used for decoding and cancelling.
     */
    private synchronized void setDecodingOptions(Thread t,
            BitmapFactory.Options options) {
        getThreadStatus(t, true).options = options;
    }

    synchronized BitmapFactory.Options getDecodingOptions(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        return status != null ? status.options : null;
    }

    synchronized void removeDecodingOptions(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        status.options = null;
    }

    /**
     * The following three methods are used to keep track of which thread
     * is being disabled for bitmap decoding.
     */
    public synchronized boolean canThreadDecoding(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        if (status == null) {
            Log.v(TAG, "can't find status for thread " + t);
            return false;
        }

        boolean result = (status.state == State.RUNNING) ||
                (status.state != State.CANCEL && !mCheckResourceLock);
        return result;
    }

    public synchronized void allowThreadDecoding(Thread t) {
        getThreadStatus(t, true).state = State.WAIT;
    }

    public synchronized void cancelThreadDecoding(Thread t) {
        ThreadStatus status = getThreadStatus(t, true);
        status.state = State.CANCEL;
        if (status.options != null) {
            status.options.requestCancelDecode();
        }

        // Wake up threads in waiting list
        notifyAll();
    }

    /**
     * The following four methods are used to control global switch of
     * bitmap decoding.
     */
    public synchronized void cancelAllDecoding() {
        allowAllDecoding(false);
        for (ThreadStatus status : mThreadStatus.values()) {
            status.state = State.CANCEL;
            if (status.options != null) {
                status.options.requestCancelDecode();
            }
        }

        // Wake up all threads in the waiting list
        notifyAll();
    }

    public synchronized void allowAllDecoding() {
        allowAllDecoding(true);
    }

    public synchronized void allowAllDecoding(boolean reset) {
        mAllowDecoding = true;
        if (reset) {
            mThreadStatus.clear();
        }
    }

    public synchronized boolean canDecode() {
        return mAllowDecoding;
    }

    /**
     * A debugging routine.
     */
    public synchronized void dump() {
        Iterator<Map.Entry<Thread, ThreadStatus>> i =
                mThreadStatus.entrySet().iterator();

        while (i.hasNext()) {
            Map.Entry<Thread, ThreadStatus> entry = i.next();
            Log.v(TAG, "[Dump] Thread " + entry.getKey() + " ("
                    + entry.getKey().getId()
                    + ")'s status is " + entry.getValue());
        }
    }

    /**
     * The real place to delegate bitmap decoding to BitmapFactory.
     */
    public Bitmap decodeFileDescriptor(FileDescriptor fd,
                                       BitmapFactory.Options options) {
        if (options.mCancel) {
            return null;
        }

        // Does the global switch turn on?
        if (!canDecode()) {
            // This is a bug, and we should fix the caller.
            Util.debugWhere(TAG, "canDecode() == false");
            return null;
        }

        // Can current thread decode?
        Thread thread = Thread.currentThread();
        if (!canThreadDecoding(thread)) {
            // This is a bug, and we should fix the caller.
            Util.debugWhere(TAG, "canThreadDecoding() == false");
            return null;
        }

        setDecodingOptions(thread, options);

        Bitmap b = BitmapFactory.decodeFileDescriptor(fd, null, options);

        // In case legacy code cancel it in traditional way
        if (options.mCancel) {
            cancelThreadDecoding(thread);
        }
        removeDecodingOptions(thread);

        return b;
    }
}
