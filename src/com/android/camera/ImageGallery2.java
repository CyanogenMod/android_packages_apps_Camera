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

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.gallery.VideoObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class ImageGallery2 extends Activity {
    private static final String TAG = "ImageGallery2";
    IImageList mAllImages;
    private int mInclusion;
    boolean mSortAscending = false;
    private View mNoImagesView;
    public static final int CROP_MSG = 2;
    public static final int VIEW_MSG = 3;
    private static final String INSTANCE_STATE_TAG = "scrollY";

    private Dialog mMediaScanningDialog;

    private MenuItem mSlideShowItem;
    private SharedPreferences mPrefs;
    private long mVideoSizeLimit = Long.MAX_VALUE;

    BroadcastReceiver mReceiver = null;

    Handler mHandler = new Handler();
    boolean mLayoutComplete;
    boolean mPausing = false;
    boolean mStopThumbnailChecking = false;

    BitmapThread mThumbnailCheckThread;
    GridViewSpecial mGvs;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Must be called before setContentView().
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

        setContentView(R.layout.image_gallery_2);

        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
                R.layout.custom_gallery_title);

        mGvs = (GridViewSpecial) findViewById(R.id.grid);
        mGvs.requestFocus();

        if (isPickIntent()) {
            mVideoSizeLimit = getIntent().getLongExtra(
                    MediaStore.EXTRA_SIZE_LIMIT, Long.MAX_VALUE);
            mGvs.mVideoSizeLimit = mVideoSizeLimit;
        } else {
            mVideoSizeLimit = Long.MAX_VALUE;
            mGvs.mVideoSizeLimit = mVideoSizeLimit;
            mGvs.setOnCreateContextMenuListener(
                    new CreateContextMenuListener());
        }
    }

    private MenuItem addSlideShowMenu(Menu menu, int position) {
        return menu.add(0, 207, position, R.string.slide_show)
                .setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        return onSlideShowClicked();
                    }
                }).setIcon(android.R.drawable.ic_menu_slideshow);
    }

    public boolean onSlideShowClicked() {
        IImage img = mSelectedImageGetter.getCurrentImage();
        if (img == null) {
            img = mAllImages.getImageAt(0);
            if (img == null) {
                return true;
            }
        }
        Uri targetUri = img.fullSizeImageUri();
        Uri thisUri = getIntent().getData();
        if (thisUri != null) {
            String bucket = thisUri.getQueryParameter("bucketId");
            if (bucket != null) {
                targetUri = targetUri.buildUpon()
                        .appendQueryParameter("bucketId", bucket)
                        .build();
            }
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);
        intent.putExtra("slideshow", true);
        startActivity(intent);
        return true;
    }

    private final Runnable mDeletePhotoRunnable = new Runnable() {
        public void run() {
            mGvs.clearCache();
            IImage currentImage = mSelectedImageGetter.getCurrentImage();
            if (currentImage != null) {
                mAllImages.removeImage(currentImage);
            }
            mGvs.invalidate();
            mGvs.requestLayout();
            mGvs.start();
            mNoImagesView.setVisibility(mAllImages.isEmpty()
                    ? View.VISIBLE
                    : View.GONE);
        }
    };

    private final SelectedImageGetter mSelectedImageGetter =
            new SelectedImageGetter() {
                public Uri getCurrentImageUri() {
                    IImage image = getCurrentImage();
                    if (image != null) {
                        return image.fullSizeImageUri();
                    } else {
                        return null;
                    }
                }
                public IImage getCurrentImage() {
                    int currentSelection = mGvs.mCurrentSelection;
                    if (currentSelection < 0
                            || currentSelection >= mAllImages.getCount()) {
                        return null;
                    } else {
                        return mAllImages.getImageAt(currentSelection);
                    }
                }
            };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mTargetScroll = mGvs.getScrollY();
    }

    private final Runnable mLongPressCallback = new Runnable() {
        public void run() {
            mGvs.select(GridViewSpecial.ORIGINAL_SELECT, false);
            mGvs.showContextMenu();
        }
    };

    boolean canHandleEvent() {
        // Don't process event in pause state.
        return (!mPausing) && (mGvs.mCurrentSpec != null);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!canHandleEvent()) return false;

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            mGvs.select(GridViewSpecial.ORIGINAL_SELECT, false);

            // The keyUp doesn't get called when the longpress menu comes up. We
            // only get here when the user lets go of the center key before the
            // longpress menu comes up.
            mHandler.removeCallbacks(mLongPressCallback);

            // open the photo
            if (mSelectedImageGetter.getCurrentImage() != null) {
                onSelect(mGvs.mCurrentSelection);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!canHandleEvent()) return false;

        boolean handled = true;
        int sel = mGvs.mCurrentSelection;
        int columns = mGvs.mCurrentSpec.mColumns;
        int count = mAllImages.getCount();
        boolean pressed = false;
        if (mGvs.mShowSelection) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (sel != count && (sel % columns < columns - 1)) {
                        sel += 1;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (sel > 0 && (sel % columns != 0)) {
                        sel -= 1;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if ((sel / columns) != 0) {
                        sel -= columns;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if ((sel / columns) != (sel + columns / columns)) {
                        sel = Math.min(count - 1, sel + columns);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    pressed = true;
                    mHandler.postDelayed(mLongPressCallback,
                            ViewConfiguration.getLongPressTimeout());
                    break;
                case KeyEvent.KEYCODE_DEL:
                    MenuHelper.deleteImage(this, mDeletePhotoRunnable,
                            mSelectedImageGetter.getCurrentImage());
                    break;
                default:
                    handled = false;
                    break;
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    int [] range = new int[2];
                    GridViewSpecial.ImageBlockManager ibm =
                            mGvs.mImageBlockManager;
                    if (ibm != null) {
                        ibm.getVisibleRange(range);
                        int topPos = range[0];
                        Rect r = mGvs.getRectForPosition(topPos);
                        if (r.top < mGvs.getScrollY()) {
                            topPos += columns;
                        }
                        topPos = Math.min(count - 1, topPos);
                        sel = topPos;
                    }
                    break;
                default:
                    handled = false;
                    break;
            }
        }
        if (handled) {
            mGvs.select(sel, pressed);
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private boolean isPickIntent() {
        String action = getIntent().getAction();
        return (Intent.ACTION_PICK.equals(action)
                || Intent.ACTION_GET_CONTENT.equals(action));
    }

    private void launchCropperOrFinish(IImage img) {
        Bundle myExtras = getIntent().getExtras();

        long size = MenuHelper.getImageFileSize(img);
        if (size < 0) {
            // return if there image file is not available.
            return;
        }

        if (size > mVideoSizeLimit) {

            DialogInterface.OnClickListener buttonListener =
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            };
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle(R.string.file_info_title)
                    .setMessage(R.string.video_exceed_mms_limit)
                    .setNeutralButton(R.string.details_ok, buttonListener)
                    .show();
            return;
        }

        String cropValue = myExtras != null ? myExtras.getString("crop") : null;
        if (cropValue != null) {
            Bundle newExtras = new Bundle();
            if (cropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }

            Intent cropIntent = new Intent();
            cropIntent.setData(img.fullSizeImageUri());
            cropIntent.setClass(this, CropImage.class);
            cropIntent.putExtras(newExtras);

            /* pass through any extras that were passed in */
            cropIntent.putExtras(myExtras);
            startActivityForResult(cropIntent, CROP_MSG);
        } else {
            Intent result = new Intent(null, img.fullSizeImageUri());
            if (myExtras != null && myExtras.getString("return-data") != null) {
                Bitmap bitmap = img.fullSizeBitmap(1000);
                if (bitmap != null) {
                    result.putExtra("data", bitmap);
                }
            }
            setResult(RESULT_OK, result);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        switch (requestCode) {
            case MenuHelper.RESULT_COMMON_MENU_CROP: {
                if (resultCode == RESULT_OK) {

                    // The CropImage activity passes back the Uri of the cropped
                    // image as the Action rather than the Data.
                    Uri dataUri = Uri.parse(data.getAction());
                    rebake(false, false);
                    IImage image = mAllImages.getImageForUri(dataUri);
                    if (image != null) {
                        int rowId = image.getRow();
                        mGvs.select(rowId, false);
                    }
                }
                break;
            }
            case CROP_MSG: {
                if (resultCode == RESULT_OK) {
                    setResult(resultCode, data);
                    finish();
                }
                break;
            }
            case VIEW_MSG: {
                IImage img = mAllImages.getImageForUri(data.getData());
                launchCropperOrFinish(img);
                break;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mPausing = true;

        BitmapManager.instance().cancelAllDecoding();
        stopCheckingThumbnails();
        mGvs.onPause();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        // Now that we've paused the threads that are using the cursor it is
        // safe to deactivate it.
        mAllImages.deactivate();
    }

    private void rebake(boolean unmounted, boolean scanning) {
        stopCheckingThumbnails();
        mGvs.clearCache();
        if (mAllImages != null) {
            mAllImages.deactivate();
            mAllImages = null;
        }
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
            mAllImages = ImageManager.emptyImageList();
        } else {
            mAllImages = allImages(!unmounted);
            mGvs.init(mHandler);
            mGvs.start();
            mGvs.requestLayout();
            checkThumbnails();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        mTargetScroll = mGvs.getScrollY();
        state.putInt(INSTANCE_STATE_TAG, mTargetScroll);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        mTargetScroll = state.getInt(INSTANCE_STATE_TAG, 0);
    }

    int mTargetScroll;

    @Override
    public void onResume() {
        super.onResume();

        BitmapManager.instance().allowAllDecoding();

        try {
            mGvs.setSizeChoice(Integer.parseInt(
                    mPrefs.getString("pref_gallery_size_key", "1")),
                    mTargetScroll);

            String sortOrder = mPrefs.getString("pref_gallery_sort_key", null);
            if (sortOrder != null) {
                mSortAscending = sortOrder.equals("ascending");
            }
        } catch (RuntimeException ex) {
        }
        mPausing = false;

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addDataScheme("file");

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    // SD card available
                    // TODO put up a "please wait" message
                    // TODO also listen for the media scanner finished message
                } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                    // SD card unavailable
                    Toast.makeText(ImageGallery2.this,
                            getResources().getString(R.string.wait), 5000);
                    rebake(true, false);
                } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                    Toast.makeText(ImageGallery2.this,
                            getResources().getString(R.string.wait), 5000);
                    rebake(false, true);
                } else if (action.equals(
                        Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                    rebake(false, false);
                } else if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                    rebake(true, false);
                }
            }
        };
        registerReceiver(mReceiver, intentFilter);
        rebake(false, ImageManager.isMediaScannerScanning(this));
    }

    private void stopCheckingThumbnails() {
        mStopThumbnailChecking = true;
        if (mThumbnailCheckThread != null) {
            mThumbnailCheckThread.join();
        }
        mStopThumbnailChecking = false;
    }

    private void checkThumbnails() {
        final long startTime = System.currentTimeMillis();
        mThumbnailCheckThread = new BitmapThread(new Runnable() {
            public void run() {
                android.content.res.Resources resources = getResources();
                final TextView progressTextView =
                        (TextView) findViewById(R.id.loading_text);
                final String progressTextFormatString =
                        resources.getString(
                        R.string.loading_progress_format_string);

                PowerManager pm =
                        (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock mWakeLock =
                    pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                                   "ImageGallery2.checkThumbnails");
                mWakeLock.acquire();
                IImageList.ThumbCheckCallback r =
                        new IImageList.ThumbCheckCallback() {
                            boolean mDidSetProgress = false;

                            public boolean checking(final int count,
                                    final int maxCount) {
                                if (mStopThumbnailChecking) {
                                    return false;
                                }

                                if (!mLayoutComplete) {
                                    return true;
                                }

                                if (!mDidSetProgress) {
                                    mHandler.post(new Runnable() {
                                            public void run() {
                                                findViewById(
                                                R.id.loading_indicator)
                                                .setVisibility(View.VISIBLE);
                                            }
                                    });
                                    mDidSetProgress = true;
                                }
                                mGvs.postInvalidate();

                                // If there is a new image done and it has been
                                // one second, update the progress text.
                                if (System.currentTimeMillis()
                                        - startTime > 1000) {
                                    mHandler.post(new Runnable() {
                                        public void run() {
                                            String s = String.format(
                                                    progressTextFormatString,
                                                    maxCount - count);
                                            progressTextView.setText(s);
                                        }
                                    });
                                }

                                return !mPausing;
                            }
                        };
                IImageList imageList = allImages(true);
                imageList.checkThumbnails(r, imageList.getCount());
                mWakeLock.release();
                mThumbnailCheckThread = null;
                mHandler.post(new Runnable() {
                    public void run() {
                        findViewById(R.id.loading_indicator).setVisibility(
                                View.GONE);
                    }
                });
            }
        });

        mThumbnailCheckThread.setName("check_thumbnails");
        mThumbnailCheckThread.start();
        mThumbnailCheckThread.toBackground();

        IImageList list = allImages(true);
        mNoImagesView.setVisibility(list.getCount() > 0
                ? View.GONE
                : View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        if (isPickIntent()) {
            MenuHelper.addCapturePictureMenuItems(menu, this);
        } else {
            MenuHelper.addCaptureMenuItems(menu, this);
            if ((mInclusion & ImageManager.INCLUDE_IMAGES) != 0) {
                mSlideShowItem = addSlideShowMenu(menu, 5);
            }
        }

        item = menu.add(0, 0, 1000, R.string.camerasettings);
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent preferences = new Intent();
                preferences.setClass(ImageGallery2.this, GallerySettings.class);
                startActivity(preferences);
                return true;
            }
        });
        item.setAlphabeticShortcut('p');
        item.setIcon(android.R.drawable.ic_menu_preferences);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if ((mInclusion & ImageManager.INCLUDE_IMAGES) != 0) {
            boolean videoSelected = isVideoSelected();
            // TODO: Only enable slide show if there is at least one image in
            // the folder.
            if (mSlideShowItem != null) {
                mSlideShowItem.setEnabled(!videoSelected);
            }
        }

        return true;
    }

    private boolean isVideoSelected() {
        IImage image = mSelectedImageGetter.getCurrentImage();
        return (image != null) && ImageManager.isVideo(image);
    }

    private synchronized IImageList allImages(boolean assumeMounted) {
        if (mAllImages == null) {
            mNoImagesView = findViewById(R.id.no_images);

            mInclusion = ImageManager.INCLUDE_IMAGES
                    | ImageManager.INCLUDE_VIDEOS;

            Intent intent = getIntent();
            if (intent != null) {
                String type = intent.resolveType(this);
                TextView leftText = (TextView) findViewById(R.id.left_text);
                if (type != null) {
                    if (type.equals("vnd.android.cursor.dir/image")
                            || type.equals("image/*")) {
                        mInclusion = ImageManager.INCLUDE_IMAGES;
                        if (isPickIntent()) {
                            leftText.setText(
                                    R.string.pick_photos_gallery_title);
                        } else {
                            leftText.setText(R.string.photos_gallery_title);
                        }
                    }
                    if (type.equals("vnd.android.cursor.dir/video")
                            || type.equals("video/*")) {
                        mInclusion = ImageManager.INCLUDE_VIDEOS;
                        if (isPickIntent()) {
                            leftText.setText(
                                    R.string.pick_videos_gallery_title);
                        } else {
                            leftText.setText(R.string.videos_gallery_title);
                        }
                    }
                }
                Bundle extras = intent.getExtras();
                String title = (extras != null)
                        ? extras.getString("windowTitle")
                        : null;
                if (title != null && title.length() > 0) {
                    leftText.setText(title);
                }

                if (extras != null) {
                    mInclusion = (ImageManager.INCLUDE_IMAGES
                            | ImageManager.INCLUDE_VIDEOS)
                            & extras.getInt("mediaTypes", mInclusion);
                }

                if (extras != null && extras.getBoolean("pick-drm")) {
                    Log.d(TAG, "pick-drm is true");
                    mInclusion = ImageManager.INCLUDE_DRM_IMAGES;
                }
            }
            Uri uri = getIntent().getData();
            if (!assumeMounted) {
                mAllImages = ImageManager.emptyImageList();
            } else {
                mAllImages = ImageManager.allImages(
                        ImageGallery2.this,
                        getContentResolver(),
                        ImageManager.DataLocation.NONE,
                        mInclusion,
                        mSortAscending
                        ? ImageManager.SORT_ASCENDING
                        : ImageManager.SORT_DESCENDING,
                        (uri != null)
                        ? uri.getQueryParameter("bucketId")
                        : null);
            }
        }
        return mAllImages;
    }

    void onSelect(int index) {
        if (index >= 0 && index < mAllImages.getCount()) {
            IImage img = mAllImages.getImageAt(index);
            if (img == null) {
                return;
            }

            if (isPickIntent()) {
                launchCropperOrFinish(img);
            } else {
                Uri targetUri = img.fullSizeImageUri();
                Uri thisUri = getIntent().getData();
                if (thisUri != null) {
                    String bucket = thisUri.getQueryParameter("bucketId");
                    if (bucket != null) {
                        targetUri = targetUri.buildUpon()
                                .appendQueryParameter("bucketId", bucket)
                                .build();
                    }
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);

                if (img instanceof VideoObject) {
                    intent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }

                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    // sdcard removal??
                }
            }
        }
    }

    private class CreateContextMenuListener implements
            View.OnCreateContextMenuListener {
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenu.ContextMenuInfo menuInfo) {
            if (mSelectedImageGetter.getCurrentImage() == null) {
                return;
            }

            boolean isImage = ImageManager.isImage(
                    mSelectedImageGetter.getCurrentImage());
            if (isImage) {
                menu.add(0, 0, 0, R.string.view).setOnMenuItemClickListener(
                        new MenuItem.OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                onSelect(mGvs.mCurrentSelection);
                                return true;
                            }
                        });
            }

            menu.setHeaderTitle(isImage
                    ? R.string.context_menu_header
                    : R.string.video_context_menu_header);
            if ((mInclusion & (ImageManager.INCLUDE_IMAGES
                    | ImageManager.INCLUDE_VIDEOS)) != 0) {
                MenuHelper.MenuItemsResult r = MenuHelper.addImageMenuItems(
                        menu,
                        MenuHelper.INCLUDE_ALL,
                        isImage,
                        ImageGallery2.this,
                        mHandler,
                        mDeletePhotoRunnable,
                        new MenuHelper.MenuInvoker() {
                            public void run(MenuHelper.MenuCallback cb) {
                                cb.run(mSelectedImageGetter
                                        .getCurrentImageUri(),
                                        mSelectedImageGetter.getCurrentImage());

                                mGvs.clearCache();
                                mGvs.invalidate();
                                mGvs.requestLayout();
                                mGvs.start();
                                mNoImagesView.setVisibility(
                                        mAllImages.getCount() > 0
                                        ? View.GONE
                                        : View.VISIBLE);
                            }
                        });
                if (r != null) {
                    r.gettingReadyToOpen(menu,
                            mSelectedImageGetter.getCurrentImage());
                }

                if (isImage) {
                    addSlideShowMenu(menu, 1000);
                }
            }
        }
    }
}


