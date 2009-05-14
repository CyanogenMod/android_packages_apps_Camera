
package com.android.camera.tests.unit;

import com.android.camera.BitmapManager;
import com.android.camera.ImageManager;
import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;

import android.content.Context;
import android.graphics.Bitmap;
import android.test.AndroidTestCase;

/**
 * BitmapManager's unit tests.
 */
public class BitmapManagerUnitTest extends AndroidTestCase {
    IImageList mImageList;
    IImage mImage;
    BitmapManager mBitmapManager;
    Context mContext;

    private class DecodeThread extends Thread {
        Bitmap bitmap;
        boolean needsLock;

        public DecodeThread(boolean needsLock) {
            this.needsLock = needsLock;
        }

        public void run() {
            if (needsLock) {
                BitmapManager.instance().acquireResourceLock();
            }
            bitmap = mImage.thumbBitmap();
            if (needsLock) {
                BitmapManager.instance().releaseResourceLock();
            }
        }

        public Bitmap getBitmap() {
            return bitmap;
        }
    }

    public void setUp() {
        mContext = getContext();
        mBitmapManager = BitmapManager.instance();
        mImageList = ImageManager.allImages(mContext.getContentResolver(),
                                            ImageManager.DataLocation.ALL,
                                            ImageManager.INCLUDE_IMAGES,
                                            ImageManager.SORT_DESCENDING);
        mImage = mImageList.getImageAt(0);
    }

    public void testSingleton() {
        BitmapManager manager = BitmapManager.instance();
        assertNotNull(manager);
        assertNotNull(mBitmapManager);
        assertSame(manager, mBitmapManager);
    }

    public void testCheckResourceLockWithoutAcquiringLock() {
        DecodeThread t = new DecodeThread(false);
        assertTrue(mBitmapManager.canThreadDecoding(t));
        mBitmapManager.setCheckResourceLock(true);
        try {
            assertFalse(mBitmapManager.canThreadDecoding(t));
            t.start();
            t.join();
        } catch (InterruptedException ex) {
        } finally {
            mBitmapManager.setCheckResourceLock(false);
            assertNull(t.getBitmap());
        }
    }

    public void testCheckResourceLockWithAcquiringLock() {
        DecodeThread t1 = new DecodeThread(true);
        DecodeThread t2 = new DecodeThread(true);
        assertTrue(mBitmapManager.canThreadDecoding(t1));
        assertTrue(mBitmapManager.canThreadDecoding(t2));

        // If checking resource lock is necessary, then we can't
        // proceed without acquiring the lock first.
        mBitmapManager.setCheckResourceLock(true);
        assertFalse(mBitmapManager.canThreadDecoding(t1));
        assertFalse(mBitmapManager.canThreadDecoding(t2));

        try {
            // Start two threads at the same time.
            t1.start();
            t2.start();

            Thread.currentThread().sleep(100);
            boolean b1 = mBitmapManager.canThreadDecoding(t1);
            boolean b2 = mBitmapManager.canThreadDecoding(t2);

            // Only one of them can get the lock.
            assertTrue(b1 ^ b2);

            t1.join();
            t2.join();
        } catch (InterruptedException ex) {
        } finally {
            mBitmapManager.setCheckResourceLock(false);

            // Both threads can decode the bitmap eventually.
            assertNotNull(t1.getBitmap());
            assertNotNull(t2.getBitmap());
        }
    }

    public void testDecoding() {
        assertNotNull(mImage);
        mBitmapManager.setCheckResourceLock(false);
        Bitmap bitmap = mImage.thumbBitmap();
        assertNotNull(bitmap);

        // Disable all decoding.
        mBitmapManager.cancelAllDecoding();
        bitmap = mImage.thumbBitmap();
        assertNull(bitmap);
    }

    public void testCanThreadDecoding() {
        Thread t = new DecodeThread(false);

        // By default all threads can decode.
        assertTrue(mBitmapManager.canThreadDecoding(t));

        // Disallow thread t to decode.
        mBitmapManager.cancelThreadDecoding(t);
        assertFalse(mBitmapManager.canThreadDecoding(t));

        // Allow thread t to decode again.
        mBitmapManager.allowThreadDecoding(t);
        assertTrue(mBitmapManager.canThreadDecoding(t));
    }

    public void testThreadDecoding() {
        DecodeThread t1 = new DecodeThread(false);
        DecodeThread t2 = new DecodeThread(false);
        mBitmapManager.setCheckResourceLock(false);
        mBitmapManager.allowThreadDecoding(t1);
        mBitmapManager.cancelThreadDecoding(t2);
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

        mBitmapManager.cancelAllDecoding();
    }

    @Override
    public String toString() {
        return "BitmapManagerUnitTest";
    }
}
