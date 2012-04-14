/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.content.ContentValues;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

public class Storage {
    private static final String TAG = "CameraStorage";

    public static final String DCIM =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

    public static final String E_DCIM = "/mnt/emmc/DCIM";

    public static final String DIRECTORY = DCIM + "/Camera";

    public static final String E_DIRECTORY = E_DCIM + "/Camera";

    // Match the code in MediaProvider.computeBucketValues().
    public static final String BUCKET_ID =
            String.valueOf(DIRECTORY.toLowerCase().hashCode());

    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long LOW_STORAGE_THRESHOLD= 50000000;
    public static final long PICTURE_SIZE = 1500000;

    private static final int BUFSIZE = 4096;

    public static Uri addImage(ContentResolver resolver, boolean place, String title, long date,
                Location location, int orientation, byte[] jpeg, int width, int height) {
        // Save the image.
        String path = generateFilepath(place, title);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(jpeg);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write image", e);
            return null;
        } finally {
            try {
                out.close();
            } catch (Exception e) {
            }
        }

        // Insert into MediaStore.
        ContentValues values = new ContentValues(9);
        values.put(ImageColumns.TITLE, title);
        values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
        values.put(ImageColumns.DATE_TAKEN, date);
        values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        values.put(ImageColumns.ORIENTATION, orientation);
        values.put(ImageColumns.DATA, path);
        values.put(ImageColumns.SIZE, jpeg.length);
        values.put(ImageColumns.WIDTH, width);
        values.put(ImageColumns.HEIGHT, height);

        if (location != null) {
            values.put(ImageColumns.LATITUDE, location.getLatitude());
            values.put(ImageColumns.LONGITUDE, location.getLongitude());
        }

        Uri uri = null;
        try {
            uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Throwable th)  {
            // This can happen when the external volume is already mounted, but
            // MediaScanner has not notify MediaProvider to add that volume.
            // The picture is still safe and MediaScanner will find it and
            // insert it into MediaProvider. The only problem is that the user
            // cannot click the thumbnail to review the picture.
            Log.e(TAG, "Failed to write MediaStore" + th);
        }
        return uri;
    }
	
    public static String storagePathBuilder(boolean external){
    	String path = null;
	path = external ? E_DIRECTORY : DIRECTORY;
	if(external)
	   new File(E_DCIM).mkdirs();
	return path;
    }

    public static String generateFilepath(boolean place, String title) {
        return storagePathBuilder(place) + '/' + title + ".jpg";
    }

    public static long getAvailableSpace() {
        String state = Environment.getExternalStorageState();
        Log.d(TAG, "External storage state=" + state);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        File dir = new File(DIRECTORY);
        dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(DIRECTORY);
            return stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatible() {
        File nnnAAAAA = new File(DCIM, "100ANDRO");
        if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
            Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
        }
    }
}
