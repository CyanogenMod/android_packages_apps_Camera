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
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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


    // Database field
    protected Uri mUri;
    protected long mId;
    protected String mDataPath;
    protected long mMiniThumbMagic;
    protected final int mIndex;
    protected String mMimeType;
    private final long mDateTaken;
    private String mTitle;
    private final String mDisplayName;

    protected BaseImageList mContainer;

    private int mWidth = UNKNOWN_LENGTH;
    private int mHeight = UNKNOWN_LENGTH;

    protected BaseImage(BaseImageList container, ContentResolver cr,
            long id, int index, Uri uri, String dataPath, long miniThumbMagic,
            String mimeType, long dateTaken, String title, String displayName) {
        mContainer = container;
        mContentResolver = cr;
        mId = id;
        mIndex = index;
        mUri = uri;
        mDataPath = dataPath;
        mMiniThumbMagic = miniThumbMagic;
        mMimeType = mimeType;
        mDateTaken = dateTaken;
        mTitle = title;
        mDisplayName = displayName;
    }

    protected abstract Bitmap.CompressFormat compressionType();

    private class CompressImageToFile extends BaseCancelable<Boolean> {
        private ThreadSafeOutputStream mOutputStream = null;

        private final Bitmap mBitmap;
        private final Uri mDestinationUri;
        private final byte[] mJpegData;

        public CompressImageToFile(Bitmap bitmap, byte[] jpegData, Uri uri) {
            mBitmap = bitmap;
            mDestinationUri = uri;
            mJpegData = jpegData;
        }

        @Override
        public boolean requestCancel() {
            if (super.requestCancel()) {
                if (mOutputStream != null) {
                    mOutputStream.close();
                }
                return true;
            }
            return false;
        }

        @Override
        public Boolean execute() {
            try {
                OutputStream delegate =
                        mContentResolver.openOutputStream(mDestinationUri);
                synchronized (this) {
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
            } catch (IOException ex) {
                return false;
            } finally {
                Util.closeSilently(mOutputStream);
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
    protected Cancelable<Boolean> compressImageToFile(
            Bitmap bitmap, byte [] jpegData, Uri uri) {
        return new CompressImageToFile(bitmap, jpegData, uri);
    }

    public String getDataPath() {
        return mDataPath;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof Image)) return false;
        return mUri.equals(((Image) other).mUri);
    }

    @Override
    public int hashCode() {
        return mUri.hashCode();
    }

    public Bitmap fullSizeBitmap(int minSideLength, int maxNumberOfPixels) {
        return fullSizeBitmap(minSideLength, maxNumberOfPixels,
                IImage.ROTATE_AS_NEEDED, IImage.NO_NATIVE);
    }

    public Bitmap fullSizeBitmap(int minSideLength, int maxNumberOfPixels,
            boolean rotateAsNeeded) {
        return fullSizeBitmap(minSideLength, maxNumberOfPixels,
                rotateAsNeeded, IImage.NO_NATIVE);
    }

    public Bitmap fullSizeBitmap(int minSideLength, int maxNumberOfPixels,
            boolean rotateAsNeeded, boolean useNative) {
        Uri url = mContainer.contentUri(mId);
        if (url == null) return null;

        Bitmap b = Util.makeBitmap(minSideLength, maxNumberOfPixels,
                url, mContentResolver, useNative);

        if (b != null && rotateAsNeeded) {
            b = Util.rotate(b, getDegreesRotated());
        }

        return b;
    }

    public InputStream fullSizeImageData() {
        try {
            InputStream input = mContentResolver.openInputStream(mUri);
            return input;
        } catch (IOException ex) {
            return null;
        }
    }

    public long fullSizeImageId() {
        return mId;
    }

    public Uri fullSizeImageUri() {
        return mUri;
    }

    public IImageList getContainer() {
        return mContainer;
    }

    public long getDateTaken() {
        return mDateTaken;
    }

    public int getDegreesRotated() {
        return 0;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    private void setupDimension() {
        ParcelFileDescriptor input = null;
        try {
            input = mContentResolver.openFileDescriptor(mUri, "r");
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
            Util.closeSilently(input);
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

            synchronized (sMiniThumbData) {
                byte [] data = null;

                // Try to get it from the file.
                if (mMiniThumbMagic != 0) {
                    data = mContainer.getMiniThumbFromFile(id, sMiniThumbData,
                            mMiniThumbMagic);
                }

                // If it does not exist, try to create the thumbnail
                if (data == null) {
                    byte[][] createdThumbData = new byte[1][];
                    try {
                        ((BaseImageList) getContainer())
                                .checkThumbnail(this, createdThumbData);
                    } catch (IOException ex) {
                        // Typically IOException because the sd card is full.
                        // But createdThumbData may have been filled in, so
                        // continue on.
                    }
                    data = createdThumbData[0];
                }

                if (data == null) {
                    // Unable to get mini-thumb.
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

    protected void onRemove() {
    }

    protected void saveMiniThumb(Bitmap source) throws IOException {
        mContainer.saveMiniThumbToFile(source, fullSizeImageId(), 0);
    }

    public void setTitle(String name) {
        if (mTitle.equals(name)) return;
        mTitle = name;
        ContentValues values = new ContentValues();
        values.put(ImageColumns.TITLE, name);
        mContentResolver.update(mUri, values, null, null);
    }

    public Uri thumbUri() {
        // The value for the query parameter cannot be null :-(,
        // so using a dummy "1"
        return mUri.buildUpon().appendQueryParameter("thumb", "1").build();
    }

    @Override
    public String toString() {
        return mUri.toString();
    }
}
