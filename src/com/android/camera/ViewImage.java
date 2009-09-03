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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import android.widget.ZoomButtonsController;

import java.util.Random;

// This activity can display a whole picture and navigate them in a specific
// gallery. It has two modes: normal mode and slide show mode. In normal mode
// the user view one image at a time, and can click "previous" and "next"
// button to see the previous or next image. In slide show mode it shows one
// image after another, with some transition effect.
public class ViewImage extends Activity implements View.OnClickListener {
    private static final String PREF_SLIDESHOW_REPEAT =
            "pref_gallery_slideshow_repeat_key";
    private static final String PREF_SHUFFLE_SLIDESHOW =
            "pref_gallery_slideshow_shuffle_key";
    private static final String STATE_URI = "uri";
    private static final String STATE_SLIDESHOW = "slideshow";
    private static final String EXTRA_SLIDESHOW = "slideshow";
    private static final String TAG = "ViewImage";

    private ImageGetter mGetter;
    private Uri mSavedUri;
    private boolean mPaused = true;
    private boolean mShowControls = true;

    // Choices for what adjacents to load.
    private static final int[] sOrderAdjacents = new int[] {0, 1, -1};
    private static final int[] sOrderSlideshow = new int[] {0};

    final GetterHandler mHandler = new GetterHandler();

    private final Random mRandom = new Random(System.currentTimeMillis());
    private int [] mShuffleOrder = null;
    private boolean mUseShuffleOrder = false;
    private boolean mSlideShowLoop = false;

    static final int MODE_NORMAL = 1;
    static final int MODE_SLIDESHOW = 2;
    private int mMode = MODE_NORMAL;

    private boolean mFullScreenInNormalMode;
    private boolean mShowActionIcons;
    private View mActionIconPanel;

    private int mSlideShowInterval;
    private int mLastSlideShowImage;
    int mCurrentPosition = 0;

    // represents which style animation to use
    private int mAnimationIndex;
    private Animation [] mSlideShowInAnimation;
    private Animation [] mSlideShowOutAnimation;

    private SharedPreferences mPrefs;

    private View mNextImageView;
    private View mPrevImageView;
    private final Animation mHideNextImageViewAnimation =
            new AlphaAnimation(1F, 0F);
    private final Animation mHidePrevImageViewAnimation =
            new AlphaAnimation(1F, 0F);
    private final Animation mShowNextImageViewAnimation =
            new AlphaAnimation(0F, 1F);
    private final Animation mShowPrevImageViewAnimation =
            new AlphaAnimation(0F, 1F);

    public static final String KEY_IMAGE_LIST = "image_list";
    private static final String STATE_SHOW_CONTROLS = "show_controls";

    IImageList mAllImages;

    // this is used to store the state of the image list. Right now, it is the
    // image list itself.
    private IImageList mAllImagesState;

    private int mSlideShowImageCurrent = 0;
    private final ImageViewTouchBase [] mSlideShowImageViews =
            new ImageViewTouchBase[2];

    GestureDetector mGestureDetector;
    private ZoomButtonsController mZoomButtonsController;

    // The image view displayed for normal mode.
    private ImageViewTouch mImageView;
    // This is the cache for thumbnail bitmaps.
    private BitmapCache mCache;
    private MenuHelper.MenuItemsResult mImageMenuRunnable;
    private final Runnable mDismissOnScreenControlRunner = new Runnable() {
        public void run() {
            hideOnScreenControls();
        }
    };

    private void updateNextPrevControls() {
        boolean showPrev = mCurrentPosition > 0;
        boolean showNext = mCurrentPosition < mAllImages.getCount() - 1;

        boolean prevIsVisible = mPrevImageView.getVisibility() == View.VISIBLE;
        boolean nextIsVisible = mNextImageView.getVisibility() == View.VISIBLE;

        if (showPrev && !prevIsVisible) {
            Animation a = mShowPrevImageViewAnimation;
            a.setDuration(500);
            mPrevImageView.startAnimation(a);
            mPrevImageView.setVisibility(View.VISIBLE);
        } else if (!showPrev && prevIsVisible) {
            Animation a = mHidePrevImageViewAnimation;
            a.setDuration(500);
            mPrevImageView.startAnimation(a);
            mPrevImageView.setVisibility(View.GONE);
        }

        if (showNext && !nextIsVisible) {
            Animation a = mShowNextImageViewAnimation;
            a.setDuration(500);
            mNextImageView.startAnimation(a);
            mNextImageView.setVisibility(View.VISIBLE);
        } else if (!showNext && nextIsVisible) {
            Animation a = mHideNextImageViewAnimation;
            a.setDuration(500);
            mNextImageView.startAnimation(a);
            mNextImageView.setVisibility(View.GONE);
        }
    }

    private void hideOnScreenControls() {
        if (mShowActionIcons
                && mActionIconPanel.getVisibility() == View.VISIBLE) {
            Animation animation = new AlphaAnimation(1, 0);
            animation.setDuration(500);
            mActionIconPanel.startAnimation(animation);
            mActionIconPanel.setVisibility(View.INVISIBLE);
        }

        if (mNextImageView.getVisibility() == View.VISIBLE) {
            Animation a = mHideNextImageViewAnimation;
            a.setDuration(500);
            mNextImageView.startAnimation(a);
            mNextImageView.setVisibility(View.INVISIBLE);
        }

        if (mPrevImageView.getVisibility() == View.VISIBLE) {
            Animation a = mHidePrevImageViewAnimation;
            a.setDuration(500);
            mPrevImageView.startAnimation(a);
            mPrevImageView.setVisibility(View.INVISIBLE);
        }

        mZoomButtonsController.setVisible(false);
    }

    private void showOnScreenControls() {
        if (mPaused) return;
        // If the view has not been attached to the window yet, the
        // zoomButtonControls will not able to show up. So delay it until the
        // view has attached to window.
        if (mActionIconPanel.getWindowToken() == null) {
            mHandler.postGetterCallback(new Runnable() {
                public void run() {
                    showOnScreenControls();
                }
            });
            return;
        }
        updateNextPrevControls();

        IImage image = mAllImages.getImageAt(mCurrentPosition);
        if (image instanceof VideoObject) {
            mZoomButtonsController.setVisible(false);
        } else {
            updateZoomButtonsEnabled();
            mZoomButtonsController.setVisible(true);
        }

        if (mShowActionIcons
                && mActionIconPanel.getVisibility() != View.VISIBLE) {
            Animation animation = new AlphaAnimation(0, 1);
            animation.setDuration(500);
            mActionIconPanel.startAnimation(animation);
            mActionIconPanel.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mPaused) return true;
        if (mZoomButtonsController.isVisible()
                && mZoomButtonsController.onTouch(null, m)) {
            scheduleDismissOnScreenControls();
        }
        if (!super.dispatchTouchEvent(m)) {
            return mGestureDetector.onTouchEvent(m);
        }
        return true;
    }

    private void updateZoomButtonsEnabled() {
        ImageViewTouch imageView = mImageView;
        float scale = imageView.getScale();
        mZoomButtonsController.setZoomInEnabled(scale < imageView.mMaxZoom);
        mZoomButtonsController.setZoomOutEnabled(scale > 1);
    }

    @Override
    protected void onDestroy() {
        // This is necessary to make the ZoomButtonsController unregister
        // its configuration change receiver.
        if (mZoomButtonsController != null) {
            mZoomButtonsController.setVisible(false);
        }
        super.onDestroy();
    }

    private void scheduleDismissOnScreenControls() {
        mHandler.removeCallbacks(mDismissOnScreenControlRunner);
        mHandler.postDelayed(mDismissOnScreenControlRunner, 2000);
    }

    private void setupOnScreenControls(final View rootView) {
        final OnTouchListener otListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                scheduleDismissOnScreenControls();
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    showOnScreenControls();
                }
                return false;
            }
        };
        rootView.setOnTouchListener(otListener);

        setupZoomButtonController(rootView, otListener);

        mNextImageView = findViewById(R.id.next_image);
        mPrevImageView = findViewById(R.id.prev_image);

        mNextImageView.setOnClickListener(this);
        mPrevImageView.setOnClickListener(this);
        mNextImageView.setOnTouchListener(otListener);
        mPrevImageView.setOnTouchListener(otListener);
    }

    private void setupZoomButtonController(
            final View rootView, final OnTouchListener otListener) {

        mGestureDetector = new GestureDetector(this, new MyGestureListener());
        mZoomButtonsController = new ZoomButtonsController(rootView);
        mZoomButtonsController.setAutoDismissed(false);
        mZoomButtonsController.setZoomSpeed(100);
        mZoomButtonsController.setOnZoomListener(
                new ZoomButtonsController.OnZoomListener() {
            public void onVisibilityChanged(boolean visible) {
                if (visible) {
                    updateZoomButtonsEnabled();
                } else {
                    rootView.setOnTouchListener(otListener);
                }
            }

            public void onZoom(boolean zoomIn) {
                if (zoomIn) {
                    mImageView.zoomIn();
                } else {
                    mImageView.zoomOut();
                }
                updateZoomButtonsEnabled();
            }
        });
    }

    private class MyGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            ImageViewTouch imageView = mImageView;
            if (imageView.getScale() > 1F) {
                imageView.postTranslateCenter(-distanceX, -distanceY);
            }
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            setMode(MODE_NORMAL);
            return true;
        }
    }

    boolean isPickIntent() {
        String action = getIntent().getAction();
        return (Intent.ACTION_PICK.equals(action)
                || Intent.ACTION_GET_CONTENT.equals(action));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem item = menu.add(Menu.CATEGORY_SECONDARY, 203, 0,
                                 R.string.slide_show);
        item.setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                setMode(MODE_SLIDESHOW);
                mLastSlideShowImage = mCurrentPosition;
                loadNextImage(mCurrentPosition, 0, true);
                return true;
            }
        });
        item.setIcon(android.R.drawable.ic_menu_slideshow);

        mImageMenuRunnable = MenuHelper.addImageMenuItems(
                menu,
                MenuHelper.INCLUDE_ALL,
                ViewImage.this,
                mHandler,
                mDeletePhotoRunnable,
                new MenuHelper.MenuInvoker() {
                    public void run(final MenuHelper.MenuCallback cb) {
                        if (mPaused) return;
                        setMode(MODE_NORMAL);

                        IImage image = mAllImages.getImageAt(mCurrentPosition);
                        Uri uri = image.fullSizeImageUri();
                        cb.run(uri, image);

                        mImageView.clear();
                        setImage(mCurrentPosition, false);
                    }
                });

        item = menu.add(Menu.CATEGORY_SECONDARY, 203, 1000,
                R.string.camerasettings);
        item.setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent preferences = new Intent();
                preferences.setClass(ViewImage.this, GallerySettings.class);
                startActivity(preferences);
                return true;
            }
        });
        item.setAlphabeticShortcut('p');
        item.setIcon(android.R.drawable.ic_menu_preferences);

        // Hidden menu just so the shortcut will bring up the zoom controls
        // the string resource is a placeholder
        menu.add(Menu.CATEGORY_SECONDARY, 203, 0, R.string.camerasettings)
                .setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                showOnScreenControls();
                return true;
            }
        })
        .setAlphabeticShortcut('z')
        .setVisible(false);

        return true;
    }

    protected Runnable mDeletePhotoRunnable = new Runnable() {
        public void run() {
            mAllImages.removeImageAt(mCurrentPosition);
            if (mAllImages.getCount() == 0) {
                finish();
                return;
            } else {
                if (mCurrentPosition == mAllImages.getCount()) {
                    mCurrentPosition -= 1;
                }
            }
            mImageView.clear();
            mCache.clear();  // Because the position number is changed.
            setImage(mCurrentPosition, true);
        }
    };

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        super.onPrepareOptionsMenu(menu);
        if (mPaused) return false;

        setMode(MODE_NORMAL);
        if (mImageMenuRunnable != null) {
            mImageMenuRunnable.gettingReadyToOpen(menu,
                    mAllImages.getImageAt(mCurrentPosition));
        }

        Uri uri = mAllImages.getImageAt(mCurrentPosition).fullSizeImageUri();
        MenuHelper.enableShareMenuItem(menu, MenuHelper.isWhiteListUri(uri));

        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        boolean b = super.onMenuItemSelected(featureId, item);
        if (mImageMenuRunnable != null) {
            mImageMenuRunnable.aboutToCall(item,
                    mAllImages.getImageAt(mCurrentPosition));
        }
        return b;
    }

    void setImage(int pos, boolean showControls) {
        mCurrentPosition = pos;

        Bitmap b = mCache.getBitmap(pos);
        if (b != null) {
            IImage image = mAllImages.getImageAt(pos);
            mImageView.setImageRotateBitmapResetBase(
                    new RotateBitmap(b, image.getDegreesRotated()), true);
            updateZoomButtonsEnabled();
        }

        ImageGetterCallback cb = new ImageGetterCallback() {
            public void completed() {
            }

            public boolean wantsThumbnail(int pos, int offset) {
                return !mCache.hasBitmap(pos + offset);
            }

            public boolean wantsFullImage(int pos, int offset) {
                return offset == 0;
            }

            public int fullImageSizeToUse(int pos, int offset) {
                // this number should be bigger so that we can zoom.  we may
                // need to get fancier and read in the fuller size image as the
                // user starts to zoom.
                // Originally the value is set to 480 in order to avoid OOM.
                // Now we set it to 2048 because of using
                // native memory allocation for Bitmaps.
                final int imageViewSize = 2048;
                return imageViewSize;
            }

            public int [] loadOrder() {
                return sOrderAdjacents;
            }

            public void imageLoaded(int pos, int offset, RotateBitmap bitmap,
                                    boolean isThumb) {
                // shouldn't get here after onPause()

                // We may get a result from a previous request. Ignore it.
                if (pos != mCurrentPosition) {
                    bitmap.recycle();
                    return;
                }

                if (isThumb) {
                    mCache.put(pos + offset, bitmap.getBitmap());
                }
                if (offset == 0) {
                    // isThumb: We always load thumb bitmap first, so we will
                    // reset the supp matrix for then thumb bitmap, and keep
                    // the supp matrix when the full bitmap is loaded.
                    mImageView.setImageRotateBitmapResetBase(bitmap, isThumb);
                    updateZoomButtonsEnabled();
                }
            }
        };

        // Could be null if we're stopping a slide show in the course of pausing
        if (mGetter != null) {
            mGetter.setPosition(pos, cb, mAllImages, mHandler);
        }
        updateActionIcons();
        if (showControls) showOnScreenControls();
        scheduleDismissOnScreenControls();
    }

    @Override
    public void onCreate(Bundle instanceState) {
        super.onCreate(instanceState);

        Intent intent = getIntent();
        mFullScreenInNormalMode = intent.getBooleanExtra(
                MediaStore.EXTRA_FULL_SCREEN, true);
        mShowActionIcons = intent.getBooleanExtra(
                MediaStore.EXTRA_SHOW_ACTION_ICONS, true);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.viewimage);

        mImageView = (ImageViewTouch) findViewById(R.id.image);
        mImageView.setEnableTrackballScroll(true);
        mCache = new BitmapCache(3);
        mImageView.setRecycler(mCache);

        makeGetter();

        mAnimationIndex = -1;

        mSlideShowInAnimation = new Animation[] {
            makeInAnimation(R.anim.transition_in),
            makeInAnimation(R.anim.slide_in),
            makeInAnimation(R.anim.slide_in_vertical),
        };

        mSlideShowOutAnimation = new Animation[] {
            makeOutAnimation(R.anim.transition_out),
            makeOutAnimation(R.anim.slide_out),
            makeOutAnimation(R.anim.slide_out_vertical),
        };

        mSlideShowImageViews[0] =
                (ImageViewTouchBase) findViewById(R.id.image1_slideShow);
        mSlideShowImageViews[1] =
                (ImageViewTouchBase) findViewById(R.id.image2_slideShow);
        for (ImageViewTouchBase v : mSlideShowImageViews) {
            v.setVisibility(View.INVISIBLE);
            v.setRecycler(mCache);
        }

        mActionIconPanel = findViewById(R.id.action_icon_panel);

        Uri uri = getIntent().getData();
        IImageList imageList = getIntent().getParcelableExtra(KEY_IMAGE_LIST);
        boolean slideshow = intent.getBooleanExtra(EXTRA_SLIDESHOW, false);

        if (instanceState != null) {
            uri = instanceState.getParcelable(STATE_URI);
            slideshow = instanceState.getBoolean(STATE_SLIDESHOW, false);
            mShowControls = instanceState.getBoolean(STATE_SHOW_CONTROLS, true);
        }

        if (!init(uri, imageList)) {
            finish();
            return;
        }

        // We only show action icons for URIs that we know we can share and
        // delete. Although we get read permission (for the images) from
        // applications like MMS, we cannot pass the permission to other
        // activities due to the current framework design.
        if (!MenuHelper.isWhiteListUri(uri)) {
            mShowActionIcons = false;
        }

        if (mShowActionIcons) {
            int[] pickIds = {R.id.attach, R.id.cancel};
            int[] normalIds = {R.id.setas, R.id.play, R.id.share, R.id.discard};
            int[] connectIds = isPickIntent() ? pickIds : normalIds;
            for (int id : connectIds) {
                View view = mActionIconPanel.findViewById(id);
                view.setVisibility(View.VISIBLE);
                view.setOnClickListener(this);
            }
        }

        // Don't show the "delete" icon for SingleImageList.
        if (ImageManager.isSingleImageMode(uri.toString())) {
            mActionIconPanel.findViewById(R.id.discard).setVisibility(View.GONE);
        }

        if (slideshow) {
            setMode(MODE_SLIDESHOW);
        } else {
            if (mFullScreenInNormalMode) {
                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            if (mShowActionIcons) {
                mActionIconPanel.setVisibility(View.VISIBLE);
            }
        }

        setupOnScreenControls(findViewById(R.id.abs));
    }

    private void updateActionIcons() {
        if (isPickIntent()) return;

        IImage image = mAllImages.getImageAt(mCurrentPosition);
        View panel = mActionIconPanel;
        if (image instanceof VideoObject) {
            panel.findViewById(R.id.setas).setVisibility(View.GONE);
            panel.findViewById(R.id.play).setVisibility(View.VISIBLE);
        } else {
            panel.findViewById(R.id.setas).setVisibility(View.VISIBLE);
            panel.findViewById(R.id.play).setVisibility(View.GONE);
        }
    }

    private Animation makeInAnimation(int id) {
        Animation inAnimation = AnimationUtils.loadAnimation(this, id);
        return inAnimation;
    }

    private Animation makeOutAnimation(int id) {
        Animation outAnimation = AnimationUtils.loadAnimation(this, id);
        return outAnimation;
    }

    private static int getPreferencesInteger(
            SharedPreferences prefs, String key, int defaultValue) {
        String value = prefs.getString(key, null);
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            Log.e(TAG, "couldn't parse preference: " + value, ex);
            return defaultValue;
        }
    }

    void setMode(int mode) {
        if (mMode == mode) {
            return;
        }
        View slideshowPanel = findViewById(R.id.slideShowContainer);
        View normalPanel = findViewById(R.id.abs);

        Window win = getWindow();
        mMode = mode;
        if (mode == MODE_SLIDESHOW) {
            slideshowPanel.setVisibility(View.VISIBLE);
            normalPanel.setVisibility(View.GONE);

            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            mImageView.clear();
            mActionIconPanel.setVisibility(View.GONE);

            slideshowPanel.getRootView().requestLayout();

            // The preferences we want to read:
            //   mUseShuffleOrder
            //   mSlideShowLoop
            //   mAnimationIndex
            //   mSlideShowInterval

            mUseShuffleOrder = mPrefs.getBoolean(PREF_SHUFFLE_SLIDESHOW, false);
            mSlideShowLoop = mPrefs.getBoolean(PREF_SLIDESHOW_REPEAT, false);
            mAnimationIndex = getPreferencesInteger(
                    mPrefs, "pref_gallery_slideshow_transition_key", 0);
            mSlideShowInterval = getPreferencesInteger(
                    mPrefs, "pref_gallery_slideshow_interval_key", 3) * 1000;
            if (mUseShuffleOrder) {
                generateShuffleOrder();
            }
        } else {
            slideshowPanel.setVisibility(View.GONE);
            normalPanel.setVisibility(View.VISIBLE);

            win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (mFullScreenInNormalMode) {
                win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else {
                win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            if (mGetter != null) {
                mGetter.cancelCurrent();
            }

            if (mShowActionIcons) {
                Animation animation = new AlphaAnimation(0F, 1F);
                animation.setDuration(500);
                mActionIconPanel.setAnimation(animation);
                mActionIconPanel.setVisibility(View.VISIBLE);
            }

            ImageViewTouchBase dst = mImageView;
            dst.mLastXTouchPos = -1;
            dst.mLastYTouchPos = -1;

            for (ImageViewTouchBase ivt : mSlideShowImageViews) {
                ivt.clear();
            }

            mShuffleOrder = null;

            // mGetter null is a proxy for being paused
            if (mGetter != null) {
                setImage(mCurrentPosition, true);
            }
        }
    }

    private void generateShuffleOrder() {
        if (mShuffleOrder == null
                || mShuffleOrder.length != mAllImages.getCount()) {
            mShuffleOrder = new int[mAllImages.getCount()];
            for (int i = 0, n = mShuffleOrder.length; i < n; i++) {
                mShuffleOrder[i] = i;
            }
        }

        for (int i = mShuffleOrder.length - 1; i >= 0; i--) {
            int r = mRandom.nextInt(i + 1);
            if (r != i) {
                int tmp = mShuffleOrder[r];
                mShuffleOrder[r] = mShuffleOrder[i];
                mShuffleOrder[i] = tmp;
            }
        }
    }

    private void loadNextImage(final int requestedPos, final long delay,
                               final boolean firstCall) {
        if (firstCall && mUseShuffleOrder) {
            generateShuffleOrder();
        }

        final long targetDisplayTime = System.currentTimeMillis() + delay;

        ImageGetterCallback cb = new ImageGetterCallback() {
            public void completed() {
            }

            public boolean wantsThumbnail(int pos, int offset) {
                return true;
            }

            public boolean wantsFullImage(int pos, int offset) {
                return false;
            }

            public int [] loadOrder() {
                return sOrderSlideshow;
            }

            public int fullImageSizeToUse(int pos, int offset) {
                return 480; // TODO compute this
            }

            public void imageLoaded(final int pos, final int offset,
                    final RotateBitmap bitmap, final boolean isThumb) {
                long timeRemaining = Math.max(0,
                        targetDisplayTime - System.currentTimeMillis());
                mHandler.postDelayedGetterCallback(new Runnable() {
                    public void run() {
                        if (mMode == MODE_NORMAL) {
                            return;
                        }

                        ImageViewTouchBase oldView =
                                mSlideShowImageViews[mSlideShowImageCurrent];

                        if (++mSlideShowImageCurrent
                                == mSlideShowImageViews.length) {
                            mSlideShowImageCurrent = 0;
                        }

                        ImageViewTouchBase newView =
                                mSlideShowImageViews[mSlideShowImageCurrent];
                        newView.setVisibility(View.VISIBLE);
                        newView.setImageRotateBitmapResetBase(bitmap, true);
                        newView.bringToFront();

                        int animation = 0;

                        if (mAnimationIndex == -1) {
                            int n = mRandom.nextInt(
                                    mSlideShowInAnimation.length);
                            animation = n;
                        } else {
                            animation = mAnimationIndex;
                        }

                        Animation aIn = mSlideShowInAnimation[animation];
                        newView.startAnimation(aIn);
                        newView.setVisibility(View.VISIBLE);

                        Animation aOut = mSlideShowOutAnimation[animation];
                        oldView.setVisibility(View.INVISIBLE);
                        oldView.startAnimation(aOut);

                        mCurrentPosition = requestedPos;

                        if (mCurrentPosition == mLastSlideShowImage
                                && !firstCall) {
                            if (mSlideShowLoop) {
                                if (mUseShuffleOrder) {
                                    generateShuffleOrder();
                                }
                            } else {
                                setMode(MODE_NORMAL);
                                return;
                            }
                        }

                        loadNextImage(
                                (mCurrentPosition + 1) % mAllImages.getCount(),
                                mSlideShowInterval, false);
                    }
                }, timeRemaining);
            }
        };
        // Could be null if we're stopping a slide show in the course of pausing
        if (mGetter != null) {
            int pos = requestedPos;
            if (mShuffleOrder != null) {
                pos = mShuffleOrder[pos];
            }
            mGetter.setPosition(pos, cb, mAllImages, mHandler);
        }
    }

    private void makeGetter() {
        mGetter = new ImageGetter();
    }

    private IImageList buildImageListFromUri(Uri uri) {
        String sortOrder = mPrefs.getString(
                "pref_gallery_sort_key", "descending");
        int sort = sortOrder.equals("ascending")
                ? ImageManager.SORT_ASCENDING
                : ImageManager.SORT_DESCENDING;
        return ImageManager.makeImageList(uri, getContentResolver(), sort);
    }

    private boolean init(Uri uri, IImageList imageList) {
        if (uri == null) return false;
        mAllImagesState = (imageList == null)
                ? buildImageListFromUri(uri)
                : imageList;
        mAllImages = mAllImagesState;
        mAllImages.open(getContentResolver());
        IImage image = mAllImages.getImageForUri(uri);
        if (image == null) return false;
        mCurrentPosition = mAllImages.getImageIndex(image);
        mLastSlideShowImage = mCurrentPosition;
        return true;
    }

    private Uri getCurrentUri() {
        if (mAllImages.getCount() == 0) return null;
        IImage image = mAllImages.getImageAt(mCurrentPosition);
        return image.fullSizeImageUri();
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putParcelable(STATE_URI,
                mAllImages.getImageAt(mCurrentPosition).fullSizeImageUri());
        b.putBoolean(STATE_SLIDESHOW, mMode == MODE_SLIDESHOW);
    }

    @Override
    public void onStart() {
        super.onStart();
        mPaused = false;

        init(mSavedUri, mAllImagesState);

        // normally this will never be zero but if one "backs" into this
        // activity after removing the sdcard it could be zero.  in that
        // case just "finish" since there's nothing useful that can happen.
        int count = mAllImages.getCount();
        if (count == 0) {
            finish();
            return;
        } else if (count <= mCurrentPosition) {
            mCurrentPosition = count - 1;
        }

        if (mGetter == null) {
            makeGetter();
        }

        if (mMode == MODE_SLIDESHOW) {
            loadNextImage(mCurrentPosition, 0, true);
        } else {  // MODE_NORMAL
            setImage(mCurrentPosition, mShowControls);
            mShowControls = false;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mPaused = true;

        mGetter.cancelCurrent();
        mGetter.stop();
        mGetter = null;
        setMode(MODE_NORMAL);

        // removing all callback in the message queue
        mHandler.removeAllGetterCallbacks();

        mSavedUri = getCurrentUri();

        mAllImages.deactivate();
        mAllImages.close();
        mAllImages = null;

        hideOnScreenControls();
        mImageView.clear();
        mCache.clear();

        for (ImageViewTouchBase iv : mSlideShowImageViews) {
            iv.clear();
        }
    }

    private void startShareMediaActivity(IImage image) {
        boolean isVideo = image instanceof VideoObject;
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType(image.getMimeType());
        intent.putExtra(Intent.EXTRA_STREAM, image.fullSizeImageUri());
        try {
            startActivity(Intent.createChooser(intent, getText(
                    isVideo ? R.string.sendVideo : R.string.sendImage)));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, isVideo
                    ? R.string.no_way_to_share_image
                    : R.string.no_way_to_share_video,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startPlayVideoActivity() {
        IImage image = mAllImages.getImageAt(mCurrentPosition);
        Intent intent = new Intent(
                Intent.ACTION_VIEW, image.fullSizeImageUri());
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + image.fullSizeImageUri(), ex);
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.discard:
                MenuHelper.deletePhoto(this, mDeletePhotoRunnable);
                break;
            case R.id.play:
                startPlayVideoActivity();
                break;
            case R.id.share: {
                IImage image = mAllImages.getImageAt(mCurrentPosition);
                if (!MenuHelper.isWhiteListUri(image.fullSizeImageUri())) {
                    return;
                }
                startShareMediaActivity(image);
                break;
            }
            case R.id.setas: {
                IImage image = mAllImages.getImageAt(mCurrentPosition);
                Intent intent = Util.createSetAsIntent(image);
                try {
                    startActivity(Intent.createChooser(
                            intent, getText(R.string.setImage)));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(this, R.string.no_way_to_share_video,
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case R.id.next_image:
                moveNextOrPrevious(1);
                break;
            case R.id.prev_image:
                moveNextOrPrevious(-1);
                break;
        }
    }

    private void moveNextOrPrevious(int delta) {
        int nextImagePos = mCurrentPosition + delta;
        if ((0 <= nextImagePos) && (nextImagePos < mAllImages.getCount())) {
            setImage(nextImagePos, true);
            showOnScreenControls();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        switch (requestCode) {
            case MenuHelper.RESULT_COMMON_MENU_CROP:
                if (resultCode == RESULT_OK) {
                    // The CropImage activity passes back the Uri of the
                    // cropped image as the Action rather than the Data.
                    mSavedUri = Uri.parse(data.getAction());

                    // if onStart() runs before, then set the returned
                    // image as currentImage.
                    if (mAllImages != null) {
                        IImage image = mAllImages.getImageForUri(mSavedUri);
                        mCurrentPosition = mAllImages.getImageIndex(image);
                        setImage(mCurrentPosition, false);
                    }
                }
                break;
        }
    }
}

class ImageViewTouch extends ImageViewTouchBase {
    private final ViewImage mViewImage;
    private boolean mEnableTrackballScroll;

    public ImageViewTouch(Context context) {
        super(context);
        mViewImage = (ViewImage) context;
    }

    public ImageViewTouch(Context context, AttributeSet attrs) {
        super(context, attrs);
        mViewImage = (ViewImage) context;
    }

    public void setEnableTrackballScroll(boolean enable) {
        mEnableTrackballScroll = enable;
    }

    protected void postTranslateCenter(float dx, float dy) {
        super.postTranslate(dx, dy);
        center(true, true);
    }

    static final float PAN_RATE = 20;

    // This is the time we allow the dpad to change the image position again.
    static long nextChangePositionTime;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Don't respond to arrow keys if trackball scrolling is not enabled
        if (!mEnableTrackballScroll) {
            if ((keyCode >= KeyEvent.KEYCODE_DPAD_UP)
                    && (keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
                return super.onKeyDown(keyCode, event);
            }
        }

        int current = mViewImage.mCurrentPosition;

        int nextImagePos = -2; // default no next image
        try {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER: {
                    if (mViewImage.isPickIntent()) {
                        IImage img = mViewImage.mAllImages
                                .getImageAt(mViewImage.mCurrentPosition);
                        mViewImage.setResult(ViewImage.RESULT_OK,
                                 new Intent().setData(img.fullSizeImageUri()));
                        mViewImage.finish();
                    }
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_LEFT: {
                    if (getScale() <= 1F && event.getEventTime()
                            >= nextChangePositionTime) {
                        nextImagePos = current - 1;
                        nextChangePositionTime = event.getEventTime() + 500;
                    } else {
                        panBy(PAN_RATE, 0);
                        center(true, false);
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    if (getScale() <= 1F && event.getEventTime()
                            >= nextChangePositionTime) {
                        nextImagePos = current + 1;
                        nextChangePositionTime = event.getEventTime() + 500;
                    } else {
                        panBy(-PAN_RATE, 0);
                        center(true, false);
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    panBy(0, PAN_RATE);
                    center(false, true);
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    panBy(0, -PAN_RATE);
                    center(false, true);
                    return true;
                }
                case KeyEvent.KEYCODE_DEL:
                    MenuHelper.deletePhoto(
                            mViewImage, mViewImage.mDeletePhotoRunnable);
                    break;
            }
        } finally {
            if (nextImagePos >= 0
                    && nextImagePos < mViewImage.mAllImages.getCount()) {
                synchronized (mViewImage) {
                    mViewImage.setMode(ViewImage.MODE_NORMAL);
                    mViewImage.setImage(nextImagePos, true);
                }
           } else if (nextImagePos != -2) {
               center(true, true);
           }
        }

        return super.onKeyDown(keyCode, event);
    }
}

// This is a cache for Bitmap displayed in ViewImage (normal mode, thumb only).
class BitmapCache implements ImageViewTouchBase.Recycler {
    public static class Entry {
        int mPos;
        Bitmap mBitmap;
        public Entry() {
            clear();
        }
        public void clear() {
            mPos = -1;
            mBitmap = null;
        }
    }

    private final Entry[] mCache;

    public BitmapCache(int size) {
        mCache = new Entry[size];
        for (int i = 0; i < mCache.length; i++) {
            mCache[i] = new Entry();
        }
    }

    // Given the position, find the associated entry. Returns null if there is
    // no such entry.
    private Entry findEntry(int pos) {
        for (Entry e : mCache) {
            if (pos == e.mPos) {
                return e;
            }
        }
        return null;
    }

    // Returns the thumb bitmap if we have it, otherwise return null.
    public synchronized Bitmap getBitmap(int pos) {
        Entry e = findEntry(pos);
        if (e != null) {
            return e.mBitmap;
        }
        return null;
    }

    public synchronized void put(int pos, Bitmap bitmap) {
        // First see if we already have this entry.
        if (findEntry(pos) != null) {
            return;
        }

        // Find the best entry we should replace.
        // See if there is any empty entry.
        // Otherwise assuming sequential access, kick out the entry with the
        // greatest distance.
        Entry best = null;
        int maxDist = -1;
        for (Entry e : mCache) {
            if (e.mPos == -1) {
                best = e;
                break;
            } else {
                int dist = Math.abs(pos - e.mPos);
                if (dist > maxDist) {
                    maxDist = dist;
                    best = e;
                }
            }
        }

        // Recycle the image being kicked out.
        // This only works because our current usage is sequential, so we
        // do not happen to recycle the image being displayed.
        if (best.mBitmap != null) {
            best.mBitmap.recycle();
        }

        best.mPos = pos;
        best.mBitmap = bitmap;
    }

    // Recycle all bitmaps in the cache and clear the cache.
    public synchronized void clear() {
        for (Entry e : mCache) {
            if (e.mBitmap != null) {
                e.mBitmap.recycle();
            }
            e.clear();
        }
    }

    // Returns whether the bitmap is in the cache.
    public synchronized boolean hasBitmap(int pos) {
        Entry e = findEntry(pos);
        return (e != null);
    }

    // Recycle the bitmap if it's not in the cache.
    // The input must be non-null.
    public synchronized void recycle(Bitmap b) {
        for (Entry e : mCache) {
            if (e.mPos != -1) {
                if (e.mBitmap == b) {
                    return;
                }
            }
        }
        b.recycle();
    }
}
