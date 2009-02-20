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

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images;
import android.util.Config;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GalleryPicker extends Activity {
    static private final String TAG = "GalleryPicker";

    private View mNoImagesView;
    GridView mGridView;
    Drawable mFrameGalleryMask;
    Drawable mCellOutline;
    Drawable mVideoOverlay;

    BroadcastReceiver mReceiver;
    GalleryPickerAdapter mAdapter;

    Dialog mMediaScanningDialog;

    SharedPreferences mPrefs;

    boolean mPausing = false;

    private static long LOW_STORAGE_THRESHOLD = 1024 * 1024 * 2;

    public GalleryPicker() {
    }

    private void rebake(boolean unmounted, boolean scanning) {
        if (mMediaScanningDialog != null) {
            mMediaScanningDialog.cancel();
            mMediaScanningDialog = null;
        }
        if (scanning) {
            mMediaScanningDialog = ProgressDialog.show(
                    this,
                    null,
                    getResources().getString(R.string.wait),
                    true,
                    true);
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
            mAdapter.init(!unmounted && !scanning);
        }

        if (!unmounted) {
            // Warn the user if space is getting low
            Thread t = new Thread(new Runnable() {
                public void run() {

                    // Check available space only if we are writable
                    if (ImageManager.hasStorage()) {
                        String storageDirectory = Environment.getExternalStorageDirectory().toString();
                        StatFs stat = new StatFs(storageDirectory);
                        long remaining = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();
                        if (remaining < LOW_STORAGE_THRESHOLD) {

                            mHandler.post(new Runnable() {
                                public void run() {
                                    Toast.makeText(GalleryPicker.this.getApplicationContext(),
                                        R.string.not_enough_space, 5000).show();
                                }
                            });
                        }
                    }
                }
            });
            t.start();
        }

        // If we just have zero or one folder, open it. (We shouldn't have just one folder
        // any more, but we can have zero folders.)
        mNoImagesView.setVisibility(View.GONE);
        if (!scanning) {
            int numItems = mAdapter.mItems.size();
            if (numItems == 0) {
                mNoImagesView.setVisibility(View.VISIBLE);
            } else if (numItems == 1) {
                mAdapter.mItems.get(0).launch(this);
                finish();
                return;
            }
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.gallerypicker);

        mNoImagesView = findViewById(R.id.no_images);
        mGridView = (GridView) findViewById(R.id.albums);
        mGridView.setSelector(android.R.color.transparent);

        mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (Config.LOGV) Log.v(TAG, "onReceiveIntent " + intent.getAction());
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    // SD card available
                    // TODO put up a "please wait" message
                    // TODO also listen for the media scanner finished message
                } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                    // SD card unavailable
                    if (Config.LOGV) Log.v(TAG, "sd card no longer available");
                    Toast.makeText(GalleryPicker.this, getResources().getString(R.string.wait), 5000);
                    rebake(true, false);
                } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                    Toast.makeText(GalleryPicker.this, getResources().getString(R.string.wait), 5000);
                    rebake(false, true);
                } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                    if (Config.LOGV)
                        Log.v(TAG, "rebake because of ACTION_MEDIA_SCANNER_FINISHED");
                    rebake(false, false);
                } else if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                    if (Config.LOGV)
                        Log.v(TAG, "rebake because of ACTION_MEDIA_EJECT");
                    rebake(true, false);
                }
            }
        };

        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launchFolderGallery(position);
            }
        });
        mGridView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            public void onCreateContextMenu(ContextMenu menu, View v, final ContextMenu.ContextMenuInfo menuInfo) {
                int position = ((AdapterContextMenuInfo)menuInfo).position;
                menu.setHeaderTitle(mAdapter.baseTitleForPosition(position));
                if ((mAdapter.getIncludeMediaTypes(position) & ImageManager.INCLUDE_IMAGES) != 0) {
                    menu.add(0, 207, 0, R.string.slide_show)
                    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
                            int position = info.position;

                            Uri targetUri;
                            synchronized (mAdapter.mItems) {
                                if (position < 0 || position >= mAdapter.mItems.size()) {
                                    return true;
                                }
                                // the mFirstImageUris list includes the "all" uri
                                targetUri = mAdapter.mItems.get(position).mFirstImageUri;
                            }
                            if (targetUri != null && position > 0) {
                                targetUri = targetUri.buildUpon().appendQueryParameter("bucketId",
                                        mAdapter.mItems.get(info.position).mId).build();
                            }
    //                      Log.v(TAG, "URI to launch slideshow " + targetUri);
                            Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);
                            intent.putExtra("slideshow", true);
                            startActivity(intent);
                            return true;
                        }
                    });
                }
                menu.add(0, 208, 0, R.string.view)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
                        launchFolderGallery(info.position);
                        return true;
                    }
                });
            }
        });
 
        ImageManager.ensureOSXCompatibleFolder();
    }

    private void launchFolderGallery(int position) {
        mAdapter.mItems.get(position).launch(this);
    }

    class ItemInfo {
        Bitmap bitmap;
        int count;
    }

    static class Item implements Comparable<Item>{
        // The type is also used as the sort order
        public final static int TYPE_NONE = -1;
        public final static int TYPE_ALL_IMAGES = 0;
        public final static int TYPE_ALL_VIDEOS = 1;
        public final static int TYPE_CAMERA_IMAGES = 2;
        public final static int TYPE_CAMERA_VIDEOS = 3;
        public final static int TYPE_NORMAL_FOLDERS = 4;

        public int mType;
        public String mId;
        public String mName;
        public Uri mFirstImageUri;
        public ItemInfo mThumb;

        public Item(int type, String id, String name) {
            mType = type;
            mId = id;
            mName = name;
        }

        public boolean needsBucketId() {
            return mType >= TYPE_CAMERA_IMAGES;
        }

        public void launch(Activity activity) {
            android.net.Uri uri = Images.Media.INTERNAL_CONTENT_URI;
            if (needsBucketId()) {
                uri = uri.buildUpon().appendQueryParameter("bucketId",mId).build();
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra("windowTitle", mName);
            intent.putExtra("mediaTypes", getIncludeMediaTypes());
            activity.startActivity(intent);
        }

        public int getIncludeMediaTypes() {
            return convertItemTypeToIncludedMediaType(mType);
        }

        public static int convertItemTypeToIncludedMediaType(int itemType) {
            switch (itemType) {
            case TYPE_ALL_IMAGES:
            case TYPE_CAMERA_IMAGES:
                return ImageManager.INCLUDE_IMAGES;
            case TYPE_ALL_VIDEOS:
            case TYPE_CAMERA_VIDEOS:
                return ImageManager.INCLUDE_VIDEOS;
            case TYPE_NORMAL_FOLDERS:
            default:
                return     ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS;
            }
        }

        public int getOverlay() {
            switch (mType) {
            case TYPE_ALL_IMAGES:
            case TYPE_CAMERA_IMAGES:
                return R.drawable.frame_overlay_gallery_camera;
            case TYPE_ALL_VIDEOS:
            case TYPE_CAMERA_VIDEOS:
                return R.drawable.frame_overlay_gallery_video;
            case TYPE_NORMAL_FOLDERS:
                return R.drawable.frame_overlay_gallery_folder;
            default:
                return     -1;
            }
        }

        // sort based on the sort order, then the case-insensitive display name, then the id.
        public int compareTo(Item other) {
            int x = mType - other.mType;
            if (x == 0) {
                x = mName.compareToIgnoreCase(other.mName);
                if (x == 0) {
                    x = mId.compareTo(other.mId);
                }
            }
            return x;
        }
    }

    class GalleryPickerAdapter extends BaseAdapter {
        ArrayList<Item> mItems = new ArrayList<Item>();

        boolean mDone = false;
        CameraThread mWorkerThread;

        public void init(boolean assumeMounted) {
            mItems.clear();

            ImageManager.IImageList images;
            if (assumeMounted) {
                images = ImageManager.instance().allImages(
                        GalleryPicker.this,
                        getContentResolver(),
                        ImageManager.DataLocation.ALL,
                        ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS,
                        ImageManager.SORT_DESCENDING);
            } else {
                images = ImageManager.instance().emptyImageList();
            }

            if (mWorkerThread != null) {
                try {
                    mDone = true;
                    if (Config.LOGV)
                        Log.v(TAG, "about to call join on thread " + mWorkerThread.getId());
                    mWorkerThread.join();
                } finally {
                    mWorkerThread = null;
                }
            }

            String cameraItem = ImageManager.CAMERA_IMAGE_BUCKET_ID;
            final HashMap<String, String> hashMap = images.getBucketIds();
            String cameraBucketId = null;
            for (Map.Entry<String, String> entry: hashMap.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                if (key.equals(cameraItem)) {
                    cameraBucketId = key;
                } else {
                    mItems.add(new Item(Item.TYPE_NORMAL_FOLDERS, key, entry.getValue()));
                }
            }
            images.deactivate();
            notifyDataSetInvalidated();

            // Conditionally add all-images and all-videos folders.
            addBucket(Item.TYPE_ALL_IMAGES, null,
                    Item.TYPE_CAMERA_IMAGES, cameraBucketId, R.string.all_images);
            addBucket(Item.TYPE_ALL_VIDEOS, null,
                    Item.TYPE_CAMERA_VIDEOS, cameraBucketId, R.string.all_videos);

            if (cameraBucketId != null) {
                addBucket(Item.TYPE_CAMERA_IMAGES, cameraBucketId,
                        R.string.gallery_camera_bucket_name);
                addBucket(Item.TYPE_CAMERA_VIDEOS, cameraBucketId,
                        R.string.gallery_camera_videos_bucket_name);
            }

            java.util.Collections.sort(mItems);

            mDone = false;
            mWorkerThread = new CameraThread(new Runnable() {
                public void run() {
                    try {
                        // no images, nothing to do
                        if (mItems.size() == 0)
                            return;

                        for (int i = 0; i < mItems.size() && !mDone; i++) {
                            final Item item = mItems.get(i);
                            ImageManager.IImageList list = createImageList(
                                    item.getIncludeMediaTypes(), item.mId);
                            try {
                                if (mPausing) {
                                    break;
                                }
                                if (list.getCount() > 0)
                                    item.mFirstImageUri = list.getImageAt(0).fullSizeImageUri();

                                final Bitmap b = makeMiniThumbBitmap(142, 142, list);
                                final int pos = i;
                                final int count = list.getCount();
                                final Thread currentThread = Thread.currentThread();
                                mHandler.post(new Runnable() {
                                    public void run() {
                                        if (mPausing || currentThread != mWorkerThread.realThread()) {
                                            if (b != null) {
                                                b.recycle();
                                            }
                                            return;
                                        }

                                        ItemInfo info = new ItemInfo();
                                        info.bitmap = b;
                                        info.count = count;
                                        item.mThumb = info;

                                        final GridView grid = GalleryPicker.this.mGridView;
                                        final int firstVisible = grid.getFirstVisiblePosition();

                                        // Minor optimization -- only notify if the specified position is visible
                                        if ((pos >= firstVisible) && (pos < firstVisible + grid.getChildCount())) {
                                            GalleryPickerAdapter.this.notifyDataSetChanged();
                                        }
                                    }
                                });
                            } finally {
                                list.deactivate();
                            }
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "got exception generating collage views ", ex);
                    }
                }
            });
            mWorkerThread.start();
            mWorkerThread.toBackground();
        }

        /**
         * Add a bucket, but only if it's interesting.
         * Interesting means non-empty and not duplicated by the
         * corresponding camera bucket.
         */
        private void addBucket(int itemType, String bucketId,
                int cameraItemType, String cameraBucketId,
                int labelId) {
            int itemCount = bucketItemCount(
                    Item.convertItemTypeToIncludedMediaType(itemType), bucketId);
            if (itemCount == 0) {
                return; // Bucket is empty, so don't show it.
            }
            int cameraItemCount = 0;
            if (cameraBucketId != null) {
                cameraItemCount = bucketItemCount(
                        Item.convertItemTypeToIncludedMediaType(cameraItemType), cameraBucketId);
            }
            if (cameraItemCount == itemCount) {
                return; // Bucket is the same as the camera bucket, so don't show it.
            }
            mItems.add(new Item(itemType, bucketId, getResources().getString(labelId)));
        }

        /**
         * Add a bucket, but only if it's interesting.
         * Interesting means non-empty.
         */
        private void addBucket(int itemType, String bucketId,
                int labelId) {
            if (!isEmptyBucket(Item.convertItemTypeToIncludedMediaType(itemType), bucketId)) {
                mItems.add(new Item(itemType, bucketId, getResources().getString(labelId)));
            }
        }

        public int getCount() {
            return mItems.size();
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        private String baseTitleForPosition(int position) {
            return mItems.get(position).mName;
        }

        private int getIncludeMediaTypes(int position) {
            return mItems.get(position).getIncludeMediaTypes();
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            View v;

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.gallery_picker_item, null);
            } else {
                v = convertView;
            }

            TextView titleView = (TextView) v.findViewById(R.id.title);

            GalleryPickerItem iv = (GalleryPickerItem) v.findViewById(R.id.thumbnail);
            iv.setOverlay(mItems.get(position).getOverlay());
            ItemInfo info = mItems.get(position).mThumb;
            if (info != null) {
                iv.setImageBitmap(info.bitmap);
                String title = baseTitleForPosition(position) + " (" + info.count + ")";
                titleView.setText(title);
            } else {
                iv.setImageResource(android.R.color.transparent);
                titleView.setText(baseTitleForPosition(position));
            }

            return v;
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        mPausing = true;
        unregisterReceiver(mReceiver);

        // free up some ram
        mAdapter = null;
        mGridView.setAdapter(null);
        System.gc();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPausing = false;

        mAdapter = new GalleryPickerAdapter();
        mGridView.setAdapter(mAdapter);
        setBackgrounds(getResources());

        boolean scanning = ImageManager.isMediaScannerScanning(this);
        rebake(false, scanning);

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addDataScheme("file");

        registerReceiver(mReceiver, intentFilter);
        MenuHelper.requestOrientation(this, mPrefs);
    }



    private void setBackgrounds(Resources r) {
        mFrameGalleryMask = r.getDrawable(R.drawable.frame_gallery_preview_album_mask);

        mCellOutline = r.getDrawable(android.R.drawable.gallery_thumb);
        mVideoOverlay = r.getDrawable(R.drawable.ic_gallery_video_overlay);
    }

    Handler mHandler = new Handler();

    private void placeImage(Bitmap image, Canvas c, Paint paint, int imageWidth, int widthPadding, int imageHeight, int heightPadding, int offsetX, int offsetY, int pos) {
        int row = pos / 2;
        int col = pos - (row * 2);

        int xPos = (col * (imageWidth + widthPadding)) - offsetX;
        int yPos = (row * (imageHeight + heightPadding)) - offsetY;

        c.drawBitmap(image, xPos, yPos, paint);
    }

    private Bitmap makeMiniThumbBitmap(int width, int height, ImageManager.IImageList images) {
        int count = images.getCount();
        // We draw three different version of the folder image depending on the number of images in the folder.
        //    For a single image, that image draws over the whole folder.
        //    For two or three images, we draw the two most recent photos.
        //    For four or more images, we draw four photos.
        final int padding = 4;
        int imageWidth = width;
        int imageHeight = height;
        int offsetWidth = 0;
        int offsetHeight = 0;

        imageWidth = (imageWidth - padding) / 2;     // 2 here because we show two images
        imageHeight = (imageHeight - padding) / 2;   // per row and column

        final Paint  p = new Paint();
        final Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);

        final Matrix m = new Matrix();

        // draw the whole canvas as transparent
        p.setColor(0x00000000);
        c.drawPaint(p);

        // draw the mask normally
        p.setColor(0xFFFFFFFF);
        mFrameGalleryMask.setBounds(0, 0, width, height);
        mFrameGalleryMask.draw(c);

        Paint pdpaint = new Paint();
        pdpaint.setXfermode(new android.graphics.PorterDuffXfermode(
                                    android.graphics.PorterDuff.Mode.SRC_IN));

        pdpaint.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, width, height, pdpaint);

        for (int i = 0; i < 4; i++) {
            if (mPausing) {
                return null;
            }

            Bitmap temp = null;
            ImageManager.IImage image = i < count ? images.getImageAt(i) : null;

            if (image != null) {
                temp = image.miniThumbBitmap();
            }

            if (temp != null) {
                if (ImageManager.isVideo(image)) {
                    Bitmap newMap = temp.copy(temp.getConfig(), true);
                    Canvas overlayCanvas = new Canvas(newMap);
                    int overlayWidth = mVideoOverlay.getIntrinsicWidth();
                    int overlayHeight = mVideoOverlay.getIntrinsicHeight();
                    int left = (newMap.getWidth() - overlayWidth) / 2;
                    int top = (newMap.getHeight() - overlayHeight) / 2;
                    Rect newBounds = new Rect(left, top, left + overlayWidth, top + overlayHeight);
                    mVideoOverlay.setBounds(newBounds);
                    mVideoOverlay.draw(overlayCanvas);
                    temp.recycle();
                    temp = newMap;
                }

                Bitmap temp2 = ImageLoader.transform(m, temp, imageWidth, imageHeight, true);
                if (temp2 != temp)
                    temp.recycle();
                temp = temp2;
            }

            Bitmap thumb = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            Canvas tempCanvas = new Canvas(thumb);
            if (temp != null)
                tempCanvas.drawBitmap(temp, new Matrix(), new Paint());
            mCellOutline.setBounds(0, 0, imageWidth, imageHeight);
            mCellOutline.draw(tempCanvas);

            placeImage(thumb, c, pdpaint, imageWidth, padding, imageHeight, padding, offsetWidth, offsetHeight, i);

            thumb.recycle();

            if (temp != null)
                temp.recycle();
        }

        return b;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuHelper.addCaptureMenuItems(menu, this);

        menu.add(0, 0, 5, R.string.camerasettings)
        .setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent preferences = new Intent();
                preferences.setClass(GalleryPicker.this, GallerySettings.class);
                startActivity(preferences);
                return true;
            }
        })
        .setAlphabeticShortcut('p')
        .setIcon(android.R.drawable.ic_menu_preferences);

        return true;
    }

    private boolean isEmptyBucket(int mediaTypes, String bucketId) {
        // TODO: Find a more efficient way of calculating this
        ImageManager.IImageList list = createImageList(mediaTypes, bucketId);
        try {
            return list.isEmpty();
        }
        finally {
            list.deactivate();
        }
    }

    private int bucketItemCount(int mediaTypes, String bucketId) {
        // TODO: Find a more efficient way of calculating this
        ImageManager.IImageList list = createImageList(mediaTypes, bucketId);
        try {
            return list.getCount();
        }
        finally {
            list.deactivate();
        }
    }
    private ImageManager.IImageList createImageList(int mediaTypes, String bucketId) {
        return ImageManager.instance().allImages(
                this,
                getContentResolver(),
                ImageManager.DataLocation.ALL,
                mediaTypes,
                ImageManager.SORT_DESCENDING,
                bucketId);
    }
}
