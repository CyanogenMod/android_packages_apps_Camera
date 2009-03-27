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

import android.content.BroadcastReceiver;
import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.Config;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.widget.Scroller;

import java.util.Calendar;
import java.util.GregorianCalendar;

import com.android.camera.ImageManager.IImage;

public class ImageGallery2 extends Activity {
    private static final String TAG = "ImageGallery2";
    private ImageManager.IImageList mAllImages;
    private int mInclusion;
    private boolean mSortAscending = false;
    private View mNoImagesView;
    public final static int CROP_MSG = 2;
    public final static int VIEW_MSG = 3;
    private static final String INSTANCE_STATE_TAG = "scrollY";

    private Dialog mMediaScanningDialog;

    private MenuItem mSlideShowItem;
    private SharedPreferences mPrefs;
    private long mVideoSizeLimit = Long.MAX_VALUE;

    public ImageGallery2() {
    }

    BroadcastReceiver mReceiver = null;

    Handler mHandler = new Handler();
    boolean mLayoutComplete;
    boolean mPausing = false;
    boolean mStopThumbnailChecking = false;

    CameraThread mThumbnailCheckThread;
    GridViewSpecial mGvs;

    @Override
    public void onCreate(Bundle icicle) {
        if (Config.LOGV) Log.v(TAG, "onCreate");
        super.onCreate(icicle);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);  // must be called before setContentView()
        setContentView(R.layout.image_gallery_2);

        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_gallery_title);
        if (Config.LOGV)
            Log.v(TAG, "findView... " + findViewById(R.id.loading_indicator));

        mGvs = (GridViewSpecial) findViewById(R.id.grid);
        mGvs.requestFocus();

        if (isPickIntent()) {
            mVideoSizeLimit = getIntent().getLongExtra(
                    MediaStore.EXTRA_SIZE_LIMIT, Long.MAX_VALUE);
            mGvs.mVideoSizeLimit = mVideoSizeLimit;
        } else {
            mVideoSizeLimit = Long.MAX_VALUE;
            mGvs.mVideoSizeLimit = mVideoSizeLimit;
            mGvs.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                    if (mSelectedImageGetter.getCurrentImage() == null)
                        return;

                    boolean isImage = ImageManager.isImage(mSelectedImageGetter.getCurrentImage());
                    if (isImage) {
                        menu.add(0, 0, 0, R.string.view).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                mGvs.onSelect(mGvs.mCurrentSelection);
                                return true;
                            }
                        });
                    }

                    menu.setHeaderTitle(isImage ? R.string.context_menu_header
                            : R.string.video_context_menu_header);
                    if ((mInclusion & (ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS)) != 0) {
                        MenuHelper.MenuItemsResult r = MenuHelper.addImageMenuItems(
                                menu,
                                MenuHelper.INCLUDE_ALL,
                                isImage,
                                ImageGallery2.this,
                                mHandler,
                                mDeletePhotoRunnable,
                                new MenuHelper.MenuInvoker() {
                                    public void run(MenuHelper.MenuCallback cb) {
                                        cb.run(mSelectedImageGetter.getCurrentImageUri(), mSelectedImageGetter.getCurrentImage());

                                        mGvs.clearCache();
                                        mGvs.invalidate();
                                        mGvs.requestLayout();
                                        mGvs.start();
                                        mNoImagesView.setVisibility(mAllImages.getCount() > 0 ? View.GONE : View.VISIBLE);
                                    }
                                });
                        if (r != null)
                            r.gettingReadyToOpen(menu, mSelectedImageGetter.getCurrentImage());

                        if (isImage) {
                            addSlideShowMenu(menu, 1000);
                        }
                    }
                }
            });
        }
    }

    private MenuItem addSlideShowMenu(Menu menu, int position) {
        return menu.add(0, 207, position, R.string.slide_show)
        .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                ImageManager.IImage img = mSelectedImageGetter.getCurrentImage();
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
                        targetUri = targetUri.buildUpon().appendQueryParameter("bucketId", bucket).build();
                    }
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);
                intent.putExtra("slideshow", true);
                startActivity(intent);
                return true;
            }
        })
        .setIcon(android.R.drawable.ic_menu_slideshow);
    }

    private Runnable mDeletePhotoRunnable = new Runnable() {
        public void run() {
            mGvs.clearCache();
            IImage currentImage = mSelectedImageGetter.getCurrentImage();
            if (currentImage != null) {
                mAllImages.removeImage(currentImage);
            }
            mGvs.invalidate();
            mGvs.requestLayout();
            mGvs.start();
            mNoImagesView.setVisibility(mAllImages.isEmpty() ? View.VISIBLE : View.GONE);
        }
    };

    private SelectedImageGetter mSelectedImageGetter = new SelectedImageGetter() {
        public Uri getCurrentImageUri() {
            ImageManager.IImage image = getCurrentImage();
            if (image != null)
                return image.fullSizeImageUri();
            else
                return null;
        }
        public ImageManager.IImage getCurrentImage() {
            int currentSelection = mGvs.mCurrentSelection;
            if (currentSelection < 0 || currentSelection >= mAllImages.getCount())
                return null;
            else
                return mAllImages.getImageAt(currentSelection);
        }
    };

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mTargetScroll = mGvs.getScrollY();
    }

    private Runnable mLongPressCallback = new Runnable() {
        public void run() {
            mGvs.select(-2, false);
            mGvs.showContextMenu();
        }
    };

    private boolean canHandleEvent() {
        // Don't process event in pause state.
        return (!mPausing) && (mGvs.mCurrentSpec != null);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!canHandleEvent())  return false;
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            mGvs.select(-2, false);
            // The keyUp doesn't get called when the longpress menu comes up. We only get here when the user
            // lets go of the center key before the longpress menu comes up.
            mHandler.removeCallbacks(mLongPressCallback);

            // open the photo
            if (mSelectedImageGetter.getCurrentImage() != null) {
                mGvs.onSelect(mGvs.mCurrentSelection);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!canHandleEvent())  return false;
        
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
                    if ((sel / columns) != (sel+columns / columns)) {
                        sel = Math.min(count-1, sel + columns);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    pressed = true;
                    mHandler.postDelayed(mLongPressCallback, ViewConfiguration.getLongPressTimeout());
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
                GridViewSpecial.ImageBlockManager ibm = mGvs.mImageBlockManager;
                if (ibm != null) {
                    mGvs.mImageBlockManager.getVisibleRange(range);
                    int topPos = range[0];
                    android.graphics.Rect r = mGvs.getRectForPosition(topPos);
                    if (r.top < mGvs.getScrollY())
                        topPos += columns;
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
        }
        else
            return super.onKeyDown(keyCode, event);
    }

    private boolean isPickIntent() {
        String action = getIntent().getAction();
        return (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action));
    }

    private void launchCropperOrFinish(ImageManager.IImage img) {
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
            if (cropValue.equals("circle"))
                newExtras.putString("circleCrop", "true");

            Intent cropIntent = new Intent();
            cropIntent.setData(img.fullSizeImageUri());
            cropIntent.setClass(this, CropImage.class);
            cropIntent.putExtras(newExtras);

            /* pass through any extras that were passed in */
            cropIntent.putExtras(myExtras);
            if (Config.LOGV) Log.v(TAG, "startSubActivity " + cropIntent);
            startActivityForResult(cropIntent, CROP_MSG);
        } else {
            Intent result = new Intent(null, img.fullSizeImageUri());
            if (myExtras != null && myExtras.getString("return-data") != null) {
                Bitmap bitmap = img.fullSizeBitmap(1000);
                if (bitmap != null)
                    result.putExtra("data", bitmap);
            }
            setResult(RESULT_OK, result);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Config.LOGV)
            Log.v(TAG, "onActivityResult: " + requestCode + "; resultCode is " + resultCode + "; data is " + data);
        switch (requestCode) {
            case MenuHelper.RESULT_COMMON_MENU_CROP: {
                if (resultCode == RESULT_OK) {
                    // The CropImage activity passes back the Uri of the cropped image as
                    // the Action rather than the Data.
                    Uri dataUri = Uri.parse(data.getAction());
                    rebake(false,false);
                    IImage image = mAllImages.getImageForUri(dataUri);
                    if (image != null ) {
                        int rowId = image.getRow();
                        mGvs.select(rowId, false);
                    }
                }
                break;
            }
            case CROP_MSG: {
                if (Config.LOGV) Log.v(TAG, "onActivityResult " + data);
                if (resultCode == RESULT_OK) {
                    setResult(resultCode, data);
                    finish();
                }
                break;
            }
            case VIEW_MSG: {
                if (Config.LOGV)
                    Log.v(TAG, "got VIEW_MSG with " + data);
                ImageManager.IImage img = mAllImages.getImageForUri(data.getData());
                launchCropperOrFinish(img);
                break;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mPausing = true;
        stopCheckingThumbnails();
        mGvs.onPause();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        // Now that we've paused the threads that are using the cursor it is safe
        // to deactivate it.
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
            mAllImages = ImageManager.instance().emptyImageList();
        } else {
            mAllImages = allImages(!unmounted);
            if (Config.LOGV)
                Log.v(TAG, "mAllImages is now " + mAllImages);
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

        try {
            mGvs.setSizeChoice(Integer.parseInt(mPrefs.getString("pref_gallery_size_key", "1")), mTargetScroll);

            String sortOrder = mPrefs.getString("pref_gallery_sort_key", null);
            if (sortOrder != null) {
                mSortAscending = sortOrder.equals("ascending");
            }
        } catch (Exception ex) {

        }
        mPausing = false;

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addDataScheme("file");

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
                    Toast.makeText(ImageGallery2.this, getResources().getString(R.string.wait), 5000);
                    rebake(true, false);
                } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                    Toast.makeText(ImageGallery2.this, getResources().getString(R.string.wait), 5000);
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
        registerReceiver(mReceiver, intentFilter);

        MenuHelper.requestOrientation(this, mPrefs);

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
        final long t1 = System.currentTimeMillis();
        mThumbnailCheckThread = new CameraThread(new Runnable() {
            public void run() {
                android.content.res.Resources resources = getResources();
                final TextView progressTextView = (TextView) findViewById(R.id.loading_text);
                final String progressTextFormatString = resources.getString(R.string.loading_progress_format_string);

                PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock mWakeLock =
                    pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                                   "ImageGallery2.checkThumbnails");
                mWakeLock.acquire();
                ImageManager.IImageList.ThumbCheckCallback r = new ImageManager.IImageList.ThumbCheckCallback() {
                    boolean mDidSetProgress = false;

                    public boolean checking(final int count, final int maxCount) {
                        if (mStopThumbnailChecking) {
                            return false;
                        }

                        if (!mLayoutComplete) {
                            return true;
                        }

                        if (!mDidSetProgress) {
                            mHandler.post(new Runnable() {
                                public void run() {
                                    findViewById(R.id.loading_indicator).setVisibility(View.VISIBLE);
                                }
                            });
                            mDidSetProgress = true;
                        }
                        mGvs.postInvalidate();

                        if (System.currentTimeMillis() - startTime > 1000) {
                            mHandler.post(new Runnable() {
                                public void run() {
                                    String s = String.format(progressTextFormatString, maxCount - count);
                                    progressTextView.setText(s);
                                }
                            });
                        }

                        return !mPausing;
                    }
                };
                ImageManager.IImageList imageList = allImages(true);
                imageList.checkThumbnails(r, imageList.getCount());
                mWakeLock.release();
                mThumbnailCheckThread = null;
                mHandler.post(new Runnable() {
                    public void run() {
                        findViewById(R.id.loading_indicator).setVisibility(View.GONE);
                    }
                });
                long t2 = System.currentTimeMillis();
                if (Config.LOGV)
                    Log.v(TAG, "check thumbnails thread finishing; took " + (t2-t1));
            }
        });

        mThumbnailCheckThread.setName("check_thumbnails");
        mThumbnailCheckThread.start();
        mThumbnailCheckThread.toBackground();

        ImageManager.IImageList list = allImages(true);
        mNoImagesView.setVisibility(list.getCount() > 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
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
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        if ((mInclusion & ImageManager.INCLUDE_IMAGES) != 0) {
            boolean videoSelected = isVideoSelected();
            // TODO: Only enable slide show if there is at least one image in the folder.
            if (mSlideShowItem != null) {
                mSlideShowItem.setEnabled(!videoSelected);
            }
        }

        return true;
    }

    private boolean isImageSelected() {
        IImage image = mSelectedImageGetter.getCurrentImage();
        return (image != null) && ImageManager.isImage(image);
    }

    private boolean isVideoSelected() {
        IImage image = mSelectedImageGetter.getCurrentImage();
        return (image != null) && ImageManager.isVideo(image);
    }

    private synchronized ImageManager.IImageList allImages(boolean assumeMounted) {
        if (mAllImages == null) {
            mNoImagesView = findViewById(R.id.no_images);

            mInclusion = ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS;

            Intent intent = getIntent();
            if (intent != null) {
                String type = intent.resolveType(this);
                if (Config.LOGV)
                    Log.v(TAG, "allImages... type is " + type);
                TextView leftText = (TextView) findViewById(R.id.left_text);
                if (type != null) {
                    if (type.equals("vnd.android.cursor.dir/image") || type.equals("image/*")) {
                        mInclusion = ImageManager.INCLUDE_IMAGES;
                        if (isPickIntent())
                            leftText.setText(R.string.pick_photos_gallery_title);
                        else
                            leftText.setText(R.string.photos_gallery_title);
                    }
                    if (type.equals("vnd.android.cursor.dir/video") || type.equals("video/*")) {
                        mInclusion = ImageManager.INCLUDE_VIDEOS;
                        if (isPickIntent())
                            leftText.setText(R.string.pick_videos_gallery_title);
                        else
                            leftText.setText(R.string.videos_gallery_title);
                    }
                }
                Bundle extras = intent.getExtras();
                String title = extras!= null ? extras.getString("windowTitle") : null;
                if (title != null && title.length() > 0) {
                    leftText.setText(title);
                }

                if (extras != null) {
                    mInclusion = (ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS)
                        & extras.getInt("mediaTypes", mInclusion);
                }

                if (extras != null && extras.getBoolean("pick-drm")) {
                    Log.d(TAG, "pick-drm is true");
                    mInclusion = ImageManager.INCLUDE_DRM_IMAGES;
                }
            }
            if (Config.LOGV)
                Log.v(TAG, "computing images... mSortAscending is " + mSortAscending
                        + "; assumeMounted is " + assumeMounted);
            Uri uri = getIntent().getData();
            if (!assumeMounted) {
                mAllImages = ImageManager.instance().emptyImageList();
            } else {
                mAllImages = ImageManager.instance().allImages(
                        ImageGallery2.this,
                        getContentResolver(),
                        ImageManager.DataLocation.NONE,
                        mInclusion,
                        mSortAscending ? ImageManager.SORT_ASCENDING : ImageManager.SORT_DESCENDING,
                        uri != null ? uri.getQueryParameter("bucketId") : null);
            }
        }
        return mAllImages;
    }

    public static class GridViewSpecial extends View {
        private ImageGallery2 mGallery;
        private Paint   mGridViewPaint = new Paint();

        private ImageBlockManager mImageBlockManager;
        private Handler mHandler;

        private LayoutSpec mCurrentSpec;
        private boolean mShowSelection = false;
        private int mCurrentSelection = -1;
        private boolean mCurrentSelectionPressed;

        private boolean mDirectionBiasDown = true;
        private final static boolean sDump = false;

        private long mVideoSizeLimit;

        class LayoutSpec {
            LayoutSpec(int cols, int w, int h, int leftEdgePadding, int rightEdgePadding, int intercellSpacing) {
                mColumns = cols;
                mCellWidth = w;
                mCellHeight = h;
                mLeftEdgePadding = leftEdgePadding;
                mRightEdgePadding = rightEdgePadding;
                mCellSpacing = intercellSpacing;
            }
            int mColumns;
            int mCellWidth, mCellHeight;
            int mLeftEdgePadding, mRightEdgePadding;
            int mCellSpacing;
        };

        private LayoutSpec [] mCellSizeChoices = new LayoutSpec[] {
                new LayoutSpec(0, 67, 67, 14, 14, 8),
                new LayoutSpec(0, 92, 92, 14, 14, 8),
        };
        private int mSizeChoice = 1;

        // Use a number like 100 or 200 here to allow the user to
        // overshoot the start (top) or end (bottom) of the gallery.
        // After overshooting the gallery will animate back to the
        // appropriate location.
        private int mMaxOvershoot = 0; // 100;
        private int mMaxScrollY;
        private int mMinScrollY;

        private boolean mFling = true;
        private Scroller mScroller = null;

        private GestureDetector mGestureDetector;

        public void dump() {
            if (Config.LOGV){
                Log.v(TAG, "mSizeChoice is " + mCellSizeChoices[mSizeChoice]);
                Log.v(TAG, "mCurrentSpec.width / mCellHeight are " + mCurrentSpec.mCellWidth + " / " + mCurrentSpec.mCellHeight);
            }
            mImageBlockManager.dump();
        }

        private void init(Context context) {
            mGridViewPaint.setColor(0xFF000000);
            mGallery = (ImageGallery2) context;

            setVerticalScrollBarEnabled(true);
            initializeScrollbars(context.obtainStyledAttributes(android.R.styleable.View));

            mGestureDetector = new GestureDetector(context, new SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    if (mScroller != null && !mScroller.isFinished()) {
                        mScroller.forceFinished(true);
                        return false;
                    }

                    int pos = computeSelectedIndex(e);
                    if (pos >= 0 && pos < mGallery.mAllImages.getCount()) {
                        select(pos, true);
                    } else {
                        select(-1, false);
                    }
                    if (mImageBlockManager != null)
                        mImageBlockManager.repaintSelection(mCurrentSelection);
                    invalidate();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    final float maxVelocity = 2500;
                    if (velocityY > maxVelocity)
                        velocityY = maxVelocity;
                    else if (velocityY < -maxVelocity)
                        velocityY = -maxVelocity;

                    select(-1, false);
                    if (mFling) {
                        mScroller = new Scroller(getContext());
                        mScroller.fling(0, mScrollY, 0, -(int)velocityY, 0, 0, 0, mMaxScrollY);
                        computeScroll();
                    }
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    performLongClick();
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    select(-1, false);
                    scrollBy(0, (int)distanceY);
                    invalidate();
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    select(mCurrentSelection, false);
                    int index = computeSelectedIndex(e);
                    if (index >= 0 && index < mGallery.mAllImages.getCount()) {
                        onSelect(index);
                        return true;
                    }
                    return false;
                }
            });
//          mGestureDetector.setIsLongpressEnabled(false);
        }

        public GridViewSpecial(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            init(context);
        }

        public GridViewSpecial(Context context, AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        public GridViewSpecial(Context context) {
            super(context);
            init(context);
        }

        @Override
        protected int computeVerticalScrollRange() {
            return mMaxScrollY + getHeight();
        }

        public void setSizeChoice(int choice, int scrollY) {
            mSizeChoice = choice;
            clearCache();
            scrollTo(0, scrollY);
            requestLayout();
            invalidate();
        }

        /**
         *
         * @param newSel -2 means use old selection, -1 means remove selection
         * @param newPressed
         */
        public void select(int newSel, boolean newPressed) {
            if (newSel == -2) {
                newSel = mCurrentSelection;
            }
            int oldSel = mCurrentSelection;
            if ((oldSel == newSel) && (mCurrentSelectionPressed == newPressed))
                return;

            mShowSelection = (newSel != -1);
            mCurrentSelection = newSel;
            mCurrentSelectionPressed = newPressed;
            if (mImageBlockManager != null) {
                mImageBlockManager.repaintSelection(oldSel);
                mImageBlockManager.repaintSelection(newSel);
            }

            if (newSel != -1)
                ensureVisible(newSel);
        }

        private void ensureVisible(int pos) {
            android.graphics.Rect r = getRectForPosition(pos);
            int top = getScrollY();
            int bot = top + getHeight();

            if (r.bottom > bot) {
                mScroller = new Scroller(getContext());
                mScroller.startScroll(mScrollX, mScrollY, 0, r.bottom - getHeight() - mScrollY, 200);
                computeScroll();
            } else if (r.top < top) {
                mScroller = new Scroller(getContext());
                mScroller.startScroll(mScrollX, mScrollY, 0, r.top - mScrollY, 200);
                computeScroll();
            }
            invalidate();
        }

        public void start() {
            if (mGallery.mLayoutComplete) {
                if (mImageBlockManager == null) {
                    mImageBlockManager = new ImageBlockManager();
                    mImageBlockManager.moveDataWindow(true, true);
                }
            }
        }

        public void onPause() {
            mScroller = null;
            if (mImageBlockManager != null) {
                mImageBlockManager.onPause();
                mImageBlockManager = null;
            }
        }

        public void clearCache() {
            if (mImageBlockManager != null) {
                mImageBlockManager.onPause();
                mImageBlockManager = null;
            }
        }


        @Override
        public void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            if (mGallery.isFinishing() || mGallery.mPausing) {
                return;
            }

            clearCache();

            mCurrentSpec = mCellSizeChoices[mSizeChoice];
            int oldColumnCount = mCurrentSpec.mColumns;

            int width = right - left;
            mCurrentSpec.mColumns = 1;
            width -= mCurrentSpec.mCellWidth;
            mCurrentSpec.mColumns += width / (mCurrentSpec.mCellWidth + mCurrentSpec.mCellSpacing);

            mCurrentSpec.mLeftEdgePadding = ((right - left) - ((mCurrentSpec.mColumns - 1) * mCurrentSpec.mCellSpacing) - (mCurrentSpec.mColumns * mCurrentSpec.mCellWidth)) / 2;
            mCurrentSpec.mRightEdgePadding = mCurrentSpec.mLeftEdgePadding;

            int rows = (mGallery.mAllImages.getCount() + mCurrentSpec.mColumns - 1) / mCurrentSpec.mColumns;
            mMaxScrollY = mCurrentSpec.mCellSpacing + (rows * (mCurrentSpec.mCellSpacing + mCurrentSpec.mCellHeight)) - (bottom - top) + mMaxOvershoot;
            mMinScrollY = 0 - mMaxOvershoot;

            mGallery.mLayoutComplete = true;

            start();

            if (mGallery.mSortAscending && mGallery.mTargetScroll == 0) {
                scrollTo(0, mMaxScrollY - mMaxOvershoot);
            } else {
                if (oldColumnCount != 0) {
                    int y = mGallery.mTargetScroll * oldColumnCount / mCurrentSpec.mColumns;
                    Log.v(TAG, "target was " + mGallery.mTargetScroll + " now " + y);
                    scrollTo(0, y);
                }
            }
        }

        Bitmap scaleTo(int width, int height, Bitmap b) {
            Matrix m = new Matrix();
            m.setScale((float)width/64F, (float)height/64F);
            Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
            if (b2 != b)
                b.recycle();
            return b2;
        }

        private class ImageBlockManager {
            private ImageLoader mLoader;
            private int mBlockCacheFirstBlockNumber = 0;

            // mBlockCache is an array with a starting point which is not necessaryily
            // zero.  The first element of the array is indicated by mBlockCacheStartOffset.
            private int mBlockCacheStartOffset = 0;
            private ImageBlock [] mBlockCache;

            private static final int sRowsPerPage    = 6;   // should compute this

            private static final int sPagesPreCache  = 2;
            private static final int sPagesPostCache = 2;

            private int mWorkCounter = 0;
            private boolean mDone = false;

            private Thread mWorkerThread;
            private Bitmap mMissingImageThumbnailBitmap;
            private Bitmap mMissingVideoThumbnailBitmap;

            private Drawable mVideoOverlay;
            private Drawable mVideoMmsErrorOverlay;

            public void dump() {
                synchronized (ImageBlockManager.this) {
                    StringBuilder line1 = new StringBuilder();
                    StringBuilder line2 = new StringBuilder();
                    if (Config.LOGV)
                        Log.v(TAG, ">>> mBlockCacheFirstBlockNumber: " + mBlockCacheFirstBlockNumber + " " + mBlockCacheStartOffset);
                    for (int i = 0; i < mBlockCache.length; i++) {
                        int index = (mBlockCacheStartOffset + i) % mBlockCache.length;
                        ImageBlock block = mBlockCache[index];
                        block.dump(line1, line2);
                    }
                    if (Config.LOGV){
                        Log.v(TAG, line1.toString());
                        Log.v(TAG, line2.toString());
                    }
                }
            }

            ImageBlockManager() {
                mLoader = new ImageLoader(mHandler, 1);

                mBlockCache = new ImageBlock[sRowsPerPage * (sPagesPreCache + sPagesPostCache + 1)];
                for (int i = 0; i < mBlockCache.length; i++) {
                    mBlockCache[i] = new ImageBlock();
                }

                mWorkerThread = new Thread(new Runnable() {
                    public void run() {
                        while (true) {
                            int workCounter;
                            synchronized (ImageBlockManager.this) {
                                workCounter = mWorkCounter;
                            }
                            if (mDone) {
                                if (Config.LOGV)
                                    Log.v(TAG, "stopping the loader here " + Thread.currentThread().getName());
                                if (mLoader != null) {
                                    mLoader.stop();
                                }
                                if (mBlockCache != null) {
                                    for (int i = 0; i < mBlockCache.length; i++) {
                                        ImageBlock block = mBlockCache[i];
                                        if (block != null) {
                                            block.recycleBitmaps();
                                            mBlockCache[i] = null;
                                        }
                                    }
                                }
                                mBlockCache = null;
                                mBlockCacheStartOffset = 0;
                                mBlockCacheFirstBlockNumber = 0;

                                break;
                            }

                            loadNext();

                            synchronized (ImageBlockManager.this) {
                                if ((workCounter == mWorkCounter) && (! mDone)) {
                                    try {
                                        ImageBlockManager.this.wait();
                                    } catch (InterruptedException ex) {
                                    }
                                }
                            }
                        }
                    }
                });
                mWorkerThread.setName("image-block-manager");
                mWorkerThread.start();
            }

            // Create this bitmap lazily, and only once for all the ImageBlocks to use
            public Bitmap getErrorBitmap(ImageManager.IImage image) {
                if (ImageManager.isImage(image)) {
                    if (mMissingImageThumbnailBitmap == null) {
                        mMissingImageThumbnailBitmap = BitmapFactory.decodeResource(GridViewSpecial.this.getResources(),
                                R.drawable.ic_missing_thumbnail_picture);
                    }
                    return mMissingImageThumbnailBitmap;
                } else {
                    if (mMissingVideoThumbnailBitmap == null) {
                        mMissingVideoThumbnailBitmap = BitmapFactory.decodeResource(GridViewSpecial.this.getResources(),
                                R.drawable.ic_missing_thumbnail_video);
                    }
                    return mMissingVideoThumbnailBitmap;
                }
            }

            private ImageBlock getBlockForPos(int pos) {
                synchronized (ImageBlockManager.this) {
                    int blockNumber = pos / mCurrentSpec.mColumns;
                    int delta = blockNumber - mBlockCacheFirstBlockNumber;
                    if (delta >= 0 && delta < mBlockCache.length) {
                        int index = (mBlockCacheStartOffset + delta) % mBlockCache.length;
                        ImageBlock b = mBlockCache[index];
                        return b;
                    }
                }
                return null;
            }

            private void repaintSelection(int pos) {
                synchronized (ImageBlockManager.this) {
                    ImageBlock b = getBlockForPos(pos);
                    if (b != null) {
                        b.repaintSelection();
                    }
                }
            }

            private void onPause() {
                synchronized (ImageBlockManager.this) {
                    mDone = true;
                    ImageBlockManager.this.notify();
                }
                if (mWorkerThread != null) {
                    try {
                        mWorkerThread.join();
                        mWorkerThread = null;
                    } catch (InterruptedException ex) {
                        //
                    }
                }
                Log.v(TAG, "/ImageBlockManager.onPause");
            }

            private void getVisibleRange(int [] range) {
                // try to work around a possible bug in the VM wherein this appears to be null
                try {
                    synchronized (ImageBlockManager.this) {
                        int blockLength = mBlockCache.length;
                        boolean lookingForStart = true;
                        ImageBlock prevBlock = null;
                        for (int i = 0; i < blockLength; i++) {
                            int index = (mBlockCacheStartOffset + i) % blockLength;
                            ImageBlock block = mBlockCache[index];
                            if (lookingForStart) {
                                if (block.mIsVisible) {
                                    range[0] = block.mBlockNumber * mCurrentSpec.mColumns;
                                    lookingForStart = false;
                                }
                            } else {
                                if (!block.mIsVisible || i == blockLength - 1) {
                                    range[1] = (prevBlock.mBlockNumber * mCurrentSpec.mColumns) + mCurrentSpec.mColumns - 1;
                                    break;
                                }
                            }
                            prevBlock = block;
                        }
                    }
                } catch (NullPointerException ex) {
                    Log.e(TAG, "this is somewhat null, what up?");
                    range[0] = range[1] = 0;
                }
            }

            private void loadNext() {
                final int blockHeight = (mCurrentSpec.mCellSpacing + mCurrentSpec.mCellHeight);

                final int firstVisBlock = Math.max(0, (mScrollY - mCurrentSpec.mCellSpacing) / blockHeight);
                final int lastVisBlock  = (mScrollY - mCurrentSpec.mCellSpacing + getHeight()) / blockHeight;

//              Log.v(TAG, "firstVisBlock == " + firstVisBlock + "; lastVisBlock == " + lastVisBlock);

                synchronized (ImageBlockManager.this) {
                    ImageBlock [] blocks = mBlockCache;
                    int numBlocks = blocks.length;
                    if (mDirectionBiasDown) {
                        int first = (mBlockCacheStartOffset + (firstVisBlock - mBlockCacheFirstBlockNumber)) % blocks.length;
                        for (int i = 0; i < numBlocks; i++) {
                            int j = first + i;
                            if (j >= numBlocks)
                                j -= numBlocks;
                            ImageBlock b = blocks[j];
                            if (b.startLoading() > 0)
                                break;
                        }
                    } else {
                        int first = (mBlockCacheStartOffset + (lastVisBlock - mBlockCacheFirstBlockNumber)) % blocks.length;
                        for (int i = 0; i < numBlocks; i++) {
                            int j = first - i;
                            if (j < 0)
                                j += numBlocks;
                            ImageBlock b = blocks[j];
                            if (b.startLoading() > 0)
                                break;
                        }
                    }
                    if (sDump)
                        this.dump();
                }
            }

            private void moveDataWindow(boolean directionBiasDown, boolean forceRefresh) {
                final int blockHeight = (mCurrentSpec.mCellSpacing + mCurrentSpec.mCellHeight);

                final int firstVisBlock = (mScrollY - mCurrentSpec.mCellSpacing) / blockHeight;
                final int lastVisBlock  = (mScrollY - mCurrentSpec.mCellSpacing + getHeight()) / blockHeight;

                final int preCache = sPagesPreCache;
                final int startBlock = Math.max(0, firstVisBlock - (preCache * sRowsPerPage));

//              Log.v(TAG, "moveDataWindow directionBiasDown == " + directionBiasDown + "; preCache is " + preCache);
                synchronized (ImageBlockManager.this) {
                    boolean any = false;
                    ImageBlock [] blocks = mBlockCache;
                    int numBlocks = blocks.length;

                    int delta = startBlock - mBlockCacheFirstBlockNumber;

                    mBlockCacheFirstBlockNumber = startBlock;
                    if (Math.abs(delta) > numBlocks || forceRefresh) {
                        for (int i = 0; i < numBlocks; i++) {
                            int blockNum = startBlock + i;
                            blocks[i].setStart(blockNum);
                            any = true;
                        }
                        mBlockCacheStartOffset = 0;
                    } else if (delta > 0) {
                        mBlockCacheStartOffset += delta;
                        if (mBlockCacheStartOffset >= numBlocks)
                            mBlockCacheStartOffset -= numBlocks;

                        for (int i = delta; i > 0; i--) {
                            int index = (mBlockCacheStartOffset + numBlocks - i) % numBlocks;
                            int blockNum = mBlockCacheFirstBlockNumber + numBlocks - i;
                            blocks[index].setStart(blockNum);
                            any = true;
                        }
                    } else if (delta < 0) {
                        mBlockCacheStartOffset += delta;
                        if (mBlockCacheStartOffset < 0)
                            mBlockCacheStartOffset += numBlocks;

                        for (int i = 0; i < -delta; i++) {
                            int index = (mBlockCacheStartOffset + i) % numBlocks;
                            int blockNum = mBlockCacheFirstBlockNumber + i;
                            blocks[index].setStart(blockNum);
                            any = true;
                        }
                    }

                    for (int i = 0; i < numBlocks; i++) {
                        int index = (mBlockCacheStartOffset + i) % numBlocks;
                        ImageBlock block = blocks[index];
                        int blockNum = block.mBlockNumber; // mBlockCacheFirstBlockNumber + i;
                        boolean isVis = blockNum >= firstVisBlock && blockNum <= lastVisBlock;
//                      Log.v(TAG, "blockNum " + blockNum + " setting vis to " + isVis);
                        block.setVisibility(isVis);
                    }

                    if (sDump)
                        mImageBlockManager.dump();

                    if (any) {
                        ImageBlockManager.this.notify();
                        mWorkCounter += 1;
                    }
                }
                if (sDump)
                    dump();
            }

            private void check() {
                ImageBlock [] blocks = mBlockCache;
                int blockLength = blocks.length;

                // check the results
                for (int i = 0; i < blockLength; i++) {
                    int index = (mBlockCacheStartOffset + i) % blockLength;
                    if (blocks[index].mBlockNumber != mBlockCacheFirstBlockNumber + i) {
                        if (blocks[index].mBlockNumber != -1)
                            Log.e(TAG, "at " + i + " block cache corrupted; found " + blocks[index].mBlockNumber + " but wanted " + (mBlockCacheFirstBlockNumber + i) + "; offset is " + mBlockCacheStartOffset);
                    }
                }
                if (true) {
                    StringBuilder sb  = new StringBuilder();
                    for (int i = 0; i < blockLength; i++) {
                        int index = (mBlockCacheStartOffset + i) % blockLength;
                        ImageBlock b = blocks[index];
                        if (b.mRequestedMask != 0)
                            sb.append("X");
                        else
                            sb.append(String.valueOf(b.mBlockNumber) + " ");
                    }
                    if (Config.LOGV)
                        Log.v(TAG, "moveDataWindow " + sb.toString());
                }
            }

            void doDraw(Canvas canvas) {
                synchronized (ImageBlockManager.this) {
                    ImageBlockManager.ImageBlock [] blocks = mBlockCache;
                    int blockCount = 0;

                    if (blocks[0] == null) {
                        return;
                    }

                    final int thisHeight = getHeight();
                    final int thisWidth  = getWidth();
                    final int height = blocks[0].mBitmap.getHeight();
                    final int scrollPos = mScrollY;

                    int currentBlock = (scrollPos < 0) ? ((scrollPos-height+1) / height) : (scrollPos / height);

                    while (true) {
                        final int yPos = currentBlock * height;
                        if (yPos >= scrollPos + thisHeight)
                            break;

                        if (currentBlock < 0) {
                            canvas.drawRect(0, yPos, thisWidth, 0, mGridViewPaint);
                            currentBlock += 1;
                            continue;
                        }
                        int effectiveOffset = (mBlockCacheStartOffset + (currentBlock++ - mBlockCacheFirstBlockNumber)) % blocks.length;
                        if (effectiveOffset < 0 || effectiveOffset >= blocks.length) {
                            break;
                        }

                        ImageBlock block = blocks[effectiveOffset];
                        if (block == null) {
                            break;
                        }
                        synchronized (block) {
                            Bitmap b = block.mBitmap;
                            if (b == null) {
                                break;
                            }
                            canvas.drawBitmap(b, 0, yPos, mGridViewPaint);
                            blockCount += 1;
                        }
                    }
                }
            }

            int blockHeight() {
                return mCurrentSpec.mCellSpacing + mCurrentSpec.mCellHeight;
            }

            private class ImageBlock {
                Drawable mCellOutline;
                Bitmap mBitmap = Bitmap.createBitmap(getWidth(), blockHeight(),
                        Bitmap.Config.RGB_565);;
                Canvas mCanvas = new Canvas(mBitmap);
                Paint mPaint = new Paint();

                int     mBlockNumber;
                int     mRequestedMask;   // columns which have been requested to the loader
                int     mCompletedMask;   // columns which have been completed from the loader
                boolean mIsVisible;

                public void dump(StringBuilder line1, StringBuilder line2) {
                    synchronized (ImageBlock.this) {
//                        Log.v(TAG, "block " + mBlockNumber + " isVis == " + mIsVisible);
                        line2.append(mCompletedMask != 0xF ? 'L' : '_');
                        line1.append(mIsVisible ? 'V' : ' ');
                    }
                }

                ImageBlock() {
                    mPaint.setTextSize(14F);
                    mPaint.setStyle(Paint.Style.FILL);

                    mBlockNumber = -1;
                    mCellOutline = GridViewSpecial.this.getResources().getDrawable(android.R.drawable.gallery_thumb);
                }

                private void recycleBitmaps() {
                    synchronized (ImageBlock.this) {
                        mBitmap.recycle();
                        mBitmap = null;
                    }
                }

                private void cancelExistingRequests() {
                    synchronized (ImageBlock.this) {
                        for (int i = 0; i < mCurrentSpec.mColumns; i++) {
                            int mask = (1 << i);
                            if ((mRequestedMask & mask) != 0) {
                                int pos = (mBlockNumber * mCurrentSpec.mColumns) + i;
                                if (mLoader.cancel(mGallery.mAllImages.getImageAt(pos))) {
                                    mRequestedMask &= ~mask;
                                }
                            }
                        }
                    }
                }

                private void setStart(final int blockNumber) {
                    synchronized (ImageBlock.this) {
                        if (blockNumber == mBlockNumber)
                            return;

                        cancelExistingRequests();

                        mBlockNumber = blockNumber;
                        mRequestedMask = 0;
                        mCompletedMask = 0;
                        mCanvas.drawColor(0xFF000000);
                        mPaint.setColor(0xFFDDDDDD);
                        int imageNumber = blockNumber * mCurrentSpec.mColumns;
                        int lastImageNumber = mGallery.mAllImages.getCount() - 1;

                        int spacing = mCurrentSpec.mCellSpacing;
                        int leftSpacing = mCurrentSpec.mLeftEdgePadding;

                        final int yPos = spacing;

                        for (int col = 0; col < mCurrentSpec.mColumns; col++) {
                            if (imageNumber++ >= lastImageNumber)
                                break;
                            final int xPos = leftSpacing + (col * (mCurrentSpec.mCellWidth + spacing));
                            mCanvas.drawRect(xPos, yPos, xPos+mCurrentSpec.mCellWidth, yPos+mCurrentSpec.mCellHeight, mPaint);
                            paintSel(0, xPos, yPos);
                        }
                    }
                }

                private boolean setVisibility(boolean isVis) {
                    synchronized (ImageBlock.this) {
                        boolean retval = mIsVisible != isVis;
                        mIsVisible = isVis;
                        return retval;
                    }
                }

                private int startLoading() {
                    synchronized (ImageBlock.this) {
                        final int startRow = mBlockNumber;
                        int count = mGallery.mAllImages.getCount();

                        if (startRow == -1)
                            return 0;

                        if ((startRow * mCurrentSpec.mColumns) >= count) {
                            return 0;
                        }

                        int retVal = 0;
                        int base = (mBlockNumber * mCurrentSpec.mColumns);
                        for (int col = 0; col < mCurrentSpec.mColumns; col++) {
                            if ((mCompletedMask & (1 << col)) != 0) {
                                continue;
                            }

                            int spacing = mCurrentSpec.mCellSpacing;
                            int leftSpacing = mCurrentSpec.mLeftEdgePadding;
                            final int yPos = spacing;
                            final int xPos = leftSpacing + (col * (mCurrentSpec.mCellWidth + spacing));

                            int pos = base + col;
                            if (pos >= count)
                                break;

                            ImageManager.IImage image = mGallery.mAllImages.getImageAt(pos);
                            if (image != null) {
//                              Log.v(TAG, "calling loadImage " + (base + col));
                                loadImage(base, col, image, xPos, yPos);
                                retVal += 1;
                            }
                        }
                        return retVal;

                    }
                }

                Bitmap resizeBitmap(Bitmap b) {
                    // assume they're both square for now
                    if (b == null || (b.getWidth() == mCurrentSpec.mCellWidth && b.getHeight() == mCurrentSpec.mCellHeight)) {
                        return b;
                    }
                    float scale = (float) mCurrentSpec.mCellWidth / (float)b.getWidth();
                    Matrix m = new Matrix();
                    m.setScale(scale, scale, b.getWidth(), b.getHeight());
                    Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
                    return b2;
                }

                private void drawBitmap(ImageManager.IImage image, int base, int baseOffset, Bitmap b, int xPos, int yPos) {
                    mCanvas.setBitmap(mBitmap);
                    if (b != null) {
                        // if the image is close to the target size then crop, otherwise scale
                        // both the bitmap and the view should be square but I suppose that could
                        // change in the future.
                        int w = mCurrentSpec.mCellWidth;
                        int h = mCurrentSpec.mCellHeight;

                        int bw = b.getWidth();
                        int bh = b.getHeight();

                        int deltaW = bw - w;
                        int deltaH = bh - h;

                        if (deltaW < 10 && deltaH < 10) {
                            int halfDeltaW = deltaW / 2;
                            int halfDeltaH = deltaH / 2;
                            android.graphics.Rect src = new android.graphics.Rect(0+halfDeltaW, 0+halfDeltaH, bw-halfDeltaW, bh-halfDeltaH);
                            android.graphics.Rect dst = new android.graphics.Rect(xPos, yPos, xPos+w, yPos+h);
                            if (src.width() != dst.width() || src.height() != dst.height()) {
                                if (Config.LOGV){
                                    Log.v(TAG, "nope... width doesn't match " + src.width() + " " + dst.width());
                                    Log.v(TAG, "nope... height doesn't match " + src.height() + " " + dst.height());
                                }
                            }
                            mCanvas.drawBitmap(b, src, dst, mPaint);
                        } else {
                            android.graphics.Rect src = new android.graphics.Rect(0, 0, bw, bh);
                            android.graphics.Rect dst = new android.graphics.Rect(xPos, yPos, xPos+w, yPos+h);
                            mCanvas.drawBitmap(b, src, dst, mPaint);
                        }
                    } else {
                        // If the thumbnail cannot be drawn, put up an error icon instead
                        Bitmap error = mImageBlockManager.getErrorBitmap(image);
                        int width = error.getWidth();
                        int height = error.getHeight();
                        Rect source = new Rect(0, 0, width, height);
                        int left = (mCurrentSpec.mCellWidth - width) / 2 + xPos;
                        int top = (mCurrentSpec.mCellHeight - height) / 2 + yPos;
                        Rect dest = new Rect(left, top, left + width, top + height);
                        mCanvas.drawBitmap(error, source, dest, mPaint);
                    }
                    if (ImageManager.isVideo(image)) {
                        Drawable overlay = null;
                        long size = MenuHelper.getImageFileSize(image);
                        if (size >= 0 && size <= mVideoSizeLimit) {
                            if (mVideoOverlay == null) {
                                mVideoOverlay = getResources().getDrawable(
                                        R.drawable.ic_gallery_video_overlay);
                            }
                            overlay = mVideoOverlay;
                        } else {
                            if (mVideoMmsErrorOverlay == null) {
                                mVideoMmsErrorOverlay = getResources().getDrawable(
                                        R.drawable.ic_error_mms_video_overlay);
                            }
                            overlay = mVideoMmsErrorOverlay;
                            Paint paint = new Paint();
                            paint.setARGB(0x80, 0x00, 0x00, 0x00);
                            mCanvas.drawRect(xPos, yPos, xPos + mCurrentSpec.mCellWidth,
                                    yPos + mCurrentSpec.mCellHeight, paint);
                        }
                        int width = overlay.getIntrinsicWidth();
                        int height = overlay.getIntrinsicHeight();
                        int left = (mCurrentSpec.mCellWidth - width) / 2 + xPos;
                        int top = (mCurrentSpec.mCellHeight - height) / 2 + yPos;
                        Rect newBounds = new Rect(left, top, left + width, top + height);
                        overlay.setBounds(newBounds);
                        overlay.draw(mCanvas);
                    }
                    paintSel(base + baseOffset, xPos, yPos);
                }

                private void repaintSelection() {
                    int count = mGallery.mAllImages.getCount();
                    int startPos = mBlockNumber * mCurrentSpec.mColumns;
                    synchronized (ImageBlock.this) {
                        for (int i = 0; i < mCurrentSpec.mColumns; i++) {
                            int pos = startPos + i;

                            if (pos >= count)
                                break;

                            int row = 0; // i / mCurrentSpec.mColumns;
                            int col = i - (row * mCurrentSpec.mColumns);

                            // this is duplicated from getOrKick (TODO: don't duplicate this code)
                            int spacing = mCurrentSpec.mCellSpacing;
                            int leftSpacing = mCurrentSpec.mLeftEdgePadding;
                            final int yPos = spacing + (row * (mCurrentSpec.mCellHeight + spacing));
                            final int xPos = leftSpacing + (col * (mCurrentSpec.mCellWidth + spacing));

                            paintSel(pos, xPos, yPos);
                        }
                    }
                }

                private void paintSel(int pos, int xPos, int yPos) {
                    int[] stateSet = EMPTY_STATE_SET;
                    if (pos == mCurrentSelection && mShowSelection) {
                        if (mCurrentSelectionPressed) {
                            stateSet = PRESSED_ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET;
                        } else {
                            stateSet = ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET;
                        }
                    }

                    mCellOutline.setState(stateSet);
                    mCanvas.setBitmap(mBitmap);
                    mCellOutline.setBounds(xPos, yPos, xPos+mCurrentSpec.mCellWidth, yPos+mCurrentSpec.mCellHeight);
                    mCellOutline.draw(mCanvas);
                }

                private void loadImage(
                        final int base,
                        final int baseOffset,
                        final ImageManager.IImage image,
                        final int xPos,
                        final int yPos) {
                    synchronized (ImageBlock.this) {
                        final int startBlock = mBlockNumber;
                        final int pos = base + baseOffset;
                        final ImageLoader.LoadedCallback r = new ImageLoader.LoadedCallback() {
                            public void run(Bitmap b) {
                                boolean more = false;
                                synchronized (ImageBlock.this) {
                                    if (startBlock != mBlockNumber) {
//                                      Log.v(TAG, "wanted block " + mBlockNumber + " but got " + startBlock);
                                        return;
                                    }

                                    if (mBitmap == null) {
                                        return;
                                    }

                                    drawBitmap(image, base, baseOffset, b, xPos, yPos);

                                    int mask = (1 << baseOffset);
                                    mRequestedMask &= ~mask;
                                    mCompletedMask |= mask;

                                 // Log.v(TAG, "for " + mBlockNumber + " mRequestedMask is " + String.format("%x", mRequestedMask) + " and mCompletedMask is " + String.format("%x", mCompletedMask));

                                    if (mRequestedMask == 0) {
                                        if (mIsVisible) {
                                            postInvalidate();
                                        }
                                        more = true;
                                    }
                                }
                                if (b != null)
                                    b.recycle();

                                if (more) {
                                    synchronized (ImageBlockManager.this) {
                                        ImageBlockManager.this.notify();
                                        mWorkCounter += 1;
                                    }
                                }
                                if (sDump)
                                    ImageBlockManager.this.dump();
                            }
                        };
                        mRequestedMask |= (1 << baseOffset);
                        mLoader.getBitmap(image, pos, r, mIsVisible, false);
                    }
                }
            }
        }

        public void init(Handler handler) {
            mHandler = handler;
        }

        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (false) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), mGridViewPaint);
                if (Config.LOGV)
                    Log.v(TAG, "painting background w/h " + getWidth() + " / " + getHeight());
                return;
            }

            if (mImageBlockManager != null) {
                mImageBlockManager.doDraw(canvas);
                mImageBlockManager.moveDataWindow(mDirectionBiasDown, false);
            }
        }

        @Override
        public void computeScroll() {
            if (mScroller != null) {
                boolean more = mScroller.computeScrollOffset();
                scrollTo(0, (int)mScroller.getCurrY());
                if (more) {
                    postInvalidate();  // So we draw again
                } else {
                    mScroller = null;
                }
            } else {
                super.computeScroll();
            }
        }

        private android.graphics.Rect getRectForPosition(int pos) {
            int row = pos / mCurrentSpec.mColumns;
            int col = pos - (row * mCurrentSpec.mColumns);

            int left = mCurrentSpec.mLeftEdgePadding + (col * mCurrentSpec.mCellWidth) + (Math.max(0, col-1) * mCurrentSpec.mCellSpacing);
            int top  = (row * mCurrentSpec.mCellHeight) + (row * mCurrentSpec.mCellSpacing);

            return new android.graphics.Rect(left, top, left + mCurrentSpec.mCellWidth + mCurrentSpec.mCellWidth, top + mCurrentSpec.mCellHeight + mCurrentSpec.mCellSpacing);
        }

        int computeSelectedIndex(android.view.MotionEvent ev) {
            int spacing = mCurrentSpec.mCellSpacing;
            int leftSpacing = mCurrentSpec.mLeftEdgePadding;

            int x = (int) ev.getX();
            int y = (int) ev.getY();
            int row = (mScrollY + y - spacing) / (mCurrentSpec.mCellHeight + spacing);
            int col = Math.min(mCurrentSpec.mColumns - 1, (x - leftSpacing) / (mCurrentSpec.mCellWidth + spacing));
            return (row * mCurrentSpec.mColumns) + col;
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent ev) {
            if (!mGallery.canHandleEvent())  return false;
            
            mGestureDetector.onTouchEvent(ev);
            return true;
        }

        private void onSelect(int index) {
            if (index >= 0 && index < mGallery.mAllImages.getCount()) {
                ImageManager.IImage img = mGallery.mAllImages.getImageAt(index);
                if (img == null)
                    return;

                if (mGallery.isPickIntent()) {
                    mGallery.launchCropperOrFinish(img);
                } else {
                    Uri targetUri = img.fullSizeImageUri();
                    Uri thisUri = mGallery.getIntent().getData();
                    if (thisUri != null) {
                        String bucket = thisUri.getQueryParameter("bucketId");
                        if (bucket != null) {
                            targetUri = targetUri.buildUpon().appendQueryParameter("bucketId", bucket).build();
                        }
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);

                    if (img instanceof ImageManager.VideoObject) {
                        intent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    }

                    try {
                        mContext.startActivity(intent);
                    } catch (Exception ex) {
                        // sdcard removal??
                    }
                }
            }
        }

        @Override
        public void scrollBy(int x, int y) {
            scrollTo(x, mScrollY + y);
        }

        Toast mDateLocationToast;
        int [] mDateRange = new int[2];

        private String month(int month) {
            String text = "";
            switch (month) {
                case 0:  text = "January";   break;
                case 1:  text = "February";  break;
                case 2:  text = "March";     break;
                case 3:  text = "April";     break;
                case 4:  text = "May";       break;
                case 5:  text = "June";      break;
                case 6:  text = "July";      break;
                case 7:  text = "August";    break;
                case 8:  text = "September"; break;
                case 9:  text = "October";   break;
                case 10: text = "November";  break;
                case 11: text = "December";  break;
            }
            return text;
        }

        Runnable mToastRunnable = new Runnable() {
            public void run() {
                if (mDateLocationToast != null) {
                    mDateLocationToast.cancel();
                    mDateLocationToast = null;
                }

                int count = mGallery.mAllImages.getCount();
                if (count == 0)
                    return;

                GridViewSpecial.this.mImageBlockManager.getVisibleRange(mDateRange);

                ImageManager.IImage firstImage = mGallery.mAllImages.getImageAt(mDateRange[0]);
                int lastOffset = Math.min(count-1, mDateRange[1]);
                ImageManager.IImage lastImage = mGallery.mAllImages.getImageAt(lastOffset);

                GregorianCalendar dateStart = new GregorianCalendar();
                GregorianCalendar dateEnd   = new GregorianCalendar();

                dateStart.setTimeInMillis(firstImage.getDateTaken());
                dateEnd.setTimeInMillis(lastImage.getDateTaken());

                String text1 = month(dateStart.get(Calendar.MONTH)) + " " + dateStart.get(Calendar.YEAR);
                String text2 = month(dateEnd  .get(Calendar.MONTH)) + " " + dateEnd  .get(Calendar.YEAR);

                String text = text1;
                if (!text2.equals(text1))
                    text = text + " : " + text2;

                mDateLocationToast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
                mDateLocationToast.show();
            }
        };

        @Override
        public void scrollTo(int x, int y) {
            y = Math.min(mMaxScrollY, y);
            y = Math.max(mMinScrollY, y);
            if (y > mScrollY)
                mDirectionBiasDown = true;
            else if (y < mScrollY)
                mDirectionBiasDown = false;
            super.scrollTo(x, y);
        }
    }
}
