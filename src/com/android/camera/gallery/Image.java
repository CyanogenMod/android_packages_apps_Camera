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
import com.android.camera.ExifInterface;
import com.android.camera.ImageManager;
import com.android.camera.Util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

/**
 * The class for normal images in gallery.
 */
public class Image extends BaseImage implements IImage {
    private static final boolean VERBOSE = false;
    private static final String TAG = "BaseImage";

    private int mRotation;

    public Image(long id, long miniThumbId, ContentResolver cr,
            BaseImageList container, int cursorRow, int rotation) {
        super(id, miniThumbId, cr, container, cursorRow);
        mRotation = rotation;
    }

    public String getDataPath() {
        String path = null;
        Cursor c = getCursor();
        synchronized (c) {
            if (c.moveToPosition(getRow())) {
                int column = ((ImageList) getContainer()).indexData();
                if (column >= 0)
                    path = c.getString(column);
            }
        }
        return path;
    }

    @Override
    protected int getDegreesRotated() {
        return mRotation;
    }

    protected void setDegreesRotated(int degrees) {
        Cursor c = getCursor();
        mRotation = degrees;
        synchronized (c) {
            if (c.moveToPosition(getRow())) {
                int column = ((ImageList) getContainer()).indexOrientation();
                if (column >= 0) {
                    c.updateInt(column, degrees);
                    getContainer().commitChanges();
                }
            }
        }
    }

    @Override
    protected Bitmap.CompressFormat compressionType() {
        String mimeType = getMimeType();
        if ("image/png".equals(mimeType)) {
            return Bitmap.CompressFormat.PNG;
        } else if ("image/gif".equals(mimeType)) {
            return Bitmap.CompressFormat.PNG;
        }
        return Bitmap.CompressFormat.JPEG;
    }

    /**
     * Does not replace the tag if already there. Otherwise, adds to the exif
     * tags.
     *
     * @param tag
     * @param value
     */
    public void addExifTag(String tag, String value) {
        if (mExifData == null) {
            mExifData = new HashMap<String, String>();
        }
        // If the key is already there, ignore it.
        if (!mExifData.containsKey(tag)) {
            mExifData.put(tag, value);
        }
    }

    /**
     * Return the value of the Exif tag as an int. Returns 0 on any type of
     * error.
     *
     * @param tag
     */
    public int getExifTagInt(String tag) {
        if (mExifData != null) {
            String tagValue = mExifData.get(tag);
            if (tagValue != null) {
                return Integer.parseInt(tagValue);
            }
        }
        return 0;
    }

    public boolean isReadonly() {
        String mimeType = getMimeType();
        return !"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType);
    }

    public boolean isDrm() {
        return false;
    }

    /**
     * Remove tag if already there. Otherwise, does nothing.
     * @param tag
     */
    public void removeExifTag(String tag) {
        if (mExifData == null) {
            mExifData = new HashMap<String, String>();
        }
        mExifData.remove(tag);
    }

    /**
     * Replaces the tag if already there. Otherwise, adds to the exif tags.
     * @param tag
     * @param value
     */
    public void replaceExifTag(String tag, String value) {
        if (mExifData == null) {
            mExifData = new HashMap<String, String>();
        }
        if (!mExifData.containsKey(tag)) {
            mExifData.remove(tag);
        }
        mExifData.put(tag, value);
    }

    private class SaveImageContentsCancelable extends BaseCancelable<Boolean> {
        private Bitmap mImage;
        private byte [] mJpegData;
        private int mOrientation;
        private Cursor mCursor;
        ICancelable<Boolean> mCurrentCancelable = null;

        SaveImageContentsCancelable(Bitmap image, byte[] jpegData,
                int orientation, Cursor cursor) {
            mImage = image;
            mJpegData = jpegData;
            mOrientation = orientation;
            mCursor = cursor;
        }

        @Override
        public boolean doCancelWork() {
            synchronized (this) {
                if (mCurrentCancelable != null) mCurrentCancelable.cancel();
            }
            return true;
        }

        public Boolean get() {
            try {
                Bitmap thumbnail = null;

                Uri uri = mContainer.contentUri(mId);
                synchronized (this) {
                    checkCanceled();
                    mCurrentCancelable =
                            compressImageToFile(mImage, mJpegData, uri);
                }

                if (!mCurrentCancelable.get()) return false;

                synchronized (this) {
                    String filePath;
                    synchronized (mCursor) {
                        mCursor.moveToPosition(0);
                        filePath = mCursor.getString(2);
                    }
                    // TODO: If thumbData is present and usable, we should call
                    // the version of storeThumbnail which takes a byte array,
                    // rather than re-encoding a new JPEG of the same
                    // dimensions.

                    byte[] thumbData = null;
                    synchronized (ImageManager.instance()) {
                        thumbData =
                                (new ExifInterface(filePath)).getThumbnail();
                    }

                    if (thumbData != null) {
                        thumbnail = BitmapFactory.decodeByteArray(
                                thumbData, 0, thumbData.length);
                    }
                    if (thumbnail == null && mImage != null) {
                        thumbnail = mImage;
                    }
                    if (thumbnail == null && mJpegData != null) {
                        thumbnail = BitmapFactory.decodeByteArray(
                                mJpegData, 0, mJpegData.length);
                    }
                }

                mContainer.storeThumbnail(
                        thumbnail, Image.this.fullSizeImageId());
                checkCanceled();

                try {
                    thumbnail = Util.rotate(thumbnail, mOrientation);
                    saveMiniThumb(thumbnail);
                } catch (IOException e) {
                    // Ignore if unable to save thumb.
                }
                checkCanceled();
                return true;
            } catch (CanceledException ex) {
                // Got canceled... need to cleanup.
                return false;
            } finally {
                /*
                 * Cursor c = getCursor(); synchronized (c) { if
                 * (c.moveTo(getRow())) { mContainer.requery(); } }
                 */
                acknowledgeCancel();
            }
        }
    }

    public ICancelable<Boolean> saveImageContents(Bitmap image,
            byte [] jpegData, int orientation, boolean newFile, Cursor cursor) {
        return new SaveImageContentsCancelable(
                image, jpegData, orientation, cursor);
    }

    private void setExifRotation(int degrees) {
        try {
            Cursor c = getCursor();
            String filePath;
            synchronized (c) {
                filePath = c.getString(mContainer.indexData());
            }
            synchronized (ImageManager.instance()) {
                ExifInterface exif = new ExifInterface(filePath);
                if (mExifData == null) {
                    mExifData = exif.getAttributes();
                }
                if (degrees < 0) degrees += 360;

                int orientation = ExifInterface.ORIENTATION_NORMAL;
                switch (degrees) {
                    case 0:
                        orientation = ExifInterface.ORIENTATION_NORMAL;
                        break;
                    case 90:
                        orientation = ExifInterface.ORIENTATION_ROTATE_90;
                        break;
                    case 180:
                        orientation = ExifInterface.ORIENTATION_ROTATE_180;
                        break;
                    case 270:
                        orientation = ExifInterface.ORIENTATION_ROTATE_270;
                        break;
                }

                replaceExifTag(ExifInterface.TAG_ORIENTATION,
                        Integer.toString(orientation));
                replaceExifTag("UserComment",
                        "saveRotatedImage comment orientation: " + orientation);
                exif.saveAttributes(mExifData);
                exif.commitChanges();
            }
        } catch (RuntimeException ex) {
            Log.e(TAG, "unable to save exif data with new orientation "
                    + fullSizeImageUri());
        }
    }

    /**
     * Save the rotated image by updating the Exif "Orientation" tag.
     * @param degrees
     */
    public boolean rotateImageBy(int degrees) {
        int newDegrees = getDegreesRotated() + degrees;
        setExifRotation(newDegrees);
        setDegreesRotated(newDegrees);

        // setting this to zero will force the call to checkCursor to generate
        // fresh thumbs
        mMiniThumbMagic = 0;
        try {
            mContainer.checkThumbnail(
                    this, mContainer.getCursor(), this.getRow());
        } catch (IOException e) {
            // Ignore inability to store mini thumbnail.
        }
        return true;
    }

    public Bitmap thumbBitmap() {
        Bitmap bitmap = null;
        if (mContainer.mThumbUri != null) {
            Cursor c = mContentResolver.query(
                    mContainer.mThumbUri, BaseImageList.THUMB_PROJECTION,
                    Thumbnails.IMAGE_ID + "=?",
                    new String[] { String.valueOf(fullSizeImageId()) },
                    null);
            try {
                if (c.moveToFirst()) bitmap = decodeCurrentImage(c);
            } catch (RuntimeException ex) {
                // sdcard removed?
                return null;
            } finally {
                c.close();
            }
        }

        if (bitmap == null) {
            bitmap = fullSizeBitmap(ImageManager.THUMBNAIL_TARGET_SIZE, false);
            // No thumbnail found... storing the new one.
            bitmap = mContainer.storeThumbnail(bitmap, fullSizeImageId());
        }

        if (bitmap != null) {
            bitmap = Util.rotate(bitmap, getDegreesRotated());
        }

        long elapsed = System.currentTimeMillis();
        return bitmap;
    }

    private Bitmap decodeCurrentImage(Cursor c) {
        Uri thumbUri = ContentUris.withAppendedId(
                mContainer.mThumbUri,
                c.getLong(ImageList.INDEX_THUMB_ID));
        ParcelFileDescriptor pfdInput;
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options options =  new BitmapFactory.Options();
            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            pfdInput = mContentResolver.openFileDescriptor(thumbUri, "r");
            bitmap = BitmapManager.instance().decodeFileDescriptor(
                    pfdInput.getFileDescriptor(), null, options);
            pfdInput.close();
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "couldn't open thumbnail " + thumbUri + "; " + ex);
        } catch (IOException ex) {
            Log.e(TAG, "couldn't open thumbnail " + thumbUri + "; " + ex);
        } catch (NullPointerException ex) {
            // we seem to get this if the file doesn't exist anymore
            Log.e(TAG, "couldn't open thumbnail " + thumbUri + "; " + ex);
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "failed to allocate memory for thumbnail "
                    + thumbUri + "; " + ex);
        }
        return bitmap;
    }
}
