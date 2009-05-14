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
import com.android.camera.Util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * The class for normal images in gallery.
 */
public class Image extends BaseImage implements IImage {
    private static final String TAG = "BaseImage";

    private int mRotation;
    private ExifInterface mExif;

    public Image(long id, long miniThumbMagic, ContentResolver cr,
            BaseImageList container, int cursorRow, int rotation) {
        super(id, miniThumbMagic, cr, container, cursorRow);
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
                    c.commitUpdates();
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
            loadExifData();
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
    public int getExifTagInt(String tag, int defaultValue) {
        String tagValue = getExifTag(tag);
        try {
            if (tagValue != null) {
                return Integer.parseInt(tagValue);
            }
        } catch (NumberFormatException ex) {
            // Simply return defaultValue if exception is thrown.
            Log.v(TAG, ex.toString());
        }
        return defaultValue;
    }

    /**
     * Return the value of the Exif tag as a String. It's caller's
     * responsibility to check nullity.
     */
    public String getExifTag(String tag) {
        if (mExifData == null) {
            loadExifData();
        }
        return mExifData.get(tag);
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
            loadExifData();
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
            loadExifData();
        }
        mExifData.put(tag, value);
    }

    private class SaveImageContentsCancelable extends BaseCancelable<Void> {
        private final Bitmap mImage;
        private final byte [] mJpegData;
        private final int mOrientation;
        private final String mFilePath;

        SaveImageContentsCancelable(Bitmap image, byte[] jpegData,
                int orientation, String filePath) {
            mImage = image;
            mJpegData = jpegData;
            mOrientation = orientation;
            mFilePath = filePath;
        }

        @Override
        public Void execute() throws InterruptedException, ExecutionException {
            Bitmap thumbnail = null;
            Uri uri = mContainer.contentUri(mId);
            runSubTask(compressImageToFile(mImage, mJpegData, uri));

            synchronized (this) {
                String filePath = mFilePath;

                // TODO: If thumbData is present and usable, we should call
                // the version of storeThumbnail which takes a byte array,
                // rather than re-encoding a new JPEG of the same
                // dimensions.
                byte[] thumbData = null;
                synchronized (ExifInterface.class) {
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
            if (isCanceling()) return null;

            try {
                thumbnail = Util.rotate(thumbnail, mOrientation);
                saveMiniThumb(thumbnail);
            } catch (IOException e) {
                // Ignore if unable to save thumb.
                Log.e(TAG, "unable to rotate / save thumb", e);
            }
            return null;
        }
    }

    public Cancelable<Void> saveImageContents(Bitmap image, byte [] jpegData,
            int orientation, boolean newFile, String filePath) {
        return new SaveImageContentsCancelable(
                image, jpegData, orientation, filePath);
    }

    private void loadExifData() {
        Cursor c = getCursor();
        String filePath;
        synchronized (c) {
            filePath = c.getString(mContainer.indexData());
        }
        ExifInterface mExif = new ExifInterface(filePath);
        if (mExifData == null) {
            mExifData = mExif.getAttributes();
        }
    }

    private void saveExifData() {
        if (mExif != null && mExifData != null) {
            mExif.saveAttributes(mExifData);
        }
    }

    private void setExifRotation(int degrees) {
        try {
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
            saveExifData();
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
            mContainer.checkThumbnail(this, this.getRow(), null);
        } catch (IOException e) {
            // Ignore inability to store mini thumbnail.
        }
        return true;
    }

    private static final String[] THUMB_PROJECTION = new String[] {
        BaseColumns._ID,
    };

    public Bitmap thumbBitmap() {
        Bitmap bitmap = null;
        if (mContainer.mThumbUri != null) {
            Cursor c = mContentResolver.query(
                    mContainer.mThumbUri, THUMB_PROJECTION,
                    Thumbnails.IMAGE_ID + "=?",
                    new String[] { String.valueOf(fullSizeImageId()) },
                    null);
            try {
                if (c.moveToFirst()) {
                    bitmap = decodeCurrentImage(c.getLong(0));
                }
            } catch (RuntimeException ex) {
                // sdcard removed?
                return null;
            } finally {
                c.close();
            }
        }

        if (bitmap == null) {
            bitmap = fullSizeBitmap(THUMBNAIL_TARGET_SIZE, false);
            // No thumbnail found... storing the new one.
            bitmap = mContainer.storeThumbnail(bitmap, fullSizeImageId());
        }

        if (bitmap != null) {
            bitmap = Util.rotate(bitmap, getDegreesRotated());
        }

        return bitmap;
    }

    private Bitmap decodeCurrentImage(long id) {
        Uri thumbUri = ContentUris.withAppendedId(
                mContainer.mThumbUri, id);
        ParcelFileDescriptor pfdInput;
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options options =  new BitmapFactory.Options();
            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            pfdInput = mContentResolver.openFileDescriptor(thumbUri, "r");
            bitmap = BitmapManager.instance().decodeFileDescriptor(
                    pfdInput.getFileDescriptor(), options);
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
