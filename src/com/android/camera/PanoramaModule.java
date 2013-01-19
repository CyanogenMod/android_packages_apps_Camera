/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.ui.LayoutChangeNotifier;
import com.android.camera.ui.LayoutNotifyView;
import com.android.camera.ui.PopupManager;
import com.android.camera.ui.Rotatable;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.exif.ExifData;
import com.android.gallery3d.exif.ExifInvalidFormatException;
import com.android.gallery3d.exif.ExifOutputStream;
import com.android.gallery3d.exif.ExifReader;
import com.android.gallery3d.exif.ExifTag;
import com.android.gallery3d.ui.GLRootView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.TimeZone;

/**
 * Activity to handle panorama capturing.
 */
@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB) // uses SurfaceTexture
public class PanoramaModule implements CameraModule,
        SurfaceTexture.OnFrameAvailableListener,
        ShutterButton.OnShutterButtonListener,
        LayoutChangeNotifier.Listener {

    public static final int DEFAULT_SWEEP_ANGLE = 160;
    public static final int DEFAULT_BLEND_MODE = Mosaic.BLENDTYPE_HORIZONTAL;
    public static final int DEFAULT_CAPTURE_PIXELS = 960 * 720;

    private static final int MSG_LOW_RES_FINAL_MOSAIC_READY = 1;
    private static final int MSG_GENERATE_FINAL_MOSAIC_ERROR = 2;
    private static final int MSG_END_DIALOG_RESET_TO_PREVIEW = 3;
    private static final int MSG_CLEAR_SCREEN_DELAY = 4;
    private static final int MSG_CONFIG_MOSAIC_PREVIEW = 5;
    private static final int MSG_RESET_TO_PREVIEW = 6;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final String TAG = "CAM PanoModule";
    private static final int PREVIEW_STOPPED = 0;
    private static final int PREVIEW_ACTIVE = 1;
    private static final int CAPTURE_STATE_VIEWFINDER = 0;
    private static final int CAPTURE_STATE_MOSAIC = 1;
    // The unit of speed is degrees per frame.
    private static final float PANNING_SPEED_THRESHOLD = 2.5f;

    private ContentResolver mContentResolver;

    private GLRootView mGLRootView;
    private ViewGroup mPanoLayout;
    private LinearLayout mCaptureLayout;
    private View mReviewLayout;
    private ImageView mReview;
    private View mCaptureIndicator;
    private PanoProgressBar mPanoProgressBar;
    private PanoProgressBar mSavingProgressBar;
    private Matrix mProgressDirectionMatrix = new Matrix();
    private float[] mProgressAngle = new float[2];
    private LayoutNotifyView mPreviewArea;
    private View mLeftIndicator;
    private View mRightIndicator;
    private MosaicPreviewRenderer mMosaicPreviewRenderer;
    private Object mRendererLock = new Object();
    private TextView mTooFastPrompt;
    private ShutterButton mShutterButton;
    private Object mWaitObject = new Object();

    private String mPreparePreviewString;
    private String mDialogTitle;
    private String mDialogOkString;
    private String mDialogPanoramaFailedString;
    private String mDialogWaitingPreviousString;

    private int mIndicatorColor;
    private int mIndicatorColorFast;
    private int mReviewBackground;

    private boolean mUsingFrontCamera;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mCameraState;
    private int mCaptureState;
    private PowerManager.WakeLock mPartialWakeLock;
    private MosaicFrameProcessor mMosaicFrameProcessor;
    private boolean mMosaicFrameProcessorInitialized;
    private AsyncTask <Void, Void, Void> mWaitProcessorTask;
    private long mTimeTaken;
    private Handler mMainHandler;
    private SurfaceTexture mCameraTexture;
    private boolean mThreadRunning;
    private boolean mCancelComputation;
    private float mHorizontalViewAngle;
    private float mVerticalViewAngle;

    // Prefer FOCUS_MODE_INFINITY to FOCUS_MODE_CONTINUOUS_VIDEO because of
    // getting a better image quality by the former.
    private String mTargetFocusMode = Parameters.FOCUS_MODE_INFINITY;

    private PanoOrientationEventListener mOrientationEventListener;
    // The value could be 0, 90, 180, 270 for the 4 different orientations measured in clockwise
    // respectively.
    private int mDeviceOrientation;
    private int mDeviceOrientationAtCapture;
    private int mCameraOrientation;
    private int mOrientationCompensation;

    private RotateDialogController mRotateDialog;

    private SoundClips.Player mSoundPlayer;

    private Runnable mOnFrameAvailableRunnable;

    private CameraActivity mActivity;
    private View mRootView;
    private CameraProxy mCameraDevice;
    private boolean mPaused;
    private boolean mIsCreatingRenderer;
    private boolean mIsConfigPending;

    private class MosaicJpeg {
        public MosaicJpeg(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.isValid = true;
        }

        public MosaicJpeg() {
            this.data = null;
            this.width = 0;
            this.height = 0;
            this.isValid = false;
        }

        public final byte[] data;
        public final int width;
        public final int height;
        public final boolean isValid;
    }

    private class PanoOrientationEventListener extends OrientationEventListener {
        public PanoOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mDeviceOrientation = Util.roundOrientation(orientation, mDeviceOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mDeviceOrientation
                    + Util.getDisplayRotation(mActivity) % 360;
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                mActivity.getGLRoot().requestLayoutContentPane();
            }
        }
    }

    @Override
    public void init(CameraActivity activity, View parent, boolean reuseScreenNail) {
        mActivity = activity;
        mRootView = parent;

        createContentView();

        mContentResolver = mActivity.getContentResolver();
        if (reuseScreenNail) {
            mActivity.reuseCameraScreenNail(true);
        } else {
            mActivity.createCameraScreenNail(true);
        }

        // This runs in UI thread.
        mOnFrameAvailableRunnable = new Runnable() {
            @Override
            public void run() {
                // Frames might still be available after the activity is paused.
                // If we call onFrameAvailable after pausing, the GL thread will crash.
                if (mPaused) return;

                MosaicPreviewRenderer renderer = null;
                synchronized (mRendererLock) {
                    try {
                        while (mMosaicPreviewRenderer == null) {
                            mRendererLock.wait();
                        }
                        renderer = mMosaicPreviewRenderer;
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Unexpected interruption", e);
                    }
                }
                if (mGLRootView.getVisibility() != View.VISIBLE) {
                    renderer.showPreviewFrameSync();
                    mGLRootView.setVisibility(View.VISIBLE);
                } else {
                    if (mCaptureState == CAPTURE_STATE_VIEWFINDER) {
                        renderer.showPreviewFrame();
                    } else {
                        renderer.alignFrameSync();
                        mMosaicFrameProcessor.processFrame();
                    }
                }
            }
        };

        PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
        mPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Panorama");

        mOrientationEventListener = new PanoOrientationEventListener(mActivity);

        mMosaicFrameProcessor = MosaicFrameProcessor.getInstance();

        Resources appRes = mActivity.getResources();
        mPreparePreviewString = appRes.getString(R.string.pano_dialog_prepare_preview);
        mDialogTitle = appRes.getString(R.string.pano_dialog_title);
        mDialogOkString = appRes.getString(R.string.dialog_ok);
        mDialogPanoramaFailedString = appRes.getString(R.string.pano_dialog_panorama_failed);
        mDialogWaitingPreviousString = appRes.getString(R.string.pano_dialog_waiting_previous);

        mGLRootView = (GLRootView) mActivity.getGLRoot();

        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_LOW_RES_FINAL_MOSAIC_READY:
                        onBackgroundThreadFinished();
                        showFinalMosaic((Bitmap) msg.obj);
                        saveHighResMosaic();
                        break;
                    case MSG_GENERATE_FINAL_MOSAIC_ERROR:
                        onBackgroundThreadFinished();
                        if (mPaused) {
                            resetToPreview();
                        } else {
                            mRotateDialog.showAlertDialog(
                                    mDialogTitle, mDialogPanoramaFailedString,
                                    mDialogOkString, new Runnable() {
                                        @Override
                                        public void run() {
                                            resetToPreview();
                                        }},
                                    null, null);
                        }
                        clearMosaicFrameProcessorIfNeeded();
                        break;
                    case MSG_END_DIALOG_RESET_TO_PREVIEW:
                        onBackgroundThreadFinished();
                        resetToPreview();
                        clearMosaicFrameProcessorIfNeeded();
                        break;
                    case MSG_CLEAR_SCREEN_DELAY:
                        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.
                                FLAG_KEEP_SCREEN_ON);
                        break;
                    case MSG_CONFIG_MOSAIC_PREVIEW:
                        configMosaicPreview(msg.arg1, msg.arg2);
                        break;
                    case MSG_RESET_TO_PREVIEW:
                        resetToPreview();
                        break;
                }
            }
        };
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        return mActivity.superDispatchTouchEvent(m);
    }

    private void setupCamera() throws CameraHardwareException, CameraDisabledException {
        openCamera();
        Parameters parameters = mCameraDevice.getParameters();
        setupCaptureParams(parameters);
        configureCamera(parameters);
    }

    private void releaseCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.setPreviewCallbackWithBuffer(null);
            CameraHolder.instance().release();
            mCameraDevice = null;
            mCameraState = PREVIEW_STOPPED;
        }
    }

    private void openCamera() throws CameraHardwareException, CameraDisabledException {
        int cameraId = CameraHolder.instance().getBackCameraId();
        // If there is no back camera, use the first camera. Camera id starts
        // from 0. Currently if a camera is not back facing, it is front facing.
        // This is also forward compatible if we have a new facing other than
        // back or front in the future.
        if (cameraId == -1) cameraId = 0;
        mCameraDevice = Util.openCamera(mActivity, cameraId);
        mCameraOrientation = Util.getCameraOrientation(cameraId);
        if (cameraId == CameraHolder.instance().getFrontCameraId()) mUsingFrontCamera = true;
    }

    private boolean findBestPreviewSize(List<Size> supportedSizes, boolean need4To3,
            boolean needSmaller) {
        int pixelsDiff = DEFAULT_CAPTURE_PIXELS;
        boolean hasFound = false;
        for (Size size : supportedSizes) {
            int h = size.height;
            int w = size.width;
            // we only want 4:3 format.
            int d = DEFAULT_CAPTURE_PIXELS - h * w;
            if (needSmaller && d < 0) { // no bigger preview than 960x720.
                continue;
            }
            if (need4To3 && (h * 4 != w * 3)) {
                continue;
            }
            d = Math.abs(d);
            if (d < pixelsDiff) {
                mPreviewWidth = w;
                mPreviewHeight = h;
                pixelsDiff = d;
                hasFound = true;
            }
        }
        return hasFound;
    }

    private void setupCaptureParams(Parameters parameters) {
        List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
        if (!findBestPreviewSize(supportedSizes, true, true)) {
            Log.w(TAG, "No 4:3 ratio preview size supported.");
            if (!findBestPreviewSize(supportedSizes, false, true)) {
                Log.w(TAG, "Can't find a supported preview size smaller than 960x720.");
                findBestPreviewSize(supportedSizes, false, false);
            }
        }
        Log.v(TAG, "preview h = " + mPreviewHeight + " , w = " + mPreviewWidth);
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);

        List<int[]> frameRates = parameters.getSupportedPreviewFpsRange();
        int last = frameRates.size() - 1;
        int minFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MIN_INDEX];
        int maxFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MAX_INDEX];
        parameters.setPreviewFpsRange(minFps, maxFps);
        Log.v(TAG, "preview fps: " + minFps + ", " + maxFps);

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        if (supportedFocusModes.indexOf(mTargetFocusMode) >= 0) {
            parameters.setFocusMode(mTargetFocusMode);
        } else {
            // Use the default focus mode and log a message
            Log.w(TAG, "Cannot set the focus mode to " + mTargetFocusMode +
                  " becuase the mode is not supported.");
        }

        parameters.set(Util.RECORDING_HINT, Util.FALSE);

        mHorizontalViewAngle = parameters.getHorizontalViewAngle();
        mVerticalViewAngle =  parameters.getVerticalViewAngle();
    }

    public int getPreviewBufSize() {
        PixelFormat pixelInfo = new PixelFormat();
        PixelFormat.getPixelFormatInfo(mCameraDevice.getParameters().getPreviewFormat(), pixelInfo);
        // TODO: remove this extra 32 byte after the driver bug is fixed.
        return (mPreviewWidth * mPreviewHeight * pixelInfo.bitsPerPixel / 8) + 32;
    }

    private void configureCamera(Parameters parameters) {
        mCameraDevice.setParameters(parameters);
    }

    private void configMosaicPreview(final int w, final int h) {
        synchronized (mRendererLock) {
            if (mIsCreatingRenderer) {
                mMainHandler.removeMessages(MSG_CONFIG_MOSAIC_PREVIEW);
                mMainHandler.obtainMessage(MSG_CONFIG_MOSAIC_PREVIEW, w, h).sendToTarget();
                mIsConfigPending = true;
                return;
            }
            mIsCreatingRenderer = true;
            mIsConfigPending = false;
        }
        stopCameraPreview();
        CameraScreenNail screenNail = (CameraScreenNail) mActivity.mCameraScreenNail;
        screenNail.setSize(w, h);
        synchronized (mRendererLock) {
            if (mMosaicPreviewRenderer != null) {
                mMosaicPreviewRenderer.release();
            }
            mMosaicPreviewRenderer = null;
            screenNail.releaseSurfaceTexture();
            screenNail.acquireSurfaceTexture();
        }
        mActivity.notifyScreenNailChanged();
        final boolean isLandscape = (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                CameraScreenNail screenNail = (CameraScreenNail) mActivity.mCameraScreenNail;
                SurfaceTexture surfaceTexture = screenNail.getSurfaceTexture();
                if (surfaceTexture == null) {
                    synchronized (mRendererLock) {
                        mIsConfigPending = true; // try config again later.
                        mIsCreatingRenderer = false;
                        mRendererLock.notifyAll();
                        return;
                    }
                }
                MosaicPreviewRenderer renderer = new MosaicPreviewRenderer(
                        screenNail.getSurfaceTexture(), w, h, isLandscape);
                synchronized (mRendererLock) {
                    mMosaicPreviewRenderer = renderer;
                    mCameraTexture = mMosaicPreviewRenderer.getInputSurfaceTexture();

                    if (!mPaused && !mThreadRunning && mWaitProcessorTask == null) {
                        mMainHandler.sendEmptyMessage(MSG_RESET_TO_PREVIEW);
                    }
                    mIsCreatingRenderer = false;
                    mRendererLock.notifyAll();
                }
            }
        }).start();
    }

    // Receives the layout change event from the preview area. So we can set
    // the camera preview screennail to the same size and initialize the mosaic
    // preview renderer.
    @Override
    public void onLayoutChange(View v, int l, int t, int r, int b) {
        Log.i(TAG, "layout change: "+(r - l) + "/" +(b - t));
        mActivity.onLayoutChange(v, l, t, r, b);
        configMosaicPreview(r - l, b - t);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surface) {
        /* This function may be called by some random thread,
         * so let's be safe and jump back to ui thread.
         * No OpenGL calls can be done here. */
        mActivity.runOnUiThread(mOnFrameAvailableRunnable);
    }

    private void hideDirectionIndicators() {
        mLeftIndicator.setVisibility(View.GONE);
        mRightIndicator.setVisibility(View.GONE);
    }

    private void showDirectionIndicators(int direction) {
        switch (direction) {
            case PanoProgressBar.DIRECTION_NONE:
                mLeftIndicator.setVisibility(View.VISIBLE);
                mRightIndicator.setVisibility(View.VISIBLE);
                break;
            case PanoProgressBar.DIRECTION_LEFT:
                mLeftIndicator.setVisibility(View.VISIBLE);
                mRightIndicator.setVisibility(View.GONE);
                break;
            case PanoProgressBar.DIRECTION_RIGHT:
                mLeftIndicator.setVisibility(View.GONE);
                mRightIndicator.setVisibility(View.VISIBLE);
                break;
        }
    }

    public void startCapture() {
        // Reset values so we can do this again.
        mCancelComputation = false;
        mTimeTaken = System.currentTimeMillis();
        mActivity.setSwipingEnabled(false);
        mActivity.hideSwitcher();
        mShutterButton.setImageResource(R.drawable.btn_shutter_recording);
        mCaptureState = CAPTURE_STATE_MOSAIC;
        mCaptureIndicator.setVisibility(View.VISIBLE);
        showDirectionIndicators(PanoProgressBar.DIRECTION_NONE);

        mMosaicFrameProcessor.setProgressListener(new MosaicFrameProcessor.ProgressListener() {
            @Override
            public void onProgress(boolean isFinished, float panningRateX, float panningRateY,
                    float progressX, float progressY) {
                float accumulatedHorizontalAngle = progressX * mHorizontalViewAngle;
                float accumulatedVerticalAngle = progressY * mVerticalViewAngle;
                if (isFinished
                        || (Math.abs(accumulatedHorizontalAngle) >= DEFAULT_SWEEP_ANGLE)
                        || (Math.abs(accumulatedVerticalAngle) >= DEFAULT_SWEEP_ANGLE)) {
                    stopCapture(false);
                } else {
                    float panningRateXInDegree = panningRateX * mHorizontalViewAngle;
                    float panningRateYInDegree = panningRateY * mVerticalViewAngle;
                    updateProgress(panningRateXInDegree, panningRateYInDegree,
                            accumulatedHorizontalAngle, accumulatedVerticalAngle);
                }
            }
        });

        mPanoProgressBar.reset();
        // TODO: calculate the indicator width according to different devices to reflect the actual
        // angle of view of the camera device.
        mPanoProgressBar.setIndicatorWidth(20);
        mPanoProgressBar.setMaxProgress(DEFAULT_SWEEP_ANGLE);
        mPanoProgressBar.setVisibility(View.VISIBLE);
        mDeviceOrientationAtCapture = mDeviceOrientation;
        keepScreenOn();
        mActivity.getOrientationManager().lockOrientation();
        setupProgressDirectionMatrix();
    }

    void setupProgressDirectionMatrix() {
        int degrees = Util.getDisplayRotation(mActivity);
        int cameraId = CameraHolder.instance().getBackCameraId();
        int orientation = Util.getDisplayOrientation(degrees, cameraId);
        mProgressDirectionMatrix.reset();
        mProgressDirectionMatrix.postRotate(orientation);
    }

    private void stopCapture(boolean aborted) {
        mCaptureState = CAPTURE_STATE_VIEWFINDER;
        mCaptureIndicator.setVisibility(View.GONE);
        hideTooFastIndication();
        hideDirectionIndicators();

        mMosaicFrameProcessor.setProgressListener(null);
        stopCameraPreview();

        mCameraTexture.setOnFrameAvailableListener(null);

        if (!aborted && !mThreadRunning) {
            mRotateDialog.showWaitingDialog(mPreparePreviewString);
            // Hide shutter button, shutter icon, etc when waiting for
            // panorama to stitch
            mActivity.hideUI();
            runBackgroundThread(new Thread() {
                @Override
                public void run() {
                    MosaicJpeg jpeg = generateFinalMosaic(false);

                    if (jpeg != null && jpeg.isValid) {
                        Bitmap bitmap = null;
                        bitmap = BitmapFactory.decodeByteArray(jpeg.data, 0, jpeg.data.length);
                        mMainHandler.sendMessage(mMainHandler.obtainMessage(
                                MSG_LOW_RES_FINAL_MOSAIC_READY, bitmap));
                    } else {
                        mMainHandler.sendMessage(mMainHandler.obtainMessage(
                                MSG_END_DIALOG_RESET_TO_PREVIEW));
                    }
                }
            });
        }
        keepScreenOnAwhile();
    }

    private void showTooFastIndication() {
        mTooFastPrompt.setVisibility(View.VISIBLE);
        // The PreviewArea also contains the border for "too fast" indication.
        mPreviewArea.setVisibility(View.VISIBLE);
        mPanoProgressBar.setIndicatorColor(mIndicatorColorFast);
        mLeftIndicator.setEnabled(true);
        mRightIndicator.setEnabled(true);
    }

    private void hideTooFastIndication() {
        mTooFastPrompt.setVisibility(View.GONE);
        // We set "INVISIBLE" instead of "GONE" here because we need mPreviewArea to have layout
        // information so we can know the size and position for mCameraScreenNail.
        mPreviewArea.setVisibility(View.INVISIBLE);
        mPanoProgressBar.setIndicatorColor(mIndicatorColor);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
    }

    private void updateProgress(float panningRateXInDegree, float panningRateYInDegree,
            float progressHorizontalAngle, float progressVerticalAngle) {
        mGLRootView.requestRender();

        if ((Math.abs(panningRateXInDegree) > PANNING_SPEED_THRESHOLD)
            || (Math.abs(panningRateYInDegree) > PANNING_SPEED_THRESHOLD)) {
            showTooFastIndication();
        } else {
            hideTooFastIndication();
        }

        // progressHorizontalAngle and progressVerticalAngle are relative to the
        // camera. Convert them to UI direction.
        mProgressAngle[0] = progressHorizontalAngle;
        mProgressAngle[1] = progressVerticalAngle;
        mProgressDirectionMatrix.mapPoints(mProgressAngle);

        int angleInMajorDirection =
                (Math.abs(mProgressAngle[0]) > Math.abs(mProgressAngle[1]))
                ? (int) mProgressAngle[0]
                : (int) mProgressAngle[1];
        mPanoProgressBar.setProgress((angleInMajorDirection));
    }

    private void setViews(Resources appRes) {
        mCaptureState = CAPTURE_STATE_VIEWFINDER;
        mPanoProgressBar = (PanoProgressBar) mRootView.findViewById(R.id.pano_pan_progress_bar);
        mPanoProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mPanoProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_done));
        mPanoProgressBar.setIndicatorColor(mIndicatorColor);
        mPanoProgressBar.setOnDirectionChangeListener(
                new PanoProgressBar.OnDirectionChangeListener () {
                    @Override
                    public void onDirectionChange(int direction) {
                        if (mCaptureState == CAPTURE_STATE_MOSAIC) {
                            showDirectionIndicators(direction);
                        }
                    }
                });

        mLeftIndicator = mRootView.findViewById(R.id.pano_pan_left_indicator);
        mRightIndicator = mRootView.findViewById(R.id.pano_pan_right_indicator);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
        mTooFastPrompt = (TextView) mRootView.findViewById(R.id.pano_capture_too_fast_textview);
        // This mPreviewArea also shows the border for visual "too fast" indication.
        mPreviewArea = (LayoutNotifyView) mRootView.findViewById(R.id.pano_preview_area);
        mPreviewArea.setOnLayoutChangeListener(this);

        mSavingProgressBar = (PanoProgressBar) mRootView.findViewById(R.id.pano_saving_progress_bar);
        mSavingProgressBar.setIndicatorWidth(0);
        mSavingProgressBar.setMaxProgress(100);
        mSavingProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mSavingProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_indication));

        mCaptureIndicator = mRootView.findViewById(R.id.pano_capture_indicator);

        mReviewLayout = mRootView.findViewById(R.id.pano_review_layout);
        mReview = (ImageView) mRootView.findViewById(R.id.pano_reviewarea);
        mReview.setBackgroundColor(mReviewBackground);
        View cancelButton = mRootView.findViewById(R.id.pano_review_cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mPaused || mCameraTexture == null) return;
                cancelHighResComputation();
            }
        });

        mShutterButton = mActivity.getShutterButton();
        mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        mShutterButton.setOnShutterButtonListener(this);

        if (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            Rotatable view = (Rotatable) mRootView.findViewById(R.id.pano_rotate_reviewarea);
            view.setOrientation(270, false);
        }
    }

    private void createContentView() {
        mActivity.getLayoutInflater().inflate(R.layout.panorama_module, (ViewGroup) mRootView);
        Resources appRes = mActivity.getResources();
        mCaptureLayout = (LinearLayout) mRootView.findViewById(R.id.camera_app_root);
        mIndicatorColor = appRes.getColor(R.color.pano_progress_indication);
        mReviewBackground = appRes.getColor(R.color.review_background);
        mIndicatorColorFast = appRes.getColor(R.color.pano_progress_indication_fast);
        mPanoLayout = (ViewGroup) mRootView.findViewById(R.id.pano_layout);
        mRotateDialog = new RotateDialogController(mActivity, R.layout.rotate_dialog);
        setViews(appRes);
    }

    @Override
    public void onShutterButtonClick() {
        // If mCameraTexture == null then GL setup is not finished yet.
        // No buttons can be pressed.
        if (mPaused || mThreadRunning || mCameraTexture == null) return;
        // Since this button will stay on the screen when capturing, we need to check the state
        // right now.
        switch (mCaptureState) {
            case CAPTURE_STATE_VIEWFINDER:
                if(mActivity.getStorageSpace() <= Storage.LOW_STORAGE_THRESHOLD) return;
                mSoundPlayer.play(SoundClips.START_VIDEO_RECORDING);
                startCapture();
                break;
            case CAPTURE_STATE_MOSAIC:
                mSoundPlayer.play(SoundClips.STOP_VIDEO_RECORDING);
                stopCapture(false);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
    }

    public void reportProgress() {
        mSavingProgressBar.reset();
        mSavingProgressBar.setRightIncreasing(true);
        Thread t = new Thread() {
            @Override
            public void run() {
                while (mThreadRunning) {
                    final int progress = mMosaicFrameProcessor.reportProgress(
                            true, mCancelComputation);

                    try {
                        synchronized (mWaitObject) {
                            mWaitObject.wait(50);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Panorama reportProgress failed", e);
                    }
                    // Update the progress bar
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mSavingProgressBar.setProgress(progress);
                        }
                    });
                }
            }
        };
        t.start();
    }

    private int getCaptureOrientation() {
        // The panorama image returned from the library is oriented based on the
        // natural orientation of a camera. We need to set an orientation for the image
        // in its EXIF header, so the image can be displayed correctly.
        // The orientation is calculated from compensating the
        // device orientation at capture and the camera orientation respective to
        // the natural orientation of the device.
        int orientation;
        if (mUsingFrontCamera) {
            // mCameraOrientation is negative with respect to the front facing camera.
            // See document of android.hardware.Camera.Parameters.setRotation.
            orientation = (mDeviceOrientationAtCapture - mCameraOrientation + 360) % 360;
        } else {
            orientation = (mDeviceOrientationAtCapture + mCameraOrientation) % 360;
        }
        return orientation;
    }

    public void saveHighResMosaic() {
        runBackgroundThread(new Thread() {
            @Override
            public void run() {
                mPartialWakeLock.acquire();
                MosaicJpeg jpeg;
                try {
                    jpeg = generateFinalMosaic(true);
                } finally {
                    mPartialWakeLock.release();
                }

                if (jpeg == null) {  // Cancelled by user.
                    mMainHandler.sendEmptyMessage(MSG_END_DIALOG_RESET_TO_PREVIEW);
                } else if (!jpeg.isValid) {  // Error when generating mosaic.
                    mMainHandler.sendEmptyMessage(MSG_GENERATE_FINAL_MOSAIC_ERROR);
                } else {
                    int orientation = getCaptureOrientation();
                    Uri uri = savePanorama(jpeg.data, jpeg.width, jpeg.height, orientation);
                    if (uri != null) {
                        mActivity.addSecureAlbumItemIfNeeded(false, uri);
                        Util.broadcastNewPicture(mActivity, uri);
                    }
                    mMainHandler.sendMessage(
                            mMainHandler.obtainMessage(MSG_END_DIALOG_RESET_TO_PREVIEW));
                }
            }
        });
        reportProgress();
    }

    private void runBackgroundThread(Thread thread) {
        mThreadRunning = true;
        thread.start();
    }

    private void onBackgroundThreadFinished() {
        mThreadRunning = false;
        mRotateDialog.dismissDialog();
    }

    private void cancelHighResComputation() {
        mCancelComputation = true;
        synchronized (mWaitObject) {
            mWaitObject.notify();
        }
    }

    // This function will be called upon the first camera frame is available.
    private void reset() {
        mCaptureState = CAPTURE_STATE_VIEWFINDER;

        mActivity.getOrientationManager().unlockOrientation();
        // We should set mGLRootView visible too. However, since there might be no
        // frame available yet, setting mGLRootView visible should be done right after
        // the first camera frame is available and therefore it is done by
        // mOnFirstFrameAvailableRunnable.
        mActivity.setSwipingEnabled(true);
        mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        mReviewLayout.setVisibility(View.GONE);
        mPanoProgressBar.setVisibility(View.GONE);
        mGLRootView.setVisibility(View.VISIBLE);
        // Orientation change will trigger onLayoutChange->configMosaicPreview->
        // resetToPreview. Do not show the capture UI in film strip.
        if (mActivity.mShowCameraAppView) {
            mCaptureLayout.setVisibility(View.VISIBLE);
            mActivity.showUI();
        }
        mMosaicFrameProcessor.reset();
    }

    private void resetToPreview() {
        reset();
        if (!mPaused) startCameraPreview();
    }

    private static class FlipBitmapDrawable extends BitmapDrawable {

        public FlipBitmapDrawable(Resources res, Bitmap bitmap) {
            super(res, bitmap);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int cx = bounds.centerX();
            int cy = bounds.centerY();
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.rotate(180, cx, cy);
            super.draw(canvas);
            canvas.restore();
        }
    }

    private void showFinalMosaic(Bitmap bitmap) {
        if (bitmap != null) {
            int orientation = getCaptureOrientation();
            if (orientation >= 180) {
                // We need to flip the drawable to compensate
                mReview.setImageDrawable(new FlipBitmapDrawable(
                        mActivity.getResources(), bitmap));
            } else {
                mReview.setImageBitmap(bitmap);
            }
        }

        mCaptureLayout.setVisibility(View.GONE);
        mReviewLayout.setVisibility(View.VISIBLE);
    }

    private Uri savePanorama(byte[] jpegData, int width, int height, int orientation) {
        if (jpegData != null) {
            String filename = PanoUtil.createName(
                    mActivity.getResources().getString(R.string.pano_file_name_format), mTimeTaken);
            String filepath = Storage.generateFilepath(filename);

            ExifOutputStream out = null;
            InputStream is = null;
            try {
                is = new ByteArrayInputStream(jpegData);
                ExifReader reader = new ExifReader();
                ExifData data = reader.read(is);

                // Add Exif tags.
                data.addGpsDateTimeStampTag(mTimeTaken);
                data.addDateTimeStampTag(ExifTag.TAG_DATE_TIME, mTimeTaken, TimeZone.getDefault());
                data.addTag(ExifTag.TAG_ORIENTATION).
                        setValue(getExifOrientation(orientation));

                out = new ExifOutputStream(new FileOutputStream(filepath));
                out.setExifData(data);
                out.write(jpegData);
            } catch (IOException e) {
                Log.e(TAG, "Cannot set EXIF for " + filepath, e);
                Storage.writeFile(filepath, jpegData);
            } catch (ExifInvalidFormatException e) {
                Log.e(TAG, "Cannot set EXIF for " + filepath, e);
                Storage.writeFile(filepath, jpegData);
            } finally {
                Util.closeSilently(out);
                Util.closeSilently(is);
            }

            int jpegLength = (int) (new File(filepath).length());
            return Storage.addImage(mContentResolver, filename, mTimeTaken,
                    null, orientation, jpegLength, filepath, width, height);
        }
        return null;
    }

    private static int getExifOrientation(int orientation) {
        switch (orientation) {
            case 0:
                return ExifTag.Orientation.TOP_LEFT;
            case 90:
                return ExifTag.Orientation.RIGHT_TOP;
            case 180:
                return ExifTag.Orientation.BOTTOM_LEFT;
            case 270:
                return ExifTag.Orientation.RIGHT_BOTTOM;
            default:
                throw new AssertionError("invalid: " + orientation);
        }
    }

    private void clearMosaicFrameProcessorIfNeeded() {
        if (!mPaused || mThreadRunning) return;
        // Only clear the processor if it is initialized by this activity
        // instance. Other activity instances may be using it.
        if (mMosaicFrameProcessorInitialized) {
            mMosaicFrameProcessor.clear();
            mMosaicFrameProcessorInitialized = false;
        }
    }

    private void initMosaicFrameProcessorIfNeeded() {
        if (mPaused || mThreadRunning) return;
        mMosaicFrameProcessor.initialize(
                mPreviewWidth, mPreviewHeight, getPreviewBufSize());
        mMosaicFrameProcessorInitialized = true;
    }

    @Override
    public void onPauseBeforeSuper() {
        mPaused = true;
    }

    @Override
    public void onPauseAfterSuper() {
        mOrientationEventListener.disable();
        if (mCameraDevice == null) {
            // Camera open failed. Nothing should be done here.
            return;
        }
        // Stop the capturing first.
        if (mCaptureState == CAPTURE_STATE_MOSAIC) {
            stopCapture(true);
            reset();
        }

        releaseCamera();
        synchronized (mRendererLock) {
            mCameraTexture = null;

            // The preview renderer might not have a chance to be initialized
            // before onPause().
            if (mMosaicPreviewRenderer != null) {
                mMosaicPreviewRenderer.release();
                mMosaicPreviewRenderer = null;
            }
        }

        clearMosaicFrameProcessorIfNeeded();
        if (mWaitProcessorTask != null) {
            mWaitProcessorTask.cancel(true);
            mWaitProcessorTask = null;
        }
        resetScreenOn();
        if (mSoundPlayer != null) {
            mSoundPlayer.release();
            mSoundPlayer = null;
        }
        CameraScreenNail screenNail = (CameraScreenNail) mActivity.mCameraScreenNail;
        screenNail.releaseSurfaceTexture();
        System.gc();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        Drawable lowResReview = null;
        if (mThreadRunning) lowResReview = mReview.getDrawable();

        // Change layout in response to configuration change
        mCaptureLayout.setOrientation(
                newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        mCaptureLayout.removeAllViews();
        LayoutInflater inflater = mActivity.getLayoutInflater();
        inflater.inflate(R.layout.preview_frame_pano, mCaptureLayout);

        mPanoLayout.removeView(mReviewLayout);
        inflater.inflate(R.layout.pano_review, mPanoLayout);

        setViews(mActivity.getResources());
        if (mThreadRunning) {
            mReview.setImageDrawable(lowResReview);
            mCaptureLayout.setVisibility(View.GONE);
            mReviewLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onOrientationChanged(int orientation) {
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    @Override
    public void onResumeAfterSuper() {
        mOrientationEventListener.enable();

        mCaptureState = CAPTURE_STATE_VIEWFINDER;

        try {
            setupCamera();
        } catch (CameraHardwareException e) {
            Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
            return;
        } catch (CameraDisabledException e) {
            Util.showErrorAndFinish(mActivity, R.string.camera_disabled);
            return;
        }

        // Set up sound playback for shutter button
        mSoundPlayer = SoundClips.getPlayer(mActivity);

        // Check if another panorama instance is using the mosaic frame processor.
        mRotateDialog.dismissDialog();
        if (!mThreadRunning && mMosaicFrameProcessor.isMosaicMemoryAllocated()) {
            mGLRootView.setVisibility(View.GONE);
            mRotateDialog.showWaitingDialog(mDialogWaitingPreviousString);
            // If stitching is still going on, make sure switcher and shutter button
            // are not showing
            mActivity.hideUI();
            mWaitProcessorTask = new WaitProcessorTask().execute();
        } else {
            mGLRootView.setVisibility(View.VISIBLE);
            // Camera must be initialized before MosaicFrameProcessor is
            // initialized. The preview size has to be decided by camera device.
            initMosaicFrameProcessorIfNeeded();
            int w = mPreviewArea.getWidth();
            int h = mPreviewArea.getHeight();
            if (w != 0 && h != 0) {  // The layout has been calculated.
                configMosaicPreview(w, h);
            }
        }
        keepScreenOnAwhile();

        // Dismiss open menu if exists.
        PopupManager.getInstance(mActivity).notifyShowPopup(null);
        mRootView.requestLayout();
    }

    /**
     * Generate the final mosaic image.
     *
     * @param highRes flag to indicate whether we want to get a high-res version.
     * @return a MosaicJpeg with its isValid flag set to true if successful; null if the generation
     *         process is cancelled; and a MosaicJpeg with its isValid flag set to false if there
     *         is an error in generating the final mosaic.
     */
    public MosaicJpeg generateFinalMosaic(boolean highRes) {
        int mosaicReturnCode = mMosaicFrameProcessor.createMosaic(highRes);
        if (mosaicReturnCode == Mosaic.MOSAIC_RET_CANCELLED) {
            return null;
        } else if (mosaicReturnCode == Mosaic.MOSAIC_RET_ERROR) {
            return new MosaicJpeg();
        }

        byte[] imageData = mMosaicFrameProcessor.getFinalMosaicNV21();
        if (imageData == null) {
            Log.e(TAG, "getFinalMosaicNV21() returned null.");
            return new MosaicJpeg();
        }

        int len = imageData.length - 8;
        int width = (imageData[len + 0] << 24) + ((imageData[len + 1] & 0xFF) << 16)
                + ((imageData[len + 2] & 0xFF) << 8) + (imageData[len + 3] & 0xFF);
        int height = (imageData[len + 4] << 24) + ((imageData[len + 5] & 0xFF) << 16)
                + ((imageData[len + 6] & 0xFF) << 8) + (imageData[len + 7] & 0xFF);
        Log.v(TAG, "ImLength = " + (len) + ", W = " + width + ", H = " + height);

        if (width <= 0 || height <= 0) {
            // TODO: pop up an error message indicating that the final result is not generated.
            Log.e(TAG, "width|height <= 0!!, len = " + (len) + ", W = " + width + ", H = " +
                    height);
            return new MosaicJpeg();
        }

        YuvImage yuvimage = new YuvImage(imageData, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        try {
            out.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception in storing final mosaic", e);
            return new MosaicJpeg();
        }
        return new MosaicJpeg(out.toByteArray(), width, height);
    }

    private void startCameraPreview() {
        if (mCameraDevice == null) {
            // Camera open failed. Return.
            return;
        }

        // This works around a driver issue. startPreview may fail if
        // stopPreview/setPreviewTexture/startPreview are called several times
        // in a row. mCameraTexture can be null after pressing home during
        // mosaic generation and coming back. Preview will be started later in
        // onLayoutChange->configMosaicPreview. This also reduces the latency.
        synchronized (mRendererLock) {
            if (mCameraTexture == null) return;

            // If we're previewing already, stop the preview first (this will
            // blank the screen).
            if (mCameraState != PREVIEW_STOPPED) stopCameraPreview();

            // Set the display orientation to 0, so that the underlying mosaic
            // library can always get undistorted mPreviewWidth x mPreviewHeight
            // image data from SurfaceTexture.
            mCameraDevice.setDisplayOrientation(0);

            mCameraTexture.setOnFrameAvailableListener(this);
            mCameraDevice.setPreviewTextureAsync(mCameraTexture);
        }
        mCameraDevice.startPreviewAsync();
        mCameraState = PREVIEW_ACTIVE;
    }

    private void stopCameraPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
        }
        mCameraState = PREVIEW_STOPPED;
    }

    @Override
    public void onUserInteraction() {
        if (mCaptureState != CAPTURE_STATE_MOSAIC) keepScreenOnAwhile();
    }

    @Override
    public boolean onBackPressed() {
        // If panorama is generating low res or high res mosaic, ignore back
        // key. So the activity will not be destroyed.
        if (mThreadRunning) return true;
        return false;
    }

    private void resetScreenOn() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMainHandler.sendEmptyMessageDelayed(MSG_CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void keepScreenOn() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private class WaitProcessorTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            synchronized (mMosaicFrameProcessor) {
                while (!isCancelled() && mMosaicFrameProcessor.isMosaicMemoryAllocated()) {
                    try {
                        mMosaicFrameProcessor.wait();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mWaitProcessorTask = null;
            mRotateDialog.dismissDialog();
            mGLRootView.setVisibility(View.VISIBLE);
            initMosaicFrameProcessorIfNeeded();
            int w = mPreviewArea.getWidth();
            int h = mPreviewArea.getHeight();
            if (w != 0 && h != 0) {  // The layout has been calculated.
                configMosaicPreview(w, h);
            }
            resetToPreview();
        }
    }

    @Override
    public void onFullScreenChanged(boolean full) {
    }


    @Override
    public void onStop() {
    }

    @Override
    public void installIntentFilter() {
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
    }

    @Override
    public void onPreviewTextureCopied() {
    }

    @Override
    public void onCaptureTextureCopied() {
    }

    @Override
    public boolean updateStorageHintOnResume() {
        return false;
    }

    @Override
    public void updateCameraAppView() {
    }

    @Override
    public boolean collapseCameraControls() {
        return false;
    }

    @Override
    public boolean needsSwitcher() {
        return true;
    }

    @Override
    public void onShowSwitcherPopup() {
    }
}
