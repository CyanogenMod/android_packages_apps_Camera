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

import android.os.Process;

/**
 * A generic thread wrapper used by several gallery activities.
 */
class BitmapThread {
    private Thread mThread;
    private int mTid;
    private boolean mTidSet;
    private boolean mFinished;

    private synchronized void setTid(int tid) {
        mTid = tid;
        mTidSet = true;
        BitmapThread.this.notifyAll();
    }

    private synchronized void setFinished() {
        mFinished = true;
    }

    public BitmapThread(final Runnable r) {
        Runnable wrapper = new Runnable() {
            public void run() {
                setTid(Process.myTid());
                try {
                    r.run();
                } finally {
                    setFinished();
                }
            }
        };

        mThread = new Thread(wrapper);
    }

    public synchronized void start() {
        mThread.start();
    }

    public synchronized void setName(String name) {
        mThread.setName(name);
    }

    public void join() {
        try {
            BitmapManager.instance().cancelThreadDecoding(mThread);
            mThread.join();
        } catch (InterruptedException ex) {
            // Ignore this exception.
        }
    }

    public long getId() {
        return mThread.getId();
    }

    public Thread realThread() {
        return mThread;
    }

    public synchronized void setPriority(int androidOsPriority) {
        while (!mTidSet) {
            try {
                BitmapThread.this.wait();
            } catch (InterruptedException ex) {
                // ok, try again
            }
        }
        if (!mFinished) {
            Process.setThreadPriority(mTid, androidOsPriority);
        }
    }

    public synchronized void toBackground() {
        setPriority(Process.THREAD_PRIORITY_BACKGROUND);
    }

    public synchronized void toForeground() {
        setPriority(Process.THREAD_PRIORITY_FOREGROUND);
    }
}
