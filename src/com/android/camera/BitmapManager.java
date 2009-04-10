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
import android.graphics.Rect;
import android.util.Config;
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
    private static final boolean VERBOSE =
            Config.LOGD && (false || Config.LOGV);
    private static enum State {RUNNING, CANCEL, WAIT}
    private static class ThreadStatus {
        public State state = State.WAIT;
        public BitmapFactory.Options options;
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
    private WeakHashMap<Thread, ThreadStatus> mThreadStatus =
            new WeakHashMap<Thread, ThreadStatus>();
    private boolean mAllowDecoding = false;
    private boolean mLocked = false;
    private boolean mCheckResourceLock = true;
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
    
    private synchronized boolean checkResourceLock() {
        return mCheckResourceLock;
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
        
        if (VERBOSE) {
            Log.v(TAG, "lock... thread " + t + "(" + t.getId() + ")");
        }

        while (mLocked) {
            try {
                if (VERBOSE) { 
                    Log.v(TAG, "waiting... thread " + t.getId());
                }
                wait();
                // remove canceled thread
                if (status.state == State.CANCEL) {
                    if (VERBOSE) {
                        Log.v(TAG, "[" + t + "] someone cancels me!!");
                    }
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(TAG, ex.toString());
            }
            
        }
        if (VERBOSE) {
            Log.v(TAG, "locked... thread " + t + "(" + t.getId() + ")");
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
        if (VERBOSE) {
            Log.v(TAG, "unlocking... thread " + t + "(" + t.getId() + ")");
        }
        mLocked = false;
        notifyAll();
    }

    /**
     * The following three methods are used to keep track of 
     * BitmapFaction.Options used for decoding and cancelling.
     */    
    private synchronized void setDecodingOptions(Thread t, 
            BitmapFactory.Options options) {
        if (VERBOSE) {
            Log.v(TAG, "setDecodingOptions for thread " + t.getId()
                    + ", options=" + options);
        }
        getThreadStatus(t, true).options = options;
    }
    
    synchronized BitmapFactory.Options getDecodingOptions(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        return status != null ? status.options : null;
    }
    
    synchronized void removeDecodingOptions(Thread t) {
        if (VERBOSE) {
            Log.v(TAG, "removeDecodingOptions for thread " + t.getId());
        }
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
        if (VERBOSE) {
            Log.v(TAG, "canThread " + t + " allow to decode "
                    + result);
        }
        return result;
    }
    
    public synchronized void allowThreadDecoding(Thread t) {
        if (VERBOSE) {
            Log.v(TAG, "allowThreadDecoding: " + t + "(" + t.getId() + ")");
        }
        getThreadStatus(t, true).state = State.WAIT;
    }
    
    public synchronized void cancelThreadDecoding(Thread t) {
        if (VERBOSE) {
            Log.v(TAG, "[Cancel Thread] cancelThreadDecode: " 
                    + t + "(" + t.getId() + ")");
        }
        ThreadStatus status = getThreadStatus(t, true);
        status.state = State.CANCEL;
        if (status.options != null) {
            if (VERBOSE) {
                Log.v(TAG, "[Cancel Decoding] options: " + status.options);
            }
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
        if (VERBOSE) {
            Log.v(TAG, ">>>>>>>> cancelAllDecoding <<<<<<<");
        }
        allowAllDecoding(false);
        for (ThreadStatus status : mThreadStatus.values()) {
            status.state = State.CANCEL;
            if (status.options != null) {
                if (VERBOSE) {
                    Log.v(TAG, "cancelDecode: " + status.options);
                }
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
        if (VERBOSE) {
            Log.v(TAG, ">>>>>>>> allowAllDecoding <<<<<<<");
        }
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
                                       Rect outPadding, 
                                       BitmapFactory.Options options) {
        // Does the global switch turn on?
        if (!canDecode() || options.mCancel) {
           if (VERBOSE) {
               Log.v(TAG, "Not allowed to decode.");
           }
           return null;
        }              
              
        // Can current thread decode?   
        Thread thread = Thread.currentThread();
        if (!canThreadDecoding(thread)) {
           if (VERBOSE) {
               Log.v(TAG, "Thread " + thread + "(" + thread.getId()
                       + ") is not allowed to decode");
           }
           return null;
        }
        
        setDecodingOptions(thread, options);
        if (VERBOSE) {
            Log.v(TAG, "decodeFileDescriptor: " + options + ", cancel=" 
                    + options.mCancel);
        }

        long t = System.currentTimeMillis();
        Bitmap b = BitmapFactory.decodeFileDescriptor(fd, null, options);
        
        // In case legacy code cancel it in traditional way
        if (options.mCancel) {
            cancelThreadDecoding(thread);
        }
        if (VERBOSE) {
            Log.v(TAG, "decodeFileDescriptor done: options=" + options 
                    + ", cancel=" + options.mCancel + ", it takes " 
                    + (System.currentTimeMillis() - t) + " ms.");
        }
        removeDecodingOptions(thread);
        
        return b;
    }
}
