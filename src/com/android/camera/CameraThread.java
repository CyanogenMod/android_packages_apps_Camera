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

public class CameraThread {
    private Thread mThread;
    private int mTid;
    private boolean mTidSet;
    private boolean mFinished;

    synchronized private void setTid(int tid) {
        mTid = tid;
        mTidSet = true;
        CameraThread.this.notifyAll();
    }
    
    synchronized private void setFinished() {
        mFinished = true;
    }
    
    public CameraThread(final Runnable r) {
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
    
    synchronized public void start() {
        mThread.start();
    }
    
    synchronized public void setName(String name) {
        mThread.setName(name);
    }
    
    public void join() {
        try {
            mThread.join();
        } catch (InterruptedException ex) {
            // ok?
        }
    }
    
    public long getId() {
        return mThread.getId();
    }
    
    public Thread realThread() {
        return mThread;
    }
    
    synchronized public void setPriority(int androidOsPriority) {
        while (!mTidSet) {
            try {
                CameraThread.this.wait();
            } catch (InterruptedException ex) {
                // ok, try again
            }
        }
        if (!mFinished)
            Process.setThreadPriority(mTid, androidOsPriority);
    }
    
    synchronized public void toBackground() {
        setPriority(Process.THREAD_PRIORITY_BACKGROUND);
    }
    
    synchronized public void toForeground() {
        setPriority(Process.THREAD_PRIORITY_FOREGROUND);
    }
}
