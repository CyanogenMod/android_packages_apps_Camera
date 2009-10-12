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

package com.android.camera;

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;

import android.content.Context;
import android.graphics.Bitmap;
import android.test.AndroidTestCase;

/**
 * BitmapManager's unit tests.
 */
public class BitmapManagerUnitTests extends AndroidTestCase {
    IImageList mImageList;
    IImage mImage;
    BitmapManager mBitmapManager;
    Context mContext;

    private class DecodeThread extends Thread {
        Bitmap bitmap;

        public DecodeThread() {
        }

        @Override
        public void run() {
            bitmap = mImage.thumbBitmap(IImage.ROTATE_AS_NEEDED);
        }

        public Bitmap getBitmap() {
            return bitmap;
        }
    }

    @Override
    public void setUp() {
        mContext = getContext();
        mBitmapManager = BitmapManager.instance();
        mImageList = ImageManager.makeImageList(
                mContext.getContentResolver(),
                ImageManager.DataLocation.ALL,
                ImageManager.INCLUDE_IMAGES,
                ImageManager.SORT_DESCENDING,
                null);
        mImage = mImageList.getImageAt(0);
    }

    public void testSingleton() {
        BitmapManager manager = BitmapManager.instance();
        assertNotNull(manager);
        assertNotNull(mBitmapManager);
        assertSame(manager, mBitmapManager);
    }

    public void testCanThreadDecoding() {
        Thread t = new DecodeThread();

        // By default all threads can decode.
        assertTrue(mBitmapManager.canThreadDecoding(t));

        // Disallow thread t to decode.
        mBitmapManager.cancelThreadDecoding(t, mContext.getContentResolver());
        assertFalse(mBitmapManager.canThreadDecoding(t));

        // Allow thread t to decode again.
        mBitmapManager.allowThreadDecoding(t);
        assertTrue(mBitmapManager.canThreadDecoding(t));
    }

    public void testDefaultAllowDecoding() {
        DecodeThread t = new DecodeThread();
        try {
            t.start();
            t.join();
        } catch (InterruptedException ex) {
        } finally {
            assertNotNull(t.getBitmap());
        }
    }

    public void testCancelDecoding() {
        DecodeThread t = new DecodeThread();
        mBitmapManager.cancelThreadDecoding(t, mContext.getContentResolver());
        try {
            t.start();
            t.join();
        } catch (InterruptedException ex) {
        } finally {
            assertNull(t.getBitmap());
        }
    }

    public void testAllowDecoding() {
        DecodeThread t = new DecodeThread();
        mBitmapManager.cancelThreadDecoding(t, mContext.getContentResolver());
        mBitmapManager.allowThreadDecoding(t);
        try {
            t.start();
            t.join();
        } catch (InterruptedException ex) {
        } finally {
            assertNotNull(t.getBitmap());
        }
    }

    public void testThreadDecoding() {
        DecodeThread t1 = new DecodeThread();
        DecodeThread t2 = new DecodeThread();
        mBitmapManager.allowThreadDecoding(t1);
        mBitmapManager.cancelThreadDecoding(t2, mContext.getContentResolver());
        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ex) {
        } finally {
            assertTrue(mBitmapManager.canThreadDecoding(t1));
            assertNotNull(t1.getBitmap());
            assertFalse(mBitmapManager.canThreadDecoding(t2));
            assertNull(t2.getBitmap());
        }
    }

    @Override
    public String toString() {
        return "BitmapManagerUnitTest";
    }
}
