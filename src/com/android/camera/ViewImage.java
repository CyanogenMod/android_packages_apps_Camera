/*
 * Copyright (C) 5163 The Android Open Source Project
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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Config;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;
import android.preference.PreferenceManager;

import com.android.camera.ImageManager.IImage;

import java.util.Random;

public class ViewImage extends Activity implements View.OnClickListener
{
    static final String TAG = "ViewImage";
    private ImageGetter mGetter;

    static final boolean sSlideShowHidesStatusBar = true;

    // Choices for what adjacents to load.
    static private final int[] sOrder_adjacents = new int[] { 0, 1, -1 };
    static private final int[] sOrder_slideshow = new int[] { 0 };

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };

    private Random mRandom = new Random(System.currentTimeMillis());
    private int [] mShuffleOrder;
    private boolean mUseShuffleOrder = false;
    private boolean mSlideShowLoop = false;

    private static final int MODE_NORMAL = 1;
    private static final int MODE_SLIDESHOW = 2;
    private int mMode = MODE_NORMAL;
    private boolean mFullScreenInNormalMode;
    private boolean mShowActionIcons;
    private View mActionIconPanel;

    private boolean mSortAscending = false;
    private int mSlideShowInterval;
    private int mLastSlideShowImage;
    private boolean mFirst = true;
    private int mCurrentPosition = 0;
    private boolean mLayoutComplete = false;

    // represents which style animation to use
    private int mAnimationIndex;
    private Animation [] mSlideShowInAnimation;
    private Animation [] mSlideShowOutAnimation;

    private SharedPreferences mPrefs;
    private MenuItem mFlipItem;

    private View mNextImageView, mPrevImageView;
    private Animation mHideNextImageViewAnimation = new AlphaAnimation(1F, 0F);
    private Animation mHidePrevImageViewAnimation = new AlphaAnimation(1F, 0F);
    private Animation mShowNextImageViewAnimation = new AlphaAnimation(0F, 1F);
    private Animation mShowPrevImageViewAnimation = new AlphaAnimation(0F, 1F);


    static final int sPadding = 20;
    static final int sHysteresis = sPadding * 2;
    static final int sBaseScrollDuration = 1000; // ms

    private ImageManager.IImageList mAllImages;

    private int mSlideShowImageCurrent = 0;
    private ImageViewTouch [] mSlideShowImageViews = new ImageViewTouch[2];


    // Array of image views.  The center view is the one the user is focused
    // on.  The one at the zeroth position and the second position reflect
    // the images to the left/right of the center image.
    private ImageViewTouch[] mImageViews = new ImageViewTouch[3];

    // Container for the three image views.  This guy can be "scrolled"
    // to reveal the image prior to and after the center image.
    private ScrollHandler mScroller;

    private MenuHelper.MenuItemsResult mImageMenuRunnable;

    Runnable mDismissOnScreenControlsRunnable;
    ZoomControls mZoomControls;

    public ViewImage() {
    }

    private void updateNextPrevControls() {
        boolean showPrev =  mCurrentPosition > 0;
        boolean showNext = mCurrentPosition < mAllImages.getCount() - 1;

        boolean prevIsVisible = mPrevImageView.getVisibility() == View.VISIBLE;
        boolean nextIsVisible = mNextImageView.getVisibility() == View.VISIBLE;

        if (showPrev && !prevIsVisible) {
            Animation a = mShowPrevImageViewAnimation;
            a.setDuration(500);
            a.startNow();
            mPrevImageView.setAnimation(a);
            mPrevImageView.setVisibility(View.VISIBLE);
        } else if (!showPrev && prevIsVisible) {
            Animation a = mHidePrevImageViewAnimation;
            a.setDuration(500);
            a.startNow();
            mPrevImageView.setAnimation(a);
            mPrevImageView.setVisibility(View.GONE);
        }

        if (showNext && !nextIsVisible) {
            Animation a = mShowNextImageViewAnimation;
            a.setDuration(500);
            a.startNow();
            mNextImageView.setAnimation(a);
            mNextImageView.setVisibility(View.VISIBLE);
        } else if (!showNext && nextIsVisible) {
            Animation a = mHideNextImageViewAnimation;
            a.setDuration(500);
            a.startNow();
            mNextImageView.setAnimation(a);
            mNextImageView.setVisibility(View.GONE);
        }
    }

    private void showOnScreenControls() {
        if (mZoomControls != null) {
            if (mZoomControls.getVisibility() == View.GONE) {
                mZoomControls.show();
                mZoomControls.requestFocus();       // this shouldn't be necessary
            }
            updateNextPrevControls();
            scheduleDismissOnScreenControls();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        boolean sup = super.dispatchTouchEvent(m);
        if (sup == false) {
            if (mMode == MODE_SLIDESHOW) {
                mSlideShowImageViews[mSlideShowImageCurrent].handleTouchEvent(m);
            } else if (mMode == MODE_NORMAL){
                mImageViews[1].handleTouchEvent(m);
            }
            return true;
        }
        return true;
    }

    private void scheduleDismissOnScreenControls() {
        mHandler.removeCallbacks(mDismissOnScreenControlsRunnable);
        mHandler.postDelayed(mDismissOnScreenControlsRunnable, 1500);
    }

    public View getZoomControls() {
        if (mZoomControls == null) {
            mZoomControls = new ZoomControls(this);
            mZoomControls.setVisibility(View.GONE);
            mZoomControls.setZoomSpeed(0);
            mDismissOnScreenControlsRunnable = new Runnable() {
                public void run() {
                    mZoomControls.hide();

                    if (mNextImageView.getVisibility() == View.VISIBLE) {
                        Animation a = mHideNextImageViewAnimation;
                        a.setDuration(500);
                        a.startNow();
                        mNextImageView.setAnimation(a);
                        mNextImageView.setVisibility(View.INVISIBLE);
                    }

                    if (mPrevImageView.getVisibility() == View.VISIBLE) {
                        Animation a = mHidePrevImageViewAnimation;
                        a.setDuration(500);
                        a.startNow();
                        mPrevImageView.setAnimation(a);
                        mPrevImageView.setVisibility(View.INVISIBLE);
                    }
                }
            };
            mZoomControls.setOnZoomInClickListener(new OnClickListener() {
                public void onClick(View v) {
                    mHandler.removeCallbacks(mDismissOnScreenControlsRunnable);
                    mImageViews[1].zoomIn();
                    scheduleDismissOnScreenControls();
                }
            });
            mZoomControls.setOnZoomOutClickListener(new OnClickListener() {
                public void onClick(View v) {
                    mHandler.removeCallbacks(mDismissOnScreenControlsRunnable);
                    mImageViews[1].zoomOut();
                    scheduleDismissOnScreenControls();
                }
            });
        }
        return mZoomControls;
    }

    private boolean isPickIntent() {
        String action = getIntent().getAction();
        return (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action));
    }

    private static final boolean sDragLeftRight = false;
    private static final boolean sUseBounce = false;
    private static final boolean sAnimateTransitions = false;

    static public class ImageViewTouch extends ImageViewTouchBase {
        private ViewImage mViewImage;

        private static int TOUCH_STATE_REST = 0;
        private static int TOUCH_STATE_LEFT_PRESS = 1;
        private static int TOUCH_STATE_RIGHT_PRESS = 2;
        private static int TOUCH_STATE_PANNING = 3;

        private static int TOUCH_AREA_WIDTH = 60;

        private int mTouchState = TOUCH_STATE_REST;

        public ImageViewTouch(Context context) {
            super(context);
            mViewImage = (ViewImage) context;
        }

        public ImageViewTouch(Context context, AttributeSet attrs) {
            super(context, attrs);
            mViewImage = (ViewImage) context;
        }

        protected void postTranslate(float dx, float dy, boolean bounceOK) {
            super.postTranslate(dx, dy);
            if (dx != 0F || dy != 0F)
                mViewImage.showOnScreenControls();

            if (!sUseBounce) {
                center(true, false, false);
            }
        }

        protected ScrollHandler scrollHandler() {
            return mViewImage.mScroller;
        }

        public boolean handleTouchEvent(MotionEvent m) {
            int viewWidth = getWidth();
            ViewImage viewImage = mViewImage;
            int x = (int) m.getX();
            int y = (int) m.getY();

            switch (m.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    viewImage.setMode(MODE_NORMAL);
                    viewImage.showOnScreenControls();
                    mLastXTouchPos = x;
                    mLastYTouchPos = y;
                    mTouchState = TOUCH_STATE_REST;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (x < TOUCH_AREA_WIDTH) {
                        if (mTouchState == TOUCH_STATE_REST) {
                            mTouchState = TOUCH_STATE_LEFT_PRESS;
                        }
                        if (mTouchState == TOUCH_STATE_LEFT_PRESS) {
                            viewImage.mPrevImageView.setPressed(true);
                            viewImage.mNextImageView.setPressed(false);
                        }
                        mLastXTouchPos = x;
                        mLastYTouchPos = y;
                    } else if (x > viewWidth - TOUCH_AREA_WIDTH) {
                        if (mTouchState == TOUCH_STATE_REST) {
                            mTouchState = TOUCH_STATE_RIGHT_PRESS;
                        }
                        if (mTouchState == TOUCH_STATE_RIGHT_PRESS) {
                            viewImage.mPrevImageView.setPressed(false);
                            viewImage.mNextImageView.setPressed(true);
                        }
                        mLastXTouchPos = x;
                        mLastYTouchPos = y;
                    } else {
                        mTouchState = TOUCH_STATE_PANNING;
                        viewImage.mPrevImageView.setPressed(false);
                        viewImage.mNextImageView.setPressed(false);

                        int deltaX;
                        int deltaY;

                        if (mLastXTouchPos == -1) {
                            deltaX = 0;
                            deltaY = 0;
                        } else {
                            deltaX = x - mLastXTouchPos;
                            deltaY = y - mLastYTouchPos;
                        }

                        mLastXTouchPos = x;
                        mLastYTouchPos = y;

                        if (mBitmapDisplayed == null)
                            return true;

                        if (deltaX != 0) {
                            // Second.  Pan to whatever degree is possible.
                            if (getScale() > 1F) {
                                postTranslate(deltaX, deltaY, sUseBounce);
                                ImageViewTouch.this.center(true, true, false);
                            }
                        }
                        setImageMatrix(getImageViewMatrix());
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    int nextImagePos = -1;
                    if (mTouchState == TOUCH_STATE_LEFT_PRESS && x < TOUCH_AREA_WIDTH) {
                        nextImagePos = viewImage.mCurrentPosition - 1;
                    } else if (mTouchState == TOUCH_STATE_RIGHT_PRESS &&
                           x > viewWidth - TOUCH_AREA_WIDTH) {
                        nextImagePos = viewImage.mCurrentPosition + 1;
                    }
                    if (nextImagePos >= 0
                            && nextImagePos < viewImage.mAllImages.getCount()) {
                        synchronized (viewImage) {
                            viewImage.setMode(MODE_NORMAL);
                            viewImage.setImage(nextImagePos);
                        }
                    }
                    viewImage.scheduleDismissOnScreenControls();
                    viewImage.mPrevImageView.setPressed(false);
                    viewImage.mNextImageView.setPressed(false);
                    mTouchState = TOUCH_STATE_REST;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    viewImage.mPrevImageView.setPressed(false);
                    viewImage.mNextImageView.setPressed(false);
                    mTouchState = TOUCH_STATE_REST;
                    break;
            }
            return true;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event)
        {
            int current = mViewImage.mCurrentPosition;

            int nextImagePos = -2; // default no next image
            try {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_CENTER: {
                        if (mViewImage.isPickIntent()) {
                            ImageManager.IImage img = mViewImage.mAllImages.getImageAt(mViewImage.mCurrentPosition);
                            mViewImage.setResult(RESULT_OK,
                                    new Intent().setData(img.fullSizeImageUri()));
                            mViewImage.finish();
                        }
                        break;
                    }
                    case KeyEvent.KEYCODE_DPAD_LEFT: {
                        panBy(sPanRate, 0);
                        int maxOffset = (current == 0) ? 0 : sHysteresis;
                        if (getScale() <= 1F || isShiftedToNextImage(true, maxOffset)) {
                            nextImagePos = current - 1;
                        } else {
                            center(true, false, true);
                        }
                        return true;
                    }
                    case KeyEvent.KEYCODE_DPAD_RIGHT: {
                        panBy(-sPanRate, 0);
                        int maxOffset = (current == mViewImage.mAllImages.getCount()-1) ? 0 : sHysteresis;
                        if (getScale() <= 1F || isShiftedToNextImage(false, maxOffset)) {
                            nextImagePos = current + 1;
                        } else {
                            center(true, false, true);
                        }
                        return true;
                    }
                    case KeyEvent.KEYCODE_DPAD_UP: {
                        panBy(0, sPanRate);
                        center(true, false, false);
                        return true;
                    }
                    case KeyEvent.KEYCODE_DPAD_DOWN: {
                        panBy(0, -sPanRate);
                        center(true, false, false);
                        return true;
                    }
                    case KeyEvent.KEYCODE_DEL:
                        MenuHelper.deletePhoto(mViewImage, mViewImage.mDeletePhotoRunnable);
                        break;
                }
            } finally {
                if (nextImagePos >= 0 && nextImagePos < mViewImage.mAllImages.getCount()) {
                    synchronized (mViewImage) {
                        mViewImage.setMode(MODE_NORMAL);
                        mViewImage.setImage(nextImagePos);
                    }
               } else if (nextImagePos != -2) {
                   center(true, true, false);
               }
            }

            return super.onKeyDown(keyCode, event);
        }

        protected boolean isShiftedToNextImage(boolean left, int maxOffset) {
            boolean retval;
            Bitmap bitmap = mBitmapDisplayed;
            Matrix m = getImageViewMatrix();
            if (left) {
                float [] t1 = new float[] { 0, 0 };
                m.mapPoints(t1);
                retval = t1[0] > maxOffset;
            } else {
                int width = bitmap != null ? bitmap.getWidth() : getWidth();
                float [] t1 = new float[] { width, 0 };
                m.mapPoints(t1);
                retval = t1[0] + maxOffset < getWidth();
            }
            return retval;
        }

        protected void scrollX(int deltaX) {
            scrollHandler().scrollBy(deltaX, 0);
        }

        protected int getScrollOffset() {
            return scrollHandler().getScrollX();
        }

    }

    static class ScrollHandler extends LinearLayout {
        private Runnable mFirstLayoutCompletedCallback = null;
        private Scroller mScrollerHelper;
        private int mWidth = -1;

        public ScrollHandler(Context context) {
            super(context);
            mScrollerHelper = new Scroller(context);
        }

        public ScrollHandler(Context context, AttributeSet attrs) {
            super(context, attrs);
            mScrollerHelper = new Scroller(context);
        }

        public void setLayoutCompletedCallback(Runnable r) {
            mFirstLayoutCompletedCallback = r;
        }

        public void startScrollTo(int newX, int newY) {
            int oldX = getScrollX();
            int oldY = getScrollY();

            int deltaX = newX - oldX;
            int deltaY = newY - oldY;

            if (mWidth == -1) {
                mWidth = findViewById(R.id.image2).getWidth();
            }
            int viewWidth = mWidth;

            int duration = viewWidth > 0
                    ? sBaseScrollDuration * Math.abs(deltaX) / viewWidth
                    : 0;
            mScrollerHelper.startScroll(oldX, oldY, deltaX, deltaY, duration);
            invalidate();
        }

        @Override
        public void computeScroll() {
            if (mScrollerHelper.computeScrollOffset()) {
                scrollTo(mScrollerHelper.getCurrX(), mScrollerHelper.getCurrY());
                postInvalidate();  // So we draw again
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int x = 0;
            for (View v : new View[] {
                    findViewById(R.id.image1),
                    findViewById(R.id.image2),
                    findViewById(R.id.image3) }) {
                v.layout(x, 0, x + width, bottom);
                x += (width + sPadding);
            }

            findViewById(R.id.padding1).layout(width, 0, width + sPadding, bottom);
            findViewById(R.id.padding2).layout(width+sPadding+width, 0, width+sPadding+width+sPadding, bottom);

            if (changed) {
                if (mFirstLayoutCompletedCallback != null) {
                    mFirstLayoutCompletedCallback.run();
                }
            }
        }
    }

    private void animateScrollTo(int xNew, int yNew) {
        mScroller.startScrollTo(xNew, yNew);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        if (true) {
            MenuItem item = menu.add(Menu.CATEGORY_SECONDARY, 203, 0, R.string.slide_show);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    setMode(MODE_SLIDESHOW);
                    mLastSlideShowImage = mCurrentPosition;
                    loadNextImage(mCurrentPosition, 0, true);
                    return true;
                }
            });
            item.setIcon(android.R.drawable.ic_menu_slideshow);
        }

        mFlipItem = MenuHelper.addFlipOrientation(menu, ViewImage.this, mPrefs);

        final SelectedImageGetter selectedImageGetter = new SelectedImageGetter() {
            public ImageManager.IImage getCurrentImage() {
                return mAllImages.getImageAt(mCurrentPosition);
            }

            public Uri getCurrentImageUri() {
                return mAllImages.getImageAt(mCurrentPosition).fullSizeImageUri();
            }
        };

        mImageMenuRunnable = MenuHelper.addImageMenuItems(
                menu,
                MenuHelper.INCLUDE_ALL,
                true,
                ViewImage.this,
                mHandler,
                mDeletePhotoRunnable,
                new MenuHelper.MenuInvoker() {
                    public void run(MenuHelper.MenuCallback cb) {
                        setMode(MODE_NORMAL);
                        cb.run(selectedImageGetter.getCurrentImageUri(), selectedImageGetter.getCurrentImage());
                        for (ImageViewTouchBase iv: mImageViews) {
                            iv.recycleBitmaps();
                            iv.setImageBitmap(null, true);
                        }
                        setImage(mCurrentPosition);
                    }
                });

        if (true) {
            MenuItem item = menu.add(Menu.CATEGORY_SECONDARY, 203, 1000, R.string.camerasettings);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    Intent preferences = new Intent();
                    preferences.setClass(ViewImage.this, GallerySettings.class);
                    startActivity(preferences);
                    return true;
                }
            });
            item.setAlphabeticShortcut('p');
            item.setIcon(android.R.drawable.ic_menu_preferences);
        }

        // Hidden menu just so the shortcut will bring up the zoom controls
        menu.add(Menu.CATEGORY_SECONDARY, 203, 0, R.string.camerasettings)      // the string resource is a placeholder
        .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
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
            } else {
                if (mCurrentPosition == mAllImages.getCount()) {
                    mCurrentPosition -= 1;
                }
            }
            for (ImageViewTouchBase iv: mImageViews) {
                iv.setImageBitmapResetBase(null, true, true);
            }
            setImage(mCurrentPosition);
        }
    };

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        super.onPrepareOptionsMenu(menu);
        setMode(MODE_NORMAL);

        if (mImageMenuRunnable != null) {
            mImageMenuRunnable.gettingReadyToOpen(menu, mAllImages.getImageAt(mCurrentPosition));
        }

        MenuHelper.setFlipOrientationEnabled(this, mFlipItem);

        menu.findItem(MenuHelper.MENU_IMAGE_SHARE).setEnabled(isCurrentImageShareable());

        return true;
    }

    private boolean isCurrentImageShareable() {
        IImage image = mAllImages.getImageAt(mCurrentPosition);
        if (image != null){
            Uri uri = image.fullSizeImageUri();
            String fullUri = uri.toString();
            return fullUri.startsWith(MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString()) ||
                    fullUri.startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString());
        }
        return true;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation != getResources().getConfiguration().orientation) {
            for (ImageViewTouchBase iv: mImageViews) {
                iv.setImageBitmapResetBase(null, false, true);
            }
            MenuHelper.requestOrientation(this, mPrefs);
        }
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        boolean b = super.onMenuItemSelected(featureId, item);
        if (mImageMenuRunnable != null)
            mImageMenuRunnable.aboutToCall(item, mAllImages.getImageAt(mCurrentPosition));
        return b;
    }

    /*
     * Here's the loading strategy.  For any given image, load the thumbnail
     * into memory and post a callback to display the resulting bitmap.
     *
     * Then proceed to load the full image bitmap.   Three things can
     * happen at this point:
     *
     * 1.  the image fails to load because the UI thread decided
     * to move on to a different image.  This "cancellation" happens
     * by virtue of the UI thread closing the stream containing the
     * image being decoded.  BitmapFactory.decodeStream returns null
     * in this case.
     *
     * 2.  the image loaded successfully.  At that point we post
     * a callback to the UI thread to actually show the bitmap.
     *
     * 3.  when the post runs it checks to see if the image that was
     * loaded is still the one we want.  The UI may have moved on
     * to some other image and if so we just drop the newly loaded
     * bitmap on the floor.
     */

    interface ImageGetterCallback {
        public void imageLoaded(int pos, int offset, Bitmap bitmap, boolean isThumb);
        public boolean wantsThumbnail(int pos, int offset);
        public boolean wantsFullImage(int pos, int offset);
        public int fullImageSizeToUse(int pos, int offset);
        public void completed(boolean wasCanceled);
        public int [] loadOrder();
    }

    class ImageGetter {
        // The thread which does the work.
        private Thread mGetterThread;

        // The base position that's being retrieved.  The actual images retrieved
        // are this base plus each of the offets.
        private int mCurrentPosition = -1;

        // The callback to invoke for each image.
        private ImageGetterCallback mCB;

        // This is the loader cancelable that gets set while we're loading an image.
        // If we change position we can cancel the current load using this.
        private ImageManager.IGetBitmap_cancelable mLoad;

        // True if we're canceling the current load.
        private boolean mCancelCurrent = false;

        // True when the therad should exit.
        private boolean mDone = false;

        // True when the loader thread is waiting for work.
        private boolean mReady = false;

        private void cancelCurrent() {
            synchronized (this) {
                if (!mReady) {
                    mCancelCurrent = true;
                    ImageManager.IGetBitmap_cancelable load = mLoad;
                    if (load != null) {
                        if (Config.LOGV)
                            Log.v(TAG, "canceling load object");
                        load.cancel();
                    }
                    mCancelCurrent = false;
                }
            }
        }

        public ImageGetter() {
            mGetterThread = new Thread(new Runnable() {

                private Runnable callback(final int position, final int offset, final boolean isThumb, final Bitmap bitmap) {
                    return new Runnable() {
                        public void run() {
                            // check for inflight callbacks that aren't applicable any longer
                            // before delivering them
                            if (!isCanceled() && position == mCurrentPosition) {
                                mCB.imageLoaded(position, offset, bitmap, isThumb);
                            } else {
                                if (bitmap != null)
                                    bitmap.recycle();
                            }
                        }
                    };
                }

                private Runnable completedCallback(final boolean wasCanceled) {
                    return new Runnable() {
                        public void run() {
                            mCB.completed(wasCanceled);
                        }
                    };
                }

                public void run() {
                    int lastPosition = -1;
                    while (!mDone) {
                        synchronized (ImageGetter.this) {
                            mReady = true;
                            ImageGetter.this.notify();

                            if (mCurrentPosition == -1 || lastPosition == mCurrentPosition) {
                                try {
                                    ImageGetter.this.wait();
                                } catch (InterruptedException ex) {
                                    continue;
                                }
                            }

                            lastPosition = mCurrentPosition;
                            mReady = false;
                        }

                        if (lastPosition != -1) {
                            int imageCount = mAllImages.getCount();

                            int [] order = mCB.loadOrder();
                            for (int i = 0; i < order.length; i++) {
                                int offset = order[i];
                                int imageNumber = lastPosition + offset;
                                if (imageNumber >= 0 && imageNumber < imageCount) {
                                    ImageManager.IImage image = mAllImages.getImageAt(lastPosition + offset);
                                    if (image == null || isCanceled()) {
                                        break;
                                    }
                                    if (mCB.wantsThumbnail(lastPosition, offset)) {
                                        if (Config.LOGV)
                                            Log.v(TAG, "starting THUMBNAIL load at offset " + offset);
                                        Bitmap b = image.thumbBitmap();
                                        mHandler.post(callback(lastPosition, offset, true, b));
                                    }
                                }
                            }

                            for (int i = 0; i < order.length; i++) {
                                int offset = order[i];
                                int imageNumber = lastPosition + offset;
                                if (imageNumber >= 0 && imageNumber < imageCount) {
                                    ImageManager.IImage image = mAllImages.getImageAt(lastPosition + offset);
                                    if (mCB.wantsFullImage(lastPosition, offset)) {
                                        if (Config.LOGV)
                                            Log.v(TAG, "starting FULL IMAGE load at offset " + offset);
                                        int sizeToUse = mCB.fullImageSizeToUse(lastPosition, offset);
                                        if (image != null && !isCanceled()) {
                                            mLoad = image.fullSizeBitmap_cancelable(sizeToUse);
                                        }
                                        if (mLoad != null) {
                                            long t1;
                                            if (Config.LOGV) t1 = System.currentTimeMillis();

                                            Bitmap b = null;
                                            try {
                                                b = mLoad.get();
                                            } catch (OutOfMemoryError e) {
                                                Log.e(TAG, "couldn't load full size bitmap for " + "");
                                            }
                                            if (Config.LOGV && b != null) {
                                                long t2 = System.currentTimeMillis();
                                                Log.v(TAG, "loading full image for " + image.fullSizeImageUri()
                                                        + " with requested size " + sizeToUse
                                                        + " took " + (t2-t1)
                                                        + " and returned a bitmap with size "
                                                        + b.getWidth() + " / " + b.getHeight());
                                            }

                                            mLoad = null;
                                            if (b != null) {
                                                if (isCanceled()) {
                                                    b.recycle();
                                                } else {
                                                    mHandler.post(callback(lastPosition, offset, false, b));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            mHandler.post(completedCallback(isCanceled()));
                        }
                    }
                }
            });
            mGetterThread.setName("ImageGettter");
            mGetterThread.start();
        }

        private boolean isCanceled() {
            synchronized (this) {
                return mCancelCurrent;
            }
        }

        public void setPosition(int position, ImageGetterCallback cb) {
            synchronized (this) {
                if (!mReady) {
                    try {
                        mCancelCurrent = true;
                        ImageManager.IGetBitmap_cancelable load = mLoad;
                        if (load != null) {
                            load.cancel();
                        }
                        // if the thread is waiting before loading the full size
                        // image then this will free it up
                        ImageGetter.this.notify();
                        ImageGetter.this.wait();
                        mCancelCurrent = false;
                    } catch (InterruptedException ex) {
                        // not sure what to do here
                    }
                }
            }

            mCurrentPosition = position;
            mCB = cb;

            synchronized (this) {
                ImageGetter.this.notify();
            }
        }

        public void stop() {
            synchronized (this) {
                mDone = true;
                ImageGetter.this.notify();
            }
            try {
                mGetterThread.join();
            } catch (InterruptedException ex) {

            }
        }
    }

    private void setImage(int pos) {
        if (!mLayoutComplete) {
            return;
        }

        final boolean left = mCurrentPosition > pos;

        mCurrentPosition = pos;

        ImageViewTouchBase current = mImageViews[1];
        current.mSuppMatrix.reset();
        current.setImageMatrix(current.getImageViewMatrix());

        if (false) {
            Log.v(TAG, "before...");
            for (ImageViewTouchBase ivtb : mImageViews)
                ivtb.dump();
        }

        if (!mFirst) {
            if (left) {
                mImageViews[2].copyFrom(mImageViews[1]);
                mImageViews[1].copyFrom(mImageViews[0]);
            } else {
                mImageViews[0].copyFrom(mImageViews[1]);
                mImageViews[1].copyFrom(mImageViews[2]);
            }
        }
        if (false) {
            Log.v(TAG, "after copy...");
            for (ImageViewTouchBase ivtb : mImageViews)
                ivtb.dump();
        }

        for (ImageViewTouchBase ivt: mImageViews) {
            ivt.mIsZooming = false;
        }
        int width = mImageViews[1].getWidth();
        int from;
        int to = width + sPadding;
        if (mFirst) {
            from = to;
            mFirst = false;
        } else {
            from = left ? (width + sPadding) + mScroller.getScrollX()
                        : mScroller.getScrollX() - (width + sPadding);
        }

        if (sAnimateTransitions) {
            mScroller.scrollTo(from, 0);
            animateScrollTo(to, 0);
        } else {
            mScroller.scrollTo(to, 0);
        }

        ImageGetterCallback cb = new ImageGetterCallback() {
            public void completed(boolean wasCanceled) {
                mImageViews[1].setFocusableInTouchMode(true);
                mImageViews[1].requestFocus();
            }

            public boolean wantsThumbnail(int pos, int offset) {
                ImageViewTouchBase ivt = mImageViews[1 + offset];
                return ivt.mThumbBitmap == null;
            }

            public boolean wantsFullImage(int pos, int offset) {
                ImageViewTouchBase ivt = mImageViews[1 + offset];
                if (ivt.mBitmapDisplayed != null && !ivt.mBitmapIsThumbnail) {
                    return false;
                }
                if (offset != 0) {
                    return false;
                }
                return true;
            }

            public int fullImageSizeToUse(int pos, int offset) {
                // TODO
                // this number should be bigger so that we can zoom.  we may need to
                // get fancier and read in the fuller size image as the user starts
                // to zoom.  use -1 to get the full full size image.
                // for now use 480 so we don't run out of memory
                final int imageViewSize = 480;
                return imageViewSize;
            }

            public int [] loadOrder() {
                return sOrder_adjacents;
            }

            public void imageLoaded(int pos, int offset, Bitmap bitmap, boolean isThumb) {
                ImageViewTouchBase ivt = mImageViews[1 + offset];
                ivt.setImageBitmapResetBase(bitmap, isThumb, isThumb);
            }
        };

        // Could be null if we're stopping a slide show in the course of pausing
        if (mGetter != null) {
            mGetter.setPosition(pos, cb);
        }
        showOnScreenControls();
    }

    @Override
    public void onCreate(Bundle instanceState)
    {
        super.onCreate(instanceState);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.viewimage);

        mImageViews[0] = (ImageViewTouch) findViewById(R.id.image1);
        mImageViews[1] = (ImageViewTouch) findViewById(R.id.image2);
        mImageViews[2] = (ImageViewTouch) findViewById(R.id.image3);

        mScroller = (ScrollHandler)findViewById(R.id.scroller);
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

        mSlideShowImageViews[0] = (ImageViewTouch) findViewById(R.id.image1_slideShow);
        mSlideShowImageViews[1] = (ImageViewTouch) findViewById(R.id.image2_slideShow);
        for (int i = 0; i < mSlideShowImageViews.length; i++) {
            mSlideShowImageViews[i].setImageBitmapResetBase(null, true, true);
            mSlideShowImageViews[i].setVisibility(View.INVISIBLE);
        }

        mActionIconPanel = findViewById(R.id.action_icon_panel);
        {
            int[] pickIds = {R.id.attach, R.id.cancel};
            int[] normalIds = {R.id.gallery, R.id.setas, R.id.share, R.id.discard};
            int[] alwaysOnIds = {R.id.mode_indicator };
            int[] hideIds = pickIds;
            int[] connectIds = normalIds;
            if (isPickIntent()) {
                hideIds = normalIds;
                connectIds = pickIds;
            }
            for(int id : hideIds) {
                findViewById(id).setVisibility(View.GONE);
            }
            for(int id : connectIds) {
                findViewById(id).setOnClickListener(this);
            }
            for(int id : alwaysOnIds) {
                findViewById(id).setOnClickListener(this);
            }
        }

        Uri uri = getIntent().getData();

        if (Config.LOGV)
            Log.v(TAG, "uri is " + uri);
        if (instanceState != null) {
            if (instanceState.containsKey("uri")) {
                uri = Uri.parse(instanceState.getString("uri"));
            }
        }
        if (uri == null) {
            finish();
            return;
        }
        init(uri);
        mFullScreenInNormalMode = getIntent().getBooleanExtra(
                MediaStore.EXTRA_SHOW_ACTION_ICONS, false);
        mShowActionIcons = getIntent().getBooleanExtra(
                MediaStore.EXTRA_SHOW_ACTION_ICONS, false);

        Bundle b = getIntent().getExtras();

        boolean slideShow = b != null ? b.getBoolean("slideshow", false) : false;
        if (slideShow) {
            setMode(MODE_SLIDESHOW);
            loadNextImage(mCurrentPosition, 0, true);
        } else {
            if (mFullScreenInNormalMode) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            if (mShowActionIcons) {
                mActionIconPanel.setVisibility(View.VISIBLE);
            }
        }

        // Get the zoom controls and add them to the bottom of the map
        View zoomControls = getZoomControls();
        RelativeLayout root = (RelativeLayout) findViewById(R.id.rootLayout);
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        p.addRule(RelativeLayout.CENTER_HORIZONTAL);
        root.addView(zoomControls, p);

        mNextImageView = findViewById(R.id.next_image);
        mPrevImageView = findViewById(R.id.prev_image);

        setOrientation();
    }

    private void setOrientation() {
        Intent intent = getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_SCREEN_ORIENTATION)) {
            int orientation = intent.getIntExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (orientation != getRequestedOrientation()) {
                setRequestedOrientation(orientation);
            }
        } else {
            MenuHelper.requestOrientation(this, mPrefs);
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

    private void setMode(int mode) {
        if (mMode == mode) {
            return;
        }

        findViewById(R.id.slideShowContainer).setVisibility(mode == MODE_SLIDESHOW ? View.VISIBLE : View.GONE);
        findViewById(R.id.abs)               .setVisibility(mode == MODE_NORMAL    ? View.VISIBLE : View.GONE);

        Window win = getWindow();
        mMode = mode;
        if (mode == MODE_SLIDESHOW) {
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (sSlideShowHidesStatusBar) {
                win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            for (ImageViewTouchBase ivt: mImageViews) {
                ivt.clear();
            }
            mActionIconPanel.setVisibility(View.GONE);

            if (false) {
                Log.v(TAG, "current is " + this.mSlideShowImageCurrent);
                this.mSlideShowImageViews[0].dump();
                this.mSlideShowImageViews[0].dump();
            }

            findViewById(R.id.slideShowContainer).getRootView().requestLayout();
            mUseShuffleOrder   = mPrefs.getBoolean("pref_gallery_slideshow_shuffle_key", false);
            mSlideShowLoop     = mPrefs.getBoolean("pref_gallery_slideshow_repeat_key", false);
            try {
                mAnimationIndex = Integer.parseInt(mPrefs.getString("pref_gallery_slideshow_transition_key", "0"));
            } catch (Exception ex) {
                Log.e(TAG, "couldn't parse preference: " + ex.toString());
                mAnimationIndex = 0;
            }
            try {
                mSlideShowInterval = Integer.parseInt(mPrefs.getString("pref_gallery_slideshow_interval_key", "3")) * 1000;
            } catch (Exception ex) {
                Log.e(TAG, "couldn't parse preference: " + ex.toString());
                mSlideShowInterval = 3000;
            }

            if (Config.LOGV) {
                Log.v(TAG, "read prefs...  shuffle: " + mUseShuffleOrder);
                Log.v(TAG, "read prefs...     loop: " + mSlideShowLoop);
                Log.v(TAG, "read prefs...  animidx: " + mAnimationIndex);
                Log.v(TAG, "read prefs... interval: " + mSlideShowInterval);
            }

            if (mUseShuffleOrder) {
                generateShuffleOrder();
            }
        } else {
            if (Config.LOGV)
                Log.v(TAG, "slide show mode off, mCurrentPosition == " + mCurrentPosition);
            win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (mFullScreenInNormalMode) {
                win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else {
                win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            if (mGetter != null)
                mGetter.cancelCurrent();

            if (sSlideShowHidesStatusBar) {
                win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            if (mShowActionIcons) {
                mActionIconPanel.setVisibility(View.VISIBLE);
            }

            ImageViewTouchBase dst = mImageViews[1];
            dst.mLastXTouchPos = -1;
            dst.mLastYTouchPos = -1;

            for (ImageViewTouchBase ivt: mSlideShowImageViews) {
                ivt.clear();
            }

            mShuffleOrder = null;

            // mGetter null is a proxy for being paused
            if (mGetter != null) {
                mFirst = true;  // don't animate
                setImage(mCurrentPosition);
            }
        }

        // this line shouldn't be necessary but the view hierarchy doesn't
        // seem to realize that the window layout changed
        mScroller.requestLayout();
    }

    private void generateShuffleOrder() {
        if (mShuffleOrder == null || mShuffleOrder.length != mAllImages.getCount()) {
            mShuffleOrder = new int[mAllImages.getCount()];
        }

        for (int i = 0; i < mShuffleOrder.length; i++) {
            mShuffleOrder[i] = i;
        }

        for (int i = mShuffleOrder.length - 1; i > 0; i--) {
            int r = mRandom.nextInt(i);
            int tmp = mShuffleOrder[r];
            mShuffleOrder[r] = mShuffleOrder[i];
            mShuffleOrder[i] = tmp;
        }
    }

    private void loadNextImage(final int requestedPos, final long delay, final boolean firstCall) {
        if (firstCall && mUseShuffleOrder) {
            generateShuffleOrder();
        }

        final long targetDisplayTime = System.currentTimeMillis() + delay;

        ImageGetterCallback cb = new ImageGetterCallback() {
            public void completed(boolean wasCanceled) {
            }

            public boolean wantsThumbnail(int pos, int offset) {
                return true;
            }

            public boolean wantsFullImage(int pos, int offset) {
                return false;
            }

            public int [] loadOrder() {
                return sOrder_slideshow;
            }

            public int fullImageSizeToUse(int pos, int offset) {
                return 480; // TODO compute this
            }

            public void imageLoaded(final int pos, final int offset, final Bitmap bitmap, final boolean isThumb) {
                long timeRemaining = Math.max(0, targetDisplayTime - System.currentTimeMillis());
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        if (mMode == MODE_NORMAL) {
                            return;
                        }

                        ImageViewTouchBase oldView = mSlideShowImageViews[mSlideShowImageCurrent];

                        if (++mSlideShowImageCurrent == mSlideShowImageViews.length) {
                            mSlideShowImageCurrent = 0;
                        }

                        ImageViewTouchBase newView = mSlideShowImageViews[mSlideShowImageCurrent];
                        newView.setVisibility(View.VISIBLE);
                        newView.setImageBitmapResetBase(bitmap, isThumb, isThumb);
                        newView.bringToFront();

                        int animation = 0;

                        if (mAnimationIndex == -1) {
                            int n = mRandom.nextInt(mSlideShowInAnimation.length);
                            animation = n;
                        } else {
                            animation = mAnimationIndex;
                        }

                        Animation aIn = mSlideShowInAnimation[animation];
                        newView.setAnimation(aIn);
                        newView.setVisibility(View.VISIBLE);
                        aIn.startNow();

                        Animation aOut = mSlideShowOutAnimation[animation];
                        oldView.setVisibility(View.INVISIBLE);
                        oldView.setAnimation(aOut);
                        aOut.startNow();

                        mCurrentPosition = requestedPos;

                        mHandler.post(new Runnable() {
                            public void run() {
                                if (mCurrentPosition == mLastSlideShowImage && !firstCall) {
                                    if (mSlideShowLoop) {
                                        if (mUseShuffleOrder) {
                                            generateShuffleOrder();
                                        }
                                    } else {
                                        setMode(MODE_NORMAL);
                                        return;
                                    }
                                }

                                if (Config.LOGV)
                                    Log.v(TAG, "mCurrentPosition is now " + mCurrentPosition);
                                loadNextImage((mCurrentPosition + 1) % mAllImages.getCount(), mSlideShowInterval, false);
                            }
                        });
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
            mGetter.setPosition(pos, cb);
        }
    }

    private void makeGetter() {
        mGetter = new ImageGetter();
    }

    private void init(Uri uri) {
        String sortOrder = mPrefs.getString("pref_gallery_sort_key", null);
        mSortAscending = false;
        if (sortOrder != null) {
            mSortAscending = sortOrder.equals("ascending");
        }
        int sort = mSortAscending ? ImageManager.SORT_ASCENDING : ImageManager.SORT_DESCENDING;
        mAllImages = ImageManager.makeImageList(uri, this, sort);

        uri = uri.buildUpon().query(null).build();
        // TODO smarter/faster here please
        for (int i = 0; i < mAllImages.getCount(); i++) {
            ImageManager.IImage image = mAllImages.getImageAt(i);
            if (image.fullSizeImageUri().equals(uri)) {
                mCurrentPosition = i;
                mLastSlideShowImage = mCurrentPosition;
                break;
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        ImageManager.IImage image = mAllImages.getImageAt(mCurrentPosition);

        if (image != null){
            Uri uri = image.fullSizeImageUri();
            String bucket = null;
            if(getIntent()!= null && getIntent().getData()!=null)
                bucket = getIntent().getData().getQueryParameter("bucketId");

            if(bucket!=null)
                uri = uri.buildUpon().appendQueryParameter("bucketId", bucket).build();

            b.putString("uri", uri.toString());
        }
        if (mMode == MODE_SLIDESHOW)
            b.putBoolean("slideshow", true);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // normally this will never be zero but if one "backs" into this
        // activity after removing the sdcard it could be zero.  in that
        // case just "finish" since there's nothing useful that can happen.
        if (mAllImages.getCount() == 0) {
            finish();
        }

        ImageManager.IImage image = mAllImages.getImageAt(mCurrentPosition);

        String sortOrder = mPrefs.getString("pref_gallery_sort_key", null);
        boolean sortAscending = false;
        if (sortOrder != null) {
            sortAscending = sortOrder.equals("ascending");
        }
        if (sortAscending != mSortAscending) {
            init(image.fullSizeImageUri());
        }

        if (mGetter == null) {
            makeGetter();
        }

        for (ImageViewTouchBase iv: mImageViews) {
            iv.setImageBitmap(null, true);
        }

        mFirst = true;
        mScroller.setLayoutCompletedCallback(new Runnable() {
            public void run() {
                mLayoutComplete = true;
                setImage(mCurrentPosition);
            }
         });
        setImage(mCurrentPosition);

        setOrientation();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        mGetter.cancelCurrent();
        mGetter.stop();
        mGetter = null;
        setMode(MODE_NORMAL);

        mAllImages.deactivate();

        for (ImageViewTouchBase iv: mImageViews) {
            iv.recycleBitmaps();
            iv.setImageBitmap(null, true);
        }

        for (ImageViewTouchBase iv: mSlideShowImageViews) {
            iv.recycleBitmaps();
            iv.setImageBitmap(null, true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public void onClick(View v) {
        switch (v.getId()) {

        case R.id.mode_indicator: {
            MenuHelper.gotoStillImageCapture(this);
        }
        break;

        case R.id.gallery: {
            MenuHelper.gotoCameraImageGallery(this);
        }
        break;

        case R.id.discard: {
            MenuHelper.displayDeleteDialog(this, mDeletePhotoRunnable, true);
        }
        break;

        case R.id.share: {
            Uri u = mAllImages.getImageAt(mCurrentPosition).fullSizeImageUri();
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM, u);
            try {
                startActivity(Intent.createChooser(intent, getText(R.string.sendImage)));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, R.string.no_way_to_share_image, Toast.LENGTH_SHORT).show();
            }
        }
        break;

        case R.id.setas: {
            Uri u = mAllImages.getImageAt(mCurrentPosition).fullSizeImageUri();
            Intent intent = new Intent(Intent.ACTION_ATTACH_DATA, u);
            try {
                startActivity(Intent.createChooser(intent, getText(R.string.setImage)));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, R.string.no_way_to_share_video, Toast.LENGTH_SHORT).show();
            }
        }
        break;
        }
    }
}
