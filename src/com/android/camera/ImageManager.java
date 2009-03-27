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

import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.location.Location;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.DrmStore;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Images.Thumbnails;
import android.provider.MediaStore.Video.VideoColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.util.Config;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * ImageManager is used to retrieve and store images
 * in the media content provider.
 *
 */
public class ImageManager {
    public static final String CAMERA_IMAGE_BUCKET_NAME =
        Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
    public static final String CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    /**
     * Matches code in MediaProvider.computeBucketValues. Should be a common function.
     */

    public static String getBucketId(String path) {
        return String.valueOf(path.toLowerCase().hashCode());
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be imported.
     * This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatibleFolder() {
        File nnnAAAAA = new File(
            Environment.getExternalStorageDirectory().toString() + "/DCIM/100ANDRO");
        if ((!nnnAAAAA.exists()) && (!nnnAAAAA.mkdir())) {
            Log.e(TAG, "create NNNAAAAA file: "+ nnnAAAAA.getPath()+" failed");
        }
    }

    // To enable verbose logging for this class, change false to true. The other logic ensures that
    // this logging can be disabled by turned off DEBUG and lower, and that it can be enabled by
    // "setprop log.tag.ImageManager VERBOSE" if desired.
    //
    // IMPORTANT: Never check in this file set to true!
    private static final boolean VERBOSE = Config.LOGD && (false || Config.LOGV);
    private static final String TAG = "ImageManager";

    private static final int MINI_THUMB_DATA_FILE_VERSION = 3;

    static public void debug_where(String tag, String msg) {
        try {
            throw new Exception();
        } catch (Exception ex) {
            if (msg != null) {
                Log.v(tag, msg);
            }
            boolean first = true;
            for (StackTraceElement s : ex.getStackTrace()) {
                if (first)
                    first = false;
                else
                    Log.v(tag, s.toString());
            }
        }
    }

    /*
     * Compute the sample size as a function of the image size and the target.
     * Scale the image down so that both the width and height are just above
     * the target.  If this means that one of the dimension goes from above
     * the target to below the target (e.g. given a width of 480 and an image
     * width of 600 but sample size of 2 -- i.e. new width 300 -- bump the
     * sample size down by 1.
     */
    private static int computeSampleSize(BitmapFactory.Options options, int target) {
        int w = options.outWidth;
        int h = options.outHeight;

        int candidateW = w / target;
        int candidateH = h / target;
        int candidate = Math.max(candidateW, candidateH);

        if (candidate == 0)
            return 1;

        if (candidate > 1) {
            if ((w > target) && (w / candidate) < target)
                candidate -= 1;
        }

        if (candidate > 1) {
            if ((h > target) && (h / candidate) < target)
                candidate -= 1;
        }

        if (VERBOSE)
            Log.v(TAG, "for w/h " + w + "/" + h + " returning " + candidate + "(" + (w/candidate) + " / " + (h/candidate));

        return candidate;
    }
    /*
     * All implementors of ICancelable should inherit from BaseCancelable
     * since it provides some convenience methods such as acknowledgeCancel
     * and checkCancel.
     */
    public abstract class BaseCancelable implements ICancelable {
        boolean mCancel = false;
        boolean mFinished = false;

        /*
         * Subclasses should call acknowledgeCancel when they're finished with
         * their operation.
         */
        protected void acknowledgeCancel() {
            synchronized (this) {
                mFinished = true;
                if (!mCancel)
                    return;
                if (mCancel) {
                    this.notify();
                }
            }
        }

        public boolean cancel() {
            synchronized (this) {
                if (mCancel) {
                    return false;
                }
                if (mFinished) {
                    return false;
                }
                mCancel = true;
                boolean retVal = doCancelWork();

                try {
                    this.wait();
                } catch (InterruptedException ex) {
                    // now what???  TODO
                }

                return retVal;
            }
        }

        /*
         * Subclasses can call this to see if they have been canceled.
         * This is the polling model.
         */
        protected void checkCanceled() throws CanceledException {
            synchronized (this) {
                if (mCancel)
                    throw new CanceledException();
            }
        }

        /*
         * Subclasses implement this method to take whatever action
         * is necessary when getting canceled.  Sometimes it's not
         * possible to do anything in which case the "checkCanceled"
         * polling model may be used (or some combination).
         */
        public abstract boolean doCancelWork();
    }

    private static final int sBytesPerMiniThumb = 10000;
    static final private byte [] sMiniThumbData = new byte[sBytesPerMiniThumb];

    /**
     * Represents a particular image and provides access
     * to the underlying bitmap and two thumbnail bitmaps
     * as well as other information such as the id, and
     * the path to the actual image data.
     */
    abstract class BaseImage implements IImage {
        protected ContentResolver mContentResolver;
        protected long mId, mMiniThumbMagic;
        protected BaseImageList mContainer;
        protected HashMap<String, String> mExifData;
        protected int mCursorRow;

        protected BaseImage(long id, long miniThumbId, ContentResolver cr, BaseImageList container, int cursorRow) {
            mContentResolver = cr;
            mId              = id;
            mMiniThumbMagic  = miniThumbId;
            mContainer       = container;
            mCursorRow       = cursorRow;
        }

        abstract Bitmap.CompressFormat compressionType();

        public void commitChanges() {
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    c.commitUpdates();
                    c.requery();
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
        protected IGetBoolean_cancelable compressImageToFile(
                final Bitmap bitmap,
                final byte [] jpegData,
                final Uri uri) {
            class CompressImageToFile extends BaseCancelable implements IGetBoolean_cancelable {
                ThreadSafeOutputStream mOutputStream = null;

                public boolean doCancelWork() {
                    if (mOutputStream != null) {
                        try {
                            mOutputStream.close();
                            return true;
                        } catch (IOException ex) {
                            // TODO what to do here
                        }
                    }
                    return false;
                }

                public boolean get() {
                    try {
                        long t1 = System.currentTimeMillis();
                        OutputStream delegate = mContentResolver.openOutputStream(uri);
                        synchronized (this) {
                            checkCanceled();
                            mOutputStream = new ThreadSafeOutputStream(delegate);
                        }
                        long t2 = System.currentTimeMillis();
                        if (bitmap != null) {
                            bitmap.compress(compressionType(), 75, mOutputStream);
                        } else {
                            long x1 = System.currentTimeMillis();
                            mOutputStream.write(jpegData);
                            long x2 = System.currentTimeMillis();
                            if (VERBOSE) Log.v(TAG, "done writing... " + jpegData.length + " bytes took " + (x2-x1));
                        }
                        long t3 = System.currentTimeMillis();
                        if (VERBOSE) Log.v(TAG, String.format("CompressImageToFile.get took %d (%d, %d)",(t3-t1),(t2-t1),(t3-t2)));
                        return true;
                    } catch (FileNotFoundException ex) {
                        return false;
                    } catch (CanceledException ex) {
                        return false;
                    } catch (IOException ex) {
                        return false;
                    }
                    finally {
                        if (mOutputStream != null) {
                            try {
                                mOutputStream.close();
                            } catch (IOException ex) {
                                // not much we can do here so ignore
                            }
                        }
                        acknowledgeCancel();
                    }
                }
            }
            return new CompressImageToFile();
        }

        @Override
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (!(other instanceof Image))
                return false;

            return fullSizeImageUri().equals(((Image)other).fullSizeImageUri());
        }

        public Bitmap fullSizeBitmap(int targetWidthHeight) {
            return fullSizeBitmap(targetWidthHeight, true);
        }

        protected Bitmap fullSizeBitmap(int targetWidthHeight, boolean rotateAsNeeded) {
            Uri url = mContainer.contentUri(mId);
            if (VERBOSE) Log.v(TAG, "getCreateBitmap for " + url);
            if (url == null)
                return null;

            Bitmap b = null;
            if (b == null) {
                b = makeBitmap(targetWidthHeight, url);
                if (b != null && rotateAsNeeded) {
                    b = rotate(b, getDegreesRotated());
                }
            }
            return b;
        }


        public IGetBitmap_cancelable fullSizeBitmap_cancelable(final int targetWidthHeight) {
            final class LoadBitmapCancelable extends BaseCancelable implements IGetBitmap_cancelable {
                ParcelFileDescriptor mPFD;
                BitmapFactory.Options mOptions = new BitmapFactory.Options();
                long mCancelInitiationTime;

                public LoadBitmapCancelable(ParcelFileDescriptor pfdInput) {
                    mPFD = pfdInput;
                }

                public boolean doCancelWork() {
                    if (VERBOSE)
                        Log.v(TAG, "requesting bitmap load cancel");
                    mCancelInitiationTime = System.currentTimeMillis();
                    mOptions.requestCancelDecode();
                    return true;
                }

                public Bitmap get() {
                    try {
                        Bitmap b = makeBitmap(targetWidthHeight, fullSizeImageUri(), mPFD, mOptions);
                        if (mCancelInitiationTime != 0) {
                            if (VERBOSE)
                                Log.v(TAG, "cancelation of bitmap load success==" + (b == null ? "TRUE" : "FALSE") + " -- took " + (System.currentTimeMillis() - mCancelInitiationTime));
                        }
                        if (b != null) {
                            b = rotate(b, getDegreesRotated());
                        }
                        return b;
                    } catch (Exception ex) {
                        return null;
                    } finally {
                        acknowledgeCancel();
                    }
                }
            }

            try {
                ParcelFileDescriptor pfdInput = mContentResolver.openFileDescriptor(fullSizeImageUri(), "r");
                return new LoadBitmapCancelable(pfdInput);
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
                Cursor c = null;
                try {
                    c = mContentResolver.query(
                        fullSizeImageUri(),
                        new String[] { "_id", Images.Media.MIME_TYPE },
                        null,
                        null, null);
                    if (c != null && c.moveToFirst()) {
                        return c.getString(1);
                    } else {
                        return "";
                    }
                } finally {
                    if (c != null)
                        c.close();
                }
            } else {
                String mimeType = null;
                Cursor c = getCursor();
                synchronized(c) {
                    if (c.moveToPosition(getRow())) {
                        mimeType = c.getString(mContainer.indexMimeType());
                    }
                }
                return mimeType;
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#getDescription()
         */
        public String getDescription() {
            if (mContainer.indexDescription() < 0) {
                Cursor c = null;
                try {
                    c = mContentResolver.query(
                        fullSizeImageUri(),
                        new String[] { "_id", Images.Media.DESCRIPTION },
                        null,
                        null, null);
                    if (c != null && c.moveToFirst()) {
                        return c.getString(1);
                    } else {
                        return "";
                    }
                } finally {
                    if (c != null)
                        c.close();
                }
            } else {
                String description = null;
                Cursor c = getCursor();
                synchronized(c) {
                    if (c.moveToPosition(getRow())) {
                        description = c.getString(mContainer.indexDescription());
                    }
                }
                return description;
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#getIsPrivate()
         */
        public boolean getIsPrivate() {
            if (mContainer.indexPrivate() < 0) return false;
            boolean isPrivate = false;
            Cursor c = getCursor();
            synchronized(c) {
                if (c.moveToPosition(getRow())) {
                    isPrivate = c.getInt(mContainer.indexPrivate()) != 0;
                }
            }
            return isPrivate;
        }

        public double getLatitude() {
            if (mContainer.indexLatitude() < 0) return 0D;
            Cursor c = getCursor();
            synchronized (c) {
                c.moveToPosition(getRow());
                return c.getDouble(mContainer.indexLatitude());
            }
        }

        public double getLongitude() {
            if (mContainer.indexLongitude() < 0) return 0D;
            Cursor c = getCursor();
            synchronized (c) {
                c.moveToPosition(getRow());
                return c.getDouble(mContainer.indexLongitude());
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#getTitle()
         */
        public String getTitle() {
            String name = null;
            Cursor c = getCursor();
            synchronized(c) {
                if (c.moveToPosition(getRow())) {
                    if (mContainer.indexTitle() != -1) {
                        name = c.getString(mContainer.indexTitle());
                    }
                }
            }
            return name != null && name.length() > 0 ? name : String.valueOf(mId);
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#getDisplayName()
         */
        public String getDisplayName() {
            if (mContainer.indexDisplayName() < 0) {
                Cursor c = null;
                try {
                    c = mContentResolver.query(
                        fullSizeImageUri(),
                        new String[] { "_id", Images.Media.DISPLAY_NAME },
                        null,
                        null, null);
                    if (c != null && c.moveToFirst()) {
                        return c.getString(1);
                    }
                } finally {
                    if (c != null)
                        c.close();
                }
            } else {
                String name = null;
                Cursor c = getCursor();
                synchronized(c) {
                    if (c.moveToPosition(getRow())) {
                        name = c.getString(mContainer.indexDisplayName());
                    }
                }
                if (name != null && name.length() > 0)
                    return name;
            }
            return String.valueOf(mId);
        }

        public String getPicasaId() {
            /*
            if (mContainer.indexPicasaWeb() < 0) return null;
            Cursor c = getCursor();
            synchronized (c) {
                c.moveTo(getRow());
                return c.getString(mContainer.indexPicasaWeb());
            }
            */
            return null;
        }

        public int getRow() {
            return mCursorRow;
        }

        public int getWidth() {
            ParcelFileDescriptor input = null;
            try {
                Uri uri = fullSizeImageUri();
                input = mContentResolver.openFileDescriptor(uri, "r");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(input.getFileDescriptor(), null, options);
                return options.outWidth;
            } catch (IOException ex) {
                return 0;
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException ex) {
                }
            }
        }

        public int getHeight() {
            ParcelFileDescriptor input = null;
            try {
                Uri uri = fullSizeImageUri();
                input = mContentResolver.openFileDescriptor(uri, "r");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(input.getFileDescriptor(), null, options);
                return options.outHeight;
            } catch (IOException ex) {
                return 0;
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException ex) {
                }
            }
        }

        public boolean hasLatLong() {
            if (mContainer.indexLatitude() < 0 || mContainer.indexLongitude() < 0) return false;
            Cursor c = getCursor();
            synchronized (c) {
                c.moveToPosition(getRow());
                return !c.isNull(mContainer.indexLatitude()) && !c.isNull(mContainer.indexLongitude());
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#imageId()
         */
        public long imageId() {
            return mId;
        }

        /**
         * Make a bitmap from a given Uri.
         *
         * @param uri
         */
        private Bitmap makeBitmap(int targetWidthOrHeight, Uri uri) {
            ParcelFileDescriptor input = null;
            try {
                input = mContentResolver.openFileDescriptor(uri, "r");
                return makeBitmap(targetWidthOrHeight, uri, input, null);
            } catch (IOException ex) {
                return null;
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException ex) {
                }
            }
        }

        protected Bitmap makeBitmap(int targetWidthHeight, Uri uri, ParcelFileDescriptor pfdInput, BitmapFactory.Options options) {
            return mContainer.makeBitmap(targetWidthHeight, uri, pfdInput, options);
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#thumb1()
         */
        public Bitmap miniThumbBitmap() {
            try {
                long id = mId;
                long dbMagic = mMiniThumbMagic;
                if (dbMagic == 0 || dbMagic == id) {
                    dbMagic = ((BaseImageList)getContainer()).checkThumbnail(this, getCursor(), getRow());
                    if (VERBOSE) Log.v(TAG, "after computing thumbnail dbMagic is " + dbMagic);
                }

                synchronized(sMiniThumbData) {
                    dbMagic = mMiniThumbMagic;
                    byte [] data = mContainer.getMiniThumbFromFile(id, sMiniThumbData, dbMagic);
                    if (data == null) {
                        byte[][] createdThumbData = new byte[1][];
                        try {
                            dbMagic = ((BaseImageList)getContainer()).checkThumbnail(this, getCursor(),
                                    getRow(), createdThumbData);
                        } catch (IOException ex) {
                            // Typically IOException because the sd card is full.
                            // But createdThumbData may have been filled in, so continue on.
                        }
                        data = createdThumbData[0];
                    }
                    if (data == null) {
                        data = mContainer.getMiniThumbFromFile(id, sMiniThumbData, dbMagic);
                    }
                    if (data == null) {
                        if (VERBOSE)
                            Log.v(TAG, "unable to get miniThumbBitmap, data is null");
                    }
                    if (data != null) {
                        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (b == null) {
                            if (VERBOSE) {
                                Log.v(TAG, "couldn't decode byte array for mini thumb, length was " + data.length);
                            }
                        }
                        return b;
                    }
                }
                return null;
            } catch (Exception ex) {
                // Typically IOException because the sd card is full.
                if (VERBOSE) {
                    Log.e(TAG, "miniThumbBitmap got exception " + ex.toString());
                    for (StackTraceElement s : ex.getStackTrace())
                        Log.e(TAG, "... " + s.toString());
                }
                return null;
            }
        }

        public void onRemove() {
            mContainer.mCache.remove(mId);
        }

        protected void saveMiniThumb(Bitmap source) throws IOException {
            mContainer.saveMiniThumbToFile(source, fullSizeImageId(), 0);
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#setName()
         */
        public void setDescription(String description) {
            if (mContainer.indexDescription() < 0) return;
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    c.updateString(mContainer.indexDescription(), description);
                }
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#setIsPrivate()
         */
        public void setIsPrivate(boolean isPrivate) {
            if (mContainer.indexPrivate() < 0) return;
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    c.updateInt(mContainer.indexPrivate(), isPrivate ? 1 : 0);
                }
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#setName()
         */
        public void setName(String name) {
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    c.updateString(mContainer.indexTitle(), name);
                }
            }
        }

        public void setPicasaId(String id) {
            Cursor c = null;
            try {
                c = mContentResolver.query(
                    fullSizeImageUri(),
                    new String[] { "_id", Images.Media.PICASA_ID },
                    null,
                    null, null);
                if (c != null && c.moveToFirst()) {
                    if (VERBOSE) {
                        Log.v(TAG, "storing picasaid " + id + " for " + fullSizeImageUri());
                    }
                    c.updateString(1, id);
                    c.commitUpdates();
                    if (VERBOSE) {
                        Log.v(TAG, "updated image with picasa id " + id);
                    }
                }
            } finally {
                if (c != null)
                    c.close();
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#thumbUri()
         */
        public Uri thumbUri() {
            Uri uri = fullSizeImageUri();
            // The value for the query parameter cannot be null :-(, so using a dummy "1"
            uri = uri.buildUpon().appendQueryParameter("thumb", "1").build();
            return uri;
        }

        @Override
        public String toString() {
            return fullSizeImageUri().toString();
        }
    }

    abstract static class BaseImageList implements IImageList {
        Context mContext;
        ContentResolver mContentResolver;
        Uri mBaseUri, mUri;
        int mSort;
        String mBucketId;
        boolean mDistinct;
        Cursor mCursor;
        boolean mCursorDeactivated;
        protected HashMap<Long, IImage> mCache = new HashMap<Long, IImage>();

        IImageList.OnChange mListener = null;
        Handler mHandler;
        protected RandomAccessFile mMiniThumbData;
        protected Uri mThumbUri;

        public BaseImageList(Context ctx, ContentResolver cr, Uri uri, int sort, String bucketId) {
            mContext = ctx;
            mSort = sort;
            mUri = uri;
            mBaseUri = uri;
            mBucketId = bucketId;

            mContentResolver = cr;
        }

        String randomAccessFilePath(int version) {
            String directoryName = Environment.getExternalStorageDirectory().toString() + "/DCIM/.thumbnails";
            String path = directoryName + "/.thumbdata" + version + "-" + mUri.hashCode();
            return path;
        }

        RandomAccessFile miniThumbDataFile() {
            if (mMiniThumbData == null) {
                String path = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION);
                File directory = new File(new File(path).getParent());
                if (!directory.isDirectory()) {
                    if (!directory.mkdirs()) {
                        Log.e(TAG, "!!!! unable to create .thumbnails directory " + directory.toString());
                    }
                }
                File f = new File(path);
                if (VERBOSE) Log.v(TAG, "file f is " + f.toString());
                try {
                    mMiniThumbData = new RandomAccessFile(f, "rw");
                } catch (IOException ex) {

                }
            }
            return mMiniThumbData;
        }

        /**
         * Store a given thumbnail in the database.
         */
        protected Bitmap storeThumbnail(Bitmap thumb, long imageId) {
            if (thumb == null)
                return null;

            try {
                Uri uri = getThumbnailUri(imageId, thumb.getWidth(), thumb.getHeight());
                if (uri == null) {
                    return thumb;
                }
                OutputStream thumbOut = mContentResolver.openOutputStream(uri);
                thumb.compress(Bitmap.CompressFormat.JPEG, 60, thumbOut);
                thumbOut.close();
                return thumb;
            }
            catch (Exception ex) {
                if (VERBOSE) Log.d(TAG, "unable to store thumbnail: " + ex);
                return thumb;
            }
        }

        /**
         * Store a JPEG thumbnail from the EXIF header in the database.
         */
        protected boolean storeThumbnail(byte[] jpegThumbnail, long imageId, int width, int height) {
            if (jpegThumbnail == null)
                return false;

            Uri uri = getThumbnailUri(imageId, width, height);
            if (uri == null) {
                return false;
            }
            try {
                OutputStream thumbOut = mContentResolver.openOutputStream(uri);
                thumbOut.write(jpegThumbnail);
                thumbOut.close();
                return true;
            }
            catch (FileNotFoundException ex) {
                return false;
            }
            catch (IOException ex) {
                return false;
            }
        }

        private Uri getThumbnailUri(long imageId, int width, int height) {
            // we do not store thumbnails for DRM'd images
            if (mThumbUri == null) {
                return null;
            }

            Uri uri = null;
            Cursor c = null;
            try {
                c = mContentResolver.query(
                        mThumbUri,
                        THUMB_PROJECTION,
                        Thumbnails.IMAGE_ID + "=?",
                        new String[]{String.valueOf(imageId)},
                        null);
                if (c != null && c.moveToFirst()) {
                    // If, for some reaosn, we already have a row with a matching
                    // image id, then just update that row rather than creating a
                    // new row.
                    uri = ContentUris.withAppendedId(mThumbUri, c.getLong(indexThumbId()));
                    c.commitUpdates();
                }
            } finally {
                if (c != null)
                    c.close();
            }
            if (uri == null) {
                ContentValues values = new ContentValues(4);
                values.put(Images.Thumbnails.KIND, Images.Thumbnails.MINI_KIND);
                values.put(Images.Thumbnails.IMAGE_ID, imageId);
                values.put(Images.Thumbnails.HEIGHT, height);
                values.put(Images.Thumbnails.WIDTH, width);
                uri = mContentResolver.insert(mThumbUri, values);
            }
            return uri;
        }

        java.util.Random mRandom = new java.util.Random(System.currentTimeMillis());

        protected SomewhatFairLock mLock = new SomewhatFairLock();

        class SomewhatFairLock {
            private Object mSync = new Object();
            private boolean mLocked = false;
            private ArrayList<Thread> mWaiting = new ArrayList<Thread>();

            void lock() {
//              if (VERBOSE) Log.v(TAG, "lock... thread " + Thread.currentThread().getId());
                synchronized (mSync) {
                    while (mLocked) {
                        try {
//                          if (VERBOSE) Log.v(TAG, "waiting... thread " + Thread.currentThread().getId());
                            mWaiting.add(Thread.currentThread());
                            mSync.wait();
                            if (mWaiting.get(0) == Thread.currentThread()) {
                                mWaiting.remove(0);
                                break;
                            }
                        } catch (InterruptedException ex) {
                            //
                        }
                    }
//                  if (VERBOSE) Log.v(TAG, "locked... thread " + Thread.currentThread().getId());
                    mLocked = true;
                }
            }

            void unlock() {
//              if (VERBOSE) Log.v(TAG, "unlocking... thread " + Thread.currentThread().getId());
                synchronized (mSync) {
                    mLocked = false;
                    mSync.notifyAll();
                }
            }
        }

        // If the photo has an EXIF thumbnail and it's big enough, extract it and save that JPEG as
        // the large thumbnail without re-encoding it. We still have to decompress it though, in
        // order to generate the minithumb.
        private Bitmap createThumbnailFromEXIF(String filePath, long id) {
            if (filePath != null) {
                byte [] thumbData = null;
                synchronized (ImageManager.instance()) {
                    thumbData = (new ExifInterface(filePath)).getThumbnail();
                }
                if (thumbData != null) {
                    // Sniff the size of the EXIF thumbnail before decoding it. Photos from the
                    // device will pass, but images that are side loaded from other cameras may not.
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(thumbData, 0, thumbData.length, options);
                    int width = options.outWidth;
                    int height = options.outHeight;
                    if (width >= THUMBNAIL_TARGET_SIZE && height >= THUMBNAIL_TARGET_SIZE) {
                        if (storeThumbnail(thumbData, id, width, height)) {
                            // this is used for *encoding* the minithumb, so
                            // we don't want to dither or convert to 565 here.
                            //
                            // Decode with a scaling factor
                            // to match MINI_THUMB_TARGET_SIZE closely
                            // which will produce much better scaling quality
                            // and is significantly faster.
                            options.inSampleSize = computeSampleSize(options, THUMBNAIL_TARGET_SIZE);

                            if (VERBOSE) {
                                Log.v(TAG, "in createThumbnailFromExif using inSampleSize of " + options.inSampleSize);
                            }
                            options.inDither = false;
                            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            options.inJustDecodeBounds = false;
                            return BitmapFactory.decodeByteArray(thumbData, 0, thumbData.length, options);
                        }
                    }
                }
            }
            return null;
        }

        // The fallback case is to decode the original photo to thumbnail size, then encode it as a
        // JPEG. We return the thumbnail Bitmap in order to create the minithumb from it.
        private Bitmap createThumbnailFromUri(Cursor c, long id) {
            Uri uri = ContentUris.withAppendedId(mBaseUri, id);
            Bitmap bitmap = makeBitmap(THUMBNAIL_TARGET_SIZE, uri, null, null);
            if (bitmap != null) {
                storeThumbnail(bitmap, id);
            } else {
                uri = ContentUris.withAppendedId(mBaseUri, id);
                bitmap = makeBitmap(MINI_THUMB_TARGET_SIZE, uri, null, null);
            }
            return bitmap;
        }

        // returns id
        public long checkThumbnail(BaseImage existingImage, Cursor c, int i) throws IOException {
            return checkThumbnail(existingImage, c, i, null);
        }

        /**
         * Checks to see if a mini thumbnail exists in the cache. If not, tries to create it and
         * add it to the cache.
         * @param existingImage
         * @param c
         * @param i
         * @param createdThumbnailData if this parameter is non-null, and a new mini-thumbnail
         * bitmap is created, the new bitmap's data will be stored in createdThumbnailData[0].
         * Note that if the sdcard is full, it's possible that
         * createdThumbnailData[0] will be set even if the method throws an IOException. This is
         * actually useful, because it allows the caller to use the created thumbnail even if
         * the sdcard is full.
         * @return
         * @throws IOException
         */
        public long checkThumbnail(BaseImage existingImage, Cursor c, int i,
                byte[][] createdThumbnailData) throws IOException {
            long magic, fileMagic = 0, id;
            try {
                mLock.lock();
                if (existingImage == null) {
                    // if we don't have an Image object then get the id and magic from
                    // the cursor.  Synchronize on the cursor object.
                    synchronized (c) {
                        if (!c.moveToPosition(i)) {
                            return -1;
                        }
                        magic = c.getLong(indexMiniThumbId());
                        id = c.getLong(indexId());
                    }
                } else {
                    // if we have an Image object then ask them for the magic/id
                    magic = existingImage.mMiniThumbMagic;
                    id = existingImage.fullSizeImageId();
                }

                if (magic != 0) {
                    // check the mini thumb file for the right data.  Right is defined as
                    // having the right magic number at the offset reserved for this "id".
                    RandomAccessFile r = miniThumbDataFile();
                    if (r != null) {
                        synchronized (r) {
                            long pos = id * sBytesPerMiniThumb;
                            try {
                                // check that we can read the following 9 bytes (1 for the "status" and 8 for the long)
                                if (r.length() >= pos + 1 + 8) {
                                    r.seek(pos);
                                    if (r.readByte() == 1) {
                                        fileMagic = r.readLong();
                                        if (fileMagic == magic && magic != 0 && magic != id) {
                                            return magic;
                                        }
                                    }
                                }
                            } catch (IOException ex) {
                                Log.v(TAG, "got exception checking file magic: " + ex);
                            }
                        }
                    }
                    if (VERBOSE) {
                        Log.v(TAG, "didn't verify... fileMagic: " + fileMagic + "; magic: " + magic + "; id: " + id + "; ");
                    }
                }

                // If we can't retrieve the thumbnail, first check if there is one embedded in the
                // EXIF data. If not, or it's not big enough, decompress the full size image.
                Bitmap bitmap = null;
                String filePath = null;
                synchronized (c) {
                    if (c.moveToPosition(i)) {
                        filePath = c.getString(indexData());
                    }
                }
                if (filePath != null) {
                    String mimeType = c.getString(indexMimeType());
                    boolean isVideo = isVideoMimeType(mimeType);
                    if (isVideo) {
                        bitmap = createVideoThumbnail(filePath);
                    } else {
                        bitmap = createThumbnailFromEXIF(filePath, id);
                        if (bitmap == null) {
                            bitmap = createThumbnailFromUri(c, id);
                        }
                    }
                    synchronized (c) {
                        int degrees = 0;
                        if (c.moveToPosition(i)) {
                            int column = indexOrientation();
                            if (column >= 0)
                                degrees = c.getInt(column);
                        }
                        if (degrees != 0) {
                            bitmap = rotate(bitmap, degrees);
                        }
                    }
                }

                // make a new magic number since things are out of sync
                do {
                    magic = mRandom.nextLong();
                } while (magic == 0);
                if (bitmap != null) {
                    byte [] data = miniThumbData(bitmap);
                    if (createdThumbnailData != null) {
                        createdThumbnailData[0] = data;
                    }
                    saveMiniThumbToFile(data, id, magic);
                }

                synchronized (c) {
                    c.moveToPosition(i);
                    c.updateLong(indexMiniThumbId(), magic);
                    c.commitUpdates();
                    c.requery();
                    c.moveToPosition(i);

                    if (existingImage != null) {
                        existingImage.mMiniThumbMagic = magic;
                    }
                    return magic;
                }
            } finally {
                mLock.unlock();
            }
        }

        public void checkThumbnails(ThumbCheckCallback cb, int totalThumbnails) {
            Cursor c = Images.Media.query(
                    mContentResolver,
                    mBaseUri,
                    new String[] { "_id", "mini_thumb_magic" },
                    thumbnailWhereClause(),
                    thumbnailWhereClauseArgs(),
                    "_id ASC");

            int count = c.getCount();
            if (VERBOSE)
                Log.v(TAG, ">>>>>>>>>>> need to check " + c.getCount() + " rows");

            c.close();

            if (!ImageManager.hasStorage()) {
                if (VERBOSE)
                    Log.v(TAG, "bailing from the image checker thread -- no storage");
                return;
            }

            String oldPath = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION - 1);
            File oldFile = new File(oldPath);

            if (count == 0) {
                // now check that we have the right thumbs file
//                Log.v(TAG, "count is zero but oldFile.exists() is " + oldFile.exists());
                if (!oldFile.exists()) {
                    return;
                }
            }

            c = getCursor();
            try {
                if (VERBOSE) Log.v(TAG, "checkThumbnails found " + c.getCount());
                int current = 0;
                for (int i = 0; i < c.getCount(); i++) {
                    try {
                        checkThumbnail(null, c, i);
                    } catch (Exception ex) {
                        Log.e(TAG, "!!!!! failed to check thumbnail... was the sd card removed?");
                        break;
                    }
                    if (cb != null) {
                        if (!cb.checking(current, totalThumbnails)) {
                            if (VERBOSE) Log.v(TAG, "got false from checking... break <<<<<<<<<<<<<<<<<<<<<<<<");
                            break;
                        }
                    }
                    current += 1;
                }
            } finally {
                if (VERBOSE) Log.v(TAG, "checkThumbnails existing after reaching count " + c.getCount());
                try {
                    oldFile.delete();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }

        protected String thumbnailWhereClause() {
            return sMiniThumbIsNull + " and " + sWhereClause;
        }

        protected String[] thumbnailWhereClauseArgs() {
            return sAcceptableImageTypes;
        }

        public void commitChanges() {
            synchronized (mCursor) {
                mCursor.commitUpdates();
                requery();
            }
        }
        protected Uri contentUri(long id) {
            try {
                // does our uri already have an id (single image query)?
                // if so just return it
                long existingId = ContentUris.parseId(mBaseUri);
                if (existingId != id)
                    Log.e(TAG, "id mismatch");
                return mBaseUri;
            } catch (NumberFormatException ex) {
                // otherwise tack on the id
                return ContentUris.withAppendedId(mBaseUri, id);
            }
        }

        public void deactivate() {
            mCursorDeactivated = true;
            try {
                mCursor.deactivate();
            } catch (IllegalStateException e) {
                // IllegalStateException may be thrown if the cursor is stale.
                Log.e(TAG, "Caught exception while deactivating cursor.", e);
            }
            if (mMiniThumbData != null) {
                try {
                    mMiniThumbData.close();
                    mMiniThumbData = null;
                } catch (IOException ex) {

                }
            }
        }

        public void dump(String msg) {
            int count = getCount();
            if (VERBOSE) Log.v(TAG, "dump ImageList (count is " + count + ") " + msg);
            for (int i = 0; i < count; i++) {
                IImage img = getImageAt(i);
                if (img == null)
                    if (VERBOSE) Log.v(TAG, "   " + i + ": " + "null");
                else
                    if (VERBOSE) Log.v(TAG, "   " + i + ": " + img.toString());
            }
            if (VERBOSE) Log.v(TAG, "end of dump container");
        }
        public int getCount() {
            Cursor c = getCursor();
            synchronized (c) {
                    try {
                        return c.getCount();
                    } catch (Exception ex) {
                    }
                return 0;
            }
        }

        public boolean isEmpty() {
            return getCount() == 0;
        }

        protected Cursor getCursor() {
            synchronized (mCursor) {
                if (mCursorDeactivated) {
                    activateCursor();
                }
                return mCursor;
            }
        }

        protected void activateCursor() {
            requery();
        }

        public IImage getImageAt(int i) {
            Cursor c = getCursor();
            synchronized (c) {
                boolean moved;
                try {
                    moved = c.moveToPosition(i);
                } catch (Exception ex) {
                    return null;
                }
                if (moved) {
                    try {
                        long id = c.getLong(0);
                        long miniThumbId = 0;
                        int rotation = 0;
                        if (indexMiniThumbId() != -1) {
                            miniThumbId = c.getLong(indexMiniThumbId());
                        }
                        if (indexOrientation() != -1) {
                            rotation = c.getInt(indexOrientation());
                        }
                        long timestamp = c.getLong(1);
                        IImage img = mCache.get(id);
                        if (img == null) {
                            img = make(id, miniThumbId, mContentResolver, this, timestamp, i, rotation);
                            mCache.put(id, img);
                        }
                        return img;
                    } catch (Exception ex) {
                        Log.e(TAG, "got this exception trying to create image object: " + ex);
                        return null;
                    }
                } else {
                    Log.e(TAG, "unable to moveTo to " + i + "; count is " + c.getCount());
                    return null;
                }
            }
        }
        public IImage getImageForUri(Uri uri) {
            // TODO make this a hash lookup
            for (int i = 0; i < getCount(); i++) {
                if (getImageAt(i).fullSizeImageUri().equals(uri)) {
                    return getImageAt(i);
                }
            }
            return null;
        }
        private byte [] getMiniThumbFromFile(long id, byte [] data, long magicCheck) {
            RandomAccessFile r = miniThumbDataFile();
            if (r == null)
                return null;

            long pos = id * sBytesPerMiniThumb;
            RandomAccessFile f = r;
            synchronized (f) {
                try {
                    f.seek(pos);
                    if (f.readByte() == 1) {
                        long magic = f.readLong();
                        if (magic != magicCheck) {
                            if (VERBOSE) Log.v(TAG, "for id " + id + "; magic: " + magic + "; magicCheck: " + magicCheck + " (fail)");
                            return null;
                        }
                        int length = f.readInt();
                        f.read(data, 0, length);
                        return data;
                    } else {
                        return null;
                    }
                } catch (IOException ex) {
                    long fileLength;
                    try {
                        fileLength = f.length();
                    } catch (IOException ex1) {
                        fileLength = -1;
                    }
                    if (VERBOSE) {
                        Log.e(TAG, "couldn't read thumbnail for " + id + "; " + ex.toString() + "; pos is " + pos + "; length is " + fileLength);
                    }
                    return null;
                }
            }
        }
        protected int getRowFor(IImage imageObj) {
            Cursor c = getCursor();
            synchronized (c) {
                int index = 0;
                long targetId = imageObj.fullSizeImageId();
                if (c.moveToFirst()) {
                    do {
                        if (c.getLong(0) == targetId) {
                            return index;
                        }
                        index += 1;
                    } while (c.moveToNext());
                }
                return -1;
            }
        }

        protected abstract int indexOrientation();
        protected abstract int indexDateTaken();
        protected abstract int indexDescription();
        protected abstract int indexMimeType();
        protected abstract int indexData();
        protected abstract int indexId();
        protected abstract int indexLatitude();
        protected abstract int indexLongitude();
        protected abstract int indexMiniThumbId();
        protected abstract int indexPicasaWeb();
        protected abstract int indexPrivate();
        protected abstract int indexTitle();
        protected abstract int indexDisplayName();
        protected abstract int indexThumbId();

        protected IImage make(long id, long miniThumbId, ContentResolver cr, IImageList list, long timestamp, int index, int rotation) {
            return null;
        }

        protected abstract Bitmap makeBitmap(int targetWidthHeight, Uri uri, ParcelFileDescriptor pfdInput, BitmapFactory.Options options);

        public boolean removeImage(IImage image) {
            Cursor c = getCursor();
            synchronized (c) {
                /*
                 * TODO: consider putting the image in a holding area so
                 * we can get it back as needed
                 * TODO: need to delete the thumbnails as well
                 */
                boolean moved;
                try {
                    moved = c.moveToPosition(image.getRow());
                } catch (Exception ex) {
                    Log.e(TAG, "removeImage got exception " + ex.toString());
                    return false;
                }
                if (moved) {
                    Uri u = image.fullSizeImageUri();
                    mContentResolver.delete(u, null, null);
                    image.onRemove();
                    requery();
                }
            }
            return true;
        }


        /* (non-Javadoc)
         * @see com.android.camera.IImageList#removeImageAt(int)
         */
        public void removeImageAt(int i) {
            Cursor c = getCursor();
            synchronized (c) {
                /*
                 * TODO: consider putting the image in a holding area so
                 * we can get it back as needed
                 * TODO: need to delete the thumbnails as well
                 */
                dump("before delete");
                IImage image = getImageAt(i);
                boolean moved;
                try {
                    moved = c.moveToPosition(i);
                } catch (Exception ex) {
                    return;
                }
                if (moved) {
                    Uri u = image.fullSizeImageUri();
                    mContentResolver.delete(u, null, null);
                    requery();
                    image.onRemove();
                }
                dump("after delete");
            }
        }

        public void removeOnChangeListener(OnChange changeCallback) {
            if (changeCallback == mListener)
                mListener = null;
        }

        protected void requery() {
            mCache.clear();
            mCursor.requery();
            mCursorDeactivated = false;
        }

        protected void saveMiniThumbToFile(Bitmap bitmap, long id, long magic) throws IOException {
            byte[] data = miniThumbData(bitmap);
            saveMiniThumbToFile(data, id, magic);
        }

        protected void saveMiniThumbToFile(byte[] data, long id, long magic) throws IOException {
            RandomAccessFile r = miniThumbDataFile();
            if (r == null)
                return;

            long pos = id * sBytesPerMiniThumb;
            long t0 = System.currentTimeMillis();
            synchronized (r) {
                try {
                    long t1 = System.currentTimeMillis();
                    long t2 = System.currentTimeMillis();
                    if (data != null) {
                        if (data.length > sBytesPerMiniThumb) {
                            if (VERBOSE) Log.v(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!! " + data.length + " > " + sBytesPerMiniThumb);
                            return;
                        }
                        r.seek(pos);
                        r.writeByte(0);     // we have no data in this slot

                        // if magic is 0 then leave it alone
                        if (magic == 0)
                            r.skipBytes(8);
                        else
                            r.writeLong(magic);
                        r.writeInt(data.length);
                        r.write(data);
                        //                      f.flush();
                        r.seek(pos);
                        r.writeByte(1);  // we have data in this slot
                        long t3 = System.currentTimeMillis();

                        if (VERBOSE) Log.v(TAG, "saveMiniThumbToFile took " + (t3-t0) + "; " + (t1-t0) + " " + (t2-t1) + " " + (t3-t2));
                    }
                } catch (IOException ex) {
                    Log.e(TAG, "couldn't save mini thumbnail data for " + id + "; " + ex.toString());
                    throw ex;
                }
            }
        }

        public void setOnChangeListener(OnChange changeCallback, Handler h) {
            mListener = changeCallback;
            mHandler = h;
        }
    }

    public class CanceledException extends Exception {

    }
    public enum DataLocation { NONE, INTERNAL, EXTERNAL, ALL }

    public interface IAddImage_cancelable extends ICancelable {
        public void get();
    }

    /*
     * The model for canceling an in-progress image save is this.  For any
     * given part of the task of saving return an ICancelable.  The "result"
     * from an ICancelable can be retrieved using the get* method.  If the
     * operation was canceled then null is returned.  The act of canceling
     * is to call "cancel" -- from another thread.
     *
     * In general an object which implements ICancelable will need to
     * check, periodically, whether they are canceled or not.  This works
     * well for some things and less well for others.
     *
     * Right now the actual jpeg encode does not check cancelation but
     * the part of encoding which writes the data to disk does.  Note,
     * though, that there is what appears to be a bug in the jpeg encoder
     * in that if the stream that's being written is closed it crashes
     * rather than returning an error.  TODO fix that.
     *
     * When an object detects that it is canceling it must, before exiting,
     * call acknowledgeCancel.  This is necessary because the caller of
     * cancel() will block until acknowledgeCancel is called.
     */
    public interface ICancelable {
        /*
         * call cancel() when the unit of work in progress needs to be
         * canceled.  This should return true if it was possible to
         * cancel and false otherwise.  If this returns false the caller
         * may still be able to cleanup and simulate cancelation.
         */
        public boolean cancel();
    }

    public interface IGetBitmap_cancelable extends ICancelable {
        // returns the bitmap or null if there was an error or we were canceled
        public Bitmap get();
    };
    public interface IGetBoolean_cancelable extends ICancelable {
        public boolean get();
    }
    public interface IImage {

        public abstract void commitChanges();

        /**
         * Get the bitmap for the full size image.
         * @return  the bitmap for the full size image.
         */
        public abstract Bitmap fullSizeBitmap(int targetWidthOrHeight);

        /**
         *
         * @return an object which can be canceled while the bitmap is loading
         */
        public abstract IGetBitmap_cancelable fullSizeBitmap_cancelable(int targetWidthOrHeight);

        /**
         * Gets the input stream associated with a given full size image.
         * This is used, for example, if one wants to email or upload
         * the image.
         * @return  the InputStream associated with the image.
         */
        public abstract InputStream fullSizeImageData();
        public abstract long fullSizeImageId();
        public abstract Uri fullSizeImageUri();
        public abstract IImageList getContainer();
        public abstract long getDateTaken();

        /**
         * Gets the description of the image.
         * @return  the description of the image.
         */
        public abstract String getDescription();
        public abstract String getMimeType();
        public abstract int getHeight();

        /**
         * Gets the flag telling whether this video/photo is private or public.
         * @return  the description of the image.
         */
        public abstract boolean getIsPrivate();

        public abstract double getLatitude();

        public abstract double getLongitude();

        /**
         * Gets the name of the image.
         * @return  the name of the image.
         */
        public abstract String getTitle();

        public abstract String getDisplayName();

        public abstract String getPicasaId();

        public abstract int getRow();

        public abstract int getWidth();

        public abstract boolean hasLatLong();

        public abstract long imageId();

        public abstract boolean isReadonly();

        public abstract boolean isDrm();

        public abstract Bitmap miniThumbBitmap();

        public abstract void onRemove();

        public abstract boolean rotateImageBy(int degrees);

        /**
         * Sets the description of the image.
         */
        public abstract void setDescription(String description);

        /**
         * Sets whether the video/photo is private or public.
         */
        public abstract void setIsPrivate(boolean isPrivate);

        /**
         * Sets the name of the image.
         */
        public abstract void setName(String name);

        public abstract void setPicasaId(String id);

        /**
         * Get the bitmap for the medium thumbnail.
         * @return  the bitmap for the medium thumbnail.
         */
        public abstract Bitmap thumbBitmap();

        public abstract Uri thumbUri();

        public abstract String getDataPath();
    }

    public interface IImageList {
        public HashMap<String, String> getBucketIds();

        public interface OnChange {
            public void onChange(IImageList list);
        }

        public interface ThumbCheckCallback {
            public boolean checking(int current, int count);
        }

        public abstract void checkThumbnails(ThumbCheckCallback cb, int totalCount);

        public abstract void commitChanges();

        public abstract void deactivate();

        /**
         * Returns the count of image objects.
         *
         * @return       the number of images
         */
        public abstract int getCount();

        /**
         * @return true if the count of image objects is zero.
         */

        public abstract boolean isEmpty();

        /**
         * Returns the image at the ith position.
         *
         * @param i     the position
         * @return      the image at the ith position
         */
        public abstract IImage getImageAt(int i);

        /**
         * Returns the image with a particular Uri.
         *
         * @param uri
         * @return      the image with a particular Uri.
         */
        public abstract IImage getImageForUri(Uri uri);;

        /**
         *
         * @param image
         * @return true if the image was removed.
         */
        public abstract boolean removeImage(IImage image);
        /**
         * Removes the image at the ith position.
         * @param i     the position
         */
        public abstract void removeImageAt(int i);

        public abstract void removeOnChangeListener(OnChange changeCallback);
        public abstract void setOnChangeListener(OnChange changeCallback, Handler h);
    }

    class Image extends BaseImage implements IImage {
        int mRotation;

        protected Image(long id, long miniThumbId, ContentResolver cr, BaseImageList container, int cursorRow, int rotation) {
            super(id, miniThumbId, cr, container, cursorRow);
            mRotation = rotation;
        }

        public String getDataPath() {
            String path = null;
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    int column = ((ImageList)getContainer()).indexData();
                    if (column >= 0)
                        path = c.getString(column);
                }
            }
            return path;
        }

        protected int getDegreesRotated() {
            return mRotation;
        }

        protected void setDegreesRotated(int degrees) {
            Cursor c = getCursor();
            mRotation = degrees;
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    int column = ((ImageList)getContainer()).indexOrientation();
                    if (column >= 0) {
                        c.updateInt(column, degrees);
                        getContainer().commitChanges();
                    }
                }
            }
        }

        protected Bitmap.CompressFormat compressionType() {
            String mimeType = getMimeType();
            if (mimeType == null)
                return Bitmap.CompressFormat.JPEG;

            if (mimeType.equals("image/png"))
                return Bitmap.CompressFormat.PNG;
            else if (mimeType.equals("image/gif"))
                return Bitmap.CompressFormat.PNG;

            return Bitmap.CompressFormat.JPEG;
        }

        /**
         * Does not replace the tag if already there. Otherwise, adds to the exif tags.
         * @param tag
         * @param value
         */
        public void addExifTag(String tag, String value) {
            if (mExifData == null) {
                mExifData = new HashMap<String, String>();
            }
            if (!mExifData.containsKey(tag)) {
                mExifData.put(tag, value);
            } else {
                if (VERBOSE) Log.v(TAG, "addExifTag where the key already was there: " + tag + " = " + value);
            }
        }

        /**
         * Return the value of the Exif tag as an int. Returns 0 on any type of error.
         * @param tag
         * @return
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

        /* (non-Javadoc)
         * @see com.android.camera.IImage#saveModifiedImage(android.graphics.Bitmap)
         */
        public IGetBoolean_cancelable saveImageContents(
                final Bitmap image,
                final byte [] jpegData,
                final int orientation,
                final boolean newFile,
                final Cursor cursor) {
            final class SaveImageContentsCancelable extends BaseCancelable implements IGetBoolean_cancelable {
                IGetBoolean_cancelable mCurrentCancelable = null;

                SaveImageContentsCancelable() {
                }

                public boolean doCancelWork() {
                    synchronized (this) {
                        if (mCurrentCancelable != null)
                            mCurrentCancelable.cancel();
                    }
                    return true;
                }

                public boolean get() {
                    try {
                        Bitmap thumbnail = null;

                        long t1 = System.currentTimeMillis();
                        Uri uri = mContainer.contentUri(mId);
                        synchronized (this) {
                            checkCanceled();
                            mCurrentCancelable = compressImageToFile(image, jpegData, uri);
                        }

                        long t2 = System.currentTimeMillis();
                        if (!mCurrentCancelable.get())
                            return false;

                        synchronized (this) {
                            String filePath;
                            synchronized (cursor) {
                                cursor.moveToPosition(0);
                                filePath = cursor.getString(2);
                            }
                            // TODO: If thumbData is present and usable, we should call the version
                            // of storeThumbnail which takes a byte array, rather than re-encoding
                            // a new JPEG of the same dimensions.
                            byte [] thumbData = null;
                            synchronized (ImageManager.instance()) {
                                thumbData = (new ExifInterface(filePath)).getThumbnail();
                            }
                            if (VERBOSE) Log.v(TAG, "for file " + filePath + " thumbData is " + thumbData + "; length " + (thumbData!=null ? thumbData.length : -1));
                            if (thumbData != null) {
                                thumbnail = BitmapFactory.decodeByteArray(thumbData, 0, thumbData.length);
                                if (VERBOSE) Log.v(TAG, "embedded thumbnail bitmap " + thumbnail.getWidth() + "/" + thumbnail.getHeight());
                            }
                            if (thumbnail == null && image != null) {
                                thumbnail = image;
                            }
                            if (thumbnail == null && jpegData != null) {
                                thumbnail = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                            }
                        }

                        long t3 = System.currentTimeMillis();
                        mContainer.storeThumbnail(thumbnail, Image.this.fullSizeImageId());
                        long t4 = System.currentTimeMillis();
                        checkCanceled();
                        if (VERBOSE) Log.v(TAG, ">>>>>>>>>>>>>>>>>>>>> rotating by " + orientation);
                        try {
                            thumbnail = rotate(thumbnail, orientation);
                            saveMiniThumb(thumbnail);
                        } catch (IOException e) {
                            // Ignore if unable to save thumb.
                        }
                        long t5 = System.currentTimeMillis();
                        checkCanceled();

                        if (VERBOSE) Log.v(TAG, String.format("Timing data %d %d %d %d", t2-t1, t3-t2, t4-t3, t5-t4));
                        return true;
                    } catch (CanceledException ex) {
                        if (VERBOSE) Log.v(TAG, "got canceled... need to cleanup");
                        return false;
                    } finally {
                        /*
                        Cursor c = getCursor();
                        synchronized (c) {
                            if (c.moveTo(getRow())) {
                                mContainer.requery();
                            }
                        }
                        */
                        acknowledgeCancel();
                    }
                }
            }
            return new SaveImageContentsCancelable();
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
                    if (degrees < 0)
                        degrees += 360;

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

                    replaceExifTag(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
                    replaceExifTag("UserComment", "saveRotatedImage comment orientation: " + orientation);
                    exif.saveAttributes(mExifData);
                    exif.commitChanges();
                }
            } catch (Exception ex) {
                Log.e(TAG, "unable to save exif data with new orientation " + fullSizeImageUri());
            }
        }

        /**
         * Save the rotated image by updating the Exif "Orientation" tag.
         * @param degrees
         * @return
         */
        public boolean rotateImageBy(int degrees) {
            int newDegrees = getDegreesRotated() + degrees;
            setExifRotation(newDegrees);
            setDegreesRotated(newDegrees);

            // setting this to zero will force the call to checkCursor to generate fresh thumbs
            mMiniThumbMagic = 0;
            try {
                mContainer.checkThumbnail(this, mContainer.getCursor(), this.getRow());
            } catch (IOException e) {
                // Ignore inability to store mini thumbnail.
            }

            return true;
        }

        public Bitmap thumbBitmap() {
            Bitmap bitmap = null;
            Cursor c = null;
            if (mContainer.mThumbUri != null) {
                try {
                    c = mContentResolver.query(
                            mContainer.mThumbUri,
                            THUMB_PROJECTION,
                            Thumbnails.IMAGE_ID + "=?",
                            new String[] { String.valueOf(fullSizeImageId()) },
                            null);
                    if (c != null && c.moveToFirst()) {
                        Uri thumbUri = ContentUris.withAppendedId(mContainer.mThumbUri, c.getLong(((ImageList)mContainer).INDEX_THUMB_ID));
                        ParcelFileDescriptor pfdInput;
                        try {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inDither = false;
                            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            pfdInput = mContentResolver.openFileDescriptor(thumbUri, "r");
                            bitmap = BitmapFactory.decodeFileDescriptor(pfdInput.getFileDescriptor(), null, options);
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
                    }
                } catch (Exception ex) {
                    // sdcard removed?
                    return null;
                } finally {
                    if (c != null)
                        c.close();
                }
            }

            if (bitmap == null) {
                bitmap = fullSizeBitmap(THUMBNAIL_TARGET_SIZE, false);
                if (VERBOSE) {
                    Log.v(TAG, "no thumbnail found... storing new one for " + fullSizeImageId());
                }
                bitmap = mContainer.storeThumbnail(bitmap, fullSizeImageId());
            }

            if (bitmap != null) {
                bitmap = rotate(bitmap, getDegreesRotated());
            }

            long elapsed = System.currentTimeMillis();
            return bitmap;
        }

    }

    final static private String sWhereClause = "(" + Images.Media.MIME_TYPE + " in (?, ?, ?))";
    final static private String[] sAcceptableImageTypes = new String[] { "image/jpeg", "image/png", "image/gif" };
    final static private String sMiniThumbIsNull = "mini_thumb_magic isnull";

    private static final String[] IMAGE_PROJECTION = new String[] {
            "_id",
            "_data",
            ImageColumns.DATE_TAKEN,
            ImageColumns.MINI_THUMB_MAGIC,
            ImageColumns.ORIENTATION,
            ImageColumns.MIME_TYPE
        };

    /**
     * Represents an ordered collection of Image objects.
     * Provides an api to add and remove an image.
     */
    class ImageList extends BaseImageList implements IImageList {
        final int INDEX_ID               = indexOf(IMAGE_PROJECTION, "_id");
        final int INDEX_DATA             = indexOf(IMAGE_PROJECTION, "_data");
        final int INDEX_MIME_TYPE        = indexOf(IMAGE_PROJECTION, MediaColumns.MIME_TYPE);
        final int INDEX_DATE_TAKEN       = indexOf(IMAGE_PROJECTION, ImageColumns.DATE_TAKEN);
        final int INDEX_MINI_THUMB_MAGIC = indexOf(IMAGE_PROJECTION, ImageColumns.MINI_THUMB_MAGIC);
        final int INDEX_ORIENTATION      = indexOf(IMAGE_PROJECTION, ImageColumns.ORIENTATION);

        final int INDEX_THUMB_ID         = indexOf(THUMB_PROJECTION, BaseColumns._ID);
        final int INDEX_THUMB_IMAGE_ID   = indexOf(THUMB_PROJECTION, Images.Thumbnails.IMAGE_ID);
        final int INDEX_THUMB_WIDTH      = indexOf(THUMB_PROJECTION, Images.Thumbnails.WIDTH);
        final int INDEX_THUMB_HEIGHT     = indexOf(THUMB_PROJECTION, Images.Thumbnails.HEIGHT);

        boolean mIsRegistered = false;
        ContentObserver mContentObserver;
        DataSetObserver mDataSetObserver;

        public HashMap<String, String> getBucketIds() {
            Cursor c = Images.Media.query(
                        mContentResolver,
                        mBaseUri.buildUpon().appendQueryParameter("distinct", "true").build(),
                        new String[] {
                            ImageColumns.BUCKET_DISPLAY_NAME,
                            ImageColumns.BUCKET_ID
                        },
                        whereClause(),
                        whereClauseArgs(),
                        sortOrder());

            HashMap<String, String> hash = new HashMap<String, String>();
            if (c != null && c.moveToFirst()) {
                do {
                    hash.put(c.getString(1), c.getString(0));
                } while (c.moveToNext());
            }
            return hash;
        }
        /**
         * ImageList constructor.
         * @param cr    ContentResolver
         */
        public ImageList(Context ctx, ContentResolver cr, Uri imageUri, Uri thumbUri, int sort, String bucketId) {
            super(ctx, cr, imageUri, sort, bucketId);
            mBaseUri = imageUri;
            mThumbUri = thumbUri;
            mSort = sort;

            mContentResolver = cr;

            mCursor = createCursor();
            if (mCursor == null) {
                Log.e(TAG, "unable to create image cursor for " + mBaseUri);
                throw new UnsupportedOperationException();
            }

            if (VERBOSE) {
                Log.v(TAG, "for " + mBaseUri.toString() + " got cursor " + mCursor + " with length " + (mCursor != null ? mCursor.getCount() : "-1"));
            }

            final Runnable updateRunnable = new Runnable() {
                public void run() {
                    // handling these external updates is causing ANR problems that are unresolved.
                    // For now ignore them since there shouldn't be anyone modifying the database on the fly.
                    if (true)
                        return;

                    synchronized (mCursor) {
                        requery();
                    }
                    if (mListener != null)
                        mListener.onChange(ImageList.this);
                }
            };

            mContentObserver = new ContentObserver(null) {
                @Override
                public boolean deliverSelfNotifications() {
                    return false;
                }

                @Override
                public void onChange(boolean selfChange) {
                    if (VERBOSE) Log.v(TAG, "MyContentObserver.onChange; selfChange == " + selfChange);
                    updateRunnable.run();
                }
            };

            mDataSetObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    if (VERBOSE) Log.v(TAG, "MyDataSetObserver.onChanged");
//                  updateRunnable.run();
                }

                @Override
                public void onInvalidated() {
                    if (VERBOSE) Log.v(TAG, "MyDataSetObserver.onInvalidated: " + mCursorDeactivated);
                }
            };

            registerObservers();
        }

        private void registerObservers() {
            if (mIsRegistered)
                return;

            mCursor.registerContentObserver(mContentObserver);
            mCursor.registerDataSetObserver(mDataSetObserver);
            mIsRegistered = true;
        }

        private void unregisterObservers() {
            if (!mIsRegistered)
                return;

            mCursor.unregisterContentObserver(mContentObserver);
            mCursor.unregisterDataSetObserver(mDataSetObserver);
            mIsRegistered = false;
        }

        public void deactivate() {
            super.deactivate();
            unregisterObservers();
        }

        protected void activateCursor() {
            super.activateCursor();
            registerObservers();
        }

        protected String whereClause() {
            if (mBucketId != null) {
                return sWhereClause + " and " + Images.Media.BUCKET_ID + " = '" + mBucketId + "'";
            } else {
                return sWhereClause;
            }
        }

        protected String[] whereClauseArgs() {
            return sAcceptableImageTypes;
        }

        protected Cursor createCursor() {
            Cursor c =
                Images.Media.query(
                    mContentResolver,
                    mBaseUri,
                    IMAGE_PROJECTION,
                    whereClause(),
                    whereClauseArgs(),
                    sortOrder());
            if (VERBOSE)
                Log.v(TAG, "createCursor got cursor with count " + (c == null ? -1 : c.getCount()));
            return c;
        }

        protected int indexOrientation() {  return INDEX_ORIENTATION;      }
        protected int indexDateTaken()   {  return INDEX_DATE_TAKEN;       }
        protected int indexDescription() {  return -1;                     }
        protected int indexMimeType()    {  return INDEX_MIME_TYPE;        }
        protected int indexData()        {  return INDEX_DATA;             }
        protected int indexId()          {  return INDEX_ID;               }
        protected int indexLatitude()    {  return -1;                     }
        protected int indexLongitude()   {  return -1;                     }
        protected int indexMiniThumbId() {  return INDEX_MINI_THUMB_MAGIC; }

        protected int indexPicasaWeb()   {  return -1;                     }
        protected int indexPrivate()     {  return -1;                     }
        protected int indexTitle()       {  return -1;                     }
        protected int indexDisplayName() {  return -1;                     }
        protected int indexThumbId()     {  return INDEX_THUMB_ID;        }

        @Override
        protected IImage make(long id, long miniThumbId, ContentResolver cr, IImageList list, long timestamp, int index, int rotation) {
            return new Image(id, miniThumbId, mContentResolver, this, index, rotation);
        }

        protected Bitmap makeBitmap(int targetWidthHeight, Uri uri, ParcelFileDescriptor pfd, BitmapFactory.Options options) {
            Bitmap b = null;

            try {
                if (pfd == null)
                    pfd = makeInputStream(uri);

                if (pfd == null)
                    return null;

                if (options == null)
                    options = new BitmapFactory.Options();

                java.io.FileDescriptor fd = pfd.getFileDescriptor();
                options.inSampleSize = 1;
                if (targetWidthHeight != -1) {
                    options.inJustDecodeBounds = true;
                    long t1 = System.currentTimeMillis();
                    BitmapFactory.decodeFileDescriptor(fd, null, options);
                    long t2 = System.currentTimeMillis();
                    if (options.mCancel || options.outWidth == -1 || options.outHeight == -1) {
                        return null;
                    }
                    options.inSampleSize = computeSampleSize(options, targetWidthHeight);
                    options.inJustDecodeBounds = false;
                }

                options.inDither = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                long t1 = System.currentTimeMillis();
                b = BitmapFactory.decodeFileDescriptor(fd, null, options);
                long t2 = System.currentTimeMillis();
                if (VERBOSE) {
                    Log.v(TAG, "A: got bitmap " + b + " with sampleSize " + options.inSampleSize + " took " + (t2-t1));
                }
            } catch (OutOfMemoryError ex) {
                if (VERBOSE) Log.v(TAG, "got oom exception " + ex);
                return null;
            } finally {
                try {
                    pfd.close();
                } catch (IOException ex) {
                }
            }
            return b;
        }

        private ParcelFileDescriptor makeInputStream(Uri uri) {
            try {
                return mContentResolver.openFileDescriptor(uri, "r");
            } catch (IOException ex) {
                return null;
            }
        }

        private String sortOrder() {
            // add id to the end so that we don't ever get random sorting
            // which could happen, I suppose, if the first two values were
            // duplicated
            String ascending = (mSort == SORT_ASCENDING ? " ASC" : " DESC");
            return
                Images.Media.DATE_TAKEN + ascending + "," +
                Images.Media._ID + ascending;
        }

    }

    /**
     * Represents an ordered collection of Image objects from the DRM provider.
     */
    class DrmImageList extends ImageList implements IImageList {
        private final String[] DRM_IMAGE_PROJECTION = new String[] {
            DrmStore.Audio._ID,
            DrmStore.Audio.DATA,
            DrmStore.Audio.MIME_TYPE,
        };

        final int INDEX_ID            = indexOf(DRM_IMAGE_PROJECTION, DrmStore.Audio._ID);
        final int INDEX_MIME_TYPE     = indexOf(DRM_IMAGE_PROJECTION, DrmStore.Audio.MIME_TYPE);

        public DrmImageList(Context ctx, ContentResolver cr, Uri imageUri, int sort, String bucketId) {
            super(ctx, cr, imageUri, null, sort, bucketId);
        }

        protected Cursor createCursor() {
            return mContentResolver.query(mBaseUri, DRM_IMAGE_PROJECTION, null, null, sortOrder());
        }

        @Override
        public void checkThumbnails(ThumbCheckCallback cb, int totalCount) {
            // do nothing
        }

        @Override
        public long checkThumbnail(BaseImage existingImage, Cursor c, int i) {
            return 0;
        }

        class DrmImage extends Image {
            protected DrmImage(long id, ContentResolver cr, BaseImageList container, int cursorRow) {
                super(id, 0, cr, container, cursorRow, 0);
            }

            public boolean isDrm() {
                return true;
            }

            public boolean isReadonly() {
                return true;
            }

            public Bitmap miniThumbBitmap() {
                return fullSizeBitmap(MINI_THUMB_TARGET_SIZE);
            }

            public Bitmap thumbBitmap() {
                return fullSizeBitmap(THUMBNAIL_TARGET_SIZE);
            }

            public String getDisplayName() {
                return getTitle();
            }
        }

        @Override
        protected IImage make(long id, long miniThumbId, ContentResolver cr, IImageList list, long timestamp, int index, int rotation) {
            return new DrmImage(id, mContentResolver, this, index);
        }

        protected int indexOrientation() {  return -1; }
        protected int indexDateTaken()   {  return -1; }
        protected int indexDescription() {  return -1; }
        protected int indexMimeType()    {  return -1; }
        protected int indexId()          {  return -1; }
        protected int indexLatitude()    {  return -1; }
        protected int indexLongitude()   {  return -1; }
        protected int indexMiniThumbId() {  return -1; }
        protected int indexPicasaWeb()   {  return -1; }
        protected int indexPrivate()     {  return -1; }
        protected int indexTitle()       {  return -1; }
        protected int indexDisplayName() {  return -1; }
        protected int indexThumbId()     {  return -1; }

        // TODO review this probably should be based on DATE_TAKEN same as images
        private String sortOrder() {
            String ascending = (mSort == SORT_ASCENDING ? " ASC" : " DESC");
            return
                DrmStore.Images.TITLE  + ascending + "," +
                DrmStore.Images._ID;
        }
    }

    class ImageListUber implements IImageList {
        private IImageList [] mSubList;
        private int mSort;
        private IImageList.OnChange mListener = null;
        Handler mHandler;

        // This is an array of Longs wherein each Long consists of
        // two components.  The first component indicates the number of
        // consecutive entries that belong to a given sublist.
        // The second component indicates which sublist we're referring
        // to (an int which is used to index into mSubList).
        ArrayList<Long> mSkipList = null;

        int [] mSkipCounts = null;

        public HashMap<String, String> getBucketIds() {
            HashMap<String, String> hashMap = new HashMap<String, String>();
            for (IImageList list: mSubList) {
                hashMap.putAll(list.getBucketIds());
            }
            return hashMap;
        }

        public ImageListUber(IImageList [] sublist, int sort) {
            mSubList = sublist.clone();
            mSort = sort;

            if (mListener != null) {
                for (IImageList list: sublist) {
                    list.setOnChangeListener(new OnChange() {
                        public void onChange(IImageList list) {
                            if (mListener != null) {
                                mListener.onChange(ImageListUber.this);
                            }
                        }
                    }, mHandler);
                }
            }
        }

        public void checkThumbnails(ThumbCheckCallback cb, int totalThumbnails) {
            for (IImageList i : mSubList) {
                int count = i.getCount();
                i.checkThumbnails(cb, totalThumbnails);
                totalThumbnails -= count;
            }
        }

        public void commitChanges() {
            final IImageList sublist[] = mSubList;
            final int length = sublist.length;
            for (int i = 0; i < length; i++)
                sublist[i].commitChanges();
        }

        public void deactivate() {
            final IImageList sublist[] = mSubList;
            final int length = sublist.length;
            int pos = -1;
            while (++pos < length) {
                IImageList sub = sublist[pos];
                sub.deactivate();
            }
        }

        public int getCount() {
            final IImageList sublist[] = mSubList;
            final int length = sublist.length;
            int count = 0;
            for (int i = 0; i < length; i++)
                count += sublist[i].getCount();
            return count;
        }

        public boolean isEmpty() {
            final IImageList sublist[] = mSubList;
            final int length = sublist.length;
            for (int i = 0; i < length; i++) {
                if (! sublist[i].isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        // mSkipCounts is used to tally the counts as we traverse
        // the mSkipList.  It's a member variable only so that
        // we don't have to allocate each time through.  Otherwise
        // it could just as easily be a local.

        public synchronized IImage getImageAt(int index) {
            if (index < 0 || index > getCount())
                throw new IndexOutOfBoundsException("index " + index + " out of range max is " + getCount());

            // first make sure our allocations are in order
            if (mSkipCounts == null || mSubList.length > mSkipCounts.length)
                mSkipCounts = new int[mSubList.length];

            if (mSkipList == null)
                mSkipList = new ArrayList<Long>();

            // zero out the mSkipCounts since that's only used for the
            // duration of the function call
            for (int i = 0; i < mSubList.length; i++)
                mSkipCounts[i] = 0;

            // a counter of how many images we've skipped in
            // trying to get to index.  alternatively we could
            // have decremented index but, alas, I liked this
            // way more.
            int skipCount = 0;

            // scan the existing mSkipList to see if we've computed
            // enough to just return the answer
            for (int i = 0; i < mSkipList.size(); i++) {
                long v = mSkipList.get(i);

                int offset = (int) (v & 0xFFFF);
                int which  = (int) (v >> 32);

                if (skipCount + offset > index) {
                    int subindex = mSkipCounts[which] + (index - skipCount);
                    IImage img = mSubList[which].getImageAt(subindex);
                    return img;
                }

                skipCount += offset;
                mSkipCounts[which] += offset;
            }

            // if we get here we haven't computed the answer for
            // "index" yet so keep computing.  This means running
            // through the list of images and either modifying the
            // last entry or creating a new one.
            long count = 0;
            while (true) {
                long maxTimestamp = mSort == SORT_ASCENDING ? Long.MAX_VALUE : Long.MIN_VALUE;
                int which = -1;
                for (int i = 0; i < mSubList.length; i++) {
                    int pos = mSkipCounts[i];
                    IImageList list = mSubList[i];
                    if (pos < list.getCount()) {
                        IImage image = list.getImageAt(pos);
                        // this should never be null but sometimes the database is
                        // causing problems and it is null
                        if (image != null) {
                            long timestamp = image.getDateTaken();
                            if (mSort == SORT_ASCENDING ? (timestamp < maxTimestamp) : (timestamp > maxTimestamp)) {
                                maxTimestamp = timestamp;
                                which = i;
                            }
                        }
                    }
                }

                if (which == -1) {
                    if (VERBOSE) Log.v(TAG, "which is -1, returning null");
                    return null;
                }

                boolean done = false;
                count = 1;
                if (mSkipList.size() > 0) {
                    int pos = mSkipList.size() - 1;
                    long oldEntry = mSkipList.get(pos);
                    if ((oldEntry >> 32) == which) {
                        long newEntry = oldEntry + 1;
                        mSkipList.set(pos, newEntry);
                        done = true;
                    }
                }
                if (!done) {
                    long newEntry = ((long)which << 32) | count;
                    if (VERBOSE) {
                        Log.v(TAG, "new entry is " + Long.toHexString(newEntry));
                    }
                    mSkipList.add(newEntry);
                }

                if (skipCount++ == index) {
                    return mSubList[which].getImageAt(mSkipCounts[which]);
                }
                mSkipCounts[which] += 1;
            }
        }

        public IImage getImageForUri(Uri uri) {
            // TODO perhaps we can preflight the base of the uri
            // against each sublist first
            for (int i = 0; i < mSubList.length; i++) {
                IImage img = mSubList[i].getImageForUri(uri);
                if (img != null)
                    return img;
            }
            return null;
        }

        /**
         * Modify the skip list when an image is deleted by finding
         * the relevant entry in mSkipList and decrementing the
         * counter.  This is simple because deletion can never
         * cause change the order of images.
         */
        public void modifySkipCountForDeletedImage(int index) {
            int skipCount = 0;

            for (int i = 0; i < mSkipList.size(); i++) {
                long v = mSkipList.get(i);

                int offset = (int) (v & 0xFFFF);
                int which  = (int) (v >> 32);

                if (skipCount + offset > index) {
                    mSkipList.set(i, v-1);
                    break;
                }

                skipCount += offset;
            }
        }

        public boolean removeImage(IImage image) {
            IImageList parent = image.getContainer();
            int pos = -1;
            int baseIndex = 0;
            while (++pos < mSubList.length) {
                IImageList sub = mSubList[pos];
                if (sub == parent) {
                    if (sub.removeImage(image)) {
                        modifySkipCountForDeletedImage(baseIndex);
                        return true;
                    } else {
                        break;
                    }
                }
                baseIndex += sub.getCount();
            }
            return false;
        }

        public void removeImageAt(int index) {
            IImage img = getImageAt(index);
            if (img != null) {
                IImageList list = img.getContainer();
                if (list != null) {
                    list.removeImage(img);
                    modifySkipCountForDeletedImage(index);
                }
            }
        }

        public void removeOnChangeListener(OnChange changeCallback) {
            if (changeCallback == mListener)
                mListener = null;
        }

        public void setOnChangeListener(OnChange changeCallback, Handler h) {
            mListener = changeCallback;
            mHandler = h;
        }

    }

    public static abstract class SimpleBaseImage implements IImage {
        public void commitChanges() {
            throw new UnsupportedOperationException();
        }

        public InputStream fullSizeImageData() {
            throw new UnsupportedOperationException();
        }

        public long fullSizeImageId() {
            return 0;
        }

        public Uri fullSizeImageUri() {
            throw new UnsupportedOperationException();
        }

        public IImageList getContainer() {
            return null;
        }

        public long getDateTaken() {
            return 0;
        }

        public String getMimeType() {
            throw new UnsupportedOperationException();
        }

        public String getDescription() {
            throw new UnsupportedOperationException();
        }

        public boolean getIsPrivate() {
            throw new UnsupportedOperationException();
        }

        public double getLatitude() {
            return 0D;
        }

        public double getLongitude() {
            return 0D;
        }

        public String getTitle() {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            throw new UnsupportedOperationException();
        }

        public String getPicasaId() {
            return null;
        }

        public int getRow() {
            throw new UnsupportedOperationException();
        }

        public int getHeight() {
            return 0;
        }

        public int getWidth() {
            return 0;
        }

        public boolean hasLatLong() {
            return false;
        }

        public boolean isReadonly() {
            return true;
        }

        public boolean isDrm() {
            return false;
        }

        public void onRemove() {
            throw new UnsupportedOperationException();
        }

        public boolean rotateImageBy(int degrees) {
            return false;
        }

        public void setDescription(String description) {
            throw new UnsupportedOperationException();
        }

        public void setIsPrivate(boolean isPrivate) {
            throw new UnsupportedOperationException();
        }

        public void setName(String name) {
            throw new UnsupportedOperationException();
        }

        public void setPicasaId(long id) {
        }

        public void setPicasaId(String id) {
        }

        public Uri thumbUri() {
            throw new UnsupportedOperationException();
        }
    }

    class SingleImageList extends BaseImageList implements IImageList {
        private IImage mSingleImage;
        private ContentResolver mContentResolver;
        private Uri mUri;

        class UriImage extends SimpleBaseImage {

            UriImage() {
            }

            public String getDataPath() {
                return mUri.getPath();
            }

            InputStream getInputStream() {
                try {
                    if (mUri.getScheme().equals("file")) {
                        String path = mUri.getPath();
                        if (VERBOSE)
                            Log.v(TAG, "path is " + path);
                        return new java.io.FileInputStream(mUri.getPath());
                    } else {
                        return mContentResolver.openInputStream(mUri);
                    }
                } catch (FileNotFoundException ex) {
                    return null;
                }
            }

            ParcelFileDescriptor getPFD() {
                try {
                    if (mUri.getScheme().equals("file")) {
                        String path = mUri.getPath();
                        if (VERBOSE)
                            Log.v(TAG, "path is " + path);
                        return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
                    } else {
                        return mContentResolver.openFileDescriptor(mUri, "r");
                    }
                } catch (FileNotFoundException ex) {
                    return null;
                }
            }

            /* (non-Javadoc)
             * @see com.android.camera.ImageManager.IImage#fullSizeBitmap(int)
             */
            public Bitmap fullSizeBitmap(int targetWidthHeight) {
                try {
                    ParcelFileDescriptor pfdInput = getPFD();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFileDescriptor(pfdInput.getFileDescriptor(), null, options);

                    if (targetWidthHeight != -1)
                        options.inSampleSize = computeSampleSize(options, targetWidthHeight);

                    options.inJustDecodeBounds = false;
                    options.inDither = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                    Bitmap b = BitmapFactory.decodeFileDescriptor(pfdInput.getFileDescriptor(), null, options);
                    if (VERBOSE) {
                        Log.v(TAG, "B: got bitmap " + b + " with sampleSize " + options.inSampleSize);
                    }
                    pfdInput.close();
                    return b;
                } catch (Exception ex) {
                    Log.e(TAG, "got exception decoding bitmap " + ex.toString());
                    return null;
                }
            }

            public IGetBitmap_cancelable fullSizeBitmap_cancelable(final int targetWidthOrHeight) {
                final class LoadBitmapCancelable extends BaseCancelable implements IGetBitmap_cancelable {
                    ParcelFileDescriptor pfdInput;
                    BitmapFactory.Options mOptions = new BitmapFactory.Options();
                    long mCancelInitiationTime;

                    public LoadBitmapCancelable(ParcelFileDescriptor pfd) {
                        pfdInput = pfd;
                    }

                    public boolean doCancelWork() {
                        if (VERBOSE)
                            Log.v(TAG, "requesting bitmap load cancel");
                        mCancelInitiationTime = System.currentTimeMillis();
                        mOptions.requestCancelDecode();
                        return true;
                    }

                    public Bitmap get() {
                        try {
                            Bitmap b = makeBitmap(targetWidthOrHeight, fullSizeImageUri(), pfdInput, mOptions);
                            if (b == null && mCancelInitiationTime != 0) {
                                if (VERBOSE)
                                    Log.v(TAG, "cancel returned null bitmap -- took " + (System.currentTimeMillis()-mCancelInitiationTime));
                            }
                            if (VERBOSE) Log.v(TAG, "b is " + b);
                            return b;
                        } catch (Exception ex) {
                            return null;
                        } finally {
                            acknowledgeCancel();
                        }
                    }
                }

                try {
                    ParcelFileDescriptor pfdInput = getPFD();
                    if (pfdInput == null)
                        return null;
                    if (VERBOSE) Log.v(TAG, "inputStream is " + pfdInput);
                    return new LoadBitmapCancelable(pfdInput);
                } catch (UnsupportedOperationException ex) {
                    return null;
                }
            }

            @Override
            public Uri fullSizeImageUri() {
                return mUri;
            }

            @Override
            public InputStream fullSizeImageData() {
                return getInputStream();
            }

            public long imageId() {
                return 0;
            }

            public Bitmap miniThumbBitmap() {
                return thumbBitmap();
            }

            @Override
            public String getTitle() {
                return mUri.toString();
            }

            @Override
            public String getDisplayName() {
                return getTitle();
            }

            @Override
            public String getDescription() {
                return "";
            }

            public Bitmap thumbBitmap() {
                Bitmap b = fullSizeBitmap(THUMBNAIL_TARGET_SIZE);
                if (b != null) {
                    Matrix m = new Matrix();
                    float scale = Math.min(1F, THUMBNAIL_TARGET_SIZE / (float) b.getWidth());
                    m.setScale(scale, scale);
                    Bitmap scaledBitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                    return scaledBitmap;
                } else {
                    return null;
                }
            }

            private BitmapFactory.Options snifBitmapOptions() {
                ParcelFileDescriptor input = getPFD();
                if (input == null)
                    return null;
                try {
                    Uri uri = fullSizeImageUri();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFileDescriptor(input.getFileDescriptor(), null, options);
                    return options;
                } finally {
                    try {
                        if (input != null) {
                            input.close();
                        }
                    } catch (IOException ex) {
                    }
                }
            }

            @Override
            public String getMimeType() {
                BitmapFactory.Options options = snifBitmapOptions();
                return (options!=null) ? options.outMimeType : "";
            }

            @Override
            public int getHeight() {
                BitmapFactory.Options options = snifBitmapOptions();
                return (options!=null) ? options.outHeight : 0;
            }

            @Override
            public int getWidth() {
                BitmapFactory.Options options = snifBitmapOptions();
                return (options!=null) ? options.outWidth : 0;
            }
        }

        public SingleImageList(ContentResolver cr, Uri uri) {
            super(null, cr, uri, ImageManager.SORT_ASCENDING, null);
            mContentResolver = cr;
            mUri = uri;
            mSingleImage = new UriImage();
        }

        public HashMap<String, String> getBucketIds() {
            throw new UnsupportedOperationException();
        }

        public void deactivate() {
            // nothing to do here
        }

        public int getCount() {
            return 1;
        }

        public boolean isEmpty() {
            return false;
        }

        public IImage getImageAt(int i) {
            if (i == 0)
                return mSingleImage;

            return null;
        }

        public IImage getImageForUri(Uri uri) {
            if (uri.equals(mUri))
                return mSingleImage;
            else
                return null;
        }

        public IImage getImageWithId(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected int indexOrientation() {
            return -1;
        }

        @Override
        protected int indexDateTaken() {
            return -1;
        }

        @Override
        protected int indexMimeType() {
            return -1;
        }

        @Override
        protected int indexDescription() {
            return -1;
        }

        @Override
        protected int indexId() {
            return -1;
        }

        @Override
        protected int indexData() {
            return -1;
        }

        @Override
        protected int indexLatitude() {
            return -1;
        }

        @Override
        protected int indexLongitude() {
            return -1;
        }

        @Override
        protected int indexMiniThumbId() {
            return -1;
        }

        @Override
        protected int indexPicasaWeb() {
            return -1;
        }

        @Override
        protected int indexPrivate() {
            return -1;
        }

        @Override
        protected int indexTitle() {
            return -1;
        }

        @Override
        protected int indexDisplayName() {
            return -1;
        }

        @Override
        protected int indexThumbId() {
            return -1;
        }

        private InputStream makeInputStream(Uri uri) {
            InputStream input = null;
            try {
                input = mContentResolver.openInputStream(uri);
                return input;
            } catch (IOException ex) {
                return null;
            }
        }

        @Override
        protected Bitmap makeBitmap(int targetWidthHeight, Uri uri, ParcelFileDescriptor pfdInput, BitmapFactory.Options options) {
            Bitmap b = null;

            try {
                if (options == null)
                    options = new BitmapFactory.Options();
                options.inSampleSize = 1;

                if (targetWidthHeight != -1) {
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFileDescriptor(pfdInput.getFileDescriptor(), null, options);

                    options.inSampleSize = computeSampleSize(options, targetWidthHeight);
                    options.inJustDecodeBounds = false;
                }
                b = BitmapFactory.decodeFileDescriptor(pfdInput.getFileDescriptor(), null, options);
                if (VERBOSE) {
                    Log.v(TAG, "C: got bitmap " + b + " with sampleSize " + options.inSampleSize);
                }
            } catch (OutOfMemoryError ex) {
                if (VERBOSE) Log.v(TAG, "got oom exception " + ex);
                return null;
            } finally {
                try {
                    pfdInput.close();
                } catch (IOException ex) {
                }
            }
            return b;
        }
   }

    class ThreadSafeOutputStream extends OutputStream {
        java.io.OutputStream mDelegateStream;
        boolean mClosed;

        public ThreadSafeOutputStream(OutputStream delegate) {
            mDelegateStream = delegate;
        }

        @Override
        synchronized public void close() throws IOException {
            try {
                mClosed = true;
                mDelegateStream.close();
            } catch (IOException ex) {

            }
        }

        @Override
        synchronized public void flush() throws IOException {
            super.flush();
        }

        @Override
        public void write(byte[] b, int offset, int length) throws IOException {
            /*
            mDelegateStream.write(b, offset, length);
            return;
            */
            while (length > 0) {
                synchronized (this) {
                    if (mClosed)
                        return;

                    int writeLength = Math.min(8192, length);
                    mDelegateStream.write(b, offset, writeLength);
                    offset += writeLength;
                    length -= writeLength;
                }
            }
        }

        @Override
        synchronized public void write(int oneByte) throws IOException {
            if (mClosed)
                return;
            mDelegateStream.write(oneByte);
        }
    }

    class VideoList extends BaseImageList implements IImageList {
        private final String[] sProjection = new String[] {
                Video.Media._ID,
                Video.Media.DATA,
                Video.Media.DATE_TAKEN,
                Video.Media.TITLE,
                Video.Media.DISPLAY_NAME,
                Video.Media.DESCRIPTION,
                Video.Media.IS_PRIVATE,
                Video.Media.TAGS,
                Video.Media.CATEGORY,
                Video.Media.LANGUAGE,
                Video.Media.LATITUDE,
                Video.Media.LONGITUDE,
                Video.Media.MINI_THUMB_MAGIC,
                Video.Media.MIME_TYPE,
        };

        final int INDEX_ID               = indexOf(sProjection, Video.Media._ID);
        final int INDEX_DATA             = indexOf(sProjection, Video.Media.DATA);
        final int INDEX_DATE_TAKEN       = indexOf(sProjection, Video.Media.DATE_TAKEN);
        final int INDEX_TITLE            = indexOf(sProjection, Video.Media.TITLE);
        final int INDEX_DISPLAY_NAME     = indexOf(sProjection, Video.Media.DISPLAY_NAME);
        final int INDEX_MIME_TYPE        = indexOf(sProjection, Video.Media.MIME_TYPE);
        final int INDEX_DESCRIPTION      = indexOf(sProjection, Video.Media.DESCRIPTION);
        final int INDEX_PRIVATE          = indexOf(sProjection, Video.Media.IS_PRIVATE);
        final int INDEX_TAGS             = indexOf(sProjection, Video.Media.TAGS);
        final int INDEX_CATEGORY         = indexOf(sProjection, Video.Media.CATEGORY);
        final int INDEX_LANGUAGE         = indexOf(sProjection, Video.Media.LANGUAGE);
        final int INDEX_LATITUDE         = indexOf(sProjection, Video.Media.LATITUDE);
        final int INDEX_LONGITUDE        = indexOf(sProjection, Video.Media.LONGITUDE);
        final int INDEX_MINI_THUMB_MAGIC = indexOf(sProjection, Video.Media.MINI_THUMB_MAGIC);
        final int INDEX_THUMB_ID         = indexOf(sProjection, BaseColumns._ID);

        public VideoList(Context ctx, ContentResolver cr, Uri uri, Uri thumbUri,
                int sort, String bucketId) {
            super(ctx, cr, uri, sort, bucketId);

            mCursor = createCursor();
            if (mCursor == null) {
                Log.e(TAG, "unable to create video cursor for " + mBaseUri);
                throw new UnsupportedOperationException();
            }

            if (Config.LOGV) {
                Log.v(TAG, "for " + mUri.toString() + " got cursor " + mCursor + " with length "
                        + (mCursor != null ? mCursor.getCount() : -1));
            }

            if (mCursor == null) {
                throw new UnsupportedOperationException();
            }
            if (mCursor != null && mCursor.moveToFirst()) {
                int row = 0;
                do {
                    long imageId = mCursor.getLong(indexId());
                    long dateTaken = mCursor.getLong(indexDateTaken());
                    long miniThumbId = mCursor.getLong(indexMiniThumbId());
                    mCache.put(imageId, new VideoObject(imageId, miniThumbId, mContentResolver,
                            this, dateTaken, row++));
                } while (mCursor.moveToNext());
            }
        }

        public HashMap<String, String> getBucketIds() {
            Cursor c = Images.Media.query(
                    mContentResolver,
                    mBaseUri.buildUpon().appendQueryParameter("distinct", "true").build(),
                    new String[] {
                        VideoColumns.BUCKET_DISPLAY_NAME,
                        VideoColumns.BUCKET_ID
                    },
                    whereClause(),
                    whereClauseArgs(),
                    sortOrder());

        HashMap<String, String> hash = new HashMap<String, String>();
        if (c != null && c.moveToFirst()) {
            do {
                hash.put(c.getString(1), c.getString(0));
            } while (c.moveToNext());
        }
        return hash;
        }

        protected String whereClause() {
            if (mBucketId != null) {
                return Images.Media.BUCKET_ID + " = '" + mBucketId + "'";
            } else {
                return null;
            }
        }

        protected String[] whereClauseArgs() {
            return null;
        }

        @Override
        protected String thumbnailWhereClause() {
            return sMiniThumbIsNull;
        }

        @Override
        protected String[] thumbnailWhereClauseArgs() {
            return null;
        }

        protected Cursor createCursor() {
            Cursor c =
                Images.Media.query(
                    mContentResolver,
                    mBaseUri,
                    sProjection,
                    whereClause(),
                    whereClauseArgs(),
                    sortOrder());
            if (VERBOSE)
                Log.v(TAG, "createCursor got cursor with count " + (c == null ? -1 : c.getCount()));
            return c;
        }

        protected int indexOrientation() {  return -1;                    }
        protected int indexDateTaken()   {  return INDEX_DATE_TAKEN;      }
        protected int indexDescription() {  return INDEX_DESCRIPTION;     }
        protected int indexMimeType()    {  return INDEX_MIME_TYPE;       }
        protected int indexData()        {  return INDEX_DATA;            }
        protected int indexId()          {  return INDEX_ID;              }
        protected int indexLatitude()    {  return INDEX_LATITUDE;        }
        protected int indexLongitude()   {  return INDEX_LONGITUDE;       }
        protected int indexMiniThumbId() {  return INDEX_MINI_THUMB_MAGIC;   }
        protected int indexPicasaWeb()   {  return -1;                    }
        protected int indexPrivate()     {  return INDEX_PRIVATE;         }
        protected int indexTitle()       {  return INDEX_TITLE;           }
        protected int indexDisplayName() {  return -1;                    }
        protected int indexThumbId()     {  return INDEX_THUMB_ID;        }

        @Override
        protected IImage make(long id, long miniThumbId, ContentResolver cr, IImageList list,
                long timestamp, int index, int rotation) {
            return new VideoObject(id, miniThumbId, mContentResolver, this, timestamp, index);
        }

        @Override
        protected Bitmap makeBitmap(int targetWidthHeight, Uri uri, ParcelFileDescriptor pfdInput,
                BitmapFactory.Options options) {
            MediaPlayer mp = new MediaPlayer();
            Bitmap thumbnail = sDefaultThumbnail;
            try {
                mp.setDataSource(mContext, uri);
//              int duration = mp.getDuration();
//              int at = duration > 2000 ? 1000 : duration / 2;
                int at = 1000;
                thumbnail = mp.getFrameAt(at);
                if (Config.LOGV) {
                    if ( thumbnail != null) {
                        Log.v(TAG, "getFrameAt @ " + at + " returned " + thumbnail + "; " +
                                thumbnail.getWidth() + " " + thumbnail.getHeight());
                    } else {
                        Log.v(TAG, "getFrame @ " + at + " failed for " + uri);
                    }
                }
            } catch (IOException ex) {
            } catch (IllegalArgumentException ex) {
            } catch (SecurityException ex) {
            } finally {
                mp.release();
            }
            return thumbnail;
        }


        private String sortOrder() {
            return Video.Media.DATE_TAKEN + (mSort == SORT_ASCENDING ? " ASC " : " DESC");
        }
    }

    private final static Bitmap sDefaultThumbnail = Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565);

    /**
     * Represents a particular video and provides access
     * to the underlying data and two thumbnail bitmaps
     * as well as other information such as the id, and
     * the path to the actual video data.
     */
    class VideoObject extends BaseImage implements IImage {
        /**
         * Constructor.
         *
         * @param id        the image id of the image
         * @param cr        the content resolver
         */
        protected VideoObject(long id, long miniThumbId, ContentResolver cr, VideoList container,
                long dateTaken, int row) {
            super(id, miniThumbId, cr, container, row);
        }

        protected Bitmap.CompressFormat compressionType() {
            return Bitmap.CompressFormat.JPEG;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (!(other instanceof VideoObject))
                return false;

            return fullSizeImageUri().equals(((VideoObject)other).fullSizeImageUri());
        }

        public String getDataPath() {
            String path = null;
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    int column = ((VideoList)getContainer()).indexData();
                    if (column >= 0)
                        path = c.getString(column);
                }
            }
            return path;
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#fullSizeBitmap()
         */
        public Bitmap fullSizeBitmap(int targetWidthHeight) {
            return sNoImageBitmap;
        }

        public IGetBitmap_cancelable fullSizeBitmap_cancelable(int targetWidthHeight) {
            return null;
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#fullSizeImageData()
         */
        public InputStream fullSizeImageData() {
            try {
                InputStream input = mContentResolver.openInputStream(
                        fullSizeImageUri());
                return input;
            } catch (IOException ex) {
                return null;
            }
        }

        /* (non-Javadoc)
         * @see com.android.camera.IImage#fullSizeImageId()
         */
        public long fullSizeImageId() {
            return mId;
        }

        public String getCategory() {
             return getStringEntry(((VideoList)mContainer).INDEX_CATEGORY);
         }

        public int getHeight() {
             return 0;
         }

        public String getLanguage() {
             return getStringEntry(((VideoList)mContainer).INDEX_LANGUAGE);
         }

        public String getPicasaId() {
            return null;
        }

        private String getStringEntry(int entryName) {
            String entry = null;
            Cursor c = getCursor();
            synchronized(c) {
                if (c.moveToPosition(getRow())) {
                    entry = c.getString(entryName);
                }
            }
            return entry;
        }

        public String getTags() {
             return getStringEntry(((VideoList)mContainer).INDEX_TAGS);
         }

        public int getWidth() {
             return 0;
         }

         /* (non-Javadoc)
         * @see com.android.camera.IImage#imageId()
         */
        public long imageId() {
            return mId;
        }

         public boolean isReadonly() {
             return false;
         }

         public boolean isDrm() {
             return false;
         }

         public boolean rotateImageBy(int degrees) {
            return false;
        }

         public void setCategory(String category) {
             setStringEntry(category, ((VideoList)mContainer).INDEX_CATEGORY);
         }

         public void setLanguage(String language) {
             setStringEntry(language, ((VideoList)mContainer).INDEX_LANGUAGE);
         }

         private void setStringEntry(String entry, int entryName) {
            Cursor c = getCursor();
            synchronized (c) {
                if (c.moveToPosition(getRow())) {
                    c.updateString(entryName, entry);
                }
            }
        }

         public void setTags(String tags) {
             setStringEntry(tags, ((VideoList)mContainer).INDEX_TAGS);
         }

         /* (non-Javadoc)
         * @see com.android.camera.IImage#thumb1()
         */
        public Bitmap thumbBitmap() {
            return fullSizeBitmap(320);
        }

         @Override
         public String toString() {
             StringBuilder sb = new StringBuilder();
             sb.append("" + mId);
             return sb.toString();
         }
    }

    private final static Bitmap sNoImageBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);

    /*
     * How much quality to use when storing the thumbnail.
     */
    private static ImageManager sInstance = null;
    private static final int MINI_THUMB_TARGET_SIZE = 96;
    private static final int THUMBNAIL_TARGET_SIZE = 320;

    private static final String[] THUMB_PROJECTION = new String[] {
        BaseColumns._ID,           // 0
        Images.Thumbnails.IMAGE_ID,      // 1
        Images.Thumbnails.WIDTH,
        Images.Thumbnails.HEIGHT
    };

    private static Uri sStorageURI   = Images.Media.EXTERNAL_CONTENT_URI;

    private static Uri sThumbURI     = Images.Thumbnails.EXTERNAL_CONTENT_URI;

    private static Uri sVideoStorageURI = Uri.parse("content://media/external/video/media");

    private static Uri sVideoThumbURI = Uri.parse("content://media/external/video/thumbnails");
    /**
     * Returns an ImageList object that contains
     * all of the images.
     * @param cr
     * @param location
     * @param includeImages
     * @param includeVideo
     * @return the singleton ImageList
     */
    static final public int SORT_ASCENDING = 1;

    static final public int SORT_DESCENDING = 2;

    static final public int INCLUDE_IMAGES     = (1 << 0);
    static final public int INCLUDE_DRM_IMAGES = (1 << 1);
    static final public int INCLUDE_VIDEOS     = (1 << 2);

    static public DataLocation getDefaultDataLocation() {
        return DataLocation.EXTERNAL;
    }
    private static int indexOf(String [] array, String s) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(s)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the singleton instance of the ImageManager.
     * @return the ImageManager instance.
     */
    public static ImageManager instance() {
        if (sInstance == null) {
            sInstance = new ImageManager();
        }
        return sInstance;
    }

    /**
     * Creates a byte[] for a given bitmap of the desired size. Recycles the input bitmap.
     */
    static public byte[] miniThumbData(Bitmap source) {
        if (source == null)
            return null;

        Bitmap miniThumbnail = extractMiniThumb(source, MINI_THUMB_TARGET_SIZE,
                MINI_THUMB_TARGET_SIZE);
        java.io.ByteArrayOutputStream miniOutStream = new java.io.ByteArrayOutputStream();
        miniThumbnail.compress(Bitmap.CompressFormat.JPEG, 75, miniOutStream);
        miniThumbnail.recycle();

        try {
            miniOutStream.close();
            byte [] data = miniOutStream.toByteArray();
            return data;
        } catch (java.io.IOException ex) {
            Log.e(TAG, "got exception ex " + ex);
        }
        return null;
    }

    /**
     * Creates a centered bitmap of the desired size. Recycles the input.
     * @param source
     * @return
     */
    static public Bitmap extractMiniThumb(Bitmap source, int width, int height) {
        return extractMiniThumb(source, width, height, true);
    }

    static public Bitmap extractMiniThumb(Bitmap source, int width, int height,
                                          boolean recycle) {
        if (source == null) {
            return null;
        }

        float scale;
        if (source.getWidth() < source.getHeight()) {
            scale = width / (float)source.getWidth();
        } else {
            scale = height / (float)source.getHeight();
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        Bitmap miniThumbnail = ImageLoader.transform(matrix, source,
                width, height, false);

        if (recycle && miniThumbnail != source) {
            source.recycle();
        }
        return miniThumbnail;
    }

    // Rotates the bitmap by the specified degree.
    // If a new bitmap is created, the original bitmap is recycled.
    static Bitmap rotate(Bitmap b, int degrees) {
        if (degrees != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees, (float) b.getWidth() / 2, (float) b.getHeight() / 2);

            try {
                Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            }
        }
        return b;
    }

    public static int roundOrientation(int orientationInput) {
        int orientation = orientationInput;
        if (orientation == -1)
            orientation = 0;

        orientation = orientation % 360;
        int retVal;
        if (orientation < (0*90) + 45) {
            retVal = 0;
        } else if (orientation < (1*90) + 45) {
            retVal = 90;
        } else if (orientation < (2*90) + 45) {
            retVal = 180;
        } else if (orientation < (3*90) + 45) {
            retVal = 270;
        } else {
            retVal = 0;
        }

        if (VERBOSE) Log.v(TAG, "map orientation " + orientationInput + " to " + retVal);
        return retVal;
    }


    /**
     * @return true if the mimetype is an image mimetype.
     */
    public static boolean isImageMimeType(String mimeType) {
        return mimeType.startsWith("image/");
    }

    /**
     * @return true if the mimetype is a video mimetype.
     */
    public static boolean isVideoMimeType(String mimeType) {
        return mimeType.startsWith("video/");
    }

    /**
     * @return true if the image is an image.
     */
    public static boolean isImage(IImage image) {
        return isImageMimeType(image.getMimeType());
    }

    /**
     * @return true if the image is a video.
     */
    public static boolean isVideo(IImage image) {
        return isVideoMimeType(image.getMimeType());
    }

    public Uri addImage(
            final Context ctx,
            final ContentResolver cr,
            final String imageName,
            final String description,
            final long dateTaken,
            final Location location,
            final int orientation,
            final String directory,
            final String filename) {
        ContentValues values = new ContentValues(7);
        values.put(Images.Media.TITLE, imageName);
        values.put(Images.Media.DISPLAY_NAME, imageName);
        values.put(Images.Media.DESCRIPTION, description);
        values.put(Images.Media.DATE_TAKEN, dateTaken);
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.ORIENTATION, orientation);

        File parentFile = new File(directory);
        // Lowercase the path for hashing. This avoids duplicate buckets if the filepath
        // case is changed externally.
        // Keep the original case for display.
        String path = parentFile.toString().toLowerCase();
        String name = parentFile.getName();

        if (VERBOSE) Log.v(TAG, "addImage id is " + path.hashCode() + "; name " + name + "; path is " + path);

        if (location != null) {
            if (VERBOSE) {
                Log.v(TAG, "lat long " + location.getLatitude() + " / " + location.getLongitude());
            }
            values.put(Images.Media.LATITUDE, location.getLatitude());
            values.put(Images.Media.LONGITUDE, location.getLongitude());
        }

        if (directory != null && filename != null) {
            String value = directory + "/" + filename;
            values.put("_data", value);
        }

        long t3 = System.currentTimeMillis();
        Uri uri = cr.insert(sStorageURI, values);

        // The line above will create a filename that ends in .jpg
        // That filename is what will be handed to gmail when a user shares a photo.
        // Gmail gets the name of the picture attachment from the "DISPLAY_NAME" field.
        // Extract the filename and jam it into the display name.
        Cursor c = cr.query(
                uri,
                new String [] { ImageColumns._ID, Images.Media.DISPLAY_NAME, "_data" },
                null,
                null,
                null);
        if (c.moveToFirst()) {
            String filePath = c.getString(2);
            if (filePath != null) {
                int pos = filePath.lastIndexOf("/");
                if (pos >= 0) {
                    filePath = filePath.substring(pos + 1);     // pick off the filename
                    c.updateString(1, filePath);
                    c.commitUpdates();
                }
            }
        }
        c.close();
        return uri;
    }

    public IAddImage_cancelable storeImage(
                           final Uri uri,
                           final Context ctx,
                           final ContentResolver cr,
                           final int orientation,
                           final Bitmap source,
                           final byte [] jpegData) {
        class AddImageCancelable extends BaseCancelable implements IAddImage_cancelable {
            private IGetBoolean_cancelable mSaveImageCancelable;

            public boolean doCancelWork() {
                if (VERBOSE) {
                    Log.v(TAG, "calling AddImageCancelable.cancel() " + mSaveImageCancelable);
                }

                if (mSaveImageCancelable != null) {
                    mSaveImageCancelable.cancel();
                }
                return true;
            }

            public void get() {
                if (source == null && jpegData == null) {
                    throw new IllegalArgumentException("source cannot be null");
                }

                try {
                    long t1 = System.currentTimeMillis();
                    synchronized (this) {
                        if (mCancel) {
                            throw new CanceledException();
                        }
                    }
                    long id = ContentUris.parseId(uri);

                    BaseImageList il = new ImageList(ctx, cr, sStorageURI, sThumbURI, SORT_ASCENDING, null);
                    ImageManager.Image image = new Image(id, 0, cr, il, il.getCount(), 0);
                    long t5 = System.currentTimeMillis();
                    Cursor c = cr.query(
                            uri,
                            new String [] { ImageColumns._ID, ImageColumns.MINI_THUMB_MAGIC, "_data" },
                            null,
                            null,
                            null);
                    c.moveToPosition(0);

                    synchronized (this) {
                        checkCanceled();
                        mSaveImageCancelable = image.saveImageContents(source, jpegData, orientation, true, c);
                    }

                    if (mSaveImageCancelable.get()) {
                        long t6 = System.currentTimeMillis();
                        if (VERBOSE) Log.v(TAG, "saveImageContents took " + (t6-t5));
                        if (VERBOSE) Log.v(TAG, "updating new picture with id " + id);
                        c.updateLong(1, id);
                        c.commitUpdates();
                        c.close();
                        long t7 = System.currentTimeMillis();
                        if (VERBOSE) Log.v(TAG, "commit updates to save mini thumb took " + (t7-t6));
                    }
                    else {
                        c.close();
                        throw new CanceledException();
                    }
                } catch (CanceledException ex) {
                    if (VERBOSE) {
                        Log.v(TAG, "caught CanceledException");
                    }
                    if (uri != null) {
                        if (VERBOSE) {
                            Log.v(TAG, "canceled... cleaning up this uri: " + uri);
                        }
                        cr.delete(uri, null, null);
                    }
                    acknowledgeCancel();
                }
            }
        }
        return new AddImageCancelable();
    }

    static public IImageList makeImageList(Uri uri, Context ctx, int sort) {
        ContentResolver cr = ctx.getContentResolver();
        String uriString = (uri != null) ? uri.toString() : "";
        // TODO we need to figure out whether we're viewing
        // DRM images in a better way.  Is there a constant
        // for content://drm somewhere??
        IImageList imageList;

        if (uriString.startsWith("content://drm")) {
            imageList = ImageManager.instance().allImages(
                    ctx,
                    cr,
                    ImageManager.DataLocation.ALL,
                    ImageManager.INCLUDE_DRM_IMAGES,
                    sort);
        } else if (!uriString.startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
            && !uriString.startsWith(MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString())) {
            imageList = ImageManager.instance().new SingleImageList(cr, uri);
        } else {
            String bucketId = uri.getQueryParameter("bucketId");
            if (VERBOSE) Log.v(TAG, "bucketId is " + bucketId);
            imageList = ImageManager.instance().allImages(
                ctx,
                cr,
                ImageManager.DataLocation.ALL,
                ImageManager.INCLUDE_IMAGES,
                sort,
                bucketId);
        }
        return imageList;
    }

    public IImageList emptyImageList() {
        return
        new IImageList() {
            public void checkThumbnails(ImageManager.IImageList.ThumbCheckCallback cb,
                    int totalThumbnails) {
            }

            public void commitChanges() {
            }

            public void deactivate() {
            }

            public HashMap<String, String> getBucketIds() {
                return new HashMap<String,String>();
            }

            public int getCount() {
                return 0;
            }

            public boolean isEmpty() {
                return true;
            }

            public IImage getImageAt(int i) {
                return null;
            }

            public IImage getImageForUri(Uri uri) {
                return null;
            }

            public boolean removeImage(IImage image) {
                return false;
            }

            public void removeImageAt(int i) {
            }

            public void removeOnChangeListener(ImageManager.IImageList.OnChange changeCallback) {
            }

            public void setOnChangeListener(ImageManager.IImageList.OnChange changeCallback,
                    Handler h) {
            }

        };
    }

    public IImageList allImages(Context ctx, ContentResolver cr, DataLocation location, int inclusion, int sort) {
        return allImages(ctx, cr, location, inclusion, sort, null, null);
    }

    public IImageList allImages(Context ctx, ContentResolver cr, DataLocation location, int inclusion, int sort, String bucketId) {
        return allImages(ctx, cr, location, inclusion, sort, bucketId, null);
    }

    public IImageList allImages(Context ctx, ContentResolver cr, DataLocation location, int inclusion, int sort, String bucketId, Uri specificImageUri) {
        if (VERBOSE) {
            Log.v(TAG, "allImages " + location + " " + ((inclusion&INCLUDE_IMAGES)!=0) + " + v=" + ((inclusion&INCLUDE_VIDEOS)!=0));
        }

        if (cr == null) {
            return null;
        } else {
            // false ==> don't require write access
            boolean haveSdCard = hasStorage(false);

            if (true) {
                // use this code to merge videos and stills into the same list
                ArrayList<IImageList> l = new ArrayList<IImageList>();

                if (VERBOSE) {
                    Log.v(TAG, "initializing ... haveSdCard == " + haveSdCard + "; inclusion is " + String.format("%x", inclusion));
                }
                if (specificImageUri != null) {
                    try {
                        if (specificImageUri.getScheme().equalsIgnoreCase("content"))
                            l.add(new ImageList(ctx, cr, specificImageUri, sThumbURI, sort, bucketId));
                        else
                            l.add(new SingleImageList(cr, specificImageUri));
                    } catch (UnsupportedOperationException ex) {
                    }
                } else {
                    if (haveSdCard && location != DataLocation.INTERNAL) {
                        if ((inclusion & INCLUDE_IMAGES) != 0) {
                            try {
                                l.add(new ImageList(ctx, cr, sStorageURI, sThumbURI, sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                            }
                        }
                        if ((inclusion & INCLUDE_VIDEOS) != 0) {
                            try {
                                l.add(new VideoList(ctx, cr, sVideoStorageURI, sVideoThumbURI, sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                            }
                        }
                    }
                    if (location == DataLocation.INTERNAL || location == DataLocation.ALL) {
                        if ((inclusion & INCLUDE_IMAGES) != 0) {
                            try {
                                l.add(new ImageList(ctx, cr, Images.Media.INTERNAL_CONTENT_URI,
                                        Images.Thumbnails.INTERNAL_CONTENT_URI, sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                            }
                        }
                        if ((inclusion & INCLUDE_DRM_IMAGES) != 0) {
                            try {
                                l.add(new DrmImageList(ctx, cr, DrmStore.Images.CONTENT_URI, sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                            }
                        }
                    }
                }

                IImageList [] imageList = l.toArray(new IImageList[l.size()]);
                return new ImageListUber(imageList, sort);
            } else {
                if (haveSdCard && location != DataLocation.INTERNAL) {
                    return new ImageList(ctx, cr, sStorageURI, sThumbURI, sort, bucketId);
                } else  {
                    return new ImageList(ctx, cr, Images.Media.INTERNAL_CONTENT_URI,
                            Images.Thumbnails.INTERNAL_CONTENT_URI, sort, bucketId);
                }
            }
        }
    }

    // Create a temporary file to see whether a volume is really writeable. It's important not to
    // put it in the root directory which may have a limit on the number of files.
    static private boolean checkFsWritable() {
        String directoryName = Environment.getExternalStorageDirectory().toString() + "/DCIM";
        File directory = new File(directoryName);
        if (!directory.isDirectory()) {
            if (!directory.mkdirs()) {
                return false;
            }
        }
        File f = new File(directoryName, ".probe");
        try {
            // Remove stale file if any
            if (f.exists()) {
                f.delete();
            }
            if (!f.createNewFile())
                return false;
            f.delete();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    static public boolean hasStorage() {
        return hasStorage(true);
    }

    static public boolean hasStorage(boolean requireWriteAccess) {
        //TODO: After fix the bug,  add "if (VERBOSE)" before logging errors.
        String state = Environment.getExternalStorageState();
        Log.v(TAG, "storage state is " + state);

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (requireWriteAccess) {
                boolean writable = checkFsWritable();
                Log.v(TAG, "storage writable is " + writable);
                return writable;
            } else {
                return true;
            }
        } else if (!requireWriteAccess && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public static Cursor query(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return null;
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
         } catch (UnsupportedOperationException ex) {
            return null;
        }

    }

    public static boolean isMediaScannerScanning(Context context) {
        boolean result = false;
        Cursor cursor = query(context, MediaStore.getMediaScannerUri(),
                new String [] { MediaStore.MEDIA_SCANNER_VOLUME }, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }

        if (VERBOSE)
            Log.v(TAG, ">>>>>>>>>>>>>>>>>>>>>>>>> isMediaScannerScanning returning " + result);
        return result;
    }

    /**
     * Create a video thumbnail for a video. May return null if the video is corrupt.
     * @param filePath
     * @return
     */
    public static Bitmap createVideoThumbnail(String filePath) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setMode(MediaMetadataRetriever.MODE_CAPTURE_FRAME_ONLY);
            retriever.setDataSource(filePath);
            bitmap = retriever.captureFrame();
        } catch(IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        return bitmap;
    }

    public static String getLastImageThumbPath() {
        return Environment.getExternalStorageDirectory().toString() +
               "/DCIM/.thumbnails/image_last_thumb";
    }

    public static String getLastVideoThumbPath() {
        return Environment.getExternalStorageDirectory().toString() +
               "/DCIM/.thumbnails/video_last_thumb";
    }
}
