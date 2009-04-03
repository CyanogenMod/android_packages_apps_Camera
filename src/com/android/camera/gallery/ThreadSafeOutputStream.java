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

import java.io.IOException;
import java.io.OutputStream;

/**
 * A wrapper of an <code>OutputStream</code>, so that all the IO operations are
 * thread safe.
 */
class ThreadSafeOutputStream extends OutputStream {
    private OutputStream mDelegateStream;
    boolean mClosed;

    public ThreadSafeOutputStream(OutputStream delegate) {
        mDelegateStream = delegate;
    }

    @Override
    public synchronized void close() {
        try {
            mClosed = true;
            mDelegateStream.close();
        } catch (IOException ex) {
            //TODO: this should be thrown out.
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        super.flush();
    }

    @Override
    public void write(byte[] b, int offset, int length) throws IOException {
        while (length > 0) {
            synchronized (this) {
                if (mClosed) return;
                int writeLength = Math.min(8192, length);
                mDelegateStream.write(b, offset, writeLength);
                offset += writeLength;
                length -= writeLength;
            }
        }
    }

    @Override
    public synchronized void write(int oneByte) throws IOException {
        if (mClosed) return;
        mDelegateStream.write(oneByte);
    }
}