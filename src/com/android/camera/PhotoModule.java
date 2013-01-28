/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.CameraProfile;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PopupManager;
import com.android.camera.ui.PreviewSurfaceView;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.TwoStateImageView;
import com.android.camera.ui.ZoomRenderer;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.filtershow.CropExtras;
import com.android.gallery3d.filtershow.FilterShowActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

public class PhotoModule
    implements CameraModule,
    FocusOverlayManager.Listener,
    CameraPreference.OnPreferenceChangedListener,
    LocationManager.Listener,
    PreviewFrameLayout.OnSizeChangedListener,
    ShutterButton.OnShutterButtonListener,
    SurfaceHolder.Callback,
    PieRenderer.PieListener,
    CountDownView.OnCountDownFinishedListener {

    private static final String TAG = "CAM_PhotoModule";

    // We number the request code from 1000 to avoid collision with Gallery.
    private static final int REQUEST_CROP = 1000;

    private static final int SETUP_PREVIEW = 1;
    private static final int FIRST_TIME_INIT = 2;
    private static final int CLEAR_SCREEN_DELAY = 3;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 4;
    private static final int CHECK_DISPLAY_ROTATION = 5;
    private static final int SHOW_TAP_TO_FOCUS_TOAST = 6;
    private static final int SWITCH_CAMERA = 7;
    private static final int SWITCH_CAMERA_START_ANIMATION = 8;
    private static final int CAMERA_OPEN_DONE = 9;
    private static final int START_PREVIEW_DONE = 10;
    private static final int OPEN_CAMERA_FAIL = 11;
    private static final int CAMERA_DISABLED = 12;
    private static final int UPDATE_SECURE_ALBUM_ITEM = 13;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_ZOOM = 2;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;

    // This is the timeout to keep the camera in onPause for the first time
    // after screen on if the activity is started from secure lock screen.
    private static final int KEEP_CAMERA_TIMEOUT = 1000; // ms

    // copied from Camera hierarchy
    private CameraActivity mActivity;
    private View mRootView;
    private CameraProxy mCameraDevice;
    private int mCameraId;
    private Parameters mParameters;
    private boolean mPaused;
    private AbstractSettingPopup mPopup;

    // these are only used by Camera

    // The activity is going to switch to the specified camera id. This is
    // needed because texture copy is done in GL thread. -1 means camera is not
    // switching.
    protected int mPendingSwitchCameraId = -1;
    private boolean mOpenCameraFail;
    private boolean mCameraDisabled;

    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private int mZoomValue;  // The current zoom value.
    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private Parameters mInitialParams;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;
    private boolean mAeLockSupported;
    private boolean mAwbLockSupported;
    private boolean mContinousFocusSupported;

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private ComboPreferences mPreferences;

    private static final String sTempCropFilename = "crop-temp";

    private ContentProviderClient mMediaProviderClient;
    private ShutterButton mShutterButton;
    private boolean mFaceDetectionStarted = false;

    private PreviewFrameLayout mPreviewFrameLayout;
    private Object mSurfaceTexture;
    private CountDownView mCountDownView;

    // for API level 10
    private PreviewSurfaceView mPreviewSurfaceView;
    private volatile SurfaceHolder mCameraSurfaceHolder;

    private FaceView mFaceView;
    private RenderOverlay mRenderOverlay;
    private Rotatable mReviewCancelButton;
    private Rotatable mReviewDoneButton;
    private View mReviewRetakeButton;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    private View mMenu;
    private View mBlocker;

    // Small indicators which show the camera settings in the viewfinder.
    private ImageView mExposureIndicator;
    private ImageView mFlashIndicator;
    private ImageView mSceneIndicator;
    private ImageView mHdrIndicator;
    // A view group that contains all the small indicators.
    private View mOnScreenIndicators;

    // We use a thread in MediaSaver to do the work of saving images. This
    // reduces the shot-to-shot time.
    private MediaSaver mMediaSaver;
    // Similarly, we use a thread to generate the name of the picture and insert
    // it into MediaStore while picture taking is still in progress.
    private NamedImages mNamedImages;

    private Runnable mDoSnapRunnable = new Runnable() {
        @Override
        public void run() {
            onShutterButtonClick();
        }
    };

    private final StringBuilder mBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mBuilder);
    private final Object[] mFormatterArgs = new Object[1];

    /**
     * An unpublished intent flag requesting to return as soon as capturing
     * is completed.
     *
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mCameraDisplayOrientation;
    // The value for UI components like indicators.
    private int mDisplayOrientation;
    // The value for android.hardware.Camera.Parameters.setRotation.
    private int mJpegRotation;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;

    private static final int PREVIEW_STOPPED = 0;
    private static final int IDLE = 1;  // preview is active
    // Focus is in progress. The exact focus state is in Focus.java.
    private static final int FOCUSING = 2;
    private static final int SNAPSHOT_IN_PROGRESS = 3;
    // Switching between cameras.
    private static final int SWITCHING_CAMERA = 4;
    private int mCameraState = PREVIEW_STOPPED;
    private boolean mSnapshotOnIdle = false;

    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;

    private final ShutterCallback mShutterCallback = new ShutterCallback();
    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final Object mAutoFocusMoveCallback =
            ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK
            ? new AutoFocusMoveCallback()
            : null;

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    private long mFocusStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private long mOnResumeTime;
    private byte[] mJpegImageData;

    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;
    public long mCaptureStartTime;

    // This handles everything about focus.
    private FocusOverlayManager mFocusManager;

    private PieRenderer mPieRenderer;
    private PhotoController mPhotoControl;

    private ZoomRenderer mZoomRenderer;

    private String mSceneMode;
    private Toast mNotSelectableToast;

    private final Handler mHandler = new MainHandler();
    private PreferenceGroup mPreferenceGroup;

    private boolean mQuickCapture;

    CameraStartUpThread mCameraStartUpThread;
    ConditionVariable mStartPreviewPrerequisiteReady = new ConditionVariable();

    private PreviewGestures mGestures;

    private MediaSaver.OnMediaSavedListener mOnMediaSavedListener = new MediaSaver.OnMediaSavedListener() {
        @Override

        public void onMediaSaved(Uri uri) {
            if (uri != null) {
                mHandler.obtainMessage(UPDATE_SECURE_ALBUM_ITEM, uri).sendToTarget();
                Util.broadcastNewPicture(mActivity, uri);
            }
        }
    };

    // The purpose is not to block the main thread in onCreate and onResume.
    private class CameraStartUpThread extends Thread {
        private volatile boolean mCancelled;

        public void cancel() {
            mCancelled = true;
            interrupt();
        }

        public boolean isCanceled() {
            return mCancelled;
        }

        @Override
        public void run() {
            try {
                // We need to check whether the activity is paused before long
                // operations to ensure that onPause() can be done ASAP.
                if (mCancelled) return;
                mCameraDevice = Util.openCamera(mActivity, mCameraId);
                mParameters = mCameraDevice.getParameters();
                // Wait until all the initialization needed by startPreview are
                // done.
                mStartPreviewPrerequisiteReady.block();

                initializeCapabilities();
                if (mFocusManager == null) initializeFocusManager();
                if (mCancelled) return;
                setCameraParameters(UPDATE_PARAM_ALL);
                mHandler.sendEmptyMessage(CAMERA_OPEN_DONE);
                if (mCancelled) return;
                startPreview();
                mHandler.sendEmptyMessage(START_PREVIEW_DONE);
                mOnResumeTime = SystemClock.uptimeMillis();
                mHandler.sendEmptyMessage(CHECK_DISPLAY_ROTATION);
            } catch (CameraHardwareException e) {
                mHandler.sendEmptyMessage(OPEN_CAMERA_FAIL);
            } catch (CameraDisabledException e) {
                mHandler.sendEmptyMessage(CAMERA_DISABLED);
            }
        }
    }

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SETUP_PREVIEW: {
                    setupPreview();
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    setCameraParametersWhenIdle(0);
                    break;
                }

                case CHECK_DISPLAY_ROTATION: {
                    // Set the display orientation if display rotation has changed.
                    // Sometimes this happens when the device is held upside
                    // down and camera app is opened. Rotation animation will
                    // take some time and the rotation value we have got may be
                    // wrong. Framework does not have a callback for this now.
                    if (Util.getDisplayRotation(mActivity) != mDisplayRotation) {
                        setDisplayOrientation();
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
                    }
                    break;
                }

                case SHOW_TAP_TO_FOCUS_TOAST: {
                    showTapToFocusToast();
                    break;
                }

                case SWITCH_CAMERA: {
                    switchCamera();
                    break;
                }

                case SWITCH_CAMERA_START_ANIMATION: {
                    ((CameraScreenNail) mActivity.mCameraScreenNail).animateSwitchCamera();
                    break;
                }

                case CAMERA_OPEN_DONE: {
                    initializeAfterCameraOpen();
                    break;
                }

                case START_PREVIEW_DONE: {
                    mCameraStartUpThread = null;
                    setCameraState(IDLE);
                    if (!ApiHelper.HAS_SURFACE_TEXTURE) {
                        // This may happen if surfaceCreated has arrived.
                        mCameraDevice.setPreviewDisplayAsync(mCameraSurfaceHolder);
                    }
                    startFaceDetection();
                    locationFirstRun();
                    break;
                }

                case OPEN_CAMERA_FAIL: {
                    mCameraStartUpThread = null;
                    mOpenCameraFail = true;
                    Util.showErrorAndFinish(mActivity,
                            R.string.cannot_connect_camera);
                    break;
                }

                case CAMERA_DISABLED: {
                    mCameraStartUpThread = null;
                    mCameraDisabled = true;
                    Util.showErrorAndFinish(mActivity,
                            R.string.camera_disabled);
                    break;
                }

                case UPDATE_SECURE_ALBUM_ITEM: {
                    mActivity.addSecureAlbumItemIfNeeded(false, (Uri) msg.obj);
                    break;
                }
            }
        }
    }

    @Override
    public void init(CameraActivity activity, View parent, boolean reuseNail) {
        mActivity = activity;
        mRootView = parent;
        mPreferences = new ComboPreferences(mActivity);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = getPreferredCameraId(mPreferences);

        mContentResolver = mActivity.getContentResolver();

        // To reduce startup time, open the camera and start the preview in
        // another thread.
        mCameraStartUpThread = new CameraStartUpThread();
        mCameraStartUpThread.start();

        mActivity.getLayoutInflater().inflate(R.layout.photo_module, (ViewGroup) mRootView);

        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsImageCaptureIntent = isImageCaptureIntent();
        if (reuseNail) {
            mActivity.reuseCameraScreenNail(!mIsImageCaptureIntent);
        } else {
            mActivity.createCameraScreenNail(!mIsImageCaptureIntent);
        }

        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        // we need to reset exposure for the preview
        resetExposureCompensation();
        // Starting the preview needs preferences, camera screen nail, and
        // focus area indicator.
        mStartPreviewPrerequisiteReady.open();

        initializeControlByIntent();
        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        initializeMiscControls();
        mLocationManager = new LocationManager(mActivity, this);
        initOnScreenIndicator();
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener(this);
    }

    // Prompt the user to pick to record location for the very first run of
    // camera only
    private void locationFirstRun() {
        if (RecordLocationPreference.isSet(mPreferences)) {
            return;
        }
        if (mActivity.isSecureCamera()) return;
        // Check if the back camera exists
        int backCameraId = CameraHolder.instance().getBackCameraId();
        if (backCameraId == -1) {
            // If there is no back camera, do not show the prompt.
            return;
        }

        new AlertDialog.Builder(mActivity)
            .setTitle(R.string.remember_location_title)
            .setMessage(R.string.remember_location_prompt)
            .setPositiveButton(R.string.remember_location_yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int arg1) {
                    setLocationPreference(RecordLocationPreference.VALUE_ON);
                }
            })
            .setNegativeButton(R.string.remember_location_no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int arg1) {
                    dialog.cancel();
                }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    setLocationPreference(RecordLocationPreference.VALUE_OFF);
                }
            })
            .show();
    }

    private void setLocationPreference(String value) {
        mPreferences.edit()
            .putString(CameraSettings.KEY_RECORD_LOCATION, value)
            .apply();
        // TODO: Fix this to use the actual onSharedPreferencesChanged listener
        // instead of invoking manually
        onSharedPreferenceChanged();
    }

    private void initializeRenderOverlay() {
        if (mPieRenderer != null) {
            mRenderOverlay.addRenderer(mPieRenderer);
            mFocusManager.setFocusRenderer(mPieRenderer);
        }
        if (mZoomRenderer != null) {
            mRenderOverlay.addRenderer(mZoomRenderer);
        }
        if (mGestures != null) {
            mGestures.clearTouchReceivers();
            mGestures.setRenderOverlay(mRenderOverlay);
            mGestures.addTouchReceiver(mMenu);
            mGestures.addTouchReceiver(mBlocker);

            if (isImageCaptureIntent()) {
                if (mReviewCancelButton != null) {
                    mGestures.addTouchReceiver((View) mReviewCancelButton);
                }
                if (mReviewDoneButton != null) {
                    mGestures.addTouchReceiver((View) mReviewDoneButton);
                }
            }
        }
        mRenderOverlay.requestLayout();
    }

    private void initializeAfterCameraOpen() {
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mPhotoControl = new PhotoController(mActivity, this, mPieRenderer);
            mPhotoControl.setListener(this);
            mPieRenderer.setPieListener(this);
        }
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
        }
        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer);
        }
        initializeRenderOverlay();
        initializePhotoControl();

        // These depend on camera parameters.
        setPreviewFrameLayoutAspectRatio();
        mFocusManager.setPreviewSize(mPreviewFrameLayout.getWidth(),
                mPreviewFrameLayout.getHeight());
        loadCameraPreferences();
        initializeZoom();
        updateOnScreenIndicators();
        showTapToFocusToastIfNeeded();
        onFullScreenChanged(mActivity.isInCameraApp());
    }

    private void initializePhotoControl() {
        loadCameraPreferences();
        if (mPhotoControl != null) {
            mPhotoControl.initialize(mPreferenceGroup);
        }
        updateSceneModeUI();
    }


    private void resetExposureCompensation() {
        String value = mPreferences.getString(CameraSettings.KEY_EXPOSURE,
                CameraSettings.EXPOSURE_DEFAULT_VALUE);
        if (!CameraSettings.EXPOSURE_DEFAULT_VALUE.equals(value)) {
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_EXPOSURE, "0");
            editor.apply();
        }
    }

    private void keepMediaProviderInstance() {
        // We want to keep a reference to MediaProvider in camera's lifecycle.
        // TODO: Utilize mMediaProviderClient instance to replace
        // ContentResolver calls.
        if (mMediaProviderClient == null) {
            mMediaProviderClient = mContentResolver
                    .acquireContentProviderClient(MediaStore.AUTHORITY);
        }
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized) return;

        // Initialize location service.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, mContentResolver);
        mLocationManager.recordLocation(recordLocation);

        keepMediaProviderInstance();

        // Initialize shutter button.
        mShutterButton = mActivity.getShutterButton();
        mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.setVisibility(View.VISIBLE);

        mMediaSaver = new MediaSaver(mContentResolver);
        mNamedImages = new NamedImages();

        mFirstTimeInitialized = true;
        addIdleHandler();

        mActivity.updateStorageSpaceAndHint();
    }

    private void showTapToFocusToastIfNeeded() {
        // Show the tap to focus toast if this is the first start.
        if (mFocusAreaSupported &&
                mPreferences.getBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, true)) {
            // Delay the toast for one second to wait for orientation.
            mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_FOCUS_TOAST, 1000);
        }
    }

    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                Storage.ensureOSXCompatible();
                return false;
            }
        });
    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {

        // Start location update if needed.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, mContentResolver);
        mLocationManager.recordLocation(recordLocation);

        mMediaSaver = new MediaSaver(mContentResolver);
        mNamedImages = new NamedImages();
        initializeZoom();
        keepMediaProviderInstance();
        hidePostCaptureAlert();

        if (mPhotoControl != null) {
            mPhotoControl.reloadPreferences();
        }
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            // Not useful to change zoom value when the activity is paused.
            if (mPaused) return;
            mZoomValue = index;
            if (mParameters == null || mCameraDevice == null) return;
            // Set zoom parameters asynchronously
            mParameters.setZoom(mZoomValue);
            mCameraDevice.setParametersAsync(mParameters);
            if (mZoomRenderer != null) {
                Parameters p = mCameraDevice.getParameters();
                mZoomRenderer.setZoomValue(mZoomRatios.get(p.getZoom()));
            }
        }

        @Override
        public void onZoomStart() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(false);
            }
        }
    }

    private void initializeZoom() {
        if ((mParameters == null) || !mParameters.isZoomSupported()
                || (mZoomRenderer == null)) return;
        mZoomMax = mParameters.getMaxZoom();
        mZoomRatios = mParameters.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        if (mZoomRenderer != null) {
            mZoomRenderer.setZoomMax(mZoomMax);
            mZoomRenderer.setZoom(mParameters.getZoom());
            mZoomRenderer.setZoomValue(mZoomRatios.get(mParameters.getZoom()));
            mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void startFaceDetection() {
        if (!ApiHelper.HAS_FACE_DETECTION) return;
        if (mFaceDetectionStarted) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = true;
            mFaceView.clear();
            mFaceView.setVisibility(View.VISIBLE);
            mFaceView.setDisplayOrientation(mDisplayOrientation);
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mFaceView.setMirror(info.facing == CameraInfo.CAMERA_FACING_FRONT);
            mFaceView.resume();
            mFocusManager.setFaceView(mFaceView);
            mCameraDevice.setFaceDetectionListener(new FaceDetectionListener() {
                @Override
                public void onFaceDetection(Face[] faces, android.hardware.Camera camera) {
                    mFaceView.setFaces(faces);
                }
            });
            mCameraDevice.startFaceDetection();
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void stopFaceDetection() {
        if (!ApiHelper.HAS_FACE_DETECTION) return;
        if (!mFaceDetectionStarted) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = false;
            mCameraDevice.setFaceDetectionListener(null);
            mCameraDevice.stopFaceDetection();
            if (mFaceView != null) mFaceView.clear();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mCameraState == SWITCHING_CAMERA) return true;
        if (mPopup != null) {
            return mActivity.superDispatchTouchEvent(m);
        } else if (mGestures != null && mRenderOverlay != null) {
            return mGestures.dispatchTouch(m);
        }
        return false;
    }

    private void initOnScreenIndicator() {
        mOnScreenIndicators = mRootView.findViewById(R.id.on_screen_indicators);
        mExposureIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_exposure_indicator);
        mFlashIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_flash_indicator);
        mSceneIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_scenemode_indicator);
        mHdrIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_hdr_indicator);
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) { }

    @Override
    public void hideGpsOnScreenIndicator() { }

    private void updateExposureOnScreenIndicator(int value) {
        if (mExposureIndicator == null) {
            return;
        }
        int id = 0;
        float step = mParameters.getExposureCompensationStep();
        value = (int) Math.round(value * step);
        switch(value) {
        case -3:
            id = R.drawable.ic_indicator_ev_n3;
            break;
        case -2:
            id = R.drawable.ic_indicator_ev_n2;
            break;
        case -1:
            id = R.drawable.ic_indicator_ev_n1;
            break;
        case 0:
            id = R.drawable.ic_indicator_ev_0;
            break;
        case 1:
            id = R.drawable.ic_indicator_ev_p1;
            break;
        case 2:
            id = R.drawable.ic_indicator_ev_p2;
            break;
        case 3:
            id = R.drawable.ic_indicator_ev_p3;
            break;
        }
        mExposureIndicator.setImageResource(id);

    }

    private void updateFlashOnScreenIndicator(String value) {
        if (mFlashIndicator == null) {
            return;
        }
        if (value == null || Parameters.FLASH_MODE_OFF.equals(value)) {
            mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_off);
        } else {
            if (Parameters.FLASH_MODE_AUTO.equals(value)) {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_auto);
            } else if (Parameters.FLASH_MODE_ON.equals(value)) {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_on);
            } else {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_off);
            }
        }
    }

    private void updateSceneOnScreenIndicator(String value) {
        if (mSceneIndicator == null) {
            return;
        }
        if ((value == null) || Parameters.SCENE_MODE_AUTO.equals(value)
                || Parameters.SCENE_MODE_HDR.equals(value)) {
            mSceneIndicator.setImageResource(R.drawable.ic_indicator_sce_off);
        } else {
            mSceneIndicator.setImageResource(R.drawable.ic_indicator_sce_on);
        }
    }

    private void updateHdrOnScreenIndicator(String value) {
        if (mHdrIndicator == null) {
            return;
        }
        if ((value != null) && Parameters.SCENE_MODE_HDR.equals(value)) {
            mHdrIndicator.setImageResource(R.drawable.ic_indicator_hdr_on);
        } else {
            mHdrIndicator.setImageResource(R.drawable.ic_indicator_hdr_off);
        }
    }

    private void updateOnScreenIndicators() {
        if (mParameters == null) return;
        updateSceneOnScreenIndicator(mParameters.getSceneMode());
        updateExposureOnScreenIndicator(CameraSettings.readExposure(mPreferences));
        updateFlashOnScreenIndicator(mParameters.getFlashMode());
        updateHdrOnScreenIndicator(mParameters.getSceneMode());
    }

    private final class ShutterCallback
            implements android.hardware.Camera.ShutterCallback {
        @Override
        public void onShutter() {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.v(TAG, "mShutterLag = " + mShutterLag + "ms");
        }
    }

    private final class PostViewPictureCallback implements PictureCallback {
        @Override
        public void onPictureTaken(
                byte [] data, android.hardware.Camera camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback implements PictureCallback {
        @Override
        public void onPictureTaken(
                byte [] rawData, android.hardware.Camera camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(
                final byte [] jpegData, final android.hardware.Camera camera) {
            if (mPaused) {
                return;
            }
            if (mSceneMode == Util.SCENE_MODE_HDR) {
                mActivity.showSwitcher();
                mActivity.setSwipingEnabled(true);
            }

            mJpegPictureCallbackTime = System.currentTimeMillis();
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");

            // Only animate when in full screen capture mode
            // i.e. If monkey/a user swipes to the gallery during picture taking,
            // don't show animation
            if (ApiHelper.HAS_SURFACE_TEXTURE && !mIsImageCaptureIntent
                    && mActivity.mShowCameraAppView) {
                // Finish capture animation
                ((CameraScreenNail) mActivity.mCameraScreenNail).animateSlide();
            }
            mFocusManager.updateFocusUI(); // Ensure focus indicator is hidden.
            if (!mIsImageCaptureIntent) {
                if (ApiHelper.CAN_START_PREVIEW_IN_JPEG_CALLBACK) {
                    setupPreview();
                } else {
                    // Camera HAL of some devices have a bug. Starting preview
                    // immediately after taking a picture will fail. Wait some
                    // time before starting the preview.
                    mHandler.sendEmptyMessageDelayed(SETUP_PREVIEW, 300);
                }
            }

            if (!mIsImageCaptureIntent) {
                // Calculate the width and the height of the jpeg.
                Size s = mParameters.getPictureSize();
                int orientation = Exif.getOrientation(jpegData);
                int width, height;
                if ((mJpegRotation + orientation) % 180 == 0) {
                    width = s.width;
                    height = s.height;
                } else {
                    width = s.height;
                    height = s.width;
                }
                String title = mNamedImages.getTitle();
                long date = mNamedImages.getDate();
                if (title == null) {
                    Log.e(TAG, "Unbalanced name/data pair");
                } else {
                    if (date == -1) date = mCaptureStartTime;
                    mMediaSaver.addImage(jpegData, title, date, mLocation, width, height,
                            orientation, mOnMediaSavedListener);
                }
            } else {
                mJpegImageData = jpegData;
                if (!mQuickCapture) {
                    showPostCaptureAlert();
                } else {
                    doAttach();
                }
            }

            // Check this in advance of each shot so we don't add to shutter
            // latency. It's true that someone else could write to the SD card in
            // the mean time and fill it, but that could have happened between the
            // shutter press and saving the JPEG too.
            mActivity.updateStorageSpaceAndHint();

            long now = System.currentTimeMillis();
            mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
            Log.v(TAG, "mJpegCallbackFinishTime = "
                    + mJpegCallbackFinishTime + "ms");
            mJpegPictureCallbackTime = 0;
        }
    }

    private final class AutoFocusCallback
            implements android.hardware.Camera.AutoFocusCallback {
        @Override
        public void onAutoFocus(
                boolean focused, android.hardware.Camera camera) {
            if (mPaused) return;

            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            setCameraState(IDLE);
            mFocusManager.onAutoFocus(focused, mShutterButton.isPressed());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private final class AutoFocusMoveCallback
            implements android.hardware.Camera.AutoFocusMoveCallback {
        @Override
        public void onAutoFocusMoving(
            boolean moving, android.hardware.Camera camera) {
                mFocusManager.onAutoFocusMoving(moving);
        }
    }

    private static class NamedImages {
        private ArrayList<NamedEntity> mQueue;
        private boolean mStop;
        private NamedEntity mNamedEntity;

        public NamedImages() {
            mQueue = new ArrayList<NamedEntity>();
        }

        public void nameNewImage(ContentResolver resolver, long date) {
            NamedEntity r = new NamedEntity();
            r.title = Util.createJpegName(date);
            r.date = date;
            mQueue.add(r);
        }

        public String getTitle() {
            if (mQueue.isEmpty()) {
                mNamedEntity = null;
                return null;
            }
            mNamedEntity = mQueue.get(0);
            mQueue.remove(0);

            return mNamedEntity.title;
        }

        // Must be called after getTitle().
        public long getDate() {
            if (mNamedEntity == null) return -1;
            return mNamedEntity.date;
        }

        private static class NamedEntity {
            String title;
            long date;
        }
    }

    private void setCameraState(int state) {
        mCameraState = state;
        switch (state) {
            case PREVIEW_STOPPED:
            case SNAPSHOT_IN_PROGRESS:
            case FOCUSING:
            case SWITCHING_CAMERA:
                if (mGestures != null) mGestures.setEnabled(false);
                break;
            case IDLE:
                if (mGestures != null && mActivity.mShowCameraAppView) {
                    // Enable gestures only when the camera app view is visible
                    mGestures.setEnabled(true);
                }
                break;
        }
    }

    private void animateFlash() {
        // Only animate when in full screen capture mode
        // i.e. If monkey/a user swipes to the gallery during picture taking,
        // don't show animation
        if (ApiHelper.HAS_SURFACE_TEXTURE && !mIsImageCaptureIntent
                && mActivity.mShowCameraAppView) {
            // Start capture animation.
            ((CameraScreenNail) mActivity.mCameraScreenNail).animateFlash(mDisplayRotation);
        }
    }

    @Override
    public boolean capture() {
        // If we are already in the middle of taking a snapshot or the image save request
        // is full then ignore.
        if (mCameraDevice == null || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA || mMediaSaver.queueFull()) {
            return false;
        }
        mCaptureStartTime = System.currentTimeMillis();
        mPostViewPictureCallbackTime = 0;
        mJpegImageData = null;

        final boolean animateBefore = (mSceneMode == Util.SCENE_MODE_HDR);

        if (animateBefore) {
            animateFlash();
        }

        // Set rotation and gps data.
        mJpegRotation = Util.getJpegRotation(mCameraId, mOrientation);
        mParameters.setRotation(mJpegRotation);
        Location loc = mLocationManager.getCurrentLocation();
        Util.setGpsParameters(mParameters, loc);
        mCameraDevice.setParameters(mParameters);

        mCameraDevice.takePicture2(mShutterCallback, mRawPictureCallback,
                mPostViewPictureCallback, new JpegPictureCallback(loc),
                mCameraState, mFocusManager.getFocusState());

        if (!animateBefore) {
            animateFlash();
        }

        mNamedImages.nameNewImage(mContentResolver, mCaptureStartTime);

        mFaceDetectionStarted = false;
        setCameraState(SNAPSHOT_IN_PROGRESS);
        return true;
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    private int getPreferredCameraId(ComboPreferences preferences) {
        int intentCameraId = Util.getCameraFacingIntentExtras(mActivity);
        if (intentCameraId != -1) {
            // Testing purpose. Launch a specific camera through the intent
            // extras.
            return intentCameraId;
        } else {
            return CameraSettings.readPreferredCameraId(preferences);
        }
    }

    private void setShowMenu(boolean show) {
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mMenu != null) {
            mMenu.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onFullScreenChanged(boolean full) {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(!full);
        }
        if (mPopup != null) {
            dismissPopup(false, full);
        }
        if (mGestures != null) {
            mGestures.setEnabled(full);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(full ? View.VISIBLE : View.GONE);
        }
        if (mPieRenderer != null) {
            mPieRenderer.setBlockFocus(!full);
        }
        setShowMenu(full);
        if (mBlocker != null) {
            mBlocker.setVisibility(full ? View.VISIBLE : View.GONE);
        }
        if (!full && mCountDownView != null) mCountDownView.cancelCountDown();
        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            if (mActivity.mCameraScreenNail != null) {
                ((CameraScreenNail) mActivity.mCameraScreenNail).setFullScreen(full);
            }
            return;
        }
        if (full) {
            mPreviewSurfaceView.expand();
        } else {
            mPreviewSurfaceView.shrink();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "surfaceChanged:" + holder + " width=" + width + ". height="
                + height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated: " + holder);
        mCameraSurfaceHolder = holder;
        // Do not access the camera if camera start up thread is not finished.
        if (mCameraDevice == null || mCameraStartUpThread != null) return;

        mCameraDevice.setPreviewDisplayAsync(holder);
        // This happens when onConfigurationChanged arrives, surface has been
        // destroyed, and there is no onFullScreenChanged.
        if (mCameraState == PREVIEW_STOPPED) {
            setupPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed: " + holder);
        mCameraSurfaceHolder = null;
        stopPreview();
    }

    private void updateSceneModeUI() {
        // If scene mode is set, we cannot set flash mode, white balance, and
        // focus mode, instead, we read it from driver
        if (!Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            overrideCameraSettings(mParameters.getFlashMode(),
                    mParameters.getWhiteBalance(), mParameters.getFocusMode());
        } else {
            overrideCameraSettings(null, null, null);
        }
    }

    private void overrideCameraSettings(final String flashMode,
            final String whiteBalance, final String focusMode) {
        if (mPhotoControl != null) {
//            mPieControl.enableFilter(true);
            mPhotoControl.overrideSettings(
                    CameraSettings.KEY_FLASH_MODE, flashMode,
                    CameraSettings.KEY_WHITE_BALANCE, whiteBalance,
                    CameraSettings.KEY_FOCUS_MODE, focusMode);
//            mPieControl.enableFilter(false);
        }
    }

    private void loadCameraPreferences() {
        CameraSettings settings = new CameraSettings(mActivity, mInitialParams,
                mCameraId, CameraHolder.instance().getCameraInfo());
        mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);
    }

    @Override
    public boolean collapseCameraControls() {
        // Remove all the popups/dialog boxes
        boolean ret = false;
        if (mPopup != null) {
            dismissPopup(false);
            ret = true;
        }
        return ret;
    }

    public boolean removeTopLevelPopup() {
        // Remove the top level popup or dialog box and return true if there's any
        if (mPopup != null) {
            dismissPopup(true);
            return true;
        }
        return false;
    }

    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        mOrientation = Util.roundOrientation(orientation, mOrientation);

        // Show the toast after getting the first orientation changed.
        if (mHandler.hasMessages(SHOW_TAP_TO_FOCUS_TOAST)) {
            mHandler.removeMessages(SHOW_TAP_TO_FOCUS_TOAST);
            showTapToFocusToast();
        }
    }

    @Override
    public void onStop() {
        if (mMediaProviderClient != null) {
            mMediaProviderClient.release();
            mMediaProviderClient = null;
        }
    }

    // onClick handler for R.id.btn_done
    @OnClickAttr
    public void onReviewDoneClicked(View v) {
        doAttach();
    }

    // onClick handler for R.id.btn_cancel
    @OnClickAttr
    public void onReviewCancelClicked(View v) {
        doCancel();
    }

    // onClick handler for R.id.btn_retake
    @OnClickAttr
    public void onReviewRetakeClicked(View v) {
        if (mPaused)
            return;

        hidePostCaptureAlert();
        setupPreview();
    }

    private void doAttach() {
        if (mPaused) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value.  If the
            // caller specifies a "save uri" then write the data to its
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    mActivity.setResultEx(Activity.RESULT_OK);
                    mActivity.finish();
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    Util.closeSilently(outputStream);
                }
            } else {
                int orientation = Exif.getOrientation(data);
                Bitmap bitmap = Util.makeBitmap(data, 50 * 1024);
                bitmap = Util.rotate(bitmap, orientation);
                mActivity.setResultEx(Activity.RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                mActivity.finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = mActivity.openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } catch (IOException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } finally {
                Util.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean(CropExtras.KEY_RETURN_DATA, true);
            }
            if (mActivity.isSecureCamera()) {
                newExtras.putBoolean(CropExtras.KEY_SHOW_WHEN_LOCKED, true);
            }

            Intent cropIntent = new Intent(FilterShowActivity.CROP_ACTION);

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            mActivity.startActivityForResult(cropIntent, REQUEST_CROP);
        }
    }

    private void doCancel() {
        mActivity.setResultEx(Activity.RESULT_CANCELED, new Intent());
        mActivity.finish();
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (mPaused || collapseCameraControls()
                || (mCameraState == SNAPSHOT_IN_PROGRESS)
                || (mCameraState == PREVIEW_STOPPED)) return;

        // Do not do focus if there is not enough storage.
        if (pressed && !canTakePicture()) return;

        if (pressed) {
            if (mSceneMode == Util.SCENE_MODE_HDR) {
                mActivity.hideSwitcher();
                mActivity.setSwipingEnabled(false);
            }
            mFocusManager.onShutterDown();
        } else {
            mFocusManager.onShutterUp();
        }
    }

    @Override
    public void onShutterButtonClick() {
        if (mPaused || collapseCameraControls()
                || (mCameraState == SWITCHING_CAMERA)
                || (mCameraState == PREVIEW_STOPPED)) return;

        // Do not take the picture if there is not enough storage.
        if (mActivity.getStorageSpace() <= Storage.LOW_STORAGE_THRESHOLD) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpace());
            return;
        }
        Log.v(TAG, "onShutterButtonClick: mCameraState=" + mCameraState);

        // If the user wants to do a snapshot while the previous one is still
        // in progress, remember the fact and do it after we finish the previous
        // one and re-start the preview. Snapshot in progress also includes the
        // state that autofocus is focusing and a picture will be taken when
        // focus callback arrives.
        if ((mFocusManager.isFocusingSnapOnFinish() || mCameraState == SNAPSHOT_IN_PROGRESS)
                && !mIsImageCaptureIntent) {
            mSnapshotOnIdle = true;
            return;
        }

        String timer = mPreferences.getString(
                CameraSettings.KEY_TIMER,
                mActivity.getString(R.string.pref_camera_timer_default));
        boolean playSound = mPreferences.getString(CameraSettings.KEY_TIMER_SOUND_EFFECTS,
                mActivity.getString(R.string.pref_camera_timer_sound_default))
                .equals(mActivity.getString(R.string.setting_on_value));

        int seconds = Integer.parseInt(timer);
        // When shutter button is pressed, check whether the previous countdown is
        // finished. If not, cancel the previous countdown and start a new one.
        if (mCountDownView.isCountingDown()) {
            mCountDownView.cancelCountDown();
            mCountDownView.startCountDown(seconds, playSound);
        } else if (seconds > 0) {
            mCountDownView.startCountDown(seconds, playSound);
        } else {
           mSnapshotOnIdle = false;
           mFocusManager.doSnap();
        }
    }

    @Override
    public void installIntentFilter() {
    }

    @Override
    public boolean updateStorageHintOnResume() {
        return mFirstTimeInitialized;
    }

    @Override
    public void updateCameraAppView() {
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    @Override
    public void onResumeAfterSuper() {
        if (mOpenCameraFail || mCameraDisabled) return;

        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;

        // Start the preview if it is not started.
        if (mCameraState == PREVIEW_STOPPED && mCameraStartUpThread == null) {
            resetExposureCompensation();
            mCameraStartUpThread = new CameraStartUpThread();
            mCameraStartUpThread.start();
        }

        // If first time initialization is not finished, put it in the
        // message queue.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }
        keepScreenOnAwhile();

        // Dismiss open menu if exists.
        PopupManager.getInstance(mActivity).notifyShowPopup(null);
    }

    void waitCameraStartUpThread() {
        try {
            if (mCameraStartUpThread != null) {
                mCameraStartUpThread.cancel();
                mCameraStartUpThread.join();
                mCameraStartUpThread = null;
                setCameraState(IDLE);
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Override
    public void onPauseBeforeSuper() {
        mPaused = true;
    }

    @Override
    public void onPauseAfterSuper() {
        // Wait the camera start up thread to finish.
        waitCameraStartUpThread();

        // When camera is started from secure lock screen for the first time
        // after screen on, the activity gets onCreate->onResume->onPause->onResume.
        // To reduce the latency, keep the camera for a short time so it does
        // not need to be opened again.
        if (mCameraDevice != null && mActivity.isSecureCamera()
                && ActivityBase.isFirstStartAfterScreenOn()) {
            ActivityBase.resetFirstStartAfterScreenOn();
            CameraHolder.instance().keep(KEEP_CAMERA_TIMEOUT);
        }
        // Reset the focus first. Camera CTS does not guarantee that
        // cancelAutoFocus is allowed after preview stops.
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            mCameraDevice.cancelAutoFocus();
        }
        stopPreview();
        mCountDownView.cancelCountDown();
        // Close the camera now because other activities may need to use it.
        closeCamera();
        if (mSurfaceTexture != null) {
            ((CameraScreenNail) mActivity.mCameraScreenNail).releaseSurfaceTexture();
            mSurfaceTexture = null;
        }
        resetScreenOn();

        // Clear UI.
        collapseCameraControls();
        if (mFaceView != null) mFaceView.clear();

        if (mFirstTimeInitialized) {
            if (mMediaSaver != null) {
                mMediaSaver.finish();
                mMediaSaver = null;
                mNamedImages = null;
            }
        }

        if (mLocationManager != null) mLocationManager.recordLocation(false);

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mJpegImageData = null;

        // Remove the messages in the event queue.
        mHandler.removeMessages(SETUP_PREVIEW);
        mHandler.removeMessages(FIRST_TIME_INIT);
        mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
        mHandler.removeMessages(SWITCH_CAMERA);
        mHandler.removeMessages(SWITCH_CAMERA_START_ANIMATION);
        mHandler.removeMessages(CAMERA_OPEN_DONE);
        mHandler.removeMessages(START_PREVIEW_DONE);
        mHandler.removeMessages(OPEN_CAMERA_FAIL);
        mHandler.removeMessages(CAMERA_DISABLED);

        mPendingSwitchCameraId = -1;
        if (mFocusManager != null) mFocusManager.removeMessages();
    }

    private void initializeControlByIntent() {
        mBlocker = mRootView.findViewById(R.id.blocker);
        mMenu = mRootView.findViewById(R.id.menu);
        mMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPieRenderer != null) {
                    // If autofocus is not finished, cancel autofocus so that the
                    // subsequent touch can be handled by PreviewGestures
                    if (mCameraState == FOCUSING) cancelAutoFocus();
                    mPieRenderer.showInCenter();
                }
            }
        });
        if (mIsImageCaptureIntent) {

            mActivity.hideSwitcher();
            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // RotateImageView.
            mReviewDoneButton = (Rotatable) mRootView.findViewById(R.id.btn_done);
            mReviewCancelButton = (Rotatable) mRootView.findViewById(R.id.btn_cancel);
            mReviewRetakeButton = mRootView.findViewById(R.id.btn_retake);
            ((View) mReviewCancelButton).setVisibility(View.VISIBLE);

            ((View) mReviewDoneButton).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onReviewDoneClicked(v);
                }
            });
            ((View) mReviewCancelButton).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onReviewCancelClicked(v);
                }
            });

            mReviewRetakeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onReviewRetakeClicked(v);
                }
            });

            // Not grayed out upon disabled, to make the follow-up fade-out
            // effect look smooth. Note that the review done button in tablet
            // layout is not a TwoStateImageView.
            if (mReviewDoneButton instanceof TwoStateImageView) {
                ((TwoStateImageView) mReviewDoneButton).enableFilter(false);
            }

            setupCaptureParams();
        }
    }

    /**
     * The focus manager is the first UI related element to get initialized,
     * and it requires the RenderOverlay, so initialize it here
     */
    private void initializeFocusManager() {
        // Create FocusManager object. startPreview needs it.
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        // if mFocusManager not null, reuse it
        // otherwise create a new instance
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        } else {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            boolean mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
            String[] defaultFocusModes = mActivity.getResources().getStringArray(
                    R.array.pref_camera_focusmode_default_array);
            mFocusManager = new FocusOverlayManager(mPreferences, defaultFocusModes,
                    mInitialParams, this, mirror,
                    mActivity.getMainLooper());
        }
    }

    private void initializeMiscControls() {
        // startPreview needs this.
        mPreviewFrameLayout = (PreviewFrameLayout) mRootView.findViewById(R.id.frame);
        // Set touch focus listener.
        mActivity.setSingleTapUpListener(mPreviewFrameLayout);

        mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
        mPreviewFrameLayout.setOnSizeChangedListener(this);
        mPreviewFrameLayout.setOnLayoutChangeListener(mActivity);
        if (!ApiHelper.HAS_SURFACE_TEXTURE) {
            mPreviewSurfaceView =
                    (PreviewSurfaceView) mRootView.findViewById(R.id.preview_surface_view);
            mPreviewSurfaceView.setVisibility(View.VISIBLE);
            mPreviewSurfaceView.getHolder().addCallback(this);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v(TAG, "onConfigurationChanged");
        setDisplayOrientation();

        // Only the views in photo_module_content need to be removed and recreated
        // i.e. CountDownView won't be recreated
        ViewGroup viewGroup = (ViewGroup) mRootView.findViewById(R.id.camera_app);
        viewGroup.removeAllViews();
        LayoutInflater inflater = mActivity.getLayoutInflater();
        inflater.inflate(R.layout.photo_module_content, (ViewGroup) viewGroup);

        // from onCreate()
        initializeControlByIntent();

        initializeFocusManager();
        initializeMiscControls();
        loadCameraPreferences();

        // from initializeFirstTime()
        mShutterButton = mActivity.getShutterButton();
        mShutterButton.setOnShutterButtonListener(this);
        initializeZoom();
        initOnScreenIndicator();
        updateOnScreenIndicators();
        if (mFaceView != null) {
            mFaceView.clear();
            mFaceView.setVisibility(View.VISIBLE);
            mFaceView.setDisplayOrientation(mDisplayOrientation);
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mFaceView.setMirror(info.facing == CameraInfo.CAMERA_FACING_FRONT);
            mFaceView.resume();
            mFocusManager.setFaceView(mFaceView);
        }
        initializeRenderOverlay();
        onFullScreenChanged(mActivity.isInCameraApp());
        if (mJpegImageData != null) {  // Jpeg data found, picture has been taken.
            showPostCaptureAlert();
        }
    }

    @Override
    public void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CROP: {
                Intent intent = new Intent();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                mActivity.setResultEx(resultCode, intent);
                mActivity.finish();

                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();

                break;
            }
        }
    }

    private boolean canTakePicture() {
        return isCameraIdle() && (mActivity.getStorageSpace() > Storage.LOW_STORAGE_THRESHOLD);
    }

    @Override
    public void autoFocus() {
        mFocusStartTime = System.currentTimeMillis();
        mCameraDevice.autoFocus(mAutoFocusCallback);
        setCameraState(FOCUSING);
    }

    @Override
    public void cancelAutoFocus() {
        mCameraDevice.cancelAutoFocus();
        setCameraState(IDLE);
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    // Preview area is touched. Handle touch focus.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || mCameraDevice == null || !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA
                || mCameraState == PREVIEW_STOPPED) {
            return;
        }

        // Do not trigger touch focus if popup window is opened.
        if (removeTopLevelPopup()) return;

        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) return;
        mFocusManager.onSingleTapUp(x, y);
    }

    @Override
    public boolean onBackPressed() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
        // In image capture mode, back button should:
        // 1) if there is any popup, dismiss them, 2) otherwise, get out of image capture
        if (mIsImageCaptureIntent) {
            if (!removeTopLevelPopup()) {
                // no popup to dismiss, cancel image capture
                doCancel();
            }
            return true;
        } else if (!isCameraIdle()) {
            // ignore backs while we're taking a picture
            return true;
        } else {
            return removeTopLevelPopup();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_UP:
        case KeyEvent.KEYCODE_VOLUME_DOWN:
        case KeyEvent.KEYCODE_FOCUS:
            if (mActivity.isInCameraApp() && mFirstTimeInitialized) {
                if (event.getRepeatCount() == 0) {
                    onShutterButtonFocus(true);
                }
                return true;
            }
            return false;
        case KeyEvent.KEYCODE_CAMERA:
            if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                onShutterButtonClick();
            }
            return true;
        case KeyEvent.KEYCODE_DPAD_CENTER:
            // If we get a dpad center event without any focused view, move
            // the focus to the shutter button and press it.
            if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                // Start auto-focus immediately to reduce shutter lag. After
                // the shutter button gets the focus, onShutterButtonFocus()
                // will be called again but it is fine.
                if (removeTopLevelPopup()) return true;
                onShutterButtonFocus(true);
                if (mShutterButton.isInTouchMode()) {
                    mShutterButton.requestFocusFromTouch();
                } else {
                    mShutterButton.requestFocus();
                }
                mShutterButton.setPressed(true);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_UP:
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            if (mActivity.isInCameraApp() && mFirstTimeInitialized) {
                onShutterButtonClick();
                return true;
            }
            return false;
        case KeyEvent.KEYCODE_FOCUS:
            if (mFirstTimeInitialized) {
                onShutterButtonFocus(false);
            }
            return true;
        }
        return false;
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.setZoomChangeListener(null);
            if(ApiHelper.HAS_FACE_DETECTION) {
                mCameraDevice.setFaceDetectionListener(null);
            }
            mCameraDevice.setErrorCallback(null);
            CameraHolder.instance().release();
            mFaceDetectionStarted = false;
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = Util.getDisplayRotation(mActivity);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCameraDisplayOrientation = Util.getDisplayOrientation(0, mCameraId);
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
        if (mFocusManager != null) {
            mFocusManager.setDisplayOrientation(mDisplayOrientation);
        }
        // GLRoot also uses the DisplayRotation, and needs to be told to layout to update
        mActivity.getGLRoot().requestLayoutContentPane();
    }

    // Only called by UI thread.
    private void setupPreview() {
        mFocusManager.resetTouchFocus();
        startPreview();
        setCameraState(IDLE);
        startFaceDetection();
    }

    // This can be called by UI Thread or CameraStartUpThread. So this should
    // not modify the views.
    private void startPreview() {
        mCameraDevice.setErrorCallback(mErrorCallback);

        // ICS camera frameworks has a bug. Face detection state is not cleared
        // after taking a picture. Stop the preview to work around it. The bug
        // was fixed in JB.
        if (mCameraState != PREVIEW_STOPPED) stopPreview();

        setDisplayOrientation();

        if (!mSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus to
            // resume it because it may have been paused by autoFocus call.
            if (Util.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode())) {
                mCameraDevice.cancelAutoFocus();
            }
            mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
        }
        setCameraParameters(UPDATE_PARAM_ALL);

        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            CameraScreenNail screenNail = (CameraScreenNail) mActivity.mCameraScreenNail;
            if (mSurfaceTexture == null) {
                Size size = mParameters.getPreviewSize();
                if (mCameraDisplayOrientation % 180 == 0) {
                    screenNail.setSize(size.width, size.height);
                } else {
                    screenNail.setSize(size.height, size.width);
                }
                screenNail.enableAspectRatioClamping();
                mActivity.notifyScreenNailChanged();
                screenNail.acquireSurfaceTexture();
                CameraStartUpThread t = mCameraStartUpThread;
                if (t != null && t.isCanceled()) {
                    return; // Exiting, so no need to get the surface texture.
                }
                mSurfaceTexture = screenNail.getSurfaceTexture();
            }
            mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
            if (mSurfaceTexture != null) {
                mCameraDevice.setPreviewTextureAsync((SurfaceTexture) mSurfaceTexture);
            }
        } else {
            mCameraDevice.setDisplayOrientation(mDisplayOrientation);
            mCameraDevice.setPreviewDisplayAsync(mCameraSurfaceHolder);
        }

        Log.v(TAG, "startPreview");
        mCameraDevice.startPreviewAsync();

        mFocusManager.onPreviewStarted();

        if (mSnapshotOnIdle) {
            mHandler.post(mDoSnapRunnable);
        }
    }

    private void stopPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
            mFaceDetectionStarted = false;
        }
        setCameraState(PREVIEW_STOPPED);
        if (mFocusManager != null) mFocusManager.onPreviewStopped();
    }

    @SuppressWarnings("deprecation")
    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        List<Integer> frameRates = mParameters.getSupportedPreviewFrameRates();
        if (frameRates != null) {
            Integer max = Collections.max(frameRates);
            mParameters.setPreviewFrameRate(max);
        }

        mParameters.set(Util.RECORDING_HINT, Util.FALSE);

        // Disable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "false");
        }
    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (mParameters.isZoomSupported()) {
            mParameters.setZoom(mZoomValue);
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setAutoExposureLockIfSupported() {
        if (mAeLockSupported) {
            mParameters.setAutoExposureLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setAutoWhiteBalanceLockIfSupported() {
        if (mAwbLockSupported) {
            mParameters.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setFocusAreasIfSupported() {
        if (mFocusAreaSupported) {
            mParameters.setFocusAreas(mFocusManager.getFocusAreas());
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setMeteringAreasIfSupported() {
        if (mMeteringAreaSupported) {
            // Use the same area for focus and metering.
            mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
        }
    }

    private void updateCameraParametersPreference() {
        setAutoExposureLockIfSupported();
        setAutoWhiteBalanceLockIfSupported();
        setFocusAreasIfSupported();
        setMeteringAreasIfSupported();

        // Set picture size.
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);
        if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(mActivity, mParameters);
        } else {
            List<Size> supported = mParameters.getSupportedPictureSizes();
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
        }
        Size size = mParameters.getPictureSize();

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size optimalSize = Util.getOptimalPreviewSize(mActivity, sizes,
                (double) size.width / size.height);
        Size original = mParameters.getPreviewSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

            // Zoom related settings will be changed for different preview
            // sizes, so set and read the parameters to get latest values
            mCameraDevice.setParameters(mParameters);
            mParameters = mCameraDevice.getParameters();
        }
        Log.v(TAG, "Preview size is " + optimalSize.width + "x" + optimalSize.height);

        // Since changing scene mode may change supported values, set scene mode
        // first. HDR is a scene mode. To promote it in UI, it is stored in a
        // separate preference.
        String hdr = mPreferences.getString(CameraSettings.KEY_CAMERA_HDR,
                mActivity.getString(R.string.pref_camera_hdr_default));
        if (mActivity.getString(R.string.setting_on_value).equals(hdr)) {
            mSceneMode = Util.SCENE_MODE_HDR;
        } else {
            mSceneMode = mPreferences.getString(
                CameraSettings.KEY_SCENE_MODE,
                mActivity.getString(R.string.pref_camera_scenemode_default));
        }
        if (Util.isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        } else {
            mSceneMode = mParameters.getSceneMode();
            if (mSceneMode == null) {
                mSceneMode = Parameters.SCENE_MODE_AUTO;
            }
        }

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        // Set exposure compensation
        int value = CameraSettings.readExposure(mPreferences);
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (value >= min && value <= max) {
            mParameters.setExposureCompensation(value);
        } else {
            Log.w(TAG, "invalid exposure range: " + value);
        }

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            // Set flash mode.
            String flashMode = mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    mActivity.getString(R.string.pref_camera_flashmode_default));
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (Util.isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            } else {
                flashMode = mParameters.getFlashMode();
                if (flashMode == null) {
                    flashMode = mActivity.getString(
                            R.string.pref_camera_flashmode_no_flash);
                }
            }

            // Set white balance parameter.
            String whiteBalance = mPreferences.getString(
                    CameraSettings.KEY_WHITE_BALANCE,
                    mActivity.getString(R.string.pref_camera_whitebalance_default));
            if (Util.isSupported(whiteBalance,
                    mParameters.getSupportedWhiteBalance())) {
                mParameters.setWhiteBalance(whiteBalance);
            } else {
                whiteBalance = mParameters.getWhiteBalance();
                if (whiteBalance == null) {
                    whiteBalance = Parameters.WHITE_BALANCE_AUTO;
                }
            }

            // Set focus mode.
            mFocusManager.overrideFocusMode(null);
            mParameters.setFocusMode(mFocusManager.getFocusMode());
        } else {
            mFocusManager.overrideFocusMode(mParameters.getFocusMode());
        }

        if (mContinousFocusSupported && ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK) {
            updateAutoFocusMoveCallback();
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void updateAutoFocusMoveCallback() {
        if (mParameters.getFocusMode().equals(Util.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mCameraDevice.setAutoFocusMoveCallback(
                (AutoFocusMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCameraDevice.setAutoFocusMoveCallback(null);
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
            updateCameraParametersInitialize();
        }

        if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
            updateCameraParametersZoom();
        }

        if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
            updateCameraParametersPreference();
        }

        mCameraDevice.setParameters(mParameters);
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
            updateSceneModeUI();
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    private boolean isCameraIdle() {
        return (mCameraState == IDLE) ||
                (mCameraState == PREVIEW_STOPPED) ||
                ((mFocusManager != null) && mFocusManager.isFocusCompleted()
                        && (mCameraState != SWITCHING_CAMERA));
    }

    private boolean isImageCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || ActivityBase.ACTION_IMAGE_CAPTURE_SECURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = mActivity.getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    private void showPostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            mOnScreenIndicators.setVisibility(View.GONE);
            mMenu.setVisibility(View.GONE);
            Util.fadeIn((View) mReviewDoneButton);
            mShutterButton.setVisibility(View.INVISIBLE);
            Util.fadeIn(mReviewRetakeButton);
        }
    }

    private void hidePostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            mOnScreenIndicators.setVisibility(View.VISIBLE);
            mMenu.setVisibility(View.VISIBLE);
            Util.fadeOut((View) mReviewDoneButton);
            mShutterButton.setVisibility(View.VISIBLE);
            Util.fadeOut(mReviewRetakeButton);
        }
    }

    @Override
    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()"
        if (mPaused) return;

        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, mContentResolver);
        mLocationManager.recordLocation(recordLocation);

        setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
        setPreviewFrameLayoutAspectRatio();
        updateOnScreenIndicators();
    }

    @Override
    public void onCameraPickerClicked(int cameraId) {
        if (mPaused || mPendingSwitchCameraId != -1) return;

        mPendingSwitchCameraId = cameraId;
        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            Log.v(TAG, "Start to copy texture. cameraId=" + cameraId);
            // We need to keep a preview frame for the animation before
            // releasing the camera. This will trigger onPreviewTextureCopied.
            ((CameraScreenNail) mActivity.mCameraScreenNail).copyTexture();
            // Disable all camera controls.
            setCameraState(SWITCHING_CAMERA);
        } else {
            switchCamera();
        }
    }

    private void switchCamera() {
        if (mPaused) return;

        Log.v(TAG, "Start to switch camera. id=" + mPendingSwitchCameraId);
        mCameraId = mPendingSwitchCameraId;
        mPendingSwitchCameraId = -1;
        mPhotoControl.setCameraId(mCameraId);

        // from onPause
        closeCamera();
        collapseCameraControls();
        if (mFaceView != null) mFaceView.clear();
        if (mFocusManager != null) mFocusManager.removeMessages();

        // Restart the camera and initialize the UI. From onCreate.
        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        try {
            mCameraDevice = Util.openCamera(mActivity, mCameraId);
            mParameters = mCameraDevice.getParameters();
        } catch (CameraHardwareException e) {
            Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
            return;
        } catch (CameraDisabledException e) {
            Util.showErrorAndFinish(mActivity, R.string.camera_disabled);
            return;
        }
        initializeCapabilities();
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        boolean mirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
        mFocusManager.setMirror(mirror);
        mFocusManager.setParameters(mInitialParams);
        setupPreview();
        loadCameraPreferences();
        initializePhotoControl();

        // from initializeFirstTime
        initializeZoom();
        updateOnScreenIndicators();
        showTapToFocusToastIfNeeded();

        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            // Start switch camera animation. Post a message because
            // onFrameAvailable from the old camera may already exist.
            mHandler.sendEmptyMessage(SWITCH_CAMERA_START_ANIMATION);
        }
    }

    @Override
    public void onPieOpened(int centerX, int centerY) {
        mActivity.cancelActivityTouchHandling();
        mActivity.setSwipingEnabled(false);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
        }
    }

    @Override
    public void onPieClosed() {
        mActivity.setSwipingEnabled(true);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(false);
        }
    }

    // Preview texture has been copied. Now camera can be released and the
    // animation can be started.
    @Override
    public void onPreviewTextureCopied() {
        mHandler.sendEmptyMessage(SWITCH_CAMERA);
    }

    @Override
    public void onCaptureTextureCopied() {
    }

    @Override
    public void onUserInteraction() {
        if (!mActivity.isFinishing()) keepScreenOnAwhile();
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    // TODO: Delete this function after old camera code is removed
    @Override
    public void onRestorePreferencesClicked() {
    }

    @Override
    public void onOverriddenPreferencesClicked() {
        if (mPaused) return;
        if (mNotSelectableToast == null) {
            String str = mActivity.getResources().getString(R.string.not_selectable_in_scene_mode);
            mNotSelectableToast = Toast.makeText(mActivity, str, Toast.LENGTH_SHORT);
        }
        mNotSelectableToast.show();
    }

    private void showTapToFocusToast() {
        // TODO: Use a toast?
        new RotateTextToast(mActivity, R.string.tap_to_focus, 0).show();
        // Clear the preference.
        Editor editor = mPreferences.edit();
        editor.putBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, false);
        editor.apply();
    }

    private void initializeCapabilities() {
        mInitialParams = mCameraDevice.getParameters();
        mFocusAreaSupported = Util.isFocusAreaSupported(mInitialParams);
        mMeteringAreaSupported = Util.isMeteringAreaSupported(mInitialParams);
        mAeLockSupported = Util.isAutoExposureLockSupported(mInitialParams);
        mAwbLockSupported = Util.isAutoWhiteBalanceLockSupported(mInitialParams);
        mContinousFocusSupported = mInitialParams.getSupportedFocusModes().contains(
                Util.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    // PreviewFrameLayout size has changed.
    @Override
    public void onSizeChanged(int width, int height) {
        if (mFocusManager != null) mFocusManager.setPreviewSize(width, height);
    }

    @Override
    public void onCountDownFinished() {
        mSnapshotOnIdle = false;
        mFocusManager.doSnap();
    }

    void setPreviewFrameLayoutAspectRatio() {
        // Set the preview frame aspect ratio according to the picture size.
        Size size = mParameters.getPictureSize();
        mPreviewFrameLayout.setAspectRatio((double) size.width / size.height);
    }

    @Override
    public boolean needsSwitcher() {
        return !mIsImageCaptureIntent;
    }

    public void showPopup(AbstractSettingPopup popup) {
        mActivity.hideUI();
        mBlocker.setVisibility(View.INVISIBLE);
        setShowMenu(false);
        mPopup = popup;
        mPopup.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        ((FrameLayout) mRootView).addView(mPopup, lp);
    }

    public void dismissPopup(boolean topPopupOnly) {
        dismissPopup(topPopupOnly, true);
    }

    private void dismissPopup(boolean topOnly, boolean fullScreen) {
        if (fullScreen) {
            mActivity.showUI();
            mBlocker.setVisibility(View.VISIBLE);
        }
        setShowMenu(fullScreen);
        if (mPopup != null) {
            ((FrameLayout) mRootView).removeView(mPopup);
            mPopup = null;
        }
        mPhotoControl.popupDismissed(topOnly);
    }

    @Override
    public void onShowSwitcherPopup() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

}
