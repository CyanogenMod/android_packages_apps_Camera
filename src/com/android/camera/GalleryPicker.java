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

public class GalleryPicker extends Activity {
    static private final String TAG = "GalleryPicker";
    
    GridView mGridView;
    Drawable mFrameGalleryMask;
    Drawable mCellOutline;

    BroadcastReceiver mReceiver;
    GalleryPickerAdapter mAdapter;
    
    Dialog mMediaScanningDialog;
    
    MenuItem mFlipItem;
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
        mAdapter.notifyDataSetChanged();
        mAdapter.init(!unmounted && !scanning);
    }
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.gallerypicker);
        
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
                menu.setHeaderTitle(
                        mAdapter.baseTitleForPosition(((AdapterContextMenuInfo)menuInfo).position));
                menu.add(0, 207, 0, R.string.slide_show)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
                        int position = info.position;
                        
                        Uri targetUri;
                        synchronized (mAdapter.mFirstImageUris) {
                            if (position >= mAdapter.mFirstImageUris.size()) {
                                // the list of ids does not include the "all" list
                                targetUri = mAdapter.firstImageUri(mAdapter.mIds.get(position-1));
                            } else {
                                // the mFirstImageUris list includes the "all" uri
                                targetUri = mAdapter.mFirstImageUris.get(position);
                            }
                        }
                        if (targetUri != null && position > 0) {
                            targetUri = targetUri.buildUpon().appendQueryParameter("bucketId", mAdapter.mIds.get(info.position-1)).build();
                        }
//                      Log.v(TAG, "URI to launch slideshow " + targetUri);
                        Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);
                        intent.putExtra("slideshow", true);
                        startActivity(intent);
                        return true;
                    }
                });
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
    }
    
    private void launchFolderGallery(int position) {
        android.net.Uri uri = Images.Media.INTERNAL_CONTENT_URI;
        if (position > 0) {
            uri = uri.buildUpon().appendQueryParameter("bucketId", mAdapter.mIds.get(position-1)).build();
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (position > 0) {
            intent.putExtra("windowTitle", mAdapter.mNames.get(position-1));
        }
        startActivity(intent);
    }
    
    class ItemInfo {
        Bitmap bitmap;
        int count;
        int overlayId;
    }
    
    class GalleryPickerAdapter extends BaseAdapter {
        ArrayList<String> mIds = new ArrayList<String>();
        ArrayList<String> mNames = new ArrayList<String>();
        ArrayList<Uri> mFirstImageUris = new ArrayList<Uri>();
        
        ArrayList<View> mAllViews = new ArrayList<View>();
        SparseArray<ItemInfo> mThumbs = new SparseArray<ItemInfo>();
        
        boolean mDone = false;
        CameraThread mWorkerThread;

        public void init(boolean assumeMounted) {
            mAllViews.clear();
            mThumbs.clear();
            
            ImageManager.IImageList images;
            if (assumeMounted) {
                images = ImageManager.instance().allImages(
                        GalleryPicker.this,
                        getContentResolver(), 
                        ImageManager.DataLocation.ALL, 
                        ImageManager.INCLUDE_IMAGES, 
                        ImageManager.SORT_DESCENDING);
            } else {
                images = ImageManager.instance().emptyImageList();
            }

            mIds.clear();
            mNames.clear();
            mFirstImageUris.clear();

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
            for (String key : hashMap.keySet()) {
                if (key.equals(cameraItem)) {
                    cameraBucketId = key;
                } else {
                    mIds.add(key);
                }
            }
            images.deactivate();
            notifyDataSetInvalidated();
            
            // sort baesd on the display name.  if two display names compare equal
            // then sort based on the id
            java.util.Collections.sort(mIds, new java.util.Comparator<String>() {
                public int compare(String first, String second) {
                    int x = hashMap.get(first).compareTo(hashMap.get(second));
                    if (x == 0)
                        x = first.compareTo(second);
                    return x;
                }
            });

            for (String s : mIds) {
                mNames.add(hashMap.get(s));
            }

            if (cameraBucketId != null) {
                mIds.add(0, cameraBucketId);
                mNames.add(0, "Camera");
            }
            final boolean foundCameraBucket = cameraBucketId != null;
            
            mDone = false;
            mWorkerThread = new CameraThread(new Runnable() {
                public void run() {
                    try {
                        // no images, nothing to do
                        if (mIds.size() == 0)
                            return;
                        
                        for (int i = 0; i < mIds.size() + 1 && !mDone; i++) {
                            String id = i == 0 ? null : mIds.get(i-1);
                            ImageManager.IImageList list = ImageManager.instance().allImages(
                                    GalleryPicker.this, 
                                    getContentResolver(), 
                                    ImageManager.DataLocation.ALL,
                                    ImageManager.INCLUDE_IMAGES, 
                                    ImageManager.SORT_DESCENDING,
                                    id);
                            try {
                                if (mPausing) {
                                    break;
                                }
                                if (list.getCount() > 0)
                                    mFirstImageUris.add(i, list.getImageAt(0).fullSizeImageUri());

                                int overlay = -1;
                                if (i == 1 && foundCameraBucket)
                                    overlay = R.drawable.frame_overlay_gallery_camera;
                                final Bitmap b = makeMiniThumbBitmap(142, 142, list);
                                final int pos = i;
                                final int count = list.getCount();
                                final int overlayId = overlay;
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
                                        info.overlayId = overlayId;
                                        mThumbs.put(pos, info); 
                                        
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
                        Log.e(TAG, "got exception generating collage views " + ex.toString());
                    }
                }
            });
            mWorkerThread.start();
            mWorkerThread.toBackground();
        }
        
        Uri firstImageUri(String id) {
            ImageManager.IImageList list = ImageManager.instance().allImages(
                    GalleryPicker.this, 
                    getContentResolver(), 
                    ImageManager.DataLocation.ALL,
                    ImageManager.INCLUDE_IMAGES, 
                    ImageManager.SORT_DESCENDING,
                    id);
            Uri uri = list.getImageAt(0).fullSizeImageUri();
            list.deactivate();
            return uri;
        }

        public int getCount() {
            return mIds.size() + 1;  // add 1 for the everything bucket
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }
        
        private String baseTitleForPosition(int position) {
            if (position == 0) {
                return getResources().getString(R.string.all_images);
            } else {
                return mNames.get(position-1);
            }
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
            ItemInfo info = mThumbs.get(position);
            if (info != null) {
                iv.setImageBitmap(info.bitmap);
                iv.setOverlay(info.overlayId);
                String title = baseTitleForPosition(position) + " (" + info.count + ")";
                titleView.setText(title);
            } else {
                iv.setImageResource(android.R.color.transparent);
                iv.setOverlay(-1);
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
        
        if (!scanning && mAdapter.mIds.size() <= 1) {
            android.net.Uri uri = Images.Media.INTERNAL_CONTENT_URI;
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            finish();
            return;
        }
    }
    

    private void setBackgrounds(Resources r) {
        mFrameGalleryMask = r.getDrawable(R.drawable.frame_gallery_preview_album_mask);

        mCellOutline = r.getDrawable(android.R.drawable.gallery_thumb);
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
        if (count < 4) {
            count = 1;
        // uncomment for 2 pictures per frame
//        if (count == 2 || count == 3) {
//            count = 2;
//            imageWidth = imageWidth * 2 / 3;
//            imageHeight = imageHeight * 2 / 3;
//            offsetWidth = imageWidth / 3 - padding;
//            offsetHeight = -imageHeight / 3 + padding * 2;
        } else if (count >= 4) {
            count = 4;
            imageWidth = (imageWidth - padding) / 2;     // 2 here because we show two images
            imageHeight = (imageHeight - padding) / 2;   // per row and column
        }
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
        
        for (int i = 0; i < count; i++) {
            if (mPausing) {
                return null;
            }
            ImageManager.IImage image = i < count ? images.getImageAt(i) : null;
            if (image == null) {
                break;
            }
            Bitmap temp = image.miniThumbBitmap();
            if (temp != null) {
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
        
        mFlipItem = MenuHelper.addFlipOrientation(menu, this, mPrefs);

        menu.add(0, 0, 0, R.string.camerasettings)
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

    @Override 
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        int keyboard = getResources().getConfiguration().keyboardHidden;
        mFlipItem.setEnabled(keyboard == android.content.res.Configuration.KEYBOARDHIDDEN_YES);

        return true;
    }
}
