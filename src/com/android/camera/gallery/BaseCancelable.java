/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera.gallery;


/**
 * A base class for the interface <code>ICancelable</code>.
 */
public abstract class BaseCancelable implements ICancelable {
    protected boolean mCancel = false;
    protected boolean mFinished = false;

    /*
     * Subclasses should call acknowledgeCancel when they're finished with
     * their operation.
     */
    protected synchronized void acknowledgeCancel() {
        mFinished = true;
        if (mCancel) {
            this.notify();
        }
    }

    public synchronized boolean cancel() {
        if (mCancel || mFinished) {
            return false;
        }
        mCancel = true;
        boolean retVal = doCancelWork();
        try {
            this.wait();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        return retVal;
    }

    /*
     * Subclasses can call this to see if they have been canceled.
     * This is the polling model.
     */
    protected synchronized void checkCanceled() throws CanceledException {
        if (mCancel) {
            throw new CanceledException();
        }
    }

    /*
     * Subclasses implement this method to take whatever action
     * is necessary when getting canceled.  Sometimes it's not
     * possible to do anything in which case the "checkCanceled"
     * polling model may be used (or some combination).
     */
    protected abstract boolean doCancelWork();
}