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

package com.android.camera;

import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera.Parameters;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.camera.ui.PopupManager;
import com.android.camera.ui.RotateImageView;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.app.PhotoPage.PageTapListener;
import com.android.gallery3d.app.GalleryActionBar;
import com.android.gallery3d.app.StateManager;
import com.android.gallery3d.util.MediaSetUtils;

import java.io.File;

/**
 * Superclass of Camera and VideoCamera activities.
 */
abstract public class ActivityBase extends AbstractGalleryActivity
        implements CameraScreenNail.PositionChangedListener,
                View.OnLayoutChangeListener, PageTapListener {

    private static final String TAG = "ActivityBase";
    private static boolean LOGV = false;
    private static final int CAMERA_APP_VIEW_TOGGLE_TIME = 100;  // milliseconds
    private int mResultCodeForTesting;
    private Intent mResultDataForTesting;
    private OnScreenHint mStorageHint;
    private UpdateCameraAppView mUpdateCameraAppView;
    private HideCameraAppView mHideCameraAppView;
    private View mSingleTapArea;

    // The bitmap of the last captured picture thumbnail and the URI of the
    // original picture.
    protected Thumbnail mThumbnail;
    // An imageview showing showing the last captured picture thumbnail.
    protected RotateImageView mThumbnailView;
    protected int mThumbnailViewWidth; // layout width of the thumbnail
    protected AsyncTask<Void, Void, Thumbnail> mLoadThumbnailTask;
    protected boolean mOpenCameraFail;
    protected boolean mCameraDisabled;
    protected CameraManager.CameraProxy mCameraDevice;
    protected Parameters mParameters;
    // The activity is paused. The classes that extend this class should set
    // mPaused the first thing in onResume/onPause.
    protected boolean mPaused;
    protected GalleryActionBar mActionBar;

    // multiple cameras support
    protected int mNumberOfCameras;
    protected int mCameraId;

    protected CameraScreenNail mCameraScreenNail; // This shows camera preview.
    // The view containing only camera related widgets like control panel,
    // indicator bar, focus indicator and etc.
    protected View mCameraAppView;
    protected boolean mShowCameraAppView = true;

    protected class CameraOpenThread extends Thread {
        @Override
        public void run() {
            try {
                mCameraDevice = Util.openCamera(ActivityBase.this, mCameraId);
                mParameters = mCameraDevice.getParameters();
            } catch (CameraHardwareException e) {
                mOpenCameraFail = true;
            } catch (CameraDisabledException e) {
                mCameraDisabled = true;
            }
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        if (Util.isTabletUI()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.disableToggleStatusBar();
        super.onCreate(icicle);
        // The full screen mode might be turned off previously. Add the flag again.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mActionBar = new GalleryActionBar(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (LOGV) Log.v(TAG, "onPause");
        saveThumbnailToFile();

        if (mLoadThumbnailTask != null) {
            mLoadThumbnailTask.cancel(true);
            mLoadThumbnailTask = null;
        }

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Prevent software keyboard or voice search from showing up.
        if (keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.isLongPress()) return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    protected void setResultEx(int resultCode) {
        mResultCodeForTesting = resultCode;
        setResult(resultCode);
    }

    protected void setResultEx(int resultCode, Intent data) {
        mResultCodeForTesting = resultCode;
        mResultDataForTesting = data;
        setResult(resultCode, data);
    }

    public int getResultCode() {
        return mResultCodeForTesting;
    }

    public Intent getResultData() {
        return mResultDataForTesting;
    }

    @Override
    protected void onDestroy() {
        PopupManager.removeInstance(this);
        super.onDestroy();
    }

    protected void updateStorageHint(long storageSpace) {
        String message = null;
        if (storageSpace == Storage.UNAVAILABLE) {
            message = getString(R.string.no_storage);
        } else if (storageSpace == Storage.PREPARING) {
            message = getString(R.string.preparing_sd);
        } else if (storageSpace == Storage.UNKNOWN_SIZE) {
            message = getString(R.string.access_sd_fail);
        } else if (storageSpace < Storage.LOW_STORAGE_THRESHOLD) {
            message = getString(R.string.spaceIsLow_content);
        }

        if (message != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, message);
            } else {
                mStorageHint.setText(message);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    private void updateThumbnailView() {
        if (mThumbnail != null) {
            mThumbnailView.setBitmap(mThumbnail.getBitmap());
            mThumbnailView.setVisibility(View.VISIBLE);
        } else {
            mThumbnailView.setBitmap(null);
            mThumbnailView.setVisibility(View.GONE);
        }
    }

    protected void getLastThumbnail() {
        mThumbnail = ThumbnailHolder.getLastThumbnail(getContentResolver());
        // Suppose users tap the thumbnail view, go to the gallery, delete the
        // image, and coming back to the camera. Thumbnail file will be invalid.
        // Since the new thumbnail will be loaded in another thread later, the
        // view should be set to gone to prevent from opening the invalid image.
        updateThumbnailView();
        if (mThumbnail == null) {
            mLoadThumbnailTask = new LoadThumbnailTask().execute();
        }
    }

    private class LoadThumbnailTask extends AsyncTask<Void, Void, Thumbnail> {
        @Override
        protected Thumbnail doInBackground(Void... params) {
            // Load the thumbnail from the file.
            ContentResolver resolver = getContentResolver();
            Thumbnail t = Thumbnail.getLastThumbnailFromFile(getFilesDir(), resolver);

            if (isCancelled()) return null;

            if (t == null) {
                // Load the thumbnail from the media provider.
                t = Thumbnail.getLastThumbnailFromContentResolver(resolver);
            }
            return t;
        }

        @Override
        protected void onPostExecute(Thumbnail thumbnail) {
            mThumbnail = thumbnail;
            updateThumbnailView();
        }
    }

    protected void gotoGallery() {
        Util.viewUri(mThumbnail.getUri(), this);
    }

    protected void saveThumbnailToFile() {
        if (mThumbnail != null && !mThumbnail.fromFile()) {
            new SaveThumbnailTask().execute(mThumbnail);
        }
    }

    private class SaveThumbnailTask extends AsyncTask<Thumbnail, Void, Void> {
        @Override
        protected Void doInBackground(Thumbnail... params) {
            final int n = params.length;
            final File filesDir = getFilesDir();
            for (int i = 0; i < n; i++) {
                params[i].saveLastThumbnailToFile(filesDir);
            }
            return null;
        }
    }

    // Call this after setContentView.
    protected void createCameraScreenNail(boolean getPictures) {
        mCameraAppView = findViewById(R.id.camera_app_root);
        Bundle data = new Bundle();
        String path = "/local/all/";
        // Intent mode does not show camera roll. Use 0 as a work around for
        // invalid bucket id.
        // TODO: add support of empty media set in gallery.
        path += (getPictures ? MediaSetUtils.CAMERA_BUCKET_ID : "0");
        data.putString(PhotoPage.KEY_MEDIA_SET_PATH, path);
        data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, path);

        // Send a CameraScreenNail to gallery to enable the camera preview.
        CameraScreenNailHolder holder = new CameraScreenNailHolder(this);
        data.putParcelable(PhotoPage.KEY_SCREENNAIL_HOLDER, holder);
        getStateManager().startState(PhotoPage.class, data);
        mCameraScreenNail = holder.getCameraScreenNail();
        mCameraScreenNail.setPositionChangedListener(this);
    }

    private class HideCameraAppView implements Runnable {
        @Override
        public void run() {
            mCameraAppView.setVisibility(View.GONE);
        }
    }
    private class UpdateCameraAppView implements Runnable {
        @Override
        public void run() {
            if (mShowCameraAppView) {
                mCameraAppView.setVisibility(View.VISIBLE);
                mCameraAppView.animate()
                        .setDuration(CAMERA_APP_VIEW_TOGGLE_TIME)
                        .withLayer().alpha(1);
            } else {
                mCameraAppView.animate()
                        .setDuration(CAMERA_APP_VIEW_TOGGLE_TIME)
                        .withLayer().alpha(0).withEndAction(mHideCameraAppView);
            }
        }
    }

    @Override
    public void onPositionChanged(int x, int y, int width, int height, boolean visible) {
        if (!mPaused && !isFinishing()) {
            View rootView = (View) getGLRoot();
            int rootWidth = rootView.getWidth();
            int rootHeight = rootView.getHeight();
            boolean showCameraAppView;
            // Check if the camera preview is in the center.
            if (visible && (x == 0 && width == rootWidth) ||
                    (y == 0 && height == rootHeight && Math.abs(x - (rootWidth - width) / 2) <= 1)) {
                showCameraAppView = true;
            } else {
                showCameraAppView = false;
            }

            if (mShowCameraAppView != showCameraAppView) {
                mShowCameraAppView = showCameraAppView;
                // Initialize the animation.
                if (mUpdateCameraAppView == null) {
                    mUpdateCameraAppView = new UpdateCameraAppView();
                    mHideCameraAppView = new HideCameraAppView();
                    mCameraAppView.animate()
                        .setInterpolator(new DecelerateInterpolator());
                }
                runOnUiThread(mUpdateCameraAppView);
            }
        }
    }

    @Override
    public GalleryActionBar getGalleryActionBar() {
        return mActionBar;
    }

    // Preview frame layout has changed. Move the preview to the center of the
    // layout.
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        // Find out the left and top of the preview frame layout relative to GL
        // root view.
        View root = (View) getGLRoot();
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        v.getLocationInWindow(viewLocation);
        int relativeLeft = viewLocation[0] - rootLocation[0];
        int relativeTop = viewLocation[1] - rootLocation[1];

        // Calculate the scale ratio between preview frame layout and GL root
        // view.
        int width = root.getWidth();
        int height = root.getHeight();
        float scale = Math.max((float) (right - left) / width,
                (float) (bottom - top) / height);
        float scalePx = width / 2f;
        float scalePy = height / 2f;

        // Calculate the translate distance.
        float translateX = relativeLeft + (right - left - width) / 2f;
        float translateY = relativeTop + (bottom - top - height) / 2f;

        mCameraScreenNail.setMatrix(scale, scalePx, scalePy, translateX, translateY);
    }

    protected void setSingleTapUpListener(View singleTapArea) {
        PhotoPage photoPage = (PhotoPage) getStateManager().getTopState();
        photoPage.setPageTapListener(this);
        mSingleTapArea = singleTapArea;
    }

    // Single tap up from PhotoPage.
    @Override
    public boolean onSingleTapUp(int x, int y) {
        // Camera control is invisible. Ignore.
        if (!mShowCameraAppView) return false;

        int[] relativeLocation = Util.getRelativeLocation((View) getGLRoot(),
                mSingleTapArea);
        x -= relativeLocation[0];
        y -= relativeLocation[1];
        if (x >= 0 && x < mSingleTapArea.getWidth() && y >= 0
                && y < mSingleTapArea.getHeight()) {
            onSingleTapUp(mSingleTapArea, x, y);
            return true;
        }
        return false;
    }

    protected void onSingleTapUp(View view, int x, int y) {
    }
}
