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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PopupManager;
import com.android.camera.ui.PreviewSurfaceView;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.TwoStateImageView;
import com.android.camera.ui.ZoomRenderer;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.AccessibilityUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class VideoModule implements CameraModule,
    CameraPreference.OnPreferenceChangedListener,
    ShutterButton.OnShutterButtonListener,
    MediaRecorder.OnErrorListener,
    MediaRecorder.OnInfoListener,
    EffectsRecorder.EffectsListener,
    PieRenderer.PieListener {

    private static final String TAG = "CAM_VideoModule";

    // We number the request code from 1000 to avoid collision with Gallery.
    private static final int REQUEST_EFFECT_BACKDROPPER = 1000;

    private static final int CHECK_DISPLAY_ROTATION = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int ENABLE_SHUTTER_BUTTON = 6;
    private static final int SHOW_TAP_TO_SNAPSHOT_TOAST = 7;
    private static final int SWITCH_CAMERA = 8;
    private static final int SWITCH_CAMERA_START_ANIMATION = 9;
    private static final int HIDE_SURFACE_VIEW = 10;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final long SHUTTER_BUTTON_TIMEOUT = 500L; // 500ms

    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped.
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    private static final int MIN_THUMB_SIZE = 64;
    // module fields
    private CameraActivity mActivity;
    private View mRootView;
    private boolean mPaused;
    private int mCameraId;
    private Parameters mParameters;

    private boolean mSnapshotInProgress = false;

    private static final String EFFECT_BG_FROM_GALLERY = "gallery";

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    private ComboPreferences mPreferences;
    private PreferenceGroup mPreferenceGroup;

    private PreviewFrameLayout mPreviewFrameLayout;
    private boolean mSurfaceViewReady;
    private SurfaceHolder.Callback mSurfaceViewCallback;
    private PreviewSurfaceView mPreviewSurfaceView;
    private CameraScreenNail.OnFrameDrawnListener mFrameDrawnListener;
    private View mReviewControl;

    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private Rotatable mReviewCancelButton;
    private Rotatable mReviewDoneButton;
    private RotateImageView mReviewPlayButton;
    private ShutterButton mShutterButton;
    private TextView mRecordingTimeView;
    private RotateLayout mBgLearningMessageRotater;
    private View mBgLearningMessageFrame;
    private LinearLayout mLabelsLinearLayout;

    private boolean mIsVideoCaptureIntent;
    private boolean mQuickCapture;

    private MediaRecorder mMediaRecorder;
    private EffectsRecorder mEffectsRecorder;
    private boolean mEffectsDisplayResult;

    private int mEffectType = EffectsRecorder.EFFECT_NONE;
    private Object mEffectParameter = null;
    private String mEffectUriFromGallery = null;
    private String mPrefVideoEffectDefault;
    private boolean mResetEffect = true;

    private boolean mSwitchingCamera;
    private boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    private boolean mRecordingTimeCountsDown = false;
    private RotateLayout mRecordingTimeRect;
    private long mOnResumeTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private String mVideoFilename;
    private ParcelFileDescriptor mVideoFileDescriptor;

    // The video file that has already been recorded, and that is being
    // examined by the user.
    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;
    private ContentValues mCurrentVideoValues;

    private CamcorderProfile mProfile;

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;

    // Time Lapse parameters.
    private boolean mCaptureTimeLapse = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private int mTimeBetweenTimeLapseFrameCaptureMs = 0;
    private View mTimeLapseLabel;

    private int mDesiredPreviewWidth;
    private int mDesiredPreviewHeight;

    boolean mPreviewing = false; // True if preview is started.
    // The display rotation in degrees. This is only valid when mPreviewing is
    // true.
    private int mDisplayRotation;
    private int mCameraDisplayOrientation;

    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;

    private VideoNamer mVideoNamer;

    private RenderOverlay mRenderOverlay;
    private PieRenderer mPieRenderer;

    private VideoController mVideoControl;
    private AbstractSettingPopup mPopup;
    private int mPendingSwitchCameraId;

    private ZoomRenderer mZoomRenderer;

    private PreviewGestures mGestures;
    private View mMenu;
    private View mBlocker;
    private View mOnScreenIndicators;
    private ImageView mFlashIndicator;

    private final Handler mHandler = new MainHandler();

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private int mZoomValue;  // The current zoom value.
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private boolean mRestoreFlash;  // This is used to check if we need to restore the flash
                                    // status when going back from gallery.

    protected class CameraOpenThread extends Thread {
        @Override
        public void run() {
            openCamera();
        }
    }

    private void openCamera() {
        try {
            mActivity.mCameraDevice = Util.openCamera(mActivity, mCameraId);
            mParameters = mActivity.mCameraDevice.getParameters();
        } catch (CameraHardwareException e) {
            mActivity.mOpenCameraFail = true;
        } catch (CameraDisabledException e) {
            mActivity.mCameraDisabled = true;
        }
    }

    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case ENABLE_SHUTTER_BUTTON:
                    mShutterButton.setEnabled(true);
                    break;

                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case UPDATE_RECORD_TIME: {
                    updateRecordingTime();
                    break;
                }

                case CHECK_DISPLAY_ROTATION: {
                    // Restart the preview if display rotation has changed.
                    // Sometimes this happens when the device is held upside
                    // down and camera app is opened. Rotation animation will
                    // take some time and the rotation value we have got may be
                    // wrong. Framework does not have a callback for this now.
                    if ((Util.getDisplayRotation(mActivity) != mDisplayRotation)
                            && !mMediaRecorderRecording && !mSwitchingCamera) {
                        startPreview();
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
                    }
                    break;
                }

                case SHOW_TAP_TO_SNAPSHOT_TOAST: {
                    showTapToSnapshotToast();
                    break;
                }

                case SWITCH_CAMERA: {
                    switchCamera();
                    break;
                }

                case SWITCH_CAMERA_START_ANIMATION: {
                    ((CameraScreenNail) mActivity.mCameraScreenNail).animateSwitchCamera();

                    // Enable all camera controls.
                    mSwitchingCamera = false;
                    break;
                }

                case HIDE_SURFACE_VIEW: {
                    mPreviewSurfaceView.setVisibility(View.GONE);
                    break;
                }

                default:
                    Log.v(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    private BroadcastReceiver mReceiver = null;

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(mActivity,
                        mActivity.getResources().getString(R.string.wait), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mActivity.getString(R.string.video_file_name_format));

        return dateFormat.format(date);
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

    private void initializeSurfaceView() {
        mPreviewSurfaceView = (PreviewSurfaceView) mRootView.findViewById(R.id.preview_surface_view);
        if (!ApiHelper.HAS_SURFACE_TEXTURE) {  // API level < 11
            if (mSurfaceViewCallback == null) {
                mSurfaceViewCallback = new SurfaceViewCallback();
            }
            mPreviewSurfaceView.getHolder().addCallback(mSurfaceViewCallback);
            mPreviewSurfaceView.setVisibility(View.VISIBLE);
        } else if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {  // API level < 16
            if (mSurfaceViewCallback == null) {
                mSurfaceViewCallback = new SurfaceViewCallback();
                mFrameDrawnListener = new CameraScreenNail.OnFrameDrawnListener() {
                    @Override
                    public void onFrameDrawn(CameraScreenNail c) {
                        mHandler.sendEmptyMessage(HIDE_SURFACE_VIEW);
                    }
                };
            }
            mPreviewSurfaceView.getHolder().addCallback(mSurfaceViewCallback);
        }
    }

    private void initializeOverlay() {
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mVideoControl = new VideoController(mActivity, this, mPieRenderer);
            mVideoControl.setListener(this);
            mPieRenderer.setPieListener(this);
        }
        mRenderOverlay.addRenderer(mPieRenderer);
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
        }
        mRenderOverlay.addRenderer(mZoomRenderer);
        if (mGestures == null) {
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer);
        }
        mGestures.setRenderOverlay(mRenderOverlay);
        mGestures.clearTouchReceivers();
        mGestures.addTouchReceiver(mMenu);
        mGestures.addTouchReceiver(mBlocker);

        if (isVideoCaptureIntent()) {
            if (mReviewCancelButton != null) {
                mGestures.addTouchReceiver((View) mReviewCancelButton);
            }
            if (mReviewDoneButton != null) {
                mGestures.addTouchReceiver((View) mReviewDoneButton);
            }
            if (mReviewPlayButton != null) {
                mGestures.addTouchReceiver((View) mReviewPlayButton);
            }
        }
    }

    @Override
    public void init(CameraActivity activity, View root, boolean reuseScreenNail) {
        mActivity = activity;
        mRootView = root;
        mPreferences = new ComboPreferences(mActivity);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = getPreferredCameraId(mPreferences);

        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mActivity.mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
        mPrefVideoEffectDefault = mActivity.getString(R.string.pref_video_effect_default);
        resetEffect();

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        CameraOpenThread cameraOpenThread = new CameraOpenThread();
        cameraOpenThread.start();

        mContentResolver = mActivity.getContentResolver();

        mActivity.getLayoutInflater().inflate(R.layout.video_module, (ViewGroup) mRootView);

        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsVideoCaptureIntent = isVideoCaptureIntent();
        if (reuseScreenNail) {
            mActivity.reuseCameraScreenNail(!mIsVideoCaptureIntent);
        } else {
            mActivity.createCameraScreenNail(!mIsVideoCaptureIntent);
        }
        initializeSurfaceView();

        // Make sure camera device is opened.
        try {
            cameraOpenThread.join();
            if (mActivity.mOpenCameraFail) {
                Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
                return;
            } else if (mActivity.mCameraDisabled) {
                Util.showErrorAndFinish(mActivity, R.string.camera_disabled);
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

        readVideoPreferences();
        new Thread(new Runnable() {
            @Override
            public void run() {
                startPreview();
            }
        }).start();

        initializeControlByIntent();
        initializeOverlay();
        initializeMiscControls();

        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mLocationManager = new LocationManager(mActivity, null);

        setOrientationIndicator(0, false);
        setDisplayOrientation();

        showTimeLapseUI(mCaptureTimeLapse);
        initializeVideoSnapshot();
        resizeForPreviewAspectRatio();

        initializeVideoControl();
        mPendingSwitchCameraId = -1;
        updateOnScreenIndicators();
    }

    @Override
    public void onStop() {}

    private void loadCameraPreferences() {
        CameraSettings settings = new CameraSettings(mActivity, mParameters,
                mCameraId, CameraHolder.instance().getCameraInfo());
        // Remove the video quality preference setting when the quality is given in the intent.
        mPreferenceGroup = filterPreferenceScreenByIntent(
                settings.getPreferenceGroup(R.xml.video_preferences));
    }

    @Override
    public boolean collapseCameraControls() {
        boolean ret = false;
        if (mPopup != null) {
            dismissPopup(false);
            ret = true;
        }
        return ret;
    }

    public boolean removeTopLevelPopup() {
        if (mPopup != null) {
            dismissPopup(true);
            return true;
        }
        return false;
    }

    private void enableCameraControls(boolean enable) {
        if (mGestures != null) {
            mGestures.setZoomOnly(!enable);
        }
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    private void initializeVideoControl() {
        loadCameraPreferences();
        mVideoControl.initialize(mPreferenceGroup);
        if (effectsActive()) {
            mVideoControl.overrideSettings(
                    CameraSettings.KEY_VIDEO_QUALITY,
                    Integer.toString(getLowVideoQuality()));
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static int getLowVideoQuality() {
        if (ApiHelper.HAS_FINE_RESOLUTION_QUALITY_LEVELS) {
            return CamcorderProfile.QUALITY_480P;
        } else {
            return CamcorderProfile.QUALITY_LOW;
        }
    }


    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        int newOrientation = Util.roundOrientation(orientation, mOrientation);

        if (mOrientation != newOrientation) {
            mOrientation = newOrientation;
            // The input of effects recorder is affected by
            // android.hardware.Camera.setDisplayOrientation. Its value only
            // compensates the camera orientation (no Display.getRotation).
            // So the orientation hint here should only consider sensor
            // orientation.
            if (effectsActive()) {
                mEffectsRecorder.setOrientationHint(mOrientation);
            }
        }

        // Show the toast after getting the first orientation changed.
        if (mHandler.hasMessages(SHOW_TAP_TO_SNAPSHOT_TOAST)) {
            mHandler.removeMessages(SHOW_TAP_TO_SNAPSHOT_TOAST);
            showTapToSnapshotToast();
        }
    }

    private void setOrientationIndicator(int orientation, boolean animation) {
        Rotatable[] indicators = {
                mBgLearningMessageRotater,
                mReviewDoneButton, mReviewPlayButton};
        for (Rotatable indicator : indicators) {
            if (indicator != null) indicator.setOrientation(orientation, animation);
        }
        if (mGestures != null) {
            mGestures.setOrientation(orientation);
        }

        // We change the orientation of the review cancel button only for tablet
        // UI because there's a label along with the X icon. For phone UI, we
        // don't change the orientation because there's only a symmetrical X
        // icon.
        if (mReviewCancelButton instanceof RotateLayout) {
            mReviewCancelButton.setOrientation(orientation, animation);
        }

        // We change the orientation of the linearlayout only for phone UI because when in portrait
        // the width is not enough.
        if (mLabelsLinearLayout != null) {
            if (((orientation / 90) & 1) == 0) {
                mLabelsLinearLayout.setOrientation(LinearLayout.VERTICAL);
            } else {
                mLabelsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
            }
        }
        mRecordingTimeRect.setOrientation(0, animation);
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(mCurrentVideoUri, convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            mActivity.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    @OnClickAttr
    public void onReviewPlayClicked(View v) {
        startPlayVideoActivity();
    }

    @OnClickAttr
    public void onReviewDoneClicked(View v) {
        doReturnToCaller(true);
    }

    @OnClickAttr
    public void onReviewCancelClicked(View v) {
        stopVideoRecording();
        doReturnToCaller(false);
    }

    private void onStopVideoRecording() {
        mEffectsDisplayResult = true;
        boolean recordFail = stopVideoRecording();
        if (mIsVideoCaptureIntent) {
            if (!effectsActive()) {
                if (mQuickCapture) {
                    doReturnToCaller(!recordFail);
                } else if (!recordFail) {
                    showAlert();
                }
            }
        } else if (!recordFail){
            // Start capture animation.
            if (!mPaused && ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                // The capture animation is disabled on ICS because we use SurfaceView
                // for preview during recording. When the recording is done, we switch
                // back to use SurfaceTexture for preview and we need to stop then start
                // the preview. This will cause the preview flicker since the preview
                // will not be continuous for a short period of time.
                ((CameraScreenNail) mActivity.mCameraScreenNail).animateCapture(mDisplayRotation);
            }
        }
    }

    public void onProtectiveCurtainClick(View v) {
        // Consume clicks
    }

    @Override
    public void onShutterButtonClick() {
        if (collapseCameraControls() || mSwitchingCamera) return;

        boolean stop = mMediaRecorderRecording;

        if (stop) {
            onStopVideoRecording();
        } else {
            startVideoRecording();
        }
        mShutterButton.setEnabled(false);

        // Keep the shutter button disabled when in video capture intent
        // mode and recording is stopped. It'll be re-enabled when
        // re-take button is clicked.
        if (!(mIsVideoCaptureIntent && stop)) {
            mHandler.sendEmptyMessageDelayed(
                    ENABLE_SHUTTER_BUTTON, SHUTTER_BUTTON_TIMEOUT);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // Do nothing (everything happens in onShutterButtonClick).
    }

    private void readVideoPreferences() {
        // The preference stores values from ListPreference and is thus string type for all values.
        // We need to convert it to int manually.
        String defaultQuality = CameraSettings.getDefaultVideoQuality(mCameraId,
                mActivity.getResources().getString(R.string.pref_video_quality_default));
        String videoQuality =
                mPreferences.getString(CameraSettings.KEY_VIDEO_QUALITY,
                        defaultQuality);
        int quality = Integer.valueOf(videoQuality);

        // Set video quality.
        Intent intent = mActivity.getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
                    intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            if (extraVideoQuality > 0) {
                quality = CamcorderProfile.QUALITY_HIGH;
            } else {  // 0 is mms.
                quality = CamcorderProfile.QUALITY_LOW;
            }
        }

        // Set video duration limit. The limit is read from the preference,
        // unless it is specified in the intent.
        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            int seconds =
                    intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mMaxVideoDurationInMs = 1000 * seconds;
        } else {
            mMaxVideoDurationInMs = CameraSettings.getMaxVideoDuration(mActivity);
        }

        // Set effect
        mEffectType = CameraSettings.readEffectType(mPreferences);
        if (mEffectType != EffectsRecorder.EFFECT_NONE) {
            mEffectParameter = CameraSettings.readEffectParameter(mPreferences);
            // Set quality to be no higher than 480p.
            CamcorderProfile profile = CamcorderProfile.get(mCameraId, quality);
            if (profile.videoFrameHeight > 480) {
                quality = getLowVideoQuality();
            }
        } else {
            mEffectParameter = null;
        }
        // Read time lapse recording interval.
        if (ApiHelper.HAS_TIME_LAPSE_RECORDING) {
            String frameIntervalStr = mPreferences.getString(
                    CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                    mActivity.getString(R.string.pref_video_time_lapse_frame_interval_default));
            mTimeBetweenTimeLapseFrameCaptureMs = Integer.parseInt(frameIntervalStr);
            mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);
        }
        // TODO: This should be checked instead directly +1000.
        if (mCaptureTimeLapse) quality += 1000;
        mProfile = CamcorderProfile.get(mCameraId, quality);
        getDesiredPreviewSize();
    }

    private void writeDefaultEffectToPrefs()  {
        ComboPreferences.Editor editor = mPreferences.edit();
        editor.putString(CameraSettings.KEY_VIDEO_EFFECT,
                mActivity.getString(R.string.pref_video_effect_default));
        editor.apply();
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private void getDesiredPreviewSize() {
        mParameters = mActivity.mCameraDevice.getParameters();
        if (ApiHelper.HAS_GET_SUPPORTED_VIDEO_SIZE) {
            if (mParameters.getSupportedVideoSizes() == null || effectsActive()) {
                mDesiredPreviewWidth = mProfile.videoFrameWidth;
                mDesiredPreviewHeight = mProfile.videoFrameHeight;
            } else {  // Driver supports separates outputs for preview and video.
                List<Size> sizes = mParameters.getSupportedPreviewSizes();
                Size preferred = mParameters.getPreferredPreviewSizeForVideo();
                int product = preferred.width * preferred.height;
                Iterator<Size> it = sizes.iterator();
                // Remove the preview sizes that are not preferred.
                while (it.hasNext()) {
                    Size size = it.next();
                    if (size.width * size.height > product) {
                        it.remove();
                    }
                }
                Size optimalSize = Util.getOptimalPreviewSize(mActivity, sizes,
                        (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
                mDesiredPreviewWidth = optimalSize.width;
                mDesiredPreviewHeight = optimalSize.height;
            }
        } else {
            mDesiredPreviewWidth = mProfile.videoFrameWidth;
            mDesiredPreviewHeight = mProfile.videoFrameHeight;
        }
        Log.v(TAG, "mDesiredPreviewWidth=" + mDesiredPreviewWidth +
                ". mDesiredPreviewHeight=" + mDesiredPreviewHeight);
    }

    private void resizeForPreviewAspectRatio() {
        mPreviewFrameLayout.setAspectRatio(
                (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
    }

    @Override
    public void installIntentFilter() {
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        mActivity.registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
    }

    @Override
    public void onResumeAfterSuper() {
        if (mActivity.mOpenCameraFail || mActivity.mCameraDisabled)
            return;

        mZoomValue = 0;

        showVideoSnapshotUI(false);


        if (!mPreviewing) {
            if (resetEffect()) {
                mBgLearningMessageFrame.setVisibility(View.GONE);
            }
            openCamera();
            if (mActivity.mOpenCameraFail) {
                Util.showErrorAndFinish(mActivity,
                        R.string.cannot_connect_camera);
                return;
            } else if (mActivity.mCameraDisabled) {
                Util.showErrorAndFinish(mActivity, R.string.camera_disabled);
                return;
            }
            readVideoPreferences();
            resizeForPreviewAspectRatio();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    startPreview();
                }
            }).start();
        }

        // Initializing it here after the preview is started.
        initializeZoom();

        keepScreenOnAwhile();

        // Initialize location service.
        boolean recordLocation = RecordLocationPreference.get(mPreferences,
                mContentResolver);
        mLocationManager.recordLocation(recordLocation);

        if (mPreviewing) {
            mOnResumeTime = SystemClock.uptimeMillis();
            mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
        }
        // Dismiss open menu if exists.
        PopupManager.getInstance(mActivity).notifyShowPopup(null);

        mVideoNamer = new VideoNamer();
    }

    private void setDisplayOrientation() {
        mDisplayRotation = Util.getDisplayRotation(mActivity);
        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            // The display rotation is handled by gallery.
            mCameraDisplayOrientation = Util.getDisplayOrientation(0, mCameraId);
        } else {
            // We need to consider display rotation ourselves.
            mCameraDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, mCameraId);
        }
        // GLRoot also uses the DisplayRotation, and needs to be told to layout to update
        mActivity.getGLRoot().requestLayoutContentPane();
    }

    private void startPreview() {
        Log.v(TAG, "startPreview");

        mActivity.mCameraDevice.setErrorCallback(mErrorCallback);
        if (mPreviewing == true) {
            stopPreview();
            if (effectsActive() && mEffectsRecorder != null) {
                mEffectsRecorder.release();
                mEffectsRecorder = null;
            }
        }

        mPreviewing = true;

        setDisplayOrientation();
        mActivity.mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        setCameraParameters();

        try {
            if (!effectsActive()) {
                if (ApiHelper.HAS_SURFACE_TEXTURE) {
                    SurfaceTexture surfaceTexture = ((CameraScreenNail) mActivity.mCameraScreenNail)
                            .getSurfaceTexture();
                    if (surfaceTexture == null) {
                        return; // The texture has been destroyed (pause, etc)
                    }
                    mActivity.mCameraDevice.setPreviewTextureAsync(surfaceTexture);
                } else {
                    mActivity.mCameraDevice.setPreviewDisplayAsync(mPreviewSurfaceView.getHolder());
                }
                mActivity.mCameraDevice.startPreviewAsync();
            } else {
                initializeEffectsPreview();
                mEffectsRecorder.startPreview();
            }
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        } finally {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mActivity.mOpenCameraFail) {
                        Util.showErrorAndFinish(mActivity, R.string.cannot_connect_camera);
                    } else if (mActivity.mCameraDisabled) {
                        Util.showErrorAndFinish(mActivity, R.string.camera_disabled);
                    }
                }
            });
        }
    }

    private void stopPreview() {
        mActivity.mCameraDevice.stopPreview();
        mPreviewing = false;
    }

    // Closing the effects out. Will shut down the effects graph.
    private void closeEffects() {
        Log.v(TAG, "Closing effects");
        mEffectType = EffectsRecorder.EFFECT_NONE;
        if (mEffectsRecorder == null) {
            Log.d(TAG, "Effects are already closed. Nothing to do");
            return;
        }
        // This call can handle the case where the camera is already released
        // after the recording has been stopped.
        mEffectsRecorder.release();
        mEffectsRecorder = null;
    }

    // By default, we want to close the effects as well with the camera.
    private void closeCamera() {
        closeCamera(true);
    }

    // In certain cases, when the effects are active, we may want to shutdown
    // only the camera related parts, and handle closing the effects in the
    // effectsUpdate callback.
    // For example, in onPause, we want to make the camera available to
    // outside world immediately, however, want to wait till the effects
    // callback to shut down the effects. In such a case, we just disconnect
    // the effects from the camera by calling disconnectCamera. That way
    // the effects can handle that when shutting down.
    //
    // @param closeEffectsAlso - indicates whether we want to close the
    // effects also along with the camera.
    private void closeCamera(boolean closeEffectsAlso) {
        Log.v(TAG, "closeCamera");
        if (mActivity.mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }

        if (mEffectsRecorder != null) {
            // Disconnect the camera from effects so that camera is ready to
            // be released to the outside world.
            mEffectsRecorder.disconnectCamera();
        }
        if (closeEffectsAlso) closeEffects();
        mActivity.mCameraDevice.setZoomChangeListener(null);
        mActivity.mCameraDevice.setErrorCallback(null);
        CameraHolder.instance().release();
        mActivity.mCameraDevice = null;
        mPreviewing = false;
        mSnapshotInProgress = false;
    }

    private void releasePreviewResources() {
        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            CameraScreenNail screenNail = (CameraScreenNail) mActivity.mCameraScreenNail;
            screenNail.releaseSurfaceTexture();
            if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                mHandler.removeMessages(HIDE_SURFACE_VIEW);
                mPreviewSurfaceView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPauseBeforeSuper() {
        mPaused = true;

        if (mMediaRecorderRecording) {
            // Camera will be released in onStopVideoRecording.
            onStopVideoRecording();
        } else {
            closeCamera();
            if (!effectsActive()) releaseMediaRecorder();
        }
        if (effectsActive()) {
            // If the effects are active, make sure we tell the graph that the
            // surfacetexture is not valid anymore. Disconnect the graph from
            // the display. This should be done before releasing the surface
            // texture.
            mEffectsRecorder.disconnectDisplay();
        } else {
            // Close the file descriptor and clear the video namer only if the
            // effects are not active. If effects are active, we need to wait
            // till we get the callback from the Effects that the graph is done
            // recording. That also needs a change in the stopVideoRecording()
            // call to not call closeCamera if the effects are active, because
            // that will close down the effects are well, thus making this if
            // condition invalid.
            closeVideoFileDescriptor();
            clearVideoNamer();
        }

        releasePreviewResources();

        if (mReceiver != null) {
            mActivity.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        resetScreenOn();

        if (mLocationManager != null) mLocationManager.recordLocation(false);

        mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
        mHandler.removeMessages(SWITCH_CAMERA);
        mHandler.removeMessages(SWITCH_CAMERA_START_ANIMATION);
        mPendingSwitchCameraId = -1;
        mSwitchingCamera = false;
        // Call onPause after stopping video recording. So the camera can be
        // released as soon as possible.
    }

    @Override
    public void onPauseAfterSuper() {
    }

    @Override
    public void onUserInteraction() {
        if (!mMediaRecorderRecording && !mActivity.isFinishing()) {
            keepScreenOnAwhile();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mPaused) return true;
        if (mMediaRecorderRecording) {
            onStopVideoRecording();
            return true;
        } else if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        } else {
            return removeTopLevelPopup();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is paused.
        if (mPaused) {
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                if (event.getRepeatCount() == 0) {
                    mShutterButton.performClick();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    mShutterButton.performClick();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MENU:
                if (mMediaRecorderRecording) return true;
                break;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                mShutterButton.setPressed(false);
                return true;
        }
        return false;
    }

    private boolean isVideoCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void doReturnToCaller(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = Activity.RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }
        mActivity.setResultEx(resultCode, resultIntent);
        mActivity.finish();
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    private void setupMediaRecorderPreviewDisplay() {
        // Nothing to do here if using SurfaceTexture.
        if (!ApiHelper.HAS_SURFACE_TEXTURE) {
            mMediaRecorder.setPreviewDisplay(mPreviewSurfaceView.getHolder().getSurface());
        } else if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
            // We stop the preview here before unlocking the device because we
            // need to change the SurfaceTexture to SurfaceView for preview.
            stopPreview();
            mActivity.mCameraDevice.setPreviewDisplayAsync(mPreviewSurfaceView.getHolder());
            // The orientation for SurfaceTexture is different from that for
            // SurfaceView. For SurfaceTexture we don't need to consider the
            // display rotation. Just consider the sensor's orientation and we
            // will set the orientation correctly when showing the texture.
            // Gallery will handle the orientation for the preview. For
            // SurfaceView we will have to take everything into account so the
            // display rotation is considered.
            mActivity.mCameraDevice.setDisplayOrientation(
                    Util.getDisplayOrientation(mDisplayRotation, mCameraId));
            mActivity.mCameraDevice.startPreviewAsync();
            mPreviewing = true;
            mMediaRecorder.setPreviewDisplay(mPreviewSurfaceView.getHolder().getSurface());
        }
    }

    // Prepares media recorder.
    private void initializeRecorder() {
        Log.v(TAG, "initializeRecorder");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mActivity.mCameraDevice == null) return;

        if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING && ApiHelper.HAS_SURFACE_TEXTURE) {
            // Set the SurfaceView to visible so the surface gets created.
            // surfaceCreated() is called immediately when the visibility is
            // changed to visible. Thus, mSurfaceViewReady should become true
            // right after calling setVisibility().
            mPreviewSurfaceView.setVisibility(View.VISIBLE);
            if (!mSurfaceViewReady) return;
        }

        Intent intent = mActivity.getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
        closeVideoFileDescriptor();
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        mMediaRecorder = new MediaRecorder();

        setupMediaRecorderPreviewDisplay();
        // Unlock the camera object before passing it to media recorder.
        mActivity.mCameraDevice.unlock();
        mMediaRecorder.setCamera(mActivity.mCameraDevice.getCamera());
        if (!mCaptureTimeLapse) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(mProfile);
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
        if (mCaptureTimeLapse) {
            double fps = 1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs;
            setCaptureRate(mMediaRecorder, fps);
        }

        setRecordLocation();

        // Set output file.
        // Try Uri in the intent first. If it doesn't exist, use our own
        // instead.
        if (mVideoFileDescriptor != null) {
            mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat);
            mMediaRecorder.setOutputFile(mVideoFilename);
        }

        // Set maximum file size.
        long maxFileSize = mActivity.getStorageSpace() - Storage.LOW_STORAGE_THRESHOLD;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
        }

        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        // Note that mOrientation here is the device orientation, which is the opposite of
        // what activity.getWindowManager().getDefaultDisplay().getRotation() would return,
        // which is the orientation the graphics need to rotate in order to render correctly.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - mOrientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + mOrientation) % 360;
            }
        }
        mMediaRecorder.setOrientationHint(rotation);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mVideoFilename, e);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }

        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static void setCaptureRate(MediaRecorder recorder, double fps) {
        recorder.setCaptureRate(fps);
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setRecordLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Location loc = mLocationManager.getCurrentLocation();
            if (loc != null) {
                mMediaRecorder.setLocation((float) loc.getLatitude(),
                        (float) loc.getLongitude());
            }
        }
    }

    private void initializeEffectsPreview() {
        Log.v(TAG, "initializeEffectsPreview");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mActivity.mCameraDevice == null) return;

        boolean inLandscape = (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE);

        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];

        mEffectsDisplayResult = false;
        mEffectsRecorder = new EffectsRecorder(mActivity);

        // TODO: Confirm none of the following need to go to initializeEffectsRecording()
        // and none of these change even when the preview is not refreshed.
        mEffectsRecorder.setCameraDisplayOrientation(mCameraDisplayOrientation);
        mEffectsRecorder.setCamera(mActivity.mCameraDevice);
        mEffectsRecorder.setCameraFacing(info.facing);
        mEffectsRecorder.setProfile(mProfile);
        mEffectsRecorder.setEffectsListener(this);
        mEffectsRecorder.setOnInfoListener(this);
        mEffectsRecorder.setOnErrorListener(this);

        // The input of effects recorder is affected by
        // android.hardware.Camera.setDisplayOrientation. Its value only
        // compensates the camera orientation (no Display.getRotation). So the
        // orientation hint here should only consider sensor orientation.
        int orientation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            orientation = mOrientation;
        }
        mEffectsRecorder.setOrientationHint(orientation);

        CameraScreenNail screenNail = (CameraScreenNail) mActivity.mCameraScreenNail;
        mEffectsRecorder.setPreviewSurfaceTexture(screenNail.getSurfaceTexture(),
                screenNail.getWidth(), screenNail.getHeight());

        if (mEffectType == EffectsRecorder.EFFECT_BACKDROPPER &&
                ((String) mEffectParameter).equals(EFFECT_BG_FROM_GALLERY)) {
            mEffectsRecorder.setEffect(mEffectType, mEffectUriFromGallery);
        } else {
            mEffectsRecorder.setEffect(mEffectType, mEffectParameter);
        }
    }

    private void initializeEffectsRecording() {
        Log.v(TAG, "initializeEffectsRecording");

        Intent intent = mActivity.getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
        closeVideoFileDescriptor();
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }

        mEffectsRecorder.setProfile(mProfile);
        // important to set the capture rate to zero if not timelapsed, since the
        // effectsrecorder object does not get created again for each recording
        // session
        if (mCaptureTimeLapse) {
            mEffectsRecorder.setCaptureRate((1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs));
        } else {
            mEffectsRecorder.setCaptureRate(0);
        }

        // Set output file
        if (mVideoFileDescriptor != null) {
            mEffectsRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat);
            mEffectsRecorder.setOutputFile(mVideoFilename);
        }

        // Set maximum file size.
        long maxFileSize = mActivity.getStorageSpace() - Storage.LOW_STORAGE_THRESHOLD;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }
        mEffectsRecorder.setMaxFileSize(maxFileSize);
        mEffectsRecorder.setMaxDuration(mMaxVideoDurationInMs);
    }


    private void releaseMediaRecorder() {
        Log.v(TAG, "Releasing media recorder.");
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        mVideoFilename = null;
    }

    private void releaseEffectsRecorder() {
        Log.v(TAG, "Releasing effects recorder.");
        if (mEffectsRecorder != null) {
            cleanupEmptyFile();
            mEffectsRecorder.release();
            mEffectsRecorder = null;
        }
        mEffectType = EffectsRecorder.EFFECT_NONE;
        mVideoFilename = null;
    }

    private void generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        // Used when emailing.
        String filename = title + convertOutputFormatToFileExt(outputFileFormat);
        String mime = convertOutputFormatToMimeType(outputFileFormat);
        String path = Storage.DIRECTORY + '/' + filename;
        String tmpPath = path + ".tmp";
        mCurrentVideoValues = new ContentValues(7);
        mCurrentVideoValues.put(Video.Media.TITLE, title);
        mCurrentVideoValues.put(Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(Video.Media.DATA, path);
        mCurrentVideoValues.put(Video.Media.RESOLUTION,
                Integer.toString(mProfile.videoFrameWidth) + "x" +
                Integer.toString(mProfile.videoFrameHeight));
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mCurrentVideoValues.put(Video.Media.LATITUDE, loc.getLatitude());
            mCurrentVideoValues.put(Video.Media.LONGITUDE, loc.getLongitude());
        }
        mVideoNamer.prepareUri(mContentResolver, mCurrentVideoValues);
        mVideoFilename = tmpPath;
        Log.v(TAG, "New video filename: " + mVideoFilename);
    }

    private boolean addVideoToMediaStore() {
        boolean fail = false;
        if (mVideoFileDescriptor == null) {
            mCurrentVideoValues.put(Video.Media.SIZE,
                    new File(mCurrentVideoFilename).length());
            long duration = SystemClock.uptimeMillis() - mRecordingStartTime;
            if (duration > 0) {
                if (mCaptureTimeLapse) {
                    duration = getTimeLapseVideoLength(duration);
                }
                mCurrentVideoValues.put(Video.Media.DURATION, duration);
            } else {
                Log.w(TAG, "Video duration <= 0 : " + duration);
            }
            try {
                mCurrentVideoUri = mVideoNamer.getUri();
                mActivity.addSecureAlbumItemIfNeeded(true, mCurrentVideoUri);

                // Rename the video file to the final name. This avoids other
                // apps reading incomplete data.  We need to do it after the
                // above mVideoNamer.getUri() call, so we are certain that the
                // previous insert to MediaProvider is completed.
                String finalName = mCurrentVideoValues.getAsString(
                        Video.Media.DATA);
                if (new File(mCurrentVideoFilename).renameTo(new File(finalName))) {
                    mCurrentVideoFilename = finalName;
                }

                mContentResolver.update(mCurrentVideoUri, mCurrentVideoValues
                        , null, null);
                mActivity.sendBroadcast(new Intent(Util.ACTION_NEW_VIDEO,
                        mCurrentVideoUri));
            } catch (Exception e) {
                // We failed to insert into the database. This can happen if
                // the SD card is unmounted.
                Log.e(TAG, "failed to add video to media store", e);
                mCurrentVideoUri = null;
                mCurrentVideoFilename = null;
                fail = true;
            } finally {
                Log.v(TAG, "Current video URI: " + mCurrentVideoUri);
            }
        }
        mCurrentVideoValues = null;
        return fail;
    }

    private void deleteCurrentVideo() {
        // Remove the video and the uri if the uri is not passed in by intent.
        if (mCurrentVideoFilename != null) {
            deleteVideoFile(mCurrentVideoFilename);
            mCurrentVideoFilename = null;
            if (mCurrentVideoUri != null) {
                mContentResolver.delete(mCurrentVideoUri, null, null);
                mCurrentVideoUri = null;
            }
        }
        mActivity.updateStorageSpaceAndHint();
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private PreferenceGroup filterPreferenceScreenByIntent(
            PreferenceGroup screen) {
        Intent intent = mActivity.getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_QUALITY);
        }

        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_QUALITY);
        }
        return screen;
    }

    // from MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecording();
            mActivity.updateStorageSpaceAndHint();
        }
    }

    // from MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording();
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording();

            // Show the toast.
            Toast.makeText(mActivity, R.string.video_reach_size_limit,
                    Toast.LENGTH_LONG).show();
        }
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void pauseAudioPlayback() {
        // Shamelessly copied from MediaPlaybackService.java, which
        // should be public, but isn't.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");

        mActivity.sendBroadcast(i);
    }

    // For testing.
    public boolean isRecording() {
        return mMediaRecorderRecording;
    }

    private void startVideoRecording() {
        Log.v(TAG, "startVideoRecording");
        mActivity.setSwipingEnabled(false);

        mActivity.updateStorageSpaceAndHint();
        if (mActivity.getStorageSpace() <= Storage.LOW_STORAGE_THRESHOLD) {
            Log.v(TAG, "Storage issue, ignore the start request");
            return;
        }

        mCurrentVideoUri = null;
        if (effectsActive()) {
            initializeEffectsRecording();
            if (mEffectsRecorder == null) {
                Log.e(TAG, "Fail to initialize effect recorder");
                return;
            }
        } else {
            initializeRecorder();
            if (mMediaRecorder == null) {
                Log.e(TAG, "Fail to initialize media recorder");
                return;
            }
        }

        pauseAudioPlayback();

        if (effectsActive()) {
            try {
                mEffectsRecorder.startRecording();
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start effects recorder. ", e);
                releaseEffectsRecorder();
                return;
            }
        } else {
            try {
                mMediaRecorder.start(); // Recording is now started
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start media recorder. ", e);
                releaseMediaRecorder();
                // If start fails, frameworks will not lock the camera for us.
                mActivity.mCameraDevice.lock();
                return;
            }
        }

        // Make sure the video recording has started before announcing
        // this in accessibility.
        AccessibilityUtils.makeAnnouncement(mShutterButton,
                mActivity.getString(R.string.video_recording_started));

        // The parameters may have been changed by MediaRecorder upon starting
        // recording. We need to alter the parameters if we support camcorder
        // zoom. To reduce latency when setting the parameters during zoom, we
        // update mParameters here once.
        if (ApiHelper.HAS_ZOOM_WHEN_RECORDING) {
            mParameters = mActivity.mCameraDevice.getParameters();
        }

        enableCameraControls(false);

        mMediaRecorderRecording = true;
        mActivity.getOrientationManager().lockOrientation();
        mRecordingStartTime = SystemClock.uptimeMillis();
        showRecordingUI(true);

        updateRecordingTime();
        keepScreenOn();
    }

    private void showRecordingUI(boolean recording) {
        mMenu.setVisibility(recording ? View.GONE : View.VISIBLE);
        mOnScreenIndicators.setVisibility(recording ? View.GONE : View.VISIBLE);
        if (recording) {
            mShutterButton.setImageResource(R.drawable.btn_shutter_video_recording);
            mActivity.hideSwitcher();
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            if (mReviewControl != null) mReviewControl.setVisibility(View.GONE);
            // The camera is not allowed to be accessed in older api levels during
            // recording. It is therefore necessary to hide the zoom UI on older
            // platforms.
            // See the documentation of android.media.MediaRecorder.start() for
            // further explanation.
            if (!ApiHelper.HAS_ZOOM_WHEN_RECORDING
                    && mParameters.isZoomSupported()) {
                // TODO: disable zoom UI here.
            }
        } else {
            mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
            mActivity.showSwitcher();
            mRecordingTimeView.setVisibility(View.GONE);
            if (mReviewControl != null) mReviewControl.setVisibility(View.VISIBLE);
            if (!ApiHelper.HAS_ZOOM_WHEN_RECORDING
                    && mParameters.isZoomSupported()) {
                // TODO: enable zoom UI here.
            }
        }
    }

    private void showAlert() {
        Bitmap bitmap = null;
        if (mVideoFileDescriptor != null) {
            bitmap = Thumbnail.createVideoThumbnailBitmap(mVideoFileDescriptor.getFileDescriptor(),
                    mPreviewFrameLayout.getWidth());
        } else if (mCurrentVideoFilename != null) {
            bitmap = Thumbnail.createVideoThumbnailBitmap(mCurrentVideoFilename,
                    mPreviewFrameLayout.getWidth());
        }
        if (bitmap != null) {
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it to match the UI orientation (and mirror if it is front-facing camera).
            CameraInfo[] info = CameraHolder.instance().getCameraInfo();
            boolean mirror = (info[mCameraId].facing == CameraInfo.CAMERA_FACING_FRONT);
            bitmap = Util.rotateAndMirror(bitmap, 0, mirror);
            mReviewImage.setImageBitmap(bitmap);
            mReviewImage.setVisibility(View.VISIBLE);
        }

        Util.fadeOut(mShutterButton);

        Util.fadeIn((View) mReviewDoneButton);
        Util.fadeIn(mReviewPlayButton);
        mMenu.setVisibility(View.GONE);
        mOnScreenIndicators.setVisibility(View.GONE);
        enableCameraControls(false);

        showTimeLapseUI(false);
    }

    private void hideAlert() {
        mReviewImage.setVisibility(View.GONE);
        mShutterButton.setEnabled(true);
        mMenu.setVisibility(View.VISIBLE);
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        enableCameraControls(true);

        Util.fadeOut((View) mReviewDoneButton);
        Util.fadeOut(mReviewPlayButton);

        Util.fadeIn(mShutterButton);

        if (mCaptureTimeLapse) {
            showTimeLapseUI(true);
        }
    }

    private boolean stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        mActivity.setSwipingEnabled(true);
        mActivity.showSwitcher();

        boolean fail = false;
        if (mMediaRecorderRecording) {
            boolean shouldAddToMediaStoreNow = false;

            try {
                if (effectsActive()) {
                    // This is asynchronous, so we can't add to media store now because thumbnail
                    // may not be ready. In such case addVideoToMediaStore is called later
                    // through a callback from the MediaEncoderFilter to EffectsRecorder,
                    // and then to the VideoModule.
                    mEffectsRecorder.stopRecording();
                } else {
                    mMediaRecorder.setOnErrorListener(null);
                    mMediaRecorder.setOnInfoListener(null);
                    mMediaRecorder.stop();
                    shouldAddToMediaStoreNow = true;
                }
                mCurrentVideoFilename = mVideoFilename;
                Log.v(TAG, "stopVideoRecording: Setting current video filename: "
                        + mCurrentVideoFilename);
                AccessibilityUtils.makeAnnouncement(mShutterButton,
                        mActivity.getString(R.string.video_recording_stopped));
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail",  e);
                if (mVideoFilename != null) deleteVideoFile(mVideoFilename);
                fail = true;
            }
            mMediaRecorderRecording = false;
            mActivity.getOrientationManager().unlockOrientation();

            // If the activity is paused, this means activity is interrupted
            // during recording. Release the camera as soon as possible because
            // face unlock or other applications may need to use the camera.
            // However, if the effects are active, then we can only release the
            // camera and cannot release the effects recorder since that will
            // stop the graph. It is possible to separate out the Camera release
            // part and the effects release part. However, the effects recorder
            // does hold on to the camera, hence, it needs to be "disconnected"
            // from the camera in the closeCamera call.
            if (mPaused) {
                // Closing only the camera part if effects active. Effects will
                // be closed in the callback from effects.
                boolean closeEffects = !effectsActive();
                closeCamera(closeEffects);
            }

            showRecordingUI(false);
            if (!mIsVideoCaptureIntent) {
                enableCameraControls(true);
            }
            // The orientation was fixed during video recording. Now make it
            // reflect the device orientation as video recording is stopped.
            setOrientationIndicator(0, true);
            keepScreenOnAwhile();
            if (shouldAddToMediaStoreNow) {
                if (addVideoToMediaStore()) fail = true;
            }
        }
        // always release media recorder if no effects running
        if (!effectsActive()) {
            releaseMediaRecorder();
            if (!mPaused) {
                mActivity.mCameraDevice.lock();
                if (ApiHelper.HAS_SURFACE_TEXTURE &&
                    !ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                    stopPreview();
                    // Switch back to use SurfaceTexture for preview.
                    ((CameraScreenNail) mActivity.mCameraScreenNail).setOneTimeOnFrameDrawnListener(
                            mFrameDrawnListener);
                    startPreview();
                }
            }
        }
        // Update the parameters here because the parameters might have been altered
        // by MediaRecorder.
        if (!mPaused) mParameters = mActivity.mCameraDevice.getParameters();
        return fail;
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

    private void keepScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private static String millisecondToTimeString(long milliSeconds, boolean displayCentiSeconds) {
        long seconds = milliSeconds / 1000; // round down to compute seconds
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        StringBuilder timeStringBuilder = new StringBuilder();

        // Hours
        if (hours > 0) {
            if (hours < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(hours);

            timeStringBuilder.append(':');
        }

        // Minutes
        if (remainderMinutes < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderMinutes);
        timeStringBuilder.append(':');

        // Seconds
        if (remainderSeconds < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderSeconds);

        // Centi seconds
        if (displayCentiSeconds) {
            timeStringBuilder.append('.');
            long remainderCentiSeconds = (milliSeconds - seconds * 1000) / 10;
            if (remainderCentiSeconds < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(remainderCentiSeconds);
        }

        return timeStringBuilder.toString();
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
    }

    private void updateRecordingTime() {
        if (!mMediaRecorderRecording) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
                && delta >= mMaxVideoDurationInMs - 60000);

        long deltaAdjusted = delta;
        if (countdownRemainingTime) {
            deltaAdjusted = Math.max(0, mMaxVideoDurationInMs - deltaAdjusted) + 999;
        }
        String text;

        long targetNextUpdateDelay;
        if (!mCaptureTimeLapse) {
            text = millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = millisecondToTimeString(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
        }

        mRecordingTimeView.setText(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = mActivity.getResources().getColor(countdownRemainingTime
                    ? R.color.recording_time_remaining_text
                    : R.color.recording_time_elapsed_text);

            mRecordingTimeView.setTextColor(color);
        }

        long actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, actualNextUpdateDelay);
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    @SuppressWarnings("deprecation")
    private void setCameraParameters() {
        mParameters.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        mParameters.setPreviewFrameRate(mProfile.videoFrameRate);

        // Set flash mode.
        String flashMode;
        if (mActivity.mShowCameraAppView) {
            flashMode = mPreferences.getString(
                    CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                    mActivity.getString(R.string.pref_camera_video_flashmode_default));
        } else {
            flashMode = Parameters.FLASH_MODE_OFF;
        }
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        if (isSupported(flashMode, supportedFlash)) {
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
        if (isSupported(whiteBalance,
                mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }

        // Set zoom.
        if (mParameters.isZoomSupported()) {
            mParameters.setZoom(mZoomValue);
        }

        // Set continuous autofocus.
        List<String> supportedFocus = mParameters.getSupportedFocusModes();
        if (isSupported(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, supportedFocus)) {
            mParameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        mParameters.set(Util.RECORDING_HINT, Util.TRUE);

        // Enable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "true");
        }

        // Set picture size.
        // The logic here is different from the logic in still-mode camera.
        // There we determine the preview size based on the picture size, but
        // here we determine the picture size based on the preview size.
        List<Size> supported = mParameters.getSupportedPictureSizes();
        Size optimalSize = Util.getOptimalVideoSnapshotPictureSize(supported,
                (double) mDesiredPreviewWidth / mDesiredPreviewHeight);
        Size original = mParameters.getPictureSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPictureSize(optimalSize.width, optimalSize.height);
        }
        Log.v(TAG, "Video snapshot size is " + optimalSize.width + "x" +
                optimalSize.height);

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);

        mActivity.mCameraDevice.setParameters(mParameters);
        // Keep preview size up to date.
        mParameters = mActivity.mCameraDevice.getParameters();

        updateCameraScreenNailSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
    }

    private void updateCameraScreenNailSize(int width, int height) {
        if (!ApiHelper.HAS_SURFACE_TEXTURE) return;

        if (mCameraDisplayOrientation % 180 != 0) {
            int tmp = width;
            width = height;
            height = tmp;
        }

        CameraScreenNail screenNail = (CameraScreenNail) mActivity.mCameraScreenNail;
        int oldWidth = screenNail.getWidth();
        int oldHeight = screenNail.getHeight();

        if (oldWidth != width || oldHeight != height) {
            screenNail.setSize(width, height);
            screenNail.enableAspectRatioClamping();
            mActivity.notifyScreenNailChanged();
        }

        if (screenNail.getSurfaceTexture() == null) {
            screenNail.acquireSurfaceTexture();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_EFFECT_BACKDROPPER:
                if (resultCode == Activity.RESULT_OK) {
                    // onActivityResult() runs before onResume(), so this parameter will be
                    // seen by startPreview from onResume()
                    mEffectUriFromGallery = data.getData().toString();
                    Log.v(TAG, "Received URI from gallery: " + mEffectUriFromGallery);
                    mResetEffect = false;
                } else {
                    mEffectUriFromGallery = null;
                    Log.w(TAG, "No URI from gallery");
                    mResetEffect = true;
                }
                break;
        }
    }

    @Override
    public void onEffectsUpdate(int effectId, int effectMsg) {
        Log.v(TAG, "onEffectsUpdate. Effect Message = " + effectMsg);
        if (effectMsg == EffectsRecorder.EFFECT_MSG_EFFECTS_STOPPED) {
            // Effects have shut down. Hide learning message if any,
            // and restart regular preview.
            mBgLearningMessageFrame.setVisibility(View.GONE);
            checkQualityAndStartPreview();
        } else if (effectMsg == EffectsRecorder.EFFECT_MSG_RECORDING_DONE) {
            // This follows the codepath from onStopVideoRecording.
            if (mEffectsDisplayResult && !addVideoToMediaStore()) {
                if (mIsVideoCaptureIntent) {
                    if (mQuickCapture) {
                        doReturnToCaller(true);
                    } else {
                        showAlert();
                    }
                }
            }
            mEffectsDisplayResult = false;
            // In onPause, these were not called if the effects were active. We
            // had to wait till the effects recording is complete to do this.
            if (mPaused) {
                closeVideoFileDescriptor();
                clearVideoNamer();
            }
        } else if (effectMsg == EffectsRecorder.EFFECT_MSG_PREVIEW_RUNNING) {
            // Enable the shutter button once the preview is complete.
            mShutterButton.setEnabled(true);
        } else if (effectId == EffectsRecorder.EFFECT_BACKDROPPER) {
            switch (effectMsg) {
                case EffectsRecorder.EFFECT_MSG_STARTED_LEARNING:
                    mBgLearningMessageFrame.setVisibility(View.VISIBLE);
                    break;
                case EffectsRecorder.EFFECT_MSG_DONE_LEARNING:
                case EffectsRecorder.EFFECT_MSG_SWITCHING_EFFECT:
                    mBgLearningMessageFrame.setVisibility(View.GONE);
                    break;
            }
        }
        // In onPause, this was not called if the effects were active. We had to
        // wait till the effects completed to do this.
        if (mPaused) {
            Log.v(TAG, "OnEffectsUpdate: closing effects if activity paused");
            closeEffects();
        }
    }

    public void onCancelBgTraining(View v) {
        // Remove training message
        mBgLearningMessageFrame.setVisibility(View.GONE);
        // Write default effect out to shared prefs
        writeDefaultEffectToPrefs();
        // Tell VideoCamer to re-init based on new shared pref values.
        onSharedPreferenceChanged();
    }

    @Override
    public synchronized void onEffectsError(Exception exception, String fileName) {
        // TODO: Eventually we may want to show the user an error dialog, and then restart the
        // camera and encoder gracefully. For now, we just delete the file and bail out.
        if (fileName != null && new File(fileName).exists()) {
            deleteVideoFile(fileName);
        }
        try {
            if (Class.forName("android.filterpacks.videosink.MediaRecorderStopException")
                    .isInstance(exception)) {
                Log.w(TAG, "Problem recoding video file. Removing incomplete file.");
                return;
            }
        } catch (ClassNotFoundException ex) {
            Log.w(TAG, ex);
        }
        throw new RuntimeException("Error during recording!", exception);
    }

    private void initializeControlByIntent() {
        mBlocker = mRootView.findViewById(R.id.blocker);
        mMenu = mRootView.findViewById(R.id.menu);
        mMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPieRenderer != null) {
                    mPieRenderer.showInCenter();
                }
            }
        });
        mOnScreenIndicators = mRootView.findViewById(R.id.on_screen_indicators);
        mFlashIndicator = (ImageView) mRootView.findViewById(R.id.menu_flash_indicator);
        if (mIsVideoCaptureIntent) {
            mActivity.hideSwitcher();
            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // RotateImageView.
            mReviewDoneButton = (Rotatable) mRootView.findViewById(R.id.btn_done);
            mReviewCancelButton = (Rotatable) mRootView.findViewById(R.id.btn_cancel);
            mReviewPlayButton = (RotateImageView) mRootView.findViewById(R.id.btn_play);

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

            ((View) mReviewPlayButton).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onReviewPlayClicked(v);
                }
            });


            // Not grayed out upon disabled, to make the follow-up fade-out
            // effect look smooth. Note that the review done button in tablet
            // layout is not a TwoStateImageView.
            if (mReviewDoneButton instanceof TwoStateImageView) {
                ((TwoStateImageView) mReviewDoneButton).enableFilter(false);
            }
        }
    }

    private void initializeMiscControls() {
        mPreviewFrameLayout = (PreviewFrameLayout) mRootView.findViewById(R.id.frame);
        mPreviewFrameLayout.setOnLayoutChangeListener(mActivity);
        mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);

        mShutterButton = mActivity.getShutterButton();
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.requestFocus();

        // Disable the shutter button if effects are ON since it might take
        // a little more time for the effects preview to be ready. We do not
        // want to allow recording before that happens. The shutter button
        // will be enabled when we get the message from effectsrecorder that
        // the preview is running. This becomes critical when the camera is
        // swapped.
        if (effectsActive()) {
            mShutterButton.setEnabled(false);
        }

        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = mRootView.findViewById(R.id.time_lapse_label);
        // The R.id.labels can only be found in phone layout.
        // That is, mLabelsLinearLayout should be null in tablet layout.
        mLabelsLinearLayout = (LinearLayout) mRootView.findViewById(R.id.labels);

        mBgLearningMessageRotater = (RotateLayout) mRootView.findViewById(R.id.bg_replace_message);
        mBgLearningMessageFrame = mRootView.findViewById(R.id.bg_replace_message_frame);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setDisplayOrientation();

        // Change layout in response to configuration change
        LayoutInflater inflater = mActivity.getLayoutInflater();
        ((ViewGroup) mRootView).removeAllViews();
        inflater.inflate(R.layout.video_module, (ViewGroup) mRootView);

        // from onCreate()
        initializeControlByIntent();
        initializeOverlay();
        initializeSurfaceView();
        initializeMiscControls();
        showTimeLapseUI(mCaptureTimeLapse);
        initializeVideoSnapshot();
        resizeForPreviewAspectRatio();

        // from onResume()
        showVideoSnapshotUI(false);
        initializeZoom();
        onFullScreenChanged(mActivity.isInCameraApp());
        updateOnScreenIndicators();
    }

    @Override
    public void onOverriddenPreferencesClicked() {
    }

    @Override
    // TODO: Delete this after old camera code is removed
    public void onRestorePreferencesClicked() {
    }

    private boolean effectsActive() {
        return (mEffectType != EffectsRecorder.EFFECT_NONE);
    }

    @Override
    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()" or preview has not started yet
        if (mPaused) return;
        synchronized (mPreferences) {
            // If mCameraDevice is not ready then we can set the parameter in
            // startPreview().
            if (mActivity.mCameraDevice == null) return;

            boolean recordLocation = RecordLocationPreference.get(
                    mPreferences, mContentResolver);
            mLocationManager.recordLocation(recordLocation);

            // Check if the current effects selection has changed
            if (updateEffectSelection()) return;

            readVideoPreferences();
            showTimeLapseUI(mCaptureTimeLapse);
            // We need to restart the preview if preview size is changed.
            Size size = mParameters.getPreviewSize();
            if (size.width != mDesiredPreviewWidth
                    || size.height != mDesiredPreviewHeight) {
                if (!effectsActive()) {
                    stopPreview();
                } else {
                    mEffectsRecorder.release();
                    mEffectsRecorder = null;
                }
                resizeForPreviewAspectRatio();
                startPreview(); // Parameters will be set in startPreview().
            } else {
                setCameraParameters();
            }
            updateOnScreenIndicators();
        }
    }

    private void updateOnScreenIndicators() {
        updateFlashOnScreenIndicator(mParameters.getFlashMode());
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
            } else if (Parameters.FLASH_MODE_ON.equals(value) ||
                    Parameters.FLASH_MODE_TORCH.equals(value)) {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_on);
            } else {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_off);
            }
        }
    }

    private void switchCamera() {
        if (mPaused) return;

        Log.d(TAG, "Start to switch camera.");
        mCameraId = mPendingSwitchCameraId;
        mPendingSwitchCameraId = -1;
        mVideoControl.setCameraId(mCameraId);

        closeCamera();

        // Restart the camera and initialize the UI. From onCreate.
        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        openCamera();
        readVideoPreferences();
        startPreview();
        initializeVideoSnapshot();
        resizeForPreviewAspectRatio();
        initializeVideoControl();

        // From onResume
        initializeZoom();
        setOrientationIndicator(0, false);

        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            // Start switch camera animation. Post a message because
            // onFrameAvailable from the old camera may already exist.
            mHandler.sendEmptyMessage(SWITCH_CAMERA_START_ANIMATION);
        }
        updateOnScreenIndicators();
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

    private boolean updateEffectSelection() {
        int previousEffectType = mEffectType;
        Object previousEffectParameter = mEffectParameter;
        mEffectType = CameraSettings.readEffectType(mPreferences);
        mEffectParameter = CameraSettings.readEffectParameter(mPreferences);

        if (mEffectType == previousEffectType) {
            if (mEffectType == EffectsRecorder.EFFECT_NONE) return false;
            if (mEffectParameter.equals(previousEffectParameter)) return false;
        }
        Log.v(TAG, "New effect selection: " + mPreferences.getString(
                CameraSettings.KEY_VIDEO_EFFECT, "none"));

        if (mEffectType == EffectsRecorder.EFFECT_NONE) {
            // Stop effects and return to normal preview
            mEffectsRecorder.stopPreview();
            mPreviewing = false;
            return true;
        }
        if (mEffectType == EffectsRecorder.EFFECT_BACKDROPPER &&
            ((String) mEffectParameter).equals(EFFECT_BG_FROM_GALLERY)) {
            // Request video from gallery to use for background
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setDataAndType(Video.Media.EXTERNAL_CONTENT_URI,
                             "video/*");
            i.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            mActivity.startActivityForResult(i, REQUEST_EFFECT_BACKDROPPER);
            return true;
        }
        if (previousEffectType == EffectsRecorder.EFFECT_NONE) {
            // Stop regular preview and start effects.
            stopPreview();
            checkQualityAndStartPreview();
        } else {
            // Switch currently running effect
            mEffectsRecorder.setEffect(mEffectType, mEffectParameter);
        }
        return true;
    }

    // Verifies that the current preview view size is correct before starting
    // preview. If not, resets the surface texture and resizes the view.
    private void checkQualityAndStartPreview() {
        readVideoPreferences();
        showTimeLapseUI(mCaptureTimeLapse);
        Size size = mParameters.getPreviewSize();
        if (size.width != mDesiredPreviewWidth
                || size.height != mDesiredPreviewHeight) {
            resizeForPreviewAspectRatio();
        }
        // Start up preview again
        startPreview();
    }

    private void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mSwitchingCamera) return true;
        if (mPopup == null && mGestures != null && mRenderOverlay != null) {
            return mGestures.dispatchTouch(m);
        } else if (mPopup != null) {
            return mActivity.superDispatchTouchEvent(m);
        }
        return false;
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int value) {
            // Not useful to change zoom value when the activity is paused.
            if (mPaused) return;
            mZoomValue = value;
            // Set zoom parameters asynchronously
            mParameters.setZoom(mZoomValue);
            mActivity.mCameraDevice.setParametersAsync(mParameters);
            Parameters p = mActivity.mCameraDevice.getParameters();
            mZoomRenderer.setZoomValue(mZoomRatios.get(p.getZoom()));
        }

        @Override
        public void onZoomStart() {
        }
        @Override
        public void onZoomEnd() {
        }
    }

    private void initializeZoom() {
        if (!mParameters.isZoomSupported()) return;
        mZoomMax = mParameters.getMaxZoom();
        mZoomRatios = mParameters.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomRenderer.setZoomMax(mZoomMax);
        mZoomRenderer.setZoom(mParameters.getZoom());
        mZoomRenderer.setZoomValue(mZoomRatios.get(mParameters.getZoom()));
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
    }

    private void initializeVideoSnapshot() {
        if (Util.isVideoSnapshotSupported(mParameters) && !mIsVideoCaptureIntent) {
            mActivity.setSingleTapUpListener(mPreviewFrameLayout);
            // Show the tap to focus toast if this is the first start.
            if (mPreferences.getBoolean(
                        CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN, true)) {
                // Delay the toast for one second to wait for orientation.
                mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_SNAPSHOT_TOAST, 1000);
            }
        } else {
            mActivity.setSingleTapUpListener(null);
        }
    }

    void showVideoSnapshotUI(boolean enabled) {
        if (Util.isVideoSnapshotSupported(mParameters) && !mIsVideoCaptureIntent) {
            if (ApiHelper.HAS_SURFACE_TEXTURE && enabled) {
                ((CameraScreenNail) mActivity.mCameraScreenNail).animateCapture(mDisplayRotation);
            } else {
                mPreviewFrameLayout.showBorder(enabled);
            }
            mShutterButton.setEnabled(!enabled);
        }
    }

    // Preview area is touched. Take a picture.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mMediaRecorderRecording && effectsActive()) {
            new RotateTextToast(mActivity, R.string.disable_video_snapshot_hint,
                    mOrientation).show();
            return;
        }

        if (mPaused || mSnapshotInProgress || effectsActive()) {
            return;
        }

        if (!mMediaRecorderRecording) {
            // check for dismissing popup
            if (mPopup != null) {
                dismissPopup(true);
            }
            return;
        }

        // Set rotation and gps data.
        int rotation = Util.getJpegRotation(mCameraId, mOrientation);
        mParameters.setRotation(rotation);
        Location loc = mLocationManager.getCurrentLocation();
        Util.setGpsParameters(mParameters, loc);
        mActivity.mCameraDevice.setParameters(mParameters);

        Log.v(TAG, "Video snapshot start");
        mActivity.mCameraDevice.takePicture(null, null, null, new JpegPictureCallback(loc));
        showVideoSnapshotUI(true);
        mSnapshotInProgress = true;
    }

    @Override
    public void updateCameraAppView() {
        if (!mPreviewing || mParameters.getFlashMode() == null) return;

        // When going to and back from gallery, we need to turn off/on the flash.
        if (!mActivity.mShowCameraAppView) {
            if (mParameters.getFlashMode().equals(Parameters.FLASH_MODE_OFF)) {
                mRestoreFlash = false;
                return;
            }
            mRestoreFlash = true;
            setCameraParameters();
        } else if (mRestoreFlash) {
            mRestoreFlash = false;
            setCameraParameters();
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
        if (mGestures != null) {
            mGestures.setEnabled(full);
        }
        if (mPopup != null) {
            dismissPopup(false, full);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(full ? View.VISIBLE : View.GONE);
        }
        setShowMenu(full);
        if (mBlocker != null) {
            // this can not happen in capture mode
            mBlocker.setVisibility(full ? View.VISIBLE : View.GONE);
        }
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

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(byte [] jpegData, android.hardware.Camera camera) {
            Log.v(TAG, "onPictureTaken");
            mSnapshotInProgress = false;
            showVideoSnapshotUI(false);
            storeImage(jpegData, mLocation);
        }
    }

    private void storeImage(final byte[] data, Location loc) {
        long dateTaken = System.currentTimeMillis();
        String title = Util.createJpegName(dateTaken);
        int orientation = Exif.getOrientation(data);
        Size s = mParameters.getPictureSize();
        Uri uri = Storage.addImage(mContentResolver, title, dateTaken, loc, orientation, data,
                s.width, s.height);
        if (uri != null) {
            Util.broadcastNewPicture(mActivity, uri);
        }
    }

    private boolean resetEffect() {
        if (mResetEffect) {
            String value = mPreferences.getString(CameraSettings.KEY_VIDEO_EFFECT,
                    mPrefVideoEffectDefault);
            if (!mPrefVideoEffectDefault.equals(value)) {
                writeDefaultEffectToPrefs();
                return true;
            }
        }
        mResetEffect = true;
        return false;
    }

    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }

    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".3gp";
    }

    private void closeVideoFileDescriptor() {
        if (mVideoFileDescriptor != null) {
            try {
                mVideoFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close fd", e);
            }
            mVideoFileDescriptor = null;
        }
    }

    private void showTapToSnapshotToast() {
        new RotateTextToast(mActivity, R.string.video_snapshot_hint, 0)
                .show();
        // Clear the preference.
        Editor editor = mPreferences.edit();
        editor.putBoolean(CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN, false);
        editor.apply();
    }

    private void clearVideoNamer() {
        if (mVideoNamer != null) {
            mVideoNamer.finish();
            mVideoNamer = null;
        }
    }

    private static class VideoNamer extends Thread {
        private boolean mRequestPending;
        private ContentResolver mResolver;
        private ContentValues mValues;
        private boolean mStop;
        private Uri mUri;

        // Runs in main thread
        public VideoNamer() {
            start();
        }

        // Runs in main thread
        public synchronized void prepareUri(
                ContentResolver resolver, ContentValues values) {
            mRequestPending = true;
            mResolver = resolver;
            mValues = new ContentValues(values);
            notifyAll();
        }

        // Runs in main thread
        public synchronized Uri getUri() {
            // wait until the request is done.
            while (mRequestPending) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    // ignore.
                }
            }
            Uri uri = mUri;
            mUri = null;
            return uri;
        }

        // Runs in namer thread
        @Override
        public synchronized void run() {
            while (true) {
                if (mStop) break;
                if (!mRequestPending) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                    continue;
                }
                cleanOldUri();
                generateUri();
                mRequestPending = false;
                notifyAll();
            }
            cleanOldUri();
        }

        // Runs in main thread
        public synchronized void finish() {
            mStop = true;
            notifyAll();
        }

        // Runs in namer thread
        private void generateUri() {
            Uri videoTable = Uri.parse("content://media/external/video/media");
            mUri = mResolver.insert(videoTable, mValues);
        }

        // Runs in namer thread
        private void cleanOldUri() {
            if (mUri == null) return;
            mResolver.delete(mUri, null, null);
            mUri = null;
        }
    }

    private class SurfaceViewCallback implements SurfaceHolder.Callback {
        public SurfaceViewCallback() {}

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(TAG, "Surface changed. width=" + width + ". height=" + height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "Surface created");
            mSurfaceViewReady = true;
            if (mPaused) return;
            if (!ApiHelper.HAS_SURFACE_TEXTURE) {
                mActivity.mCameraDevice.setPreviewDisplayAsync(mPreviewSurfaceView.getHolder());
                if (!mPreviewing) {
                    startPreview();
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "Surface destroyed");
            mSurfaceViewReady = false;
            if (mPaused) return;
            if (!ApiHelper.HAS_SURFACE_TEXTURE) {
                stopVideoRecording();
                stopPreview();
            }
        }
    }

    @Override
    public boolean updateStorageHintOnResume() {
        return true;
    }

    // required by OnPreferenceChangedListener
    @Override
    public void onCameraPickerClicked(int cameraId) {
        if (mPaused || mPendingSwitchCameraId != -1) return;

        mPendingSwitchCameraId = cameraId;
        if (ApiHelper.HAS_SURFACE_TEXTURE) {
            Log.d(TAG, "Start to copy texture.");
            // We need to keep a preview frame for the animation before
            // releasing the camera. This will trigger onPreviewTextureCopied.
            ((CameraScreenNail) mActivity.mCameraScreenNail).copyTexture();
            // Disable all camera controls.
            mSwitchingCamera = true;
        } else {
            switchCamera();
        }
    }

    @Override
    public boolean needsSwitcher() {
        return !mIsVideoCaptureIntent;
    }

    @Override
    public void onPieOpened(int centerX, int centerY) {
        mActivity.cancelActivityTouchHandling();
        mActivity.setSwipingEnabled(false);
    }

    @Override
    public void onPieClosed() {
        mActivity.setSwipingEnabled(true);
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

    public void dismissPopup(boolean topLevelOnly) {
        dismissPopup(topLevelOnly, true);
    }

    public void dismissPopup(boolean topLevelPopupOnly, boolean fullScreen) {
        if (fullScreen) {
            mActivity.showUI();
            mBlocker.setVisibility(View.VISIBLE);
        }
        setShowMenu(fullScreen);
        if (mPopup != null) {
            ((FrameLayout) mRootView).removeView(mPopup);
            mPopup = null;
        }
        mVideoControl.popupDismissed(topLevelPopupOnly);
    }

    @Override
    public void onShowSwitcherPopup() {
        if (mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }
}
