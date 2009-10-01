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
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.Toast;
import android.widget.ZoomButtonsController;

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.gallery.VideoObject;

// This activity can display a whole picture and navigate them in a specific
// gallery. It has two modes: normal mode and slide show mode. In normal mode
// the user view one image at a time, and can click "previous" and "next"
// button to see the previous or next image. In slide show mode it shows one
// image after another, with some transition effect.
public class ReviewImage extends Activity implements View.OnClickListener {
    private static final String STATE_URI = "uri";
    private static final String TAG = "ReviewImage";
    private static final double ASPECT_RATIO = 4.0 / 3.0;

    private ImageGetter mGetter;
    private Uri mSavedUri;
    boolean mPaused = true;
    private boolean mShowControls = true;

    // Choices for what adjacents to load.
    private static final int[] sOrderAdjacents = new int[] {0, 1, -1};

    final GetterHandler mHandler = new GetterHandler();

    private boolean mFullScreenInNormalMode;

    int mCurrentPosition = 0;

    private View mRootView;
    private View mControlBar;
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

    ImageManager.ImageListParam mParam;
    IImageList mAllImages;

    private final ImageViewTouchBase [] mSlideShowImageViews =
            new ImageViewTouchBase[2];

    GestureDetector mGestureDetector;
    private ZoomButtonsController mZoomButtonsController;

    // The image view displayed for normal mode.
    private ImageViewTouch2 mImageView;
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

    private void showOnScreenControls() {

        // If the view has not been attached to the window yet, the
        // zoomButtonControls will not able to show up. So delay it until the
        // view has attached to window.
        if (mRootView.getWindowToken() == null) {
            mHandler.postGetterCallback(new Runnable() {
                public void run() {
                    showOnScreenControls();
                }
            });
            return;
        }

        // we may need to update the next/prev button due to index changing
        updateNextPrevControls();

        if (ImageManager.isImage(mAllImages.getImageAt(mCurrentPosition))) {
            updateZoomButtonsEnabled();
            mZoomButtonsController.setVisible(true);
        } else {
            mZoomButtonsController.setVisible(false);
        }
    }

    private void hideOnScreenControls() {
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

    private void scheduleDismissOnScreenControls() {
        mHandler.removeCallbacks(mDismissOnScreenControlRunner);
        mHandler.postDelayed(mDismissOnScreenControlRunner, 2000);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mZoomButtonsController.isVisible()) {
            scheduleDismissOnScreenControls();
        }
        return super.dispatchTouchEvent(m);
    }

    private void updateZoomButtonsEnabled() {
        ImageViewTouch2 imageView = mImageView;
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

    private void setupOnScreenControls(View rootView, View ownerView) {
        mNextImageView = rootView.findViewById(R.id.next_image);
        mPrevImageView = rootView.findViewById(R.id.prev_image);

        mNextImageView.setOnClickListener(this);
        mPrevImageView.setOnClickListener(this);

        setupZoomButtonController(ownerView);
        setupOnTouchListeners(rootView);
    }

    private void setupZoomButtonController(final View ownerView) {
        mZoomButtonsController = new ZoomButtonsController(ownerView);
        mZoomButtonsController.setAutoDismissed(false);
        mZoomButtonsController.setZoomSpeed(100);
        mZoomButtonsController.setOnZoomListener(
                new ZoomButtonsController.OnZoomListener() {
            public void onVisibilityChanged(boolean visible) {
                if (visible) {
                    updateZoomButtonsEnabled();
                }
            }

            public void onZoom(boolean zoomIn) {
                if (zoomIn) {
                    mImageView.zoomIn();
                } else {
                    mImageView.zoomOut();
                }
                mZoomButtonsController.setVisible(true);
                updateZoomButtonsEnabled();
            }
        });
    }

    private void setupOnTouchListeners(View rootView) {
        mGestureDetector = new GestureDetector(this, new MyGestureListener());

        // If the user touches anywhere on the panel (including the
        // next/prev button). We show the on-screen controls. In addition
        // to that, if the touch is not on the prev/next button, we
        // pass the event to the gesture detector to detect double tap.
        final OnTouchListener buttonListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                scheduleDismissOnScreenControls();
                return false;
            }
        };

        OnTouchListener rootListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                buttonListener.onTouch(v, event);
                mGestureDetector.onTouchEvent(event);

                // We do not use the return value of
                // mGestureDetector.onTouchEvent because we will not receive
                // the "up" event if we return false for the "down" event.
                return true;
            }
        };

        mNextImageView.setOnTouchListener(buttonListener);
        mPrevImageView.setOnTouchListener(buttonListener);
        rootView.setOnTouchListener(rootListener);
    }

    private class MyGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            ImageViewTouch2 imageView = mImageView;
            if (imageView.getScale() > 1F) {
                imageView.postTranslateCenter(-distanceX, -distanceY);
            }
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            showOnScreenControls();
            scheduleDismissOnScreenControls();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            ImageViewTouch2 imageView = mImageView;

            // Switch between the original scale and 3x scale.
            if (imageView.getScale() > 2F) {
                mImageView.zoomTo(1f);
            } else {
                mImageView.zoomToPoint(3f, e.getX(), e.getY());
            }
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

        // These are the menu items already covered by the side buttons.
        int sideButtons = MenuHelper.INCLUDE_DELETE_MENU
                | MenuHelper.INCLUDE_SHARE_MENU
                | MenuHelper.INCLUDE_SET_MENU
                | MenuHelper.INCLUDE_VIEWPLAY_MENU;

        mImageMenuRunnable = MenuHelper.addImageMenuItems(
                menu,
                MenuHelper.INCLUDE_ALL & ~sideButtons,
                ReviewImage.this,
                mHandler,
                mDeletePhotoRunnable,
                new MenuHelper.MenuInvoker() {
                    public void run(final MenuHelper.MenuCallback cb) {
                        if (mPaused) return;
                        IImage image = mAllImages.getImageAt(mCurrentPosition);
                        Uri uri = image.fullSizeImageUri();
                        cb.run(uri, image);

                        mImageView.clear();
                        setImage(mCurrentPosition, false);
                    }
                });

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

        IImage image = mAllImages.getImageAt(mCurrentPosition);
        if (mImageMenuRunnable != null) {
            mImageMenuRunnable.gettingReadyToOpen(menu, image);
        }

        Uri uri = mAllImages.getImageAt(mCurrentPosition).fullSizeImageUri();
        MenuHelper.enableShareMenuItem(menu, MenuHelper.isWhiteListUri(uri));

        MenuHelper.enableShowOnMapMenuItem(menu, MenuHelper.hasLatLngData(image));

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

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.review_image);

        mRootView = findViewById(R.id.root);
        mControlBar = findViewById(R.id.control_bar);
        mImageView = (ImageViewTouch2) findViewById(R.id.image);
        mImageView.setEnableTrackballScroll(true);
        mCache = new BitmapCache(3);
        mImageView.setRecycler(mCache);

        ((PreviewFrameLayout) findViewById(
                R.id.frame_layout)).setAspectRatio(ASPECT_RATIO);


        makeGetter();

        mSlideShowImageViews[0] =
                (ImageViewTouchBase) findViewById(R.id.image1_slideShow);
        mSlideShowImageViews[1] =
                (ImageViewTouchBase) findViewById(R.id.image2_slideShow);
        for (ImageViewTouchBase v : mSlideShowImageViews) {
            v.setVisibility(View.INVISIBLE);
            v.setRecycler(mCache);
        }

        mParam = getIntent().getParcelableExtra(KEY_IMAGE_LIST);

        if (instanceState != null) {
            mSavedUri = instanceState.getParcelable(STATE_URI);
            mShowControls = instanceState.getBoolean(STATE_SHOW_CONTROLS, true);
        } else {
            mSavedUri = getIntent().getData();
        }

        int[] pickIds = {R.id.attach, R.id.cancel};
        int[] reviewIds = {R.id.btn_delete, R.id.btn_share, R.id.btn_set_as,
                R.id.btn_play, R.id.btn_done};
        int[] connectIds = isPickIntent() ? pickIds : reviewIds;
        for (int id : connectIds) {
            View view = mControlBar.findViewById(id);
            view.setOnClickListener(this);
            // Set the LinearLayout of the given button to visible
            ((View) view.getParent()).setVisibility(View.VISIBLE);
        }

        if (mFullScreenInNormalMode) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setupOnScreenControls(findViewById(R.id.mainPanel), mImageView);
    }

    private void setButtonPanelVisibility(int id, int visibility) {
        View button = mControlBar.findViewById(id);
        ((View) button.getParent()).setVisibility(visibility);
    }

    private void updateActionIcons() {
        if (isPickIntent()) return;

        IImage image = mAllImages.getImageAt(mCurrentPosition);
        if (image instanceof VideoObject) {
            setButtonPanelVisibility(R.id.btn_set_as, View.GONE);
            setButtonPanelVisibility(R.id.btn_play, View.VISIBLE);
        } else {
            setButtonPanelVisibility(R.id.btn_set_as, View.VISIBLE);
            setButtonPanelVisibility(R.id.btn_play, View.GONE);
        }
    }

    private void makeGetter() {
        mGetter = new ImageGetter(getContentResolver());
    }

    private IImageList buildImageListFromUri(Uri uri) {
        int sort = ImageManager.SORT_ASCENDING;
        return ImageManager.makeImageList(getContentResolver(), uri, sort);
    }

    private boolean init(Uri uri) {
        if (uri == null) return false;
        mAllImages = (mParam == null)
                ? buildImageListFromUri(uri)
                : ImageManager.makeImageList(getContentResolver(), mParam);
        IImage image = mAllImages.getImageForUri(uri);
        if (image == null) return false;
        mCurrentPosition = mAllImages.getImageIndex(image);
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
        b.putBoolean(STATE_SHOW_CONTROLS, mShowControls);
    }

    @Override
    public void onStart() {
        super.onStart();
        mPaused = false;

        if (!init(mSavedUri)) {
            Log.w(TAG, "init failed: " + mSavedUri);
            finish();
            return;
        }

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

        //show controls only for first time
        setImage(mCurrentPosition, mShowControls);
        mShowControls = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        mPaused = true;

        // mGetter could be null if we call finish() and leave early in
        // onStart().
        if (mGetter != null) {
            mGetter.cancelCurrent();
            mGetter.stop();
            mGetter = null;
        }

        // removing all callback in the message queue
        mHandler.removeAllGetterCallbacks();

        if (mAllImages != null) {
            mSavedUri = getCurrentUri();
            mAllImages.close();
            mAllImages = null;
        }

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
            case R.id.btn_delete:
                MenuHelper.deleteImage(this, mDeletePhotoRunnable,
                        mAllImages.getImageAt(mCurrentPosition));
                break;
            case R.id.btn_play:
                startPlayVideoActivity();
                break;
            case R.id.btn_share: {
                IImage image = mAllImages.getImageAt(mCurrentPosition);
                if (!MenuHelper.isWhiteListUri(image.fullSizeImageUri())) {
                    return;
                }
                startShareMediaActivity(image);
                break;
            }
            case R.id.btn_set_as: {
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
            case R.id.btn_done:
                finish();
                break;
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
                        // image could be null if SD card is removed.
                        if (image == null) {
                            finish();
                        } else {
                            mCurrentPosition = mAllImages.getImageIndex(image);
                            setImage(mCurrentPosition, false);
                        }
                    }
                }
                break;
        }
    }
}

class ImageViewTouch2 extends ImageViewTouchBase {
    private final ReviewImage mViewImage;
    private boolean mEnableTrackballScroll;

    public ImageViewTouch2(Context context) {
        super(context);
        mViewImage = (ReviewImage) context;
    }

    public ImageViewTouch2(Context context, AttributeSet attrs) {
        super(context, attrs);
        mViewImage = (ReviewImage) context;
    }

    public void setEnableTrackballScroll(boolean enable) {
        mEnableTrackballScroll = enable;
    }

    protected void postTranslateCenter(float dx, float dy) {
        super.postTranslate(dx, dy);
        center(true, true);
    }

    private static final float PAN_RATE = 20;

    // This is the time we allow the dpad to change the image position again.
    private long mNextChangePositionTime;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mViewImage.mPaused) return false;

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
                        mViewImage.setResult(ReviewImage.RESULT_OK,
                                 new Intent().setData(img.fullSizeImageUri()));
                        mViewImage.finish();
                    }
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_LEFT: {
                    if (getScale() <= 1F && event.getEventTime()
                            >= mNextChangePositionTime) {
                        nextImagePos = current - 1;
                        mNextChangePositionTime = event.getEventTime() + 500;
                    } else {
                        panBy(PAN_RATE, 0);
                        center(true, false);
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    if (getScale() <= 1F && event.getEventTime()
                            >= mNextChangePositionTime) {
                        nextImagePos = current + 1;
                        mNextChangePositionTime = event.getEventTime() + 500;
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
                    mViewImage.setImage(nextImagePos, true);
                }
           } else if (nextImagePos != -2) {
               center(true, true);
           }
        }

        return super.onKeyDown(keyCode, event);
    }
}
