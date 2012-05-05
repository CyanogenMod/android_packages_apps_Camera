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

package com.android.camera.panorama;

import com.android.camera.ActivityBase;
import com.android.camera.CameraDisabledException;
import com.android.camera.CameraHardwareException;
import com.android.camera.CameraHolder;
import com.android.camera.CameraSettings;
import com.android.camera.ComboPreferences;
import com.android.camera.Exif;
import com.android.camera.MenuHelper;
import com.android.camera.ModePicker;
import com.android.camera.OnClickAttr;
import com.android.camera.R;
import com.android.camera.RotateDialogController;
import com.android.camera.ShutterButton;
import com.android.camera.Storage;
import com.android.camera.Thumbnail;
import com.android.camera.Util;
import com.android.camera.ui.PopupManager;
import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.SharePopup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.CameraSound;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Activity to handle panorama capturing.
 */
public class PanoramaActivity extends ActivityBase implements
        ModePicker.OnModeChangeListener, SurfaceTexture.OnFrameAvailableListener,
        ShutterButton.OnShutterButtonListener,
        MosaicRendererSurfaceViewRenderer.MosaicSurfaceCreateListener {
    public static final int DEFAULT_SWEEP_ANGLE = 160;
    public static final int DEFAULT_BLEND_MODE = Mosaic.BLENDTYPE_HORIZONTAL;
    public static final int DEFAULT_CAPTURE_PIXELS = 960 * 720;

    private static final int MSG_LOW_RES_FINAL_MOSAIC_READY = 1;
    private static final int MSG_RESET_TO_PREVIEW_WITH_THUMBNAIL = 2;
    private static final int MSG_GENERATE_FINAL_MOSAIC_ERROR = 3;
    private static final int MSG_RESET_TO_PREVIEW = 4;
    private static final int MSG_CLEAR_SCREEN_DELAY = 5;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final String TAG = "PanoramaActivity";
    private static final int PREVIEW_STOPPED = 0;
    private static final int PREVIEW_ACTIVE = 1;
    private static final int CAPTURE_STATE_VIEWFINDER = 0;
    private static final int CAPTURE_STATE_MOSAIC = 1;

    // Speed is in unit of deg/sec
    private static final float PANNING_SPEED_THRESHOLD = 20f;

    // Ratio of nanosecond to second
    private static final float NS2S = 1.0f / 1000000000.0f;

    private ComboPreferences mPreferences;
    private String mStorage;

    private boolean mPausing;

    private View mPanoLayout;
    private View mCaptureLayout;
    private View mReviewLayout;
    private ImageView mReview;
    private RotateLayout mCaptureIndicator;
    private PanoProgressBar mPanoProgressBar;
    private PanoProgressBar mSavingProgressBar;
    private View mFastIndicationBorder;
    private View mLeftIndicator;
    private View mRightIndicator;
    private MosaicRendererSurfaceView mMosaicView;
    private TextView mTooFastPrompt;
    private ShutterButton mShutterButton;
    private Object mWaitObject = new Object();

    private String mPreparePreviewString;
    private String mDialogTitle;
    private String mDialogOkString;
    private String mDialogPanoramaFailedString;

    private int mIndicatorColor;
    private int mIndicatorColorFast;

    private float mCompassValueX;
    private float mCompassValueY;
    private float mCompassValueXStart;
    private float mCompassValueYStart;
    private float mCompassValueXStartBuffer;
    private float mCompassValueYStartBuffer;
    private int mCompassThreshold;
    private int mTraversedAngleX;
    private int mTraversedAngleY;
    private long mTimestamp;

    private RotateImageView mThumbnailView;
    private Thumbnail mThumbnail;
    private SharePopup mSharePopup;

    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mCameraState;
    private int mCaptureState;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private PowerManager.WakeLock mPartialWakeLock;
    private ModePicker mModePicker;
    private MosaicFrameProcessor mMosaicFrameProcessor;
    private long mTimeTaken;
    private Handler mMainHandler;
    private SurfaceTexture mSurfaceTexture;
    private boolean mThreadRunning;
    private boolean mCancelComputation;
    private float[] mTransformMatrix;
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

    private CameraSound mCameraSound;

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
                    + Util.getDisplayRotation(PanoramaActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                setOrientationIndicator(mOrientationCompensation);
            }
        }
    }

    private void setOrientationIndicator(int degree) {
        if (mSharePopup != null) mSharePopup.setOrientation(degree);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        addBaseMenuItems(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Only show the menu when idle.
        boolean idle = (mCaptureState == CAPTURE_STATE_VIEWFINDER && !mThreadRunning);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setVisible(idle);
            item.setEnabled(idle);
        }

        return true;
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_CAMERA, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_CAMERA);
            }
        });
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_VIDEO, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_VIDEO);
            }
        });
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPreferences = new ComboPreferences(this);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        powerShutter(mPreferences);
        mPreferences.setLocalId(this, CameraHolder.instance().getBackCameraId());
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        mStorage = CameraSettings.readStorage(mPreferences);

        Window window = getWindow();
        Util.enterLightsOutMode(window);
        Util.initializeScreenBrightness(window, getContentResolver());

        createContentView();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (mSensor == null) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Panorama");

        mOrientationEventListener = new PanoOrientationEventListener(this);

        mTransformMatrix = new float[16];

        mPreparePreviewString =
                getResources().getString(R.string.pano_dialog_prepare_preview);
        mDialogTitle = getResources().getString(R.string.pano_dialog_title);
        mDialogOkString = getResources().getString(R.string.dialog_ok);
        mDialogPanoramaFailedString =
                getResources().getString(R.string.pano_dialog_panorama_failed);
        mCameraSound = new CameraSound();

        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_LOW_RES_FINAL_MOSAIC_READY:
                        onBackgroundThreadFinished();
                        showFinalMosaic((Bitmap) msg.obj);
                        saveHighResMosaic();
                        break;
                    case MSG_RESET_TO_PREVIEW_WITH_THUMBNAIL:
                        onBackgroundThreadFinished();
                        // If the activity is paused, save the thumbnail to the file here.
                        // If not, it will be saved in onPause.
                        if (mPausing) saveThumbnailToFile();
                        // Set the thumbnail bitmap here because mThumbnailView must be accessed
                        // from the UI thread.
                        updateThumbnailButton();

                        // Share popup may still have the reference to the old thumbnail. Clear it.
                        mSharePopup = null;
                        resetToPreview();
                        break;
                    case MSG_GENERATE_FINAL_MOSAIC_ERROR:
                        onBackgroundThreadFinished();
                        if (mPausing) {
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
                        break;
                    case MSG_RESET_TO_PREVIEW:
                        onBackgroundThreadFinished();
                        resetToPreview();
                        break;
                    case MSG_CLEAR_SCREEN_DELAY:
                        getWindow().clearFlags(WindowManager.LayoutParams.
                                FLAG_KEEP_SCREEN_ON);
                        break;
                }
                clearMosaicFrameProcessorIfNeeded();
            }
        };
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
        int backCameraId = CameraHolder.instance().getBackCameraId();
        mCameraDevice = Util.openCamera(this, backCameraId);
        mCameraOrientation = Util.getCameraOrientation(backCameraId);
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
        if (frameRates != null) {
            int last = frameRates.size() - 1;
            int minFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MIN_INDEX];
            int maxFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MAX_INDEX];
            parameters.setPreviewFpsRange(minFps, maxFps);
            Log.v(TAG, "preview fps: " + minFps + ", " + maxFps);
        } else {
            // Use the default FPS range and log an error message
            Log.e(TAG, "Could not get/set preview FPS range! Using default.");
        }

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        if (supportedFocusModes.indexOf(mTargetFocusMode) >= 0) {
            parameters.setFocusMode(mTargetFocusMode);
        } else {
            // Use the default focus mode and log a message
            Log.w(TAG, "Cannot set the focus mode to " + mTargetFocusMode +
                  " becuase the mode is not supported.");
        }

        parameters.setRecordingHint(false);

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

    private boolean switchToOtherMode(int mode) {
        if (isFinishing()) {
            return false;
        }
        MenuHelper.gotoMode(mode, this);
        finish();
        return true;
    }

    public boolean onModeChanged(int mode) {
        if (mode != ModePicker.MODE_PANORAMA) {
            return switchToOtherMode(mode);
        } else {
            return true;
        }
    }

    @Override
    public void onMosaicSurfaceChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // If panorama is generating low res or high res mosaic, it
                // means users exit and come back to panorama. Do not start the
                // preview. Preview will be started after final mosaic is
                // generated.
                if (!mPausing && !mThreadRunning) {
                    startCameraPreview();
                }
            }
        });
    }

    @Override
    public void onMosaicSurfaceCreated(final int textureID) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.release();
                }
                mSurfaceTexture = new SurfaceTexture(textureID);
                if (!mPausing) {
                    mSurfaceTexture.setOnFrameAvailableListener(PanoramaActivity.this);
                }
            }
        });
    }

    public void runViewFinder() {
        mMosaicView.setWarping(false);
        // Call preprocess to render it to low-res and high-res RGB textures.
        mMosaicView.preprocess(mTransformMatrix);
        mMosaicView.setReady();
        mMosaicView.requestRender();
    }

    public void runMosaicCapture() {
        mMosaicView.setWarping(true);
        // Call preprocess to render it to low-res and high-res RGB textures.
        mMosaicView.preprocess(mTransformMatrix);
        // Lock the conditional variable to ensure the order of transferGPUtoCPU and
        // mMosaicFrame.processFrame().
        mMosaicView.lockPreviewReadyFlag();
        // Now, transfer the textures from GPU to CPU memory for processing
        mMosaicView.transferGPUtoCPU();
        // Wait on the condition variable (will be opened when GPU->CPU transfer is done).
        mMosaicView.waitUntilPreviewReady();
        mMosaicFrameProcessor.processFrame();
    }

    public synchronized void onFrameAvailable(SurfaceTexture surface) {
        /* This function may be called by some random thread,
         * so let's be safe and use synchronize. No OpenGL calls can be done here.
         */
        // Frames might still be available after the activity is paused. If we call onFrameAvailable
        // after pausing, the GL thread will crash.
        if (mPausing) return;

        // Updating the texture should be done in the GL thread which mMosaicView is attached.
        mMosaicView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // Check if the activity is paused here can speed up the onPause() process.
                if (mPausing) return;
                mSurfaceTexture.updateTexImage();
                mSurfaceTexture.getTransformMatrix(mTransformMatrix);
            }
        });
        // Update the transformation matrix for mosaic pre-process.
        if (mCaptureState == CAPTURE_STATE_VIEWFINDER) {
            runViewFinder();
        } else {
            runMosaicCapture();
        }
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
        mCaptureState = CAPTURE_STATE_MOSAIC;
        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_pan_recording);
        mCaptureIndicator.setVisibility(View.VISIBLE);
        showDirectionIndicators(PanoProgressBar.DIRECTION_NONE);
        mThumbnailView.setEnabled(false);

        mCompassValueXStart = mCompassValueXStartBuffer;
        mCompassValueYStart = mCompassValueYStartBuffer;
        mTimestamp = 0;

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

        if (mModePicker != null) mModePicker.setEnabled(false);

        mPanoProgressBar.reset();
        // TODO: calculate the indicator width according to different devices to reflect the actual
        // angle of view of the camera device.
        mPanoProgressBar.setIndicatorWidth(20);
        mPanoProgressBar.setMaxProgress(DEFAULT_SWEEP_ANGLE);
        mPanoProgressBar.setVisibility(View.VISIBLE);
        mDeviceOrientationAtCapture = mDeviceOrientation;
        keepScreenOn();
    }

    private void stopCapture(boolean aborted) {
        mCaptureState = CAPTURE_STATE_VIEWFINDER;
        mCaptureIndicator.setVisibility(View.GONE);
        hideTooFastIndication();
        hideDirectionIndicators();
        mThumbnailView.setEnabled(true);

        mMosaicFrameProcessor.setProgressListener(null);
        stopCameraPreview();

        mSurfaceTexture.setOnFrameAvailableListener(null);

        if (!aborted && !mThreadRunning) {
            mRotateDialog.showWaitingDialog(mPreparePreviewString);
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
                                MSG_RESET_TO_PREVIEW));
                    }
                }
            });
        }
        // do we have to wait for the thread to complete before enabling this?
        if (mModePicker != null) mModePicker.setEnabled(true);
        keepScreenOnAwhile();
    }

    private void showTooFastIndication() {
        mTooFastPrompt.setVisibility(View.VISIBLE);
        mFastIndicationBorder.setVisibility(View.VISIBLE);
        mPanoProgressBar.setIndicatorColor(mIndicatorColorFast);
        mLeftIndicator.setEnabled(true);
        mRightIndicator.setEnabled(true);
    }

    private void hideTooFastIndication() {
        mTooFastPrompt.setVisibility(View.GONE);
        mFastIndicationBorder.setVisibility(View.GONE);
        mPanoProgressBar.setIndicatorColor(mIndicatorColor);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
    }

    private void updateProgress(float panningRateXInDegree, float panningRateYInDegree,
            float progressHorizontalAngle, float progressVerticalAngle) {
        mMosaicView.setReady();
        mMosaicView.requestRender();

        // TODO: Now we just display warning message by the panning speed.
        // Since we only support horizontal panning, we should display a warning message
        // in UI when there're significant vertical movements.
        if ((Math.abs(panningRateXInDegree) > PANNING_SPEED_THRESHOLD)
            || (Math.abs(panningRateYInDegree) > PANNING_SPEED_THRESHOLD)) {
            showTooFastIndication();
        } else {
            hideTooFastIndication();
        }
        int angleInMajorDirection =
                (Math.abs(progressHorizontalAngle) > Math.abs(progressVerticalAngle))
                ? (int) progressHorizontalAngle
                : (int) progressVerticalAngle;
        mPanoProgressBar.setProgress((angleInMajorDirection));
    }

    private void createContentView() {
        setContentView(R.layout.panorama);

        mCaptureState = CAPTURE_STATE_VIEWFINDER;

        Resources appRes = getResources();

        mCaptureLayout = (View) findViewById(R.id.pano_capture_layout);
        mPanoProgressBar = (PanoProgressBar) findViewById(R.id.pano_pan_progress_bar);
        mPanoProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mPanoProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_done));
        mIndicatorColor = appRes.getColor(R.color.pano_progress_indication);
        mIndicatorColorFast = appRes.getColor(R.color.pano_progress_indication_fast);
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

        mLeftIndicator = (ImageView) findViewById(R.id.pano_pan_left_indicator);
        mRightIndicator = (ImageView) findViewById(R.id.pano_pan_right_indicator);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
        mTooFastPrompt = (TextView) findViewById(R.id.pano_capture_too_fast_textview);
        mFastIndicationBorder = (View) findViewById(R.id.pano_speed_indication_border);

        mSavingProgressBar = (PanoProgressBar) findViewById(R.id.pano_saving_progress_bar);
        mSavingProgressBar.setIndicatorWidth(0);
        mSavingProgressBar.setMaxProgress(100);
        mSavingProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mSavingProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_indication));

        mCaptureIndicator = (RotateLayout) findViewById(R.id.pano_capture_indicator);

        mThumbnailView = (RotateImageView) findViewById(R.id.thumbnail);
        mThumbnailView.enableFilter(false);

        mReviewLayout = (View) findViewById(R.id.pano_review_layout);
        mReview = (ImageView) findViewById(R.id.pano_reviewarea);
        mMosaicView = (MosaicRendererSurfaceView) findViewById(R.id.pano_renderer);
        mMosaicView.getRenderer().setMosaicSurfaceCreateListener(this);

        mModePicker = (ModePicker) findViewById(R.id.mode_picker);
        mModePicker.setVisibility(View.VISIBLE);
        mModePicker.setOnModeChangeListener(this);
        mModePicker.setCurrentMode(ModePicker.MODE_PANORAMA);

        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_pan);
        mShutterButton.setOnShutterButtonListener(this);

        mPanoLayout = findViewById(R.id.pano_layout);

        mRotateDialog = new RotateDialogController(this, R.layout.rotate_dialog);

        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            Rotatable[] rotateLayout = {
                    (Rotatable) findViewById(R.id.pano_pan_progress_bar_layout),
                    (Rotatable) findViewById(R.id.pano_capture_too_fast_textview_layout),
                    (Rotatable) findViewById(R.id.pano_review_saving_indication_layout),
                    (Rotatable) findViewById(R.id.pano_saving_progress_bar_layout),
                    (Rotatable) findViewById(R.id.pano_review_cancel_button_layout),
                    (Rotatable) findViewById(R.id.pano_rotate_reviewarea),
                    (Rotatable) mRotateDialog,
                    (Rotatable) mCaptureIndicator,
                    (Rotatable) mModePicker,
                    (Rotatable) mThumbnailView};
            for (Rotatable r : rotateLayout) {
                r.setOrientation(270);
            }
        }
    }

    @Override
    public void onShutterButtonClick() {
        // If mSurfaceTexture == null then GL setup is not finished yet.
        // No buttons can be pressed.
        if (mPausing || mThreadRunning || mSurfaceTexture == null) return;
        // Since this button will stay on the screen when capturing, we need to check the state
        // right now.
        switch (mCaptureState) {
            case CAPTURE_STATE_VIEWFINDER:
                mCameraSound.playSound(CameraSound.START_VIDEO_RECORDING);
                startCapture();
                break;
            case CAPTURE_STATE_MOSAIC:
                mCameraSound.playSound(CameraSound.STOP_VIDEO_RECORDING);
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
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mSavingProgressBar.setProgress(progress);
                        }
                    });
                }
            }
        };
        t.start();
    }

    private void initThumbnailButton() {
        // Load the thumbnail from the disk.
        if (mThumbnail == null) {
            mThumbnail = Thumbnail.loadFrom(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
        }
        updateThumbnailButton();
    }

    private void updateThumbnailButton() {
        // Update last image if URI is invalid and the storage is ready.
        ContentResolver contentResolver = getContentResolver();
        if ((mThumbnail == null || !Util.isUriValid(mThumbnail.getUri(), contentResolver))) {
            mThumbnail = Thumbnail.getLastThumbnail(contentResolver,
                Storage.generateBucketId(mStorage));
        }
        if (mThumbnail != null) {
            mThumbnailView.setBitmap(mThumbnail.getBitmap());
        } else {
            mThumbnailView.setBitmap(null);
        }
    }

    private void saveThumbnailToFile() {
        if (mThumbnail != null && !mThumbnail.fromFile()) {
            mThumbnail.saveTo(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
        }
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
                    mMainHandler.sendEmptyMessage(MSG_RESET_TO_PREVIEW);
                } else if (!jpeg.isValid) {  // Error when generating mosaic.
                    mMainHandler.sendEmptyMessage(MSG_GENERATE_FINAL_MOSAIC_ERROR);
                } else {
                    // The panorama image returned from the library is orientated based on the
                    // natural orientation of a camera. We need to set an orientation for the image
                    // in its EXIF header, so the image can be displayed correctly.
                    // The orientation is calculated from compensating the
                    // device orientation at capture and the camera orientation respective to
                    // the natural orientation of the device.
                    int orientation = (mDeviceOrientationAtCapture + mCameraOrientation) % 360;
                    Uri uri = savePanorama(jpeg.data, jpeg.width, jpeg.height, orientation);
                    if (uri != null) {
                        // Create a thumbnail whose width or height is equal or bigger
                        // than the screen's width or height.
                        int widthRatio = (int) Math.ceil((double) jpeg.width
                                / mPanoLayout.getWidth());
                        int heightRatio = (int) Math.ceil((double) jpeg.height
                                / mPanoLayout.getHeight());
                        int inSampleSize = Integer.highestOneBit(
                                Math.max(widthRatio, heightRatio));
                        mThumbnail = Thumbnail.createThumbnail(
                                jpeg.data, orientation, inSampleSize, uri);
                        Util.broadcastNewPicture(PanoramaActivity.this, uri);
                    }
                    mMainHandler.sendMessage(
                            mMainHandler.obtainMessage(MSG_RESET_TO_PREVIEW_WITH_THUMBNAIL));
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

    @OnClickAttr
    public void onCancelButtonClicked(View v) {
        if (mPausing || mSurfaceTexture == null) return;
        cancelHighResComputation();
    }

    @OnClickAttr
    public void onThumbnailClicked(View v) {
        if (mPausing || mThreadRunning || mSurfaceTexture == null) return;
        showSharePopup();
    }

    private void showSharePopup() {
        if (mThumbnail == null) return;
        Uri uri = mThumbnail.getUri();
        if (mSharePopup == null || !uri.equals(mSharePopup.getUri())) {
            // The orientation compensation is set to 0 here because we only support landscape.
            mSharePopup = new SharePopup(this, uri, mThumbnail.getBitmap(),
                    mOrientationCompensation,
                    findViewById(R.id.frame_layout));
        }
        mSharePopup.showAtLocation(mThumbnailView, Gravity.NO_GRAVITY, 0, 0);
    }

    private void reset() {
        mCaptureState = CAPTURE_STATE_VIEWFINDER;

        mReviewLayout.setVisibility(View.GONE);
        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_pan);
        mPanoProgressBar.setVisibility(View.GONE);
        mCaptureLayout.setVisibility(View.VISIBLE);
        mMosaicFrameProcessor.reset();

        mSurfaceTexture.setOnFrameAvailableListener(this);
    }

    private void resetToPreview() {
        reset();
        if (!mPausing) startCameraPreview();
    }

    private void showFinalMosaic(Bitmap bitmap) {
        if (bitmap != null) {
            mReview.setImageBitmap(bitmap);
        }
        mCaptureLayout.setVisibility(View.GONE);
        mReviewLayout.setVisibility(View.VISIBLE);
    }

    private Uri savePanorama(byte[] jpegData, int width, int height, int orientation) {
        if (jpegData != null) {
            String filename = PanoUtil.createName(
                    getResources().getString(R.string.pano_file_name_format), mTimeTaken);
            Uri uri = Storage.addImage(getContentResolver(), mStorage, filename, mTimeTaken, null,
                    orientation, jpegData, width, height);
            if (uri != null && orientation != 0) {
                String filepath = Storage.generateFilepath(mStorage, filename);
                try {
                    // Save the orientation in EXIF.
                    ExifInterface exif = new ExifInterface(filepath);
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                            getExifOrientation(orientation));
                    exif.saveAttributes();
                } catch (IOException e) {
                    Log.e(TAG, "cannot set exif data: " + filepath);
                }
            }
            return uri;
        }
        return null;
    }

    private static String getExifOrientation(int orientation) {
        switch (orientation) {
            case 0:
                return String.valueOf(ExifInterface.ORIENTATION_NORMAL);
            case 90:
                return String.valueOf(ExifInterface.ORIENTATION_ROTATE_90);
            case 180:
                return String.valueOf(ExifInterface.ORIENTATION_ROTATE_180);
            case 270:
                return String.valueOf(ExifInterface.ORIENTATION_ROTATE_270);
            default:
                throw new AssertionError("invalid: " + orientation);
        }
    }

    private void clearMosaicFrameProcessorIfNeeded() {
        if (!mPausing || mThreadRunning) return;
        mMosaicFrameProcessor.clear();
    }

    private void initMosaicFrameProcessorIfNeeded() {
        if (mPausing || mThreadRunning) return;
        if (mMosaicFrameProcessor == null) {
            // Start the activity for the first time.
            mMosaicFrameProcessor = new MosaicFrameProcessor(
                    mPreviewWidth, mPreviewHeight, getPreviewBufSize());
        }
        mMosaicFrameProcessor.initialize();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mPausing = true;
        // Stop the capturing first.
        if (mCaptureState == CAPTURE_STATE_MOSAIC) {
            stopCapture(true);
            reset();
        }
        if (mSharePopup != null) mSharePopup.dismiss();

        saveThumbnailToFile();

        releaseCamera();
        mMosaicView.onPause();
        clearMosaicFrameProcessorIfNeeded();
        mOrientationEventListener.disable();
        resetScreenOn();
        mCameraSound.release();
        System.gc();
    }

    @Override
    protected void doOnResume() {
        mPausing = false;
        mOrientationEventListener.enable();

        mCaptureState = CAPTURE_STATE_VIEWFINDER;
        try {
            setupCamera();

            // Camera must be initialized before MosaicFrameProcessor is initialized.
            // The preview size has to be decided by camera device.
            initMosaicFrameProcessorIfNeeded();
            mMosaicView.onResume();

            initThumbnailButton();
            keepScreenOnAwhile();
        } catch (CameraHardwareException e) {
            Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
            return;
        } catch (CameraDisabledException e) {
            Util.showErrorAndFinish(this, R.string.camera_disabled);
            return;
        }
        // Dismiss open menu if exists.
        PopupManager.getInstance(this).notifyShowPopup(null);
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
            // TODO: pop up a error meesage indicating that the final result is not generated.
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

    private void setPreviewTexture(SurfaceTexture surface) {
        try {
            mCameraDevice.setPreviewTexture(surface);
        } catch (Throwable ex) {
            releaseCamera();
            throw new RuntimeException("setPreviewTexture failed", ex);
        }
    }

    private void startCameraPreview() {
        // If we're previewing already, stop the preview first (this will blank
        // the screen).
        if (mCameraState != PREVIEW_STOPPED) stopCameraPreview();

        // Set the display orientation to 0, so that the underlying mosaic library
        // can always get undistorted mPreviewWidth x mPreviewHeight image data
        // from SurfaceTexture.
        mCameraDevice.setDisplayOrientation(0);

        setPreviewTexture(mSurfaceTexture);

        try {
            Log.v(TAG, "startPreview");
            mCameraDevice.startPreview();
        } catch (Throwable ex) {
            releaseCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
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
        super.onUserInteraction();
        if (mCaptureState != CAPTURE_STATE_MOSAIC) keepScreenOnAwhile();
    }

    @Override
    public void onBackPressed() {
        // If panorama is generating low res or high res mosaic, ignore back
        // key. So the activity will not be destroyed.
        if (mThreadRunning) return;
        super.onBackPressed();
    }

    private void resetScreenOn() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMainHandler.sendEmptyMessageDelayed(MSG_CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void keepScreenOn() {
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
