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

import com.android.camera.BitmapManager;
import com.android.camera.Util;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Images;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Represents a particular image and provides access to the underlying bitmap
 * and two thumbnail bitmaps as well as other information such as the id, and
 * the path to the actual image data.
 */
public abstract class BaseImage implements IImage {
    private static final String TAG = "BaseImage";
    private static final int UNKNOWN_LENGTH = -1;

    private static final byte [] sMiniThumbData =
            new byte[MiniThumbFile.BYTES_PER_MINTHUMB];

    protected ContentResolver mContentResolver;
    protected long mId, mMiniThumbMagic;
    protected BaseImageList mContainer;
    protected HashMap<String, String> mExifData;
    protected int mCursorRow;

    private int mWidth = UNKNOWN_LENGTH;
    private int mHeight = UNKNOWN_LENGTH;

    protected BaseImage(long id, long miniThumbMagic, ContentResolver cr,
            BaseImageList container, int cursorRow) {
        mContentResolver = cr;
        mId              = id;
        mMiniThumbMagic  = miniThumbMagic;
        mContainer       = container;
        mCursorRow       = cursorRow;
    }

    protected abstract Bitmap.CompressFormat compressionType();

    private class CompressImageToFile extends BaseCancelable<Boolean> {
        private ThreadSafeOutputStream mOutputStream = null;

        private final Bitmap mBitmap;
        private final Uri mUri;
        private final byte[] mJpegData;

        public CompressImageToFile(Bitmap bitmap, byte[] jpegData, Uri uri) {
            mBitmap = bitmap;
            mUri = uri;
            mJpegData = jpegData;
        }

        @Override
        public boolean doCancelWork() {
            if (mOutputStream != null) {
                mOutputStream.close();
                return true;
            }
            return false;
        }

        public Boolean get() {
            try {
                OutputStream delegate = mContentResolver.openOutputStream(mUri);
                synchronized (this) {
                    checkCanceled();
                    mOutputStream = new ThreadSafeOutputStream(delegate);
                }
                if (mBitmap != null) {
                    mBitmap.compress(compressionType(), 75, mOutputStream);
                } else {
                    mOutputStream.write(mJpegData);
                }
                return true;
            } catch (FileNotFoundException ex) {
                return false;
            } catch (CanceledException ex) {
                return false;
            } catch (IOException ex) {
                return false;
            } finally {
                Util.closeSiliently(mOutputStream);
                acknowledgeCancel();
            }
        }
    }

    /**
     * Take a given bitmap and compress it to a file as described
     * by the Uri parameter.
     *
     * @param bitmap    the bitmap to be compressed/stored
     * @param uri       where to store the bitmap
     * @return          true if we succeeded
     */
    protected ICancelable<Boolean> compressImageToFile(
            Bitmap bitmap, byte [] jpegData, Uri uri) {
        return new CompressImageToFile(bitmap, jpegData, uri);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof Image)) return false;
        return fullSizeImageUri().equals(((Image) other).fullSizeImageUri());
    }

    @Override
    public int hashCode() {
        return fullSizeImageUri().toString().hashCode();
    }

    public Bitmap fullSizeBitmap(int targetWidthHeight) {
        return fullSizeBitmap(targetWidthHeight, true);
    }

    protected Bitmap fullSizeBitmap(
            int targetWidthHeight, boolean rotateAsNeeded) {
        Uri url = mContainer.contentUri(mId);
        if (url == null) return null;

        Bitmap b = Util.makeBitmap(targetWidthHeight, url, mContentResolver);
        if (b != null && rotateAsNeeded) {
            b = Util.rotate(b, getDegreesRotated());
        }
        return b;
    }

    private class LoadBitmapCancelable extends BaseCancelable<Bitmap> {
        private final ParcelFileDescriptor mPFD;
        private final BitmapFactory.Options mOptions =
                new BitmapFactory.Options();
        private final int mTargetWidthHeight;

        public LoadBitmapCancelable(
                ParcelFileDescriptor pfdInput, int targetWidthHeight) {
            mPFD = pfdInput;
            mTargetWidthHeight = targetWidthHeight;
        }

        @Override
        public boolean doCancelWork() {
            mOptions.requestCancelDecode();
            return true;
        }

        public Bitmap get() {
            try {
                Bitmap b = Util.makeBitmap(
                        mTargetWidthHeight, fullSizeImageUri(),
                        mContentResolver, mPFD, mOptions);
                if (b != null) {
                    b = Util.rotate(b, getDegreesRotated());
                }
                return b;
            } catch (RuntimeException ex) {
                return null;
            } catch (Error e) {
                return null;
            } finally {
                acknowledgeCancel();
            }
        }
    }


    public ICancelable<Bitmap> fullSizeBitmapCancelable(
            int targetWidthHeight) {
        try {
            ParcelFileDescriptor pfdInput = mContentResolver
                    .openFileDescriptor(fullSizeImageUri(), "r");
            return new LoadBitmapCancelable(pfdInput, targetWidthHeight);
        } catch (FileNotFoundException ex) {
            return null;
        } catch (UnsupportedOperationException ex) {
            return null;
        }
    }

    public InputStream fullSizeImageData() {
        try {
            InputStream input = mContentResolver.openInputStream(
                    fullSizeImageUri());
            return input;
        } catch (IOException ex) {
            return null;
        }
    }

    public long fullSizeImageId() {
        return mId;
    }

    public Uri fullSizeImageUri() {
        return mContainer.contentUri(mId);
    }

    public IImageList getContainer() {
        return mContainer;
    }

    Cursor getCursor() {
        return mContainer.getCursor();
    }

    public long getDateTaken() {
        if (mContainer.indexDateTaken() < 0) return 0;
        Cursor c = getCursor();
        synchronized (c) {
            c.moveToPosition(getRow());
            return c.getLong(mContainer.indexDateTaken());
        }
    }

    protected int getDegreesRotated() {
        return 0;
    }

    public String getMimeType() {
        if (mContainer.indexMimeType() < 0) {
            Cursor c = mContentResolver.query(fullSizeImageUri(),
                    new String[] { "_id", Images.Media.MIME_TYPE },
                    null, null, null);
            try {
                return c.moveToFirst() ? c.getString(1) : "";
            } finally {
                c.close();
            }
        } else {
            String mimeType = null;
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    mimeType = c.getString(mContainer.indexMimeType());
                }
            }
            return mimeType;
        }
    }

    public String getTitle() {
        String name = null;
        Cursor c = getCursor();
        synchronized (c) {
            if (c.moveToPosition(getRow())) {
                if (mContainer.indexTitle() != -1) {
                    name = c.getString(mContainer.indexTitle());
                }
            }
        }
        return name != null && name.length() > 0 ? name : String.valueOf(mId);
    }

    public String getDisplayName() {
        if (mContainer.indexDisplayName() < 0) {
            Cursor c = mContentResolver.query(fullSizeImageUri(),
                    new String[] { "_id", Images.Media.DISPLAY_NAME },
                    null, null, null);
            try {
                if (c.moveToFirst()) return c.getString(1);
            } finally {
                c.close();
            }
        } else {
            String name = null;
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    name = c.getString(mContainer.indexDisplayName());
                }
            }
            if (name != null && name.length() > 0) {
                return name;
            }
        }
        return String.valueOf(mId);
    }

    public int getRow() {
        return mCursorRow;
    }

    private void setupDimension() {
        ParcelFileDescriptor input = null;
        try {
            input = mContentResolver
                    .openFileDescriptor(fullSizeImageUri(), "r");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapManager.instance().decodeFileDescriptor(
                    input.getFileDescriptor(), options);
            mWidth = options.outWidth;
            mHeight = options.outHeight;
        } catch (FileNotFoundException ex) {
            mWidth = 0;
            mHeight = 0;
        } finally {
            Util.closeSiliently(input);
        }
    }

    public int getWidth() {
        if (mWidth == UNKNOWN_LENGTH) setupDimension();
        return mWidth;
    }

    public int getHeight() {
        if (mHeight == UNKNOWN_LENGTH) setupDimension();
        return mHeight;
    }

    public Bitmap miniThumbBitmap() {
        try {
            long id = mId;
            long dbMagic = mMiniThumbMagic;
            if (dbMagic == 0 || dbMagic == id) {
                dbMagic = ((BaseImageList) getContainer())
                        .checkThumbnail(this, getRow(), null);
            }

            synchronized (sMiniThumbData) {
                dbMagic = mMiniThumbMagic;
                byte [] data = mContainer.getMiniThumbFromFile(id,
                        sMiniThumbData, dbMagic);
                if (data == null) {
                    byte[][] createdThumbData = new byte[1][];
                    try {
                        dbMagic = ((BaseImageList) getContainer())
                                .checkThumbnail(this, getRow(),
                                createdThumbData);
                    } catch (IOException ex) {
                        // Typically IOException because the sd card is full.
                        // But createdThumbData may have been filled in, so
                        // continue on.
                    }
                    data = createdThumbData[0];
                }
                if (data == null) {
                    data = mContainer.getMiniThumbFromFile(id, sMiniThumbData,
                            dbMagic);
                }
                if (data == null) {
                    // Unable to get mini-thumb data from file.
                }
                if (data != null) {
                    Bitmap b = BitmapFactory.decodeByteArray(data, 0,
                            data.length);
                    if (b == null) {
                        Log.v(TAG, "couldn't decode byte array, "
                                + "length was " + data.length);
                    }
                    return b;
                }
            }
            return null;
        } catch (Throwable ex) {
            Log.e(TAG, "miniThumbBitmap got exception", ex);
            return null;
        }
    }

    public void onRemove() {
        mContainer.mCache.remove(mId);
    }

    protected void saveMiniThumb(Bitmap source) throws IOException {
        mContainer.saveMiniThumbToFile(source, fullSizeImageId(), 0);
    }

    public void setTitle(String name) {
        Cursor c = getCursor();
        synchronized (c) {
            if (c.moveToPosition(getRow())) {
                c.updateString(mContainer.indexTitle(), name);
                c.commitUpdates();
            }
        }
    }

    public Uri thumbUri() {
        Uri uri = fullSizeImageUri();
        // The value for the query parameter cannot be null :-(,
        // so using a dummy "1"
        uri = uri.buildUpon().appendQueryParameter("thumb", "1").build();
        return uri;
    }

    @Override
    public String toString() {
        return fullSizeImageUri().toString();
    }
}
