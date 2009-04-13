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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.DrmStore;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Config;
import android.util.Log;

import com.android.camera.gallery.BaseCancelable;
import com.android.camera.gallery.BaseImageList;
import com.android.camera.gallery.CanceledException;
import com.android.camera.gallery.DrmImageList;
import com.android.camera.gallery.IAddImageCancelable;
import com.android.camera.gallery.IGetBooleanCancelable;
import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.gallery.Image;
import com.android.camera.gallery.ImageList;
import com.android.camera.gallery.ImageListUber;
import com.android.camera.gallery.SingleImageList;
import com.android.camera.gallery.Util;
import com.android.camera.gallery.VideoList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * ImageManager is used to retrieve and store images
 * in the media content provider.
 */
public class ImageManager {
    // To enable verbose logging for this class, change false to true. The other
    // logic ensures that this logging can be disabled by turned off DEBUG and
    // lower, and that it can be enabled by "setprop log.tag.ImageManager
    // VERBOSE" if desired.
    //
    // IMPORTANT: Never check in this file set to true!
    private static final boolean VERBOSE =
            Config.LOGD && (false || Config.LOGV);
    private static final String TAG = "ImageManager";
    private static ImageManager sInstance = null;

    private static Uri sStorageURI = Images.Media.EXTERNAL_CONTENT_URI;
    private static Uri sThumbURI = Images.Thumbnails.EXTERNAL_CONTENT_URI;

    private static Uri sVideoStorageURI =
            Uri.parse("content://media/external/video/media");

    private static Uri sVideoThumbURI =
            Uri.parse("content://media/external/video/thumbnails");

    /**
     * Enumerate type for the location of the images in gallery.
     */
    public static enum DataLocation { NONE, INTERNAL, EXTERNAL, ALL }

    public static final Bitmap DEFAULT_THUMBNAIL =
            Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565);
    public static final Bitmap NO_IMAGE_BITMAP =
            Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);

    public static final int SORT_ASCENDING = 1;
    public static final int SORT_DESCENDING = 2;

    public static final int INCLUDE_IMAGES = (1 << 0);
    public static final int INCLUDE_DRM_IMAGES = (1 << 1);
    public static final int INCLUDE_VIDEOS = (1 << 2);

    public static final String CAMERA_IMAGE_BUCKET_NAME =
            Environment.getExternalStorageDirectory().toString()
            + "/DCIM/Camera";
    public static final String CAMERA_IMAGE_BUCKET_ID =
            getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    public static final int MINI_THUMB_TARGET_SIZE = 96;
    public static final int THUMBNAIL_TARGET_SIZE = 320;

    /**
     * Matches code in MediaProvider.computeBucketValues. Should be a common
     * function.
     */
    public static String getBucketId(String path) {
        return String.valueOf(path.toLowerCase().hashCode());
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatibleFolder() {
        File nnnAAAAA = new File(
            Environment.getExternalStorageDirectory().toString()
            + "/DCIM/100ANDRO");
        if ((!nnnAAAAA.exists()) && (!nnnAAAAA.mkdir())) {
            Log.e(TAG, "create NNNAAAAA file: " + nnnAAAAA.getPath()
                    + " failed");
        }
    }

    public static void debugWhere(String tag, String msg) {
        Exception ex = new Exception();
        if (msg != null) {
            Log.v(tag, msg);
        }
        boolean first = true;
        for (StackTraceElement s : ex.getStackTrace()) {
            if (first) {
                first = false;
            } else {
                Log.v(tag, s.toString());
            }
        }
    }

    public static DataLocation getDefaultDataLocation() {
        return DataLocation.EXTERNAL;
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

    public static int roundOrientation(int orientationInput) {
        int orientation = orientationInput;
        if (orientation == -1) {
            orientation = 0;
        }

        orientation = orientation % 360;
        int retVal;
        if (orientation < (0 * 90) + 45) {
            retVal = 0;
        } else if (orientation < (1 * 90) + 45) {
            retVal = 90;
        } else if (orientation < (2 * 90) + 45) {
            retVal = 180;
        } else if (orientation < (3 * 90) + 45) {
            retVal = 270;
        } else {
            retVal = 0;
        }

        if (VERBOSE) {
            Log.v(TAG, "map orientation " + orientationInput + " to " + retVal);
        }
        return retVal;
    }

    /**
     * @return true if the mimetype is an image mimetype.
     */
    public static boolean isImageMimeType(String mimeType) {
        return mimeType.startsWith("image/");
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
        return Util.isVideoMimeType(image.getMimeType());
    }

    public Uri addImage(Context ctx, ContentResolver cr, String title,
            long dateTaken, Location location,
            int orientation, String directory, String filename) {

        ContentValues values = new ContentValues(7);
        values.put(Images.Media.TITLE, title);
        values.put(Images.Media.DISPLAY_NAME, title);
        values.put(Images.Media.DATE_TAKEN, dateTaken);
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.ORIENTATION, orientation);

        File parentFile = new File(directory);

        // Lowercase the path for hashing. This avoids duplicate buckets if the
        // filepath case is changed externally.
        // Keep the original case for display.
        String path = parentFile.toString().toLowerCase();
        String name = parentFile.getName();

        if (VERBOSE) {
            Log.v(TAG, "addImage id is " + path.hashCode() + "; name "
                    + name + "; path is " + path);
        }

        if (location != null) {
            if (VERBOSE) {
                Log.v(TAG, "lat long " + location.getLatitude() + " / "
                        + location.getLongitude());
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
        // That filename is what will be handed to gmail when a user shares a
        // photo. Gmail gets the name of the picture attachment from the
        // "DISPLAY_NAME" field. Extract the filename and jam it into the
        // display name.
        String projection[] = new String [] {
                ImageColumns._ID, Images.Media.DISPLAY_NAME, "_data"};
        Cursor c = cr.query(uri, projection, null, null, null);

        if (c.moveToFirst()) {
            String filePath = c.getString(2);
            if (filePath != null) {
                int pos = filePath.lastIndexOf("/");
                if (pos >= 0) {
                    // pick off the filename
                    filePath = filePath.substring(pos + 1);
                    c.updateString(1, filePath);
                    c.commitUpdates();
                }
            }
        }
        c.close();
        return uri;
    }

    private static class AddImageCancelable extends BaseCancelable
            implements IAddImageCancelable {
        private IGetBooleanCancelable mSaveImageCancelable;
        private Uri mUri;
        private Context mCtx;
        private ContentResolver mCr;
        private int mOrientation;
        private Bitmap mSource;
        private byte [] mJpegData;

        public AddImageCancelable(Uri uri, Context ctx, ContentResolver cr,
                int orientation, Bitmap source, byte[] jpegData) {
            mUri = uri;
            mCtx = ctx;
            mCr = cr;
            mOrientation = orientation;
            mSource = source;
            mJpegData = jpegData;
        }

        @Override
        public boolean doCancelWork() {
            if (VERBOSE) {
                Log.v(TAG, "calling AddImageCancelable.cancel() "
                        + mSaveImageCancelable);
            }
            if (mSaveImageCancelable != null) {
                mSaveImageCancelable.cancel();
            }
            return true;
        }

        public void get() {
            if (mSource == null && mJpegData == null) {
                throw new IllegalArgumentException("source cannot be null");
            }

            try {
                long t1 = System.currentTimeMillis();
                synchronized (this) {
                    if (mCancel) {
                        throw new CanceledException();
                    }
                }
                long id = ContentUris.parseId(mUri);

                BaseImageList il = new ImageList(mCtx, mCr, sStorageURI,
                        sThumbURI, SORT_ASCENDING, null);
                Image image = new Image(id, 0, mCr, il, il.getCount(), 0);
                long t5 = System.currentTimeMillis();
                String[] projection = new String[] {
                        ImageColumns._ID,
                        ImageColumns.MINI_THUMB_MAGIC, "_data"};

                Cursor c = mCr.query(mUri, projection, null, null, null);
                c.moveToPosition(0);

                synchronized (this) {
                    checkCanceled();
                    mSaveImageCancelable = image.saveImageContents(
                            mSource, mJpegData, mOrientation, true, c);
                }

                if (mSaveImageCancelable.get()) {
                    long t6 = System.currentTimeMillis();
                    if (VERBOSE) {
                        Log.v(TAG, "saveImageContents took " + (t6 - t5));
                        Log.v(TAG, "updating new picture with id " + id);
                    }
                    c.updateLong(1, id);
                    c.commitUpdates();
                    c.close();
                    long t7 = System.currentTimeMillis();
                    if (VERBOSE) {
                        Log.v(TAG, "commit updates to save mini thumb took "
                                + (t7 - t6));
                    }
                } else {
                    c.close();
                    throw new CanceledException();
                }
            } catch (CanceledException ex) {
                if (VERBOSE) {
                    Log.v(TAG, "caught CanceledException");
                }
                if (mUri != null) {
                    if (VERBOSE) {
                        Log.v(TAG, "canceled... cleaning up this uri: " + mUri);
                    }
                    mCr.delete(mUri, null, null);
                }
                acknowledgeCancel();
            }
        }
    }

    public IAddImageCancelable storeImage(
            Uri uri, Context ctx, ContentResolver cr, int orientation,
            Bitmap source, byte [] jpegData) {
        return new AddImageCancelable(
                uri, ctx, cr, orientation, source, jpegData);
    }

    public static IImageList makeImageList(Uri uri, Context ctx, int sort) {
        ContentResolver cr = ctx.getContentResolver();
        String uriString = (uri != null) ? uri.toString() : "";

        // TODO: we need to figure out whether we're viewing
        // DRM images in a better way.  Is there a constant
        // for content://drm somewhere??
        IImageList imageList;

        if (uriString.startsWith("content://drm")) {
            imageList = ImageManager.instance().allImages(
                    ctx, cr, ImageManager.DataLocation.ALL,
                    ImageManager.INCLUDE_DRM_IMAGES, sort);
        } else if (isSingleImageMode(uriString)) {
            imageList = new SingleImageList(cr, uri);
        } else {
            String bucketId = uri.getQueryParameter("bucketId");
            if (VERBOSE) {
                Log.v(TAG, "bucketId is " + bucketId);
            }
            imageList = ImageManager.instance().allImages(
                ctx, cr, ImageManager.DataLocation.ALL,
                ImageManager.INCLUDE_IMAGES, sort, bucketId);
        }
        return imageList;
    }

    private static boolean isSingleImageMode(String uriString) {
        return !uriString.startsWith(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
                && !uriString.startsWith(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString());
    }

    private static class EmptyImageList implements IImageList {
        public void checkThumbnails(IImageList.ThumbCheckCallback cb,
                int totalThumbnails) {
        }

        public void commitChanges() {
        }

        public void deactivate() {
        }

        public HashMap<String, String> getBucketIds() {
            return new HashMap<String, String>();
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
    }

    public IImageList emptyImageList() {
        return new EmptyImageList();
    }

    public IImageList allImages(Context ctx, ContentResolver cr,
            DataLocation location, int inclusion, int sort) {
        return allImages(ctx, cr, location, inclusion, sort, null, null);
    }

    public IImageList allImages(Context ctx, ContentResolver cr,
            DataLocation location, int inclusion, int sort, String bucketId) {
        return allImages(ctx, cr, location, inclusion, sort, bucketId, null);
    }

    public IImageList allImages(
            Context ctx, ContentResolver cr, DataLocation location,
            int inclusion, int sort, String bucketId, Uri specificImageUri) {
        if (VERBOSE) {
            Log.v(TAG, "allImages " + location + " "
                    + ((inclusion & INCLUDE_IMAGES) != 0) + " + v="
                    + ((inclusion & INCLUDE_VIDEOS) != 0));
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
                    Log.v(TAG, "initializing ... haveSdCard == " + haveSdCard
                            + "; inclusion is "
                            + String.format("%x", inclusion));
                }
                if (specificImageUri != null) {
                    try {
                        if (specificImageUri.getScheme()
                                .equalsIgnoreCase("content")) {
                            l.add(new ImageList(ctx, cr, specificImageUri,
                                    sThumbURI, sort, bucketId));
                        } else {
                            l.add(new SingleImageList(cr, specificImageUri));
                        }
                    } catch (UnsupportedOperationException ex) {
                        // ignore exception
                    }
                } else {
                    if (haveSdCard && location != DataLocation.INTERNAL) {
                        if ((inclusion & INCLUDE_IMAGES) != 0) {
                            try {
                                l.add(new ImageList(ctx, cr, sStorageURI,
                                        sThumbURI, sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                                // ignore exception
                            }
                        }
                        if ((inclusion & INCLUDE_VIDEOS) != 0) {
                            try {
                                l.add(new VideoList(ctx, cr, sVideoStorageURI,
                                        sVideoThumbURI, sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                                // ignore exception
                            }
                        }
                    }
                    if (location == DataLocation.INTERNAL
                            || location == DataLocation.ALL) {
                        if ((inclusion & INCLUDE_IMAGES) != 0) {
                            try {
                                l.add(new ImageList(ctx, cr,
                                        Images.Media.INTERNAL_CONTENT_URI,
                                        Images.Thumbnails.INTERNAL_CONTENT_URI,
                                        sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                                // ignore exception
                            }
                        }
                        if ((inclusion & INCLUDE_DRM_IMAGES) != 0) {
                            try {
                                l.add(new DrmImageList(ctx, cr,
                                        DrmStore.Images.CONTENT_URI,
                                        sort, bucketId));
                            } catch (UnsupportedOperationException ex) {
                                // ignore exception
                            }
                        }
                    }
                }

                IImageList [] imageList = l.toArray(new IImageList[l.size()]);
                return new ImageListUber(imageList, sort);
            } else {
                if (haveSdCard && location != DataLocation.INTERNAL) {
                    return new ImageList(
                            ctx, cr, sStorageURI, sThumbURI, sort, bucketId);
                } else  {
                    return new ImageList(ctx, cr,
                            Images.Media.INTERNAL_CONTENT_URI,
                            Images.Thumbnails.INTERNAL_CONTENT_URI, sort,
                            bucketId);
                }
            }
        }
    }

    private static boolean checkFsWritable() {
        // Create a temporary file to see whether a volume is really writeable.
        // It's important not to put it in the root directory which may have a
        // limit on the number of files.
        String directoryName =
                Environment.getExternalStorageDirectory().toString() + "/DCIM";
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
            if (!f.createNewFile()) {
                return false;
            }
            f.delete();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static boolean hasStorage() {
        return hasStorage(true);
    }

    public static boolean hasStorage(boolean requireWriteAccess) {
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
        } else if (!requireWriteAccess
                && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
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
            return resolver.query(
                    uri, projection, selection, selectionArgs, sortOrder);
         } catch (UnsupportedOperationException ex) {
            return null;
        }

    }

    public static boolean isMediaScannerScanning(Context context) {
        boolean result = false;
        Cursor cursor = query(context, MediaStore.getMediaScannerUri(),
                new String [] {MediaStore.MEDIA_SCANNER_VOLUME},
                null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }

        if (VERBOSE) {
            Log.v(TAG, "isMediaScannerScanning returning " + result);
        }
        return result;
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
