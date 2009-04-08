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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;

import com.android.camera.ExifInterface;
import com.android.camera.ImageManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A collection of <code>BaseImage</code>s.
 */
public abstract class BaseImageList implements IImageList {
    private static final boolean VERBOSE = false;
    private static final String TAG = "BaseImageList";

    private static final int MINI_THUMB_TARGET_SIZE = 96;
    private static final int THUMBNAIL_TARGET_SIZE = 320;
    private static final int MINI_THUMB_DATA_FILE_VERSION = 3;

    private static final String WHERE_CLAUSE =
            "(" + Images.Media.MIME_TYPE + " in (?, ?, ?))";

    static final String[] IMAGE_PROJECTION = new String[] {
            "_id",
            "_data",
            ImageColumns.DATE_TAKEN,
            ImageColumns.MINI_THUMB_MAGIC,
            ImageColumns.ORIENTATION,
            ImageColumns.MIME_TYPE};

    static final String[] THUMB_PROJECTION = new String[] {
            BaseColumns._ID,           // 0
            Images.Thumbnails.IMAGE_ID,      // 1
            Images.Thumbnails.WIDTH,
            Images.Thumbnails.HEIGHT};

    static final int INDEX_ID = Util.indexOf(IMAGE_PROJECTION, "_id");
    static final int INDEX_DATA = Util.indexOf(IMAGE_PROJECTION, "_data");
    static final int INDEX_MIME_TYPE =
            Util.indexOf(IMAGE_PROJECTION, MediaColumns.MIME_TYPE);
    static final int INDEX_DATE_TAKEN =
            Util.indexOf(IMAGE_PROJECTION, ImageColumns.DATE_TAKEN);
    static final int INDEX_MINI_THUMB_MAGIC =
            Util.indexOf(IMAGE_PROJECTION, ImageColumns.MINI_THUMB_MAGIC);
    static final int INDEX_ORIENTATION =
            Util.indexOf(IMAGE_PROJECTION, ImageColumns.ORIENTATION);
    static final int INDEX_THUMB_ID =
            Util.indexOf(THUMB_PROJECTION, BaseColumns._ID);
    static final int INDEX_THUMB_IMAGE_ID =
            Util.indexOf(THUMB_PROJECTION, Images.Thumbnails.IMAGE_ID);
    static final int INDEX_THUMB_WIDTH =
            Util.indexOf(THUMB_PROJECTION, Images.Thumbnails.WIDTH);
    static final int INDEX_THUMB_HEIGHT =
            Util.indexOf(THUMB_PROJECTION, Images.Thumbnails.HEIGHT);

    protected static final String[] ACCEPTABLE_IMAGE_TYPES =
            new String[] { "image/jpeg", "image/png", "image/gif" };
    protected static final String MINITHUMB_IS_NULL = "mini_thumb_magic isnull";

    protected ContentResolver mContentResolver;
    protected int mSort;
    protected Uri mBaseUri;
    protected Cursor mCursor;
    protected IImageList.OnChange mListener = null;
    protected boolean mCursorDeactivated;
    protected String mBucketId;
    protected Context mContext;
    protected Uri mUri;
    protected HashMap<Long, IImage> mCache = new HashMap<Long, IImage>();
    protected RandomAccessFile mMiniThumbData;
    protected Uri mThumbUri;

    public BaseImageList(Context ctx, ContentResolver cr, Uri uri, int sort,
            String bucketId) {
        mContext = ctx;
        mSort = sort;
        mUri = uri;
        mBaseUri = uri;
        mBucketId = bucketId;
        mContentResolver = cr;
    }

    String randomAccessFilePath(int version) {
        String directoryName =
                Environment.getExternalStorageDirectory().toString()
                + "/DCIM/.thumbnails";
        return directoryName + "/.thumbdata" + version + "-" + mUri.hashCode();
    }

    RandomAccessFile miniThumbDataFile() {
        if (mMiniThumbData == null) {
            String path = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION);
            File directory = new File(new File(path).getParent());
            if (!directory.isDirectory()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "!!!! unable to create .thumbnails directory "
                            + directory.toString());
                }
            }
            File f = new File(path);
            if (VERBOSE) Log.v(TAG, "file f is " + f.toString());
            try {
                mMiniThumbData = new RandomAccessFile(f, "rw");
            } catch (IOException ex) {
                // ignore exception
            }
        }
        return mMiniThumbData;
    }

    /**
     * Store a given thumbnail in the database.
     */
    protected Bitmap storeThumbnail(Bitmap thumb, long imageId) {
        if (thumb == null) return null;
        try {
            Uri uri = getThumbnailUri(
                    imageId, thumb.getWidth(), thumb.getHeight());
            if (uri == null) {
                return thumb;
            }
            OutputStream thumbOut = mContentResolver.openOutputStream(uri);
            thumb.compress(Bitmap.CompressFormat.JPEG, 60, thumbOut);
            thumbOut.close();
            return thumb;
        } catch (Exception ex) {
            if (VERBOSE) Log.d(TAG, "unable to store thumbnail: " + ex);
            return thumb;
        }
    }

    /**
     * Store a JPEG thumbnail from the EXIF header in the database.
     */
    protected boolean storeThumbnail(
            byte[] jpegThumbnail, long imageId, int width, int height) {
        if (jpegThumbnail == null) return false;

        Uri uri = getThumbnailUri(imageId, width, height);
        if (uri == null) {
            return false;
        }
        try {
            OutputStream thumbOut = mContentResolver.openOutputStream(uri);
            thumbOut.write(jpegThumbnail);
            thumbOut.close();
            return true;
        } catch (FileNotFoundException ex) {
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    private Uri getThumbnailUri(long imageId, int width, int height) {
        // we do not store thumbnails for DRM'd images
        if (mThumbUri == null) {
            return null;
        }

        Uri uri = null;
        Cursor c = mContentResolver.query(mThumbUri, THUMB_PROJECTION,
                Thumbnails.IMAGE_ID + "=?",
                new String[]{String.valueOf(imageId)}, null);
        try {
            if (c.moveToFirst()) {
                // If, for some reaosn, we already have a row with a matching
                // image id, then just update that row rather than creating a
                // new row.
                uri = ContentUris.withAppendedId(
                        mThumbUri, c.getLong(indexThumbId()));
                c.commitUpdates();
            }
        } finally {
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

    private static final java.util.Random sRandom =
            new java.util.Random(System.currentTimeMillis());

    protected SomewhatFairLock mLock = new SomewhatFairLock();

    private static class SomewhatFairLock {
        private boolean mLocked = false;
        private ArrayList<Thread> mWaiting = new ArrayList<Thread>();

        public synchronized void lock() {
            while (mLocked) {
                try {
                    mWaiting.add(Thread.currentThread());
                    wait();
                    if (mWaiting.get(0) == Thread.currentThread()) {
                        mWaiting.remove(0);
                        break;
                    }
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            mLocked = true;
        }

        public synchronized void unlock() {
            mLocked = false;
            notifyAll();
        }
    }

    // If the photo has an EXIF thumbnail and it's big enough, extract it and
    // save that JPEG as the large thumbnail without re-encoding it. We still
    // have to decompress it though, in order to generate the minithumb.
    private Bitmap createThumbnailFromEXIF(String filePath, long id) {
        if (filePath == null) return null;

        byte [] thumbData = null;
        synchronized (ImageManager.instance()) {
            thumbData = (new ExifInterface(filePath)).getThumbnail();
        }
        if (thumbData == null) return null;

        // Sniff the size of the EXIF thumbnail before decoding it. Photos
        // from the device will pass, but images that are side loaded from
        // other cameras may not.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(thumbData, 0, thumbData.length, options);
        int width = options.outWidth;
        int height = options.outHeight;
        if (width >= THUMBNAIL_TARGET_SIZE && height >= THUMBNAIL_TARGET_SIZE
                && storeThumbnail(thumbData, id, width, height)) {
            // this is used for *encoding* the minithumb, so
            // we don't want to dither or convert to 565 here.
            //
            // Decode with a scaling factor
            // to match MINI_THUMB_TARGET_SIZE closely
            // which will produce much better scaling quality
            // and is significantly faster.
            options.inSampleSize =
                    Util.computeSampleSize(options, THUMBNAIL_TARGET_SIZE);

            if (VERBOSE) {
                Log.v(TAG, "in createThumbnailFromExif using inSampleSize of "
                        + options.inSampleSize);
            }
            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(
                    thumbData, 0, thumbData.length, options);
        }
        return null;
    }

    // The fallback case is to decode the original photo to thumbnail size,
    // then encode it as a JPEG. We return the thumbnail Bitmap in order to
    // create the minithumb from it.
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
    public long checkThumbnail(BaseImage existingImage, Cursor c, int i)
            throws IOException {
        return checkThumbnail(existingImage, c, i, null);
    }

    /**
     * Checks to see if a mini thumbnail exists in the cache. If not, tries to
     * create it and add it to the cache.
     * @param existingImage
     * @param c
     * @param i
     * @param createdThumbnailData if this parameter is non-null, and a new
     *         mini-thumbnail bitmap is created, the new bitmap's data will be
     *         stored in createdThumbnailData[0]. Note that if the sdcard is
     *         full, it's possible that createdThumbnailData[0] will be set
     *         even if the method throws an IOException. This is actually
     *         useful, because it allows the caller to use the created
     *         thumbnail even if the sdcard is full.
     * @throws IOException
     */
    public long checkThumbnail(BaseImage existingImage, Cursor c, int i,
            byte[][] createdThumbnailData) throws IOException {
        long magic, fileMagic = 0, id;
        mLock.lock();
        try {
            if (existingImage == null) {
                // if we don't have an Image object then get the id and magic
                // from the cursor.  Synchronize on the cursor object.
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
                // check the mini thumb file for the right data.  Right is
                // defined as having the right magic number at the offset
                // reserved for this "id".
                RandomAccessFile r = miniThumbDataFile();
                if (r != null) {
                    synchronized (r) {
                        long pos = id * BaseImage.BYTES_PER_MINTHUMB;
                        try {
                            // check that we can read the following 9 bytes
                            // (1 for the "status" and 8 for the long)
                            if (r.length() >= pos + 1 + 8) {
                                r.seek(pos);
                                if (r.readByte() == 1) {
                                    fileMagic = r.readLong();
                                    if (fileMagic == magic && magic != 0
                                            && magic != id) {
                                        return magic;
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            Log.v(TAG, "got exception checking file magic: "
                                    + ex);
                        }
                    }
                }
                if (VERBOSE) {
                    Log.v(TAG, "didn't verify... fileMagic: " + fileMagic
                            + "; magic: " + magic + "; id: " + id + "; ");
                }
            }

            // If we can't retrieve the thumbnail, first check if there is one
            // embedded in the EXIF data. If not, or it's not big enough,
            // decompress the full size image.
            Bitmap bitmap = null;
            String filePath = null;
            synchronized (c) {
                if (c.moveToPosition(i)) {
                    filePath = c.getString(indexData());
                }
            }
            if (filePath != null) {
                String mimeType = c.getString(indexMimeType());
                boolean isVideo = Util.isVideoMimeType(mimeType);
                if (isVideo) {
                    bitmap = Util.createVideoThumbnail(filePath);
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
                        if (column >= 0) degrees = c.getInt(column);
                    }
                    if (degrees != 0) {
                        bitmap = Util.rotate(bitmap, degrees);
                    }
                }
            }

            // make a new magic number since things are out of sync
            do {
                magic = sRandom.nextLong();
            } while (magic == 0);
            if (bitmap != null) {
                byte [] data = Util.miniThumbData(bitmap);
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
        Cursor c = Images.Media.query(mContentResolver, mBaseUri,
                new String[] { "_id", "mini_thumb_magic" },
                thumbnailWhereClause(), thumbnailWhereClauseArgs(),
                "_id ASC");

        int count = c.getCount();
        if (VERBOSE) {
            Log.v(TAG, ">>>>>>>>>>> need to check " + c.getCount() + " rows");
        }
        c.close();

        if (!ImageManager.hasStorage()) {
            if (VERBOSE) {
                Log.v(TAG, "bailing from the image checker thread "
                        + "-- no storage");
            }
            return;
        }

        String oldPath = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION - 1);
        File oldFile = new File(oldPath);

        if (count == 0) {
            // now check that we have the right thumbs file
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
                } catch (IOException ex) {
                    Log.e(TAG, "!!!!! failed to check thumbnail..."
                            + " was the sd card removed? - " + ex.getMessage());
                    break;
                }
                if (cb != null) {
                    if (!cb.checking(current, totalThumbnails)) {
                        if (VERBOSE) {
                            Log.v(TAG, "got false from checking... break");
                        }
                        break;
                    }
                }
                current += 1;
            }
        } finally {
            if (VERBOSE) {
                Log.v(TAG, "checkThumbnails existing after reaching count "
                        + c.getCount());
            }
            try {
                oldFile.delete();
            } catch (SecurityException ex) {
                // ignore
            }
        }
    }

    protected String thumbnailWhereClause() {
        return MINITHUMB_IS_NULL + " and " + WHERE_CLAUSE;
    }

    protected String[] thumbnailWhereClauseArgs() {
        return ACCEPTABLE_IMAGE_TYPES;
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
            if (existingId != id) Log.e(TAG, "id mismatch");
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
                // ignore exception
            }
        }
    }

    public void dump(String msg) {
        int count = getCount();
        if (VERBOSE) {
            Log.v(TAG, "dump ImageList (count is " + count + ") " + msg);
        }
        for (int i = 0; i < count; i++) {
            IImage img = getImageAt(i);
            if (VERBOSE) Log.v(TAG, "   " + i + ": " + img);
        }
        if (VERBOSE) Log.v(TAG, "end of dump container");
    }

    public int getCount() {
        Cursor c = getCursor();
        synchronized (c) {
            try {
                return c.getCount();
            } catch (RuntimeException ex) {
                return 0;
            }
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
            } catch (RuntimeException ex) {
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
                        img = make(id, miniThumbId, mContentResolver, this,
                                timestamp, i, rotation);
                        mCache.put(id, img);
                    }
                    return img;
                } catch (RuntimeException ex) {
                    Log.e(TAG, "got this exception trying to create image: "
                            + ex);
                    return null;
                }
            } else {
                Log.e(TAG, "unable to moveTo to " + i + "; count is "
                        + c.getCount());
                return null;
            }
        }
    }

    public IImage getImageForUri(Uri uri) {
        // TODO: make this a hash lookup
        for (int i = 0; i < getCount(); i++) {
            if (getImageAt(i).fullSizeImageUri().equals(uri)) {
                return getImageAt(i);
            }
        }
        return null;
    }

    byte [] getMiniThumbFromFile(long id, byte [] data, long magicCheck) {
        RandomAccessFile r = miniThumbDataFile();
        if (r == null) return null;

        long pos = id * BaseImage.BYTES_PER_MINTHUMB;
        synchronized (r) {
            try {
                r.seek(pos);
                if (r.readByte() == 1) {
                    long magic = r.readLong();
                    if (magic != magicCheck) {
                        if (VERBOSE) {
                            Log.v(TAG, "for id " + id + "; magic: " + magic
                                    + "; magicCheck: " + magicCheck
                                    + " (fail)");
                        }
                        return null;
                    }
                    int length = r.readInt();
                    r.read(data, 0, length);
                    return data;
                } else {
                    return null;
                }
            } catch (IOException ex) {
                long fileLength;
                try {
                    fileLength = r.length();
                } catch (IOException ex1) {
                    fileLength = -1;
                }
                if (VERBOSE) {
                    Log.e(TAG, "couldn't read thumbnail for " + id + "; "
                            + ex.toString() + "; pos is " + pos + "; length is "
                            + fileLength);
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

    protected abstract int indexTitle();

    protected abstract int indexDisplayName();

    protected abstract int indexThumbId();

    protected IImage make(long id, long miniThumbId, ContentResolver cr,
            IImageList list, long timestamp, int index, int rotation) {
        return null;
    }

    protected abstract Bitmap makeBitmap(
            int targetWidthHeight, Uri uri, ParcelFileDescriptor pfdInput,
            BitmapFactory.Options options);

    public boolean removeImage(IImage image) {
        Cursor c = getCursor();
        synchronized (c) {
            /*
             * TODO: consider putting the image in a holding area so
             *       we can get it back as needed
             * TODO: need to delete the thumbnails as well
             */
            boolean moved;
            try {
                moved = c.moveToPosition(image.getRow());
            } catch (RuntimeException ex) {
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

    public void removeImageAt(int i) {
        Cursor c = getCursor();
        synchronized (c) {
            /*
             * TODO: consider putting the image in a holding area so
             *       we can get it back as needed
             * TODO: need to delete the thumbnails as well
             */
            dump("before delete");
            IImage image = getImageAt(i);
            boolean moved;
            try {
                moved = c.moveToPosition(i);
            } catch (RuntimeException ex) {
                Log.e(TAG, "removeImageAt " + i + " get " + ex);
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
        if (changeCallback == mListener) mListener = null;
    }

    protected void requery() {
        mCache.clear();
        mCursor.requery();
        mCursorDeactivated = false;
    }

    protected void saveMiniThumbToFile(Bitmap bitmap, long id, long magic)
            throws IOException {
        byte[] data = Util.miniThumbData(bitmap);
        saveMiniThumbToFile(data, id, magic);
    }

    protected void saveMiniThumbToFile(byte[] data, long id, long magic)
            throws IOException {
        RandomAccessFile r = miniThumbDataFile();
        if (r == null) return;

        long pos = id * BaseImage.BYTES_PER_MINTHUMB;
        long t0 = System.currentTimeMillis();
        synchronized (r) {
            try {
                long t1 = System.currentTimeMillis();
                long t2 = System.currentTimeMillis();
                if (data != null) {
                    if (data.length > BaseImage.BYTES_PER_MINTHUMB) {
                        if (VERBOSE) {
                            Log.v(TAG, "warning: " + data.length + " > "
                                    + BaseImage.BYTES_PER_MINTHUMB);
                        }
                        return;
                    }
                    r.seek(pos);
                    r.writeByte(0);     // we have no data in this slot

                    // if magic is 0 then leave it alone
                    if (magic == 0) {
                        r.skipBytes(8);
                    } else {
                        r.writeLong(magic);
                    }
                    r.writeInt(data.length);
                    r.write(data);
                    //                      f.flush();
                    r.seek(pos);
                    r.writeByte(1);  // we have data in this slot
                    long t3 = System.currentTimeMillis();

                    if (VERBOSE) {
                        Log.v(TAG, "saveMiniThumbToFile took " + (t3 - t0)
                                + "; " + (t1 - t0) + " " + (t2 - t1) + " "
                                + (t3 - t2));
                    }
                }
            } catch (IOException ex) {
                Log.e(TAG, "couldn't save mini thumbnail data for "
                        + id + "; " + ex.toString());
                throw ex;
            }
        }
    }

    public void setOnChangeListener(OnChange changeCallback, Handler h) {
        mListener = changeCallback;
    }
}
