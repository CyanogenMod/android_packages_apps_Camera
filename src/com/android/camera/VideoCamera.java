/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera;

import com.android.camera.ui.CameraPicker;
import com.android.camera.ui.IndicatorControlContainer;
import com.android.camera.ui.IndicatorControlWheelContainer;
import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.SharePopup;
import com.android.camera.ui.ZoomControl;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.filterpacks.videosink.MediaRecorderStopException;

/**
 * The Camcorder activity.
 */
public class VideoCamera extends ActivityBase
        implements CameraPreference.OnPreferenceChangedListener,
        ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback,
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener,
        ModePicker.OnModeChangeListener, View.OnTouchListener,
        EffectsRecorder.EffectsListener {

    private static final String TAG = "videocamera";

    private static final int CHECK_DISPLAY_ROTATION = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int ENABLE_SHUTTER_BUTTON = 6;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    // The brightness settings used when it is set to automatic in the system.
    // The reason why it is set to 0.7 is just because 1.0 is too bright.
    private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;

    private static final boolean SWITCH_CAMERA = true;
    private static final boolean SWITCH_VIDEO = false;

    private static final long SHUTTER_BUTTON_TIMEOUT = 500L; // 500ms

    private static final int[] TIME_LAPSE_VIDEO_QUALITY = {
            CamcorderProfile.QUALITY_TIME_LAPSE_1080P,
            CamcorderProfile.QUALITY_TIME_LAPSE_720P,
            CamcorderProfile.QUALITY_TIME_LAPSE_480P,
            CamcorderProfile.QUALITY_TIME_LAPSE_CIF,
            1007, /* TODO: replace it with QUALITY_TIME_LAPSE_QVGA if public. */
            CamcorderProfile.QUALITY_TIME_LAPSE_QCIF};

    private static final int[] VIDEO_QUALITY = {
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_CIF,
            7, /* TODO: replace it with CamcorderProfile.QUALITY_QVGA */
            CamcorderProfile.QUALITY_QCIF};

    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped.
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    private boolean mSnapshotInProgress = false;

    private static final String EFFECT_BG_FROM_GALLERY = "gallery";

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    private ComboPreferences mPreferences;
    private PreferenceGroup mPreferenceGroup;

    private View mPreviewPanel;  // The container of PreviewFrameLayout.
    private PreviewFrameLayout mPreviewFrameLayout;
    private SurfaceHolder mSurfaceHolder = null;
    private IndicatorControlContainer mIndicatorControlContainer;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private View mReviewControl;

    private Toast mNoShareToast;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    // A popup window that contains a bigger thumbnail and a list of apps to share.
    private SharePopup mSharePopup;
    // The bitmap of the last captured video thumbnail and the URI of the
    // original video.
    private Thumbnail mThumbnail;
    // An imageview showing showing the last captured picture thumbnail.
    private RotateImageView mThumbnailView;
    private Rotatable mReviewCancelButton;
    private Rotatable mReviewDoneButton;
    private Rotatable mReviewPlayButton;
    private ModePicker mModePicker;
    private ShutterButton mShutterButton;
    private TextView mRecordingTimeView;
    private RotateLayout mBgLearningMessageRotater;
    private View mBgLearningMessageFrame;
    private LinearLayout mLabelsLinearLayout;

    private boolean mIsVideoCaptureIntent;
    private boolean mQuickCapture;

    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false;

    private long mStorageSpace;

    private MediaRecorder mMediaRecorder;
    private EffectsRecorder mEffectsRecorder;

    private int mEffectType = EffectsRecorder.EFFECT_NONE;
    private Object mEffectParameter = null;
    private String mEffectUriFromGallery = null;
    private String mPrefVideoEffectDefault;
    private boolean mResetEffect = true;
    public static final String RESET_EFFECT_EXTRA = "reset_effect";

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

    boolean mPausing = false;
    boolean mPreviewing = false; // True if preview is started.
    // The display rotation in degrees. This is only valid when mPreviewing is
    // true.
    private int mDisplayRotation;

    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;

    private final Handler mHandler = new MainHandler();
    private Parameters mParameters;

    // multiple cameras support
    private int mNumberOfCameras;
    private int mCameraId;
    private int mFrontCameraId;
    private int mBackCameraId;

    private GestureDetector mPopupGestureDetector;

    private MyOrientationEventListener mOrientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons and thumbnails. Ex: if the value
    // is 90, the UI components should be rotated 90 degrees counter-clockwise.
    private int mOrientationCompensation = 0;
    // The orientation compenstaion when we start recording.
    private int mOrientationCompensationAtRecordStart;

    private static final int ZOOM_STOPPED = 0;
    private static final int ZOOM_START = 1;
    private static final int ZOOM_STOPPING = 2;

    private int mZoomState = ZOOM_STOPPED;
    private boolean mSmoothZoomSupported = false;
    private int mZoomValue;  // The current zoom value.
    private int mZoomMax;
    private int mTargetZoomValue;
    private ZoomControl mZoomControl;
    private final ZoomListener mZoomListener = new ZoomListener();

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
                    getWindow().clearFlags(
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
                    if ((Util.getDisplayRotation(VideoCamera.this) != mDisplayRotation)
                            && !mMediaRecorderRecording) {
                        startPreview();
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
                    }
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
                updateAndShowStorageHint();
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                updateAndShowStorageHint();
                updateThumbnailButton();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
                // handled in ACTION_MEDIA_EJECT
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(VideoCamera.this,
                        getResources().getString(R.string.wait), Toast.LENGTH_LONG).show();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                updateAndShowStorageHint();
            }
        }
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                getString(R.string.video_file_name_format));

        return dateFormat.format(date);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Util.initializeScreenBrightness(getWindow(), getContentResolver());

        mPreferences = new ComboPreferences(this);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CameraSettings.readPreferredCameraId(mPreferences);

        //Testing purpose. Launch a specific camera through the intent extras.
        int intentCameraId = Util.getCameraFacingIntentExtras(this);
        if (intentCameraId != -1) {
            mCameraId = intentCameraId;
        }

        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
        mPrefVideoEffectDefault = getString(R.string.pref_video_effect_default);
        // Do not reset the effect if users are switching between back and front
        // cameras.
        mResetEffect = getIntent().getBooleanExtra(RESET_EFFECT_EXTRA, true);
        resetEffect();

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        Thread startPreviewThread = new Thread(new Runnable() {
            public void run() {
                try {
                    mCameraDevice = Util.openCamera(VideoCamera.this, mCameraId);
                    readVideoPreferences();
                    startPreview();
                } catch (CameraHardwareException e) {
                    mOpenCameraFail = true;
                } catch (CameraDisabledException e) {
                    mCameraDisabled = true;
                }
            }
        });
        startPreviewThread.start();

        Util.enterLightsOutMode(getWindow());

        mContentResolver = getContentResolver();

        requestWindowFeature(Window.FEATURE_PROGRESS);
        mIsVideoCaptureIntent = isVideoCaptureIntent();
        setContentView(R.layout.video_camera);
        if (mIsVideoCaptureIntent) {
            mReviewDoneButton = (Rotatable) findViewById(R.id.btn_done);
            mReviewPlayButton = (Rotatable) findViewById(R.id.btn_play);
            mReviewCancelButton = (Rotatable) findViewById(R.id.btn_cancel);
            findViewById(R.id.btn_cancel).setVisibility(View.VISIBLE);
        } else {
            initThumbnailButton();
            mModePicker = (ModePicker) findViewById(R.id.mode_picker);
            mModePicker.setVisibility(View.VISIBLE);
            mModePicker.setOnModeChangeListener(this);
        }

        mPreviewPanel = findViewById(R.id.frame_layout);
        mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame);
        mReviewImage = (ImageView) findViewById(R.id.review_image);

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceView preview = (SurfaceView) findViewById(R.id.camera_preview);
        SurfaceHolder holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mQuickCapture = getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);

        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setBackgroundResource(R.drawable.btn_shutter_video);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.requestFocus();

        mRecordingTimeView = (TextView) findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) findViewById(R.id.recording_time_rect);
        mOrientationListener = new MyOrientationEventListener(this);
        mTimeLapseLabel = findViewById(R.id.time_lapse_label);
        // The R.id.labels can only be found in phone layout. For tablet, the id is
        // R.id.labels_w1024. That is, mLabelsLinearLayout should be null in tablet layout.
        mLabelsLinearLayout = (LinearLayout) findViewById(R.id.labels);

        mBgLearningMessageRotater = (RotateLayout) findViewById(R.id.bg_replace_message);
        mBgLearningMessageFrame = findViewById(R.id.bg_replace_message_frame);

        mLocationManager = new LocationManager(this, null);

        // Make sure preview is started.
        try {
            startPreviewThread.join();
            if (mOpenCameraFail) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } else if (mCameraDisabled) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

        showTimeLapseUI(mCaptureTimeLapse);
        initializeVideoSnapshot();
        resizeForPreviewAspectRatio();

        mBackCameraId = CameraHolder.instance().getBackCameraId();
        mFrontCameraId = CameraHolder.instance().getFrontCameraId();

        initializeIndicatorControl();
    }

    private void loadCameraPreferences() {
        CameraSettings settings = new CameraSettings(this, mParameters,
                mCameraId, CameraHolder.instance().getCameraInfo());
        // Remove the video quality preference setting when the quality is given in the intent.
        mPreferenceGroup = filterPreferenceScreenByIntent(
                settings.getPreferenceGroup(R.xml.video_preferences));
    }

    private boolean collapseCameraControls() {
        if ((mIndicatorControlContainer != null)
                && mIndicatorControlContainer.dismissSettingPopup()) {
            return true;
        }
        return false;
    }

    private void enableCameraControls(boolean enable) {
        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.setEnabled(enable);
        }
        if (mModePicker != null) mModePicker.setEnabled(enable);
    }

    private void initializeIndicatorControl() {
        mIndicatorControlContainer =
                (IndicatorControlContainer) findViewById(R.id.indicator_control);
        if (mIndicatorControlContainer == null) return;
        loadCameraPreferences();

        final String[] SETTING_KEYS = {
                    CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                    CameraSettings.KEY_WHITE_BALANCE,
                    CameraSettings.KEY_VIDEO_EFFECT,
                    CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                    CameraSettings.KEY_VIDEO_QUALITY};
        final String[] OTHER_SETTING_KEYS = {
                    CameraSettings.KEY_RECORD_LOCATION};

        CameraPicker.setImageResourceId(R.drawable.ic_switch_video_facing_holo_light);
        mIndicatorControlContainer.initialize(this, mPreferenceGroup,
                mParameters.isZoomSupported(), SETTING_KEYS, OTHER_SETTING_KEYS);
        mIndicatorControlContainer.setListener(this);
        mPopupGestureDetector = new GestureDetector(this,
                new PopupGestureListener());

        if (effectsActive()) {
            mIndicatorControlContainer.overrideSettings(
                    CameraSettings.KEY_VIDEO_QUALITY,
                    Integer.toString(CamcorderProfile.QUALITY_480P));
        }
    }


    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(VideoCamera.this);

            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                if (effectsActive()) {
                    mEffectsRecorder.setOrientationHint(
                            mOrientationCompensation % 360);
                }
                // Do not rotate the icons during recording because the video
                // orientation is fixed after recording.
                if (!mMediaRecorderRecording) {
                    setOrientationIndicator(mOrientationCompensation);
                }
            }
        }
    }

    private void setOrientationIndicator(int degree) {
        if (mThumbnailView != null) mThumbnailView.setDegree(degree);
        if (mModePicker != null) mModePicker.setDegree(degree);
        if (mSharePopup != null) mSharePopup.setOrientation(degree);
        if (mBgLearningMessageRotater != null) mBgLearningMessageRotater.setOrientation(degree);
        if (mIndicatorControlContainer != null) mIndicatorControlContainer.setDegree(degree);
        if (mReviewDoneButton != null) mReviewDoneButton.setOrientation(degree);
        if (mReviewPlayButton != null) mReviewPlayButton.setOrientation(degree);
        if (mReviewCancelButton!= null) mReviewCancelButton.setOrientation(degree);
        // We change the orientation of the linearlayout only for phone UI because when in portrait
        // the width is not enough.
        if (mLabelsLinearLayout != null) {
            if (((degree / 90) & 1) == 1) {
                mLabelsLinearLayout.setOrientation(mLabelsLinearLayout.VERTICAL);
            } else {
                mLabelsLinearLayout.setOrientation(mLabelsLinearLayout.HORIZONTAL);
            }
        }
        mRecordingTimeRect.setOrientation(mOrientationCompensation);
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(mCurrentVideoUri, convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    @OnClickAttr
    public void onThumbnailClicked(View v) {
        if (!mMediaRecorderRecording && mThumbnail != null) {
            showSharePopup();
        }
    }

    @OnClickAttr
    public void onReviewRetakeClicked(View v) {
        deleteCurrentVideo();
        hideAlert();
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

    private void onStopVideoRecording(boolean valid) {
        stopVideoRecording();
        if (mIsVideoCaptureIntent) {
            if (mQuickCapture) {
                doReturnToCaller(valid);
            } else {
                showAlert();
            }
        } else {
            getThumbnail();
        }
    }

    public void onProtectiveCurtainClick(View v) {
        // Consume clicks
    }

    @Override
    public void onShutterButtonClick() {
        if (collapseCameraControls()) return;
        boolean stop = mMediaRecorderRecording;

        if (stop) {
            onStopVideoRecording(true);
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

    private OnScreenHint mStorageHint;

    private void updateAndShowStorageHint() {
        mStorageSpace = Storage.getAvailableSpace();
        showStorageHint();
    }

    private void showStorageHint() {
        String errorMessage = null;
        if (mStorageSpace == Storage.UNAVAILABLE) {
            errorMessage = getString(R.string.no_storage);
        } else if (mStorageSpace == Storage.PREPARING) {
            errorMessage = getString(R.string.preparing_sd);
        } else if (mStorageSpace == Storage.UNKNOWN_SIZE) {
            errorMessage = getString(R.string.access_sd_fail);
        } else if (mStorageSpace < Storage.LOW_STORAGE_THRESHOLD) {
            errorMessage = getString(R.string.spaceIsLow_content);
        }

        if (errorMessage != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, errorMessage);
            } else {
                mStorageHint.setText(errorMessage);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    private void readVideoPreferences() {
        // The preference stores values from ListPreference and is thus string type for all values.
        // We need to convert it to int manually.
        String defaultQuality = CameraSettings.getDefaultVideoQuality(mCameraId,
                getResources().getString(R.string.pref_video_quality_default));
        String videoQuality =
                mPreferences.getString(CameraSettings.KEY_VIDEO_QUALITY,
                        defaultQuality);
        int quality = Integer.valueOf(videoQuality);

        // Set video quality.
        Intent intent = getIntent();
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
            mMaxVideoDurationInMs = CameraSettings.DEFAULT_VIDEO_DURATION;
        }

        // Set effect
        mEffectType = CameraSettings.readEffectType(mPreferences);
        if (mEffectType != EffectsRecorder.EFFECT_NONE) {
            mEffectParameter = CameraSettings.readEffectParameter(mPreferences);
            // Set quality to 480p for effects, unless intent is overriding it
            if (!intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
                quality = CamcorderProfile.QUALITY_480P;
            }
            // On initial startup, can get here before indicator control is
            // enabled. In that case, UI quality override handled in
            // initializeIndicatorControl.
            if (mIndicatorControlContainer != null) {
                mIndicatorControlContainer.overrideSettings(
                        CameraSettings.KEY_VIDEO_QUALITY,
                        Integer.toString(CamcorderProfile.QUALITY_480P));
            }
        } else {
            mEffectParameter = null;
            if (mIndicatorControlContainer != null) {
                mIndicatorControlContainer.overrideSettings(
                        CameraSettings.KEY_VIDEO_QUALITY,
                        null);
            }
        }
        // Read time lapse recording interval.
        String frameIntervalStr = mPreferences.getString(
                CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                getString(R.string.pref_video_time_lapse_frame_interval_default));
        mTimeBetweenTimeLapseFrameCaptureMs = Integer.parseInt(frameIntervalStr);

        mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);
        // TODO: This should be checked instead directly +1000.
        if (mCaptureTimeLapse) quality += 1000;
        mProfile = CamcorderProfile.get(mCameraId, quality);
        getDesiredPreviewSize();
    }

    private void writeDefaultEffectToPrefs()  {
        ComboPreferences.Editor editor = mPreferences.edit();
        editor.putString(CameraSettings.KEY_VIDEO_EFFECT,
                getString(R.string.pref_video_effect_default));
        editor.apply();
    }

    private void getDesiredPreviewSize() {
        mParameters = mCameraDevice.getParameters();
        if (mParameters.getSupportedVideoSizes() == null || effectsActive()) {
            mDesiredPreviewWidth = mProfile.videoFrameWidth;
            mDesiredPreviewHeight = mProfile.videoFrameHeight;
        } else {  // Driver supports separates outputs for preview and video.
            List<Size> sizes = mParameters.getSupportedPreviewSizes();
            Size preferred = mParameters.getPreferredPreviewSizeForVideo();
            int product = preferred.width * preferred.height;
            Iterator it = sizes.iterator();
            // Remove the preview sizes that are not preferred.
            while (it.hasNext()) {
                Size size = (Size) it.next();
                if (size.width * size.height > product) {
                    it.remove();
                }
            }
            Size optimalSize = Util.getOptimalPreviewSize(this, sizes,
                (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
            mDesiredPreviewWidth = optimalSize.width;
            mDesiredPreviewHeight = optimalSize.height;
        }
        Log.v(TAG, "mDesiredPreviewWidth=" + mDesiredPreviewWidth +
                ". mDesiredPreviewHeight=" + mDesiredPreviewHeight);
    }

    private void resizeForPreviewAspectRatio() {
        mPreviewFrameLayout.setAspectRatio(
                (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
    }

    @Override
    protected void doOnResume() {
        if (mOpenCameraFail || mCameraDisabled) return;

        mPausing = false;
        mZoomValue = 0;

        showVideoSnapshotUI(false);

        // Start orientation listener as soon as possible because it takes
        // some time to get first orientation.
        mOrientationListener.enable();
        if (!mPreviewing) {
            if (resetEffect()) {
                mBgLearningMessageFrame.setVisibility(View.GONE);
                mIndicatorControlContainer.reloadPreferences();
            }
            try {
                mCameraDevice = Util.openCamera(this, mCameraId);
                readVideoPreferences();
                resizeForPreviewAspectRatio();
                startPreview();
            } catch (CameraHardwareException e) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } catch (CameraDisabledException e) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        }

        // Initializing it here after the preview is started.
        initializeZoom();

        keepScreenOnAwhile();

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        registerReceiver(mReceiver, intentFilter);
        mStorageSpace = Storage.getAvailableSpace();

        mHandler.postDelayed(new Runnable() {
            public void run() {
                showStorageHint();
            }
        }, 200);

        // Initialize location sevice.
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, getContentResolver());
        mLocationManager.recordLocation(recordLocation);

        if (!mIsVideoCaptureIntent) {
            updateThumbnailButton();  // Update the last video thumbnail.
            mModePicker.setCurrentMode(ModePicker.MODE_VIDEO);
        }

        if (mPreviewing) {
            mOnResumeTime = SystemClock.uptimeMillis();
            mHandler.sendEmptyMessageDelayed(CHECK_DISPLAY_ROTATION, 100);
        }
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            if (effectsActive()) {
                mEffectsRecorder.setPreviewDisplay(
                        mSurfaceHolder,
                        mSurfaceWidth,
                        mSurfaceHeight);
            } else {
                mCameraDevice.setPreviewDisplay(holder);
            }
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void startPreview() {
        Log.v(TAG, "startPreview");

        mCameraDevice.setErrorCallback(mErrorCallback);
        if (mPreviewing == true) {
            mCameraDevice.stopPreview();
            if (effectsActive() && mEffectsRecorder != null) {
                mEffectsRecorder.release();
            }
            mPreviewing = false;
        }

        mDisplayRotation = Util.getDisplayRotation(this);
        int orientation = Util.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCameraDevice.setDisplayOrientation(orientation);
        setCameraParameters();

        if (!effectsActive()) {
            setPreviewDisplay(mSurfaceHolder);
            try {
                mCameraDevice.startPreview();
            } catch (Throwable ex) {
                closeCamera();
                throw new RuntimeException("startPreview failed", ex);
            }
        } else {
            initializeEffectsPreview();
            Log.v(TAG, "effectsStartPreview");
            mEffectsRecorder.startPreview();
        }

        mZoomState = ZOOM_STOPPED;
        mPreviewing = true;
    }

    private void closeCamera() {
        Log.v(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        if (mEffectsRecorder != null) {
            mEffectsRecorder.release();
        }
        mEffectType = EffectsRecorder.EFFECT_NONE;
        CameraHolder.instance().release();
        mCameraDevice.setZoomChangeListener(null);
        mCameraDevice.setErrorCallback(null);
        mCameraDevice = null;
        mPreviewing = false;
        mSnapshotInProgress = false;
    }

    private void finishRecorderAndCloseCamera() {
        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        if (mMediaRecorderRecording) {
            if (mIsVideoCaptureIntent) {
                stopVideoRecording();
                showAlert();
            } else {
                stopVideoRecording();
                getThumbnail();
            }
        } else {
            stopVideoRecording();
        }
        closeCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPausing = true;

        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.dismissSettingPopup();
        }

        finishRecorderAndCloseCamera();
        closeVideoFileDescriptor();

        if (mSharePopup != null) mSharePopup.dismiss();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        resetScreenOn();

        if (!mIsVideoCaptureIntent && mThumbnail != null && !mThumbnail.fromFile()) {
            mThumbnail.saveTo(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
        }

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

        mOrientationListener.disable();
        mLocationManager.recordLocation(false);

        mHandler.removeMessages(CHECK_DISPLAY_ROTATION);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!mMediaRecorderRecording) keepScreenOnAwhile();
    }

    @Override
    public void onBackPressed() {
        if (mPausing) return;
        if (mMediaRecorderRecording) {
            onStopVideoRecording(false);
        } else if (!collapseCameraControls()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is paused.
        if (mPausing) {
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

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                mShutterButton.setPressed(false);
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        Log.v(TAG, "surfaceChanged. w=" + w + ". h=" + h);

        mSurfaceHolder = holder;
        mSurfaceWidth = w;
        mSurfaceHeight = h;

        if (mPausing) {
            // We're pausing, the screen is off and we already stopped
            // video recording. We don't want to start the camera again
            // in this case in order to conserve power.
            // The fact that surfaceChanged is called _after_ an onPause appears
            // to be legitimate since in that case the lockscreen always returns
            // to portrait orientation possibly triggering the notification.
            return;
        }

        // The mCameraDevice will be null if it is fail to connect to the
        // camera hardware. In this case we will show a dialog and then
        // finish the activity, so it's OK to ignore it.
        if (mCameraDevice == null) return;

        // Set preview display if the surface is being created. Preview was
        // already started. Also restart the preview if display rotation has
        // changed. Sometimes this happens when the device is held in portrait
        // and camera app is opened. Rotation animation takes some time and
        // display rotation in onCreate may not be what we want.
        if (mPreviewing && (Util.getDisplayRotation(this) == mDisplayRotation)
                && holder.isCreating()) {
            setPreviewDisplay(holder);
        } else {
            stopVideoRecording();
            startPreview();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }

    private void gotoGallery() {
        MenuHelper.gotoCameraVideoGallery(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mIsVideoCaptureIntent) {
            // No options menu for attach mode.
            return false;
        } else {
            addBaseMenuItems(menu);
        }
        return true;
    }

    private boolean isVideoCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void doReturnToCaller(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
        } else {
            resultCode = RESULT_CANCELED;
        }
        setResultEx(resultCode, resultIntent);
        finish();
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

    // Prepares media recorder.
    private void initializeRecorder() {
        Log.v(TAG, "initializeRecorder");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) return;

        if (mSurfaceHolder == null) {
            Log.v(TAG, "Surface holder is null. Wait for surface changed.");
            return;
        }

        Intent intent = getIntent();
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

        // Unlock the camera object before passing it to media recorder.
        mCameraDevice.unlock();
        mMediaRecorder.setCamera(mCameraDevice);
        if (!mCaptureTimeLapse) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(mProfile);
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
        if (mCaptureTimeLapse) {
            mMediaRecorder.setCaptureRate((1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs));
        }

        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mMediaRecorder.setLocation((float) loc.getLatitude(),
                    (float) loc.getLongitude());
        }


        // Set output file.
        // Try Uri in the intent first. If it doesn't exist, use our own
        // instead.
        if (mVideoFileDescriptor != null) {
            mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat);
            mMediaRecorder.setOutputFile(mVideoFilename);
        }

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        // Set maximum file size.
        long maxFileSize = mStorageSpace - Storage.LOW_STORAGE_THRESHOLD;
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
        mOrientationCompensationAtRecordStart = mOrientationCompensation;

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

    private void initializeEffectsPreview() {
        Log.v(TAG, "initializeEffectsPreview");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) return;

        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];

        mEffectsRecorder = new EffectsRecorder(this);

        // TODO: Confirm none of the foll need to go to initializeEffectsRecording()
        // and none of these change even when the preview is not refreshed.
        mEffectsRecorder.setCamera(mCameraDevice);
        mEffectsRecorder.setCameraFacing(info.facing);
        mEffectsRecorder.setProfile(mProfile);
        mEffectsRecorder.setEffectsListener(this);
        mEffectsRecorder.setOnInfoListener(this);
        mEffectsRecorder.setOnErrorListener(this);

        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            rotation = mOrientationCompensation % 360;
        }
        mEffectsRecorder.setOrientationHint(rotation);

        mOrientationCompensationAtRecordStart = mOrientationCompensation;

        mEffectsRecorder.setPreviewDisplay(
                mSurfaceHolder,
                mSurfaceWidth,
                mSurfaceHeight);

        if (mEffectType == EffectsRecorder.EFFECT_BACKDROPPER &&
                ((String) mEffectParameter).equals(EFFECT_BG_FROM_GALLERY)) {
            mEffectsRecorder.setEffect(mEffectType, mEffectUriFromGallery);
        } else {
            mEffectsRecorder.setEffect(mEffectType, mEffectParameter);
        }
    }

    private void initializeEffectsRecording() {
        Log.v(TAG, "initializeEffectsRecording");

        Intent intent = getIntent();
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
        long maxFileSize = mStorageSpace - Storage.LOW_STORAGE_THRESHOLD;
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
        mVideoFilename = null;
    }

    private void generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        // Used when emailing.
        String filename = title + convertOutputFormatToFileExt(outputFileFormat);
        String mime = convertOutputFormatToMimeType(outputFileFormat);
        mVideoFilename = Storage.DIRECTORY + '/' + filename;
        mCurrentVideoValues = new ContentValues(7);
        mCurrentVideoValues.put(Video.Media.TITLE, title);
        mCurrentVideoValues.put(Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(Video.Media.DATA, mVideoFilename);
        mCurrentVideoValues.put(Video.Media.RESOLUTION,
                Integer.toString(mProfile.videoFrameWidth) + "x" +
                Integer.toString(mProfile.videoFrameHeight));
        Log.v(TAG, "New video filename: " + mVideoFilename);
    }

    private void addVideoToMediaStore() {
        if (mVideoFileDescriptor == null) {
            Uri videoTable = Uri.parse("content://media/external/video/media");
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
                mCurrentVideoUri = mContentResolver.insert(videoTable,
                        mCurrentVideoValues);
                sendBroadcast(new Intent(android.hardware.Camera.ACTION_NEW_VIDEO,
                        mCurrentVideoUri));
            } catch (Exception e) {
                // We failed to insert into the database. This can happen if
                // the SD card is unmounted.
                mCurrentVideoUri = null;
                mCurrentVideoFilename = null;
            } finally {
                Log.v(TAG, "Current video URI: " + mCurrentVideoUri);
            }
        }
        mCurrentVideoValues = null;
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
        updateAndShowStorageHint();
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_CAMERA, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_CAMERA);
            }
        });
        MenuHelper.addSwitchModeMenuItem(menu, ModePicker.MODE_PANORAMA, new Runnable() {
            public void run() {
                switchToOtherMode(ModePicker.MODE_PANORAMA);
            }
        });

        if (mNumberOfCameras > 1) {
            menu.add(R.string.switch_camera_id)
                    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    CameraSettings.writePreferredCameraId(mPreferences,
                            ((mCameraId == mFrontCameraId)
                            ? mBackCameraId : mFrontCameraId));
                    onSharedPreferenceChanged();
                    return true;
                }
            }).setIcon(android.R.drawable.ic_menu_camera);
        }
    }

    private PreferenceGroup filterPreferenceScreenByIntent(
            PreferenceGroup screen) {
        Intent intent = getIntent();
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
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecording();
            updateAndShowStorageHint();
        }
    }

    // from MediaRecorder.OnInfoListener
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording(true);
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording(true);

            // Show the toast.
            Toast.makeText(this, R.string.video_reach_size_limit,
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

        sendBroadcast(i);
    }

    // For testing.
    public boolean isRecording() {
        return mMediaRecorderRecording;
    }

    private void startVideoRecording() {
        Log.v(TAG, "startVideoRecording");

        updateAndShowStorageHint();
        if (mStorageSpace < Storage.LOW_STORAGE_THRESHOLD) {
            Log.v(TAG, "Storage issue, ignore the start request");
            return;
        }

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
                mCameraDevice.lock();
                return;
            }
        }

        enableCameraControls(false);

        mMediaRecorderRecording = true;
        mRecordingStartTime = SystemClock.uptimeMillis();
        showRecordingUI(true);

        updateRecordingTime();
        keepScreenOn();
    }

    private void showRecordingUI(boolean recording) {
        if (recording) {
            mIndicatorControlContainer.dismissSecondLevelIndicator();
            if (mThumbnailView != null) mThumbnailView.setEnabled(false);
            mShutterButton.setBackgroundResource(R.drawable.btn_shutter_video_recording);
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            if (mReviewControl != null) mReviewControl.setVisibility(View.GONE);
            if (mCaptureTimeLapse) {
                if (Util.isTabletUI()) {
                    ((IndicatorControlWheelContainer) mIndicatorControlContainer)
                            .startTimeLapseAnimation(
                                    mTimeBetweenTimeLapseFrameCaptureMs,
                                    mRecordingStartTime);
                }
            }
        } else {
            if (mThumbnailView != null) mThumbnailView.setEnabled(true);
            mShutterButton.setBackgroundResource(R.drawable.btn_shutter_video);
            mRecordingTimeView.setVisibility(View.GONE);
            if (mReviewControl != null) mReviewControl.setVisibility(View.VISIBLE);
            if (mCaptureTimeLapse) {
                if (Util.isTabletUI()) {
                    ((IndicatorControlWheelContainer) mIndicatorControlContainer)
                            .stopTimeLapseAnimation();
                }
            }
        }
    }

    private void getThumbnail() {
        if (mCurrentVideoUri != null) {
            Bitmap videoFrame = Thumbnail.createVideoThumbnail(mCurrentVideoFilename,
                    mPreviewFrameLayout.getWidth());
            if (videoFrame != null) {
                mThumbnail = new Thumbnail(mCurrentVideoUri, videoFrame, 0);
                mThumbnailView.setBitmap(mThumbnail.getBitmap());
                // Share popup may still have the reference to the old thumbnail. Clear it.
                mSharePopup = null;
            }
        }
    }

    private void showAlert() {
        Bitmap bitmap = null;
        if (mVideoFileDescriptor != null) {
            bitmap = Thumbnail.createVideoThumbnail(mVideoFileDescriptor.getFileDescriptor(),
                    mPreviewFrameLayout.getWidth());
        } else if (mCurrentVideoFilename != null) {
            bitmap = Thumbnail.createVideoThumbnail(mCurrentVideoFilename,
                    mPreviewFrameLayout.getWidth());
        }
        if (bitmap != null) {
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it to match the UI orientation (and mirror if it is front-facing camera).
            CameraInfo[] info = CameraHolder.instance().getCameraInfo();
            boolean mirror = (info[mCameraId].facing == CameraInfo.CAMERA_FACING_FRONT);
            bitmap = Util.rotateAndMirror(bitmap, -mOrientationCompensationAtRecordStart,
                    mirror);
            mReviewImage.setImageBitmap(bitmap);
            mReviewImage.setVisibility(View.VISIBLE);
        }

        Util.fadeOut(mShutterButton);
        Util.fadeOut(mIndicatorControlContainer);
        int[] pickIds = {R.id.btn_retake, R.id.btn_done, R.id.btn_play};
        for (int id : pickIds) {
            Util.fadeIn(findViewById(id));
        }

        showTimeLapseUI(false);
    }

    private void hideAlert() {
        mReviewImage.setVisibility(View.GONE);
        mShutterButton.setEnabled(true);
        enableCameraControls(true);

        int[] pickIds = {R.id.btn_retake, R.id.btn_done, R.id.btn_play};
        for (int id : pickIds) {
            Util.fadeOut(findViewById(id));
        }
        Util.fadeIn(mShutterButton);
        Util.fadeIn(mIndicatorControlContainer);

        if (mCaptureTimeLapse) {
            showTimeLapseUI(true);
        }
    }

    private void stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        if (mMediaRecorderRecording) {
            boolean shouldAddToMediaStoreNow = false;

            try {
                if (effectsActive()) {
                    // This is asynchronous, so we can't add to media store now because thumbnail
                    // may not be ready. In such case addVideoToMediaStore is called later
                    // through a callback from the MediaEncoderFilter to EffectsRecorder,
                    // and then to the VideoCamera.
                    mEffectsRecorder.stopRecording();
                } else {
                    mMediaRecorder.setOnErrorListener(null);
                    mMediaRecorder.setOnInfoListener(null);
                    mMediaRecorder.stop();
                    shouldAddToMediaStoreNow = true;
                }
                mCurrentVideoFilename = mVideoFilename;
                Log.v(TAG, "Setting current video filename: "
                        + mCurrentVideoFilename);
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail",  e);
                if (mVideoFilename != null) deleteVideoFile(mVideoFilename);
            }

            mMediaRecorderRecording = false;
            showRecordingUI(false);
            if (!mIsVideoCaptureIntent) {
                enableCameraControls(true);
            }
            // The orientation was fixed during video recording. Now make it
            // reflect the device orientation as video recording is stopped.
            setOrientationIndicator(mOrientationCompensation);
            keepScreenOnAwhile();
            if (shouldAddToMediaStoreNow) {
                addVideoToMediaStore();
            }
        }
        // always release media recorder
        if (!effectsActive()) {
            releaseMediaRecorder();
        }
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void keepScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void initThumbnailButton() {
        mThumbnailView = (RotateImageView) findViewById(R.id.thumbnail);
        mThumbnailView.enableFilter(false);
        mThumbnailView.setVisibility(View.VISIBLE);
        // Load the thumbnail from the disk.
        mThumbnail = Thumbnail.loadFrom(new File(getFilesDir(), Thumbnail.LAST_THUMB_FILENAME));
    }

    private void updateThumbnailButton() {
        if (mThumbnail == null || !Util.isUriValid(mThumbnail.getUri(), mContentResolver)) {
            mThumbnail = Thumbnail.getLastThumbnail(mContentResolver);
        }
        if (mThumbnail != null) {
            mThumbnailView.setBitmap(mThumbnail.getBitmap());
        } else {
            mThumbnailView.setBitmap(null);
        }
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

            int color = getResources().getColor(countdownRemainingTime
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

    private void setCameraParameters() {
        mParameters = mCameraDevice.getParameters();

        mParameters.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        mParameters.setPreviewFrameRate(mProfile.videoFrameRate);

        // Set flash mode.
        String flashMode = mPreferences.getString(
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                getString(R.string.pref_camera_video_flashmode_default));
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        if (isSupported(flashMode, supportedFlash)) {
            mParameters.setFlashMode(flashMode);
        } else {
            flashMode = mParameters.getFlashMode();
            if (flashMode == null) {
                flashMode = getString(
                        R.string.pref_camera_flashmode_no_flash);
            }
        }

        // Set white balance parameter.
        String whiteBalance = mPreferences.getString(
                CameraSettings.KEY_WHITE_BALANCE,
                getString(R.string.pref_camera_whitebalance_default));
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

        mParameters.setRecordingHint(true);

        // Enable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "true");
        }

        // Set picture size.
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);
        if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(this, mParameters);
        } else {
            List<Size> supported = mParameters.getSupportedPictureSizes();
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
        }

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);

        mCameraDevice.setParameters(mParameters);
        // Keep preview size up to date.
        mParameters = mCameraDevice.getParameters();
    }

    private boolean switchToOtherMode(int mode) {
        if (isFinishing()) return false;
        MenuHelper.gotoMode(mode, this);
        finish();
        return true;
    }

    public boolean onModeChanged(int mode) {
        if (mode != ModePicker.MODE_VIDEO) {
            return switchToOtherMode(mode);
        } else {
            return true;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case EffectsRecorder.EFFECT_BACKDROPPER:
                if (resultCode == RESULT_OK) {
                    // onActivityResult() runs before onResume(), so this parameter will be
                    // seen by startPreview from onResume()
                    mEffectUriFromGallery = ((Uri) data.getData()).toString();
                    Log.v(TAG, "Received URI from gallery: " + mEffectUriFromGallery);
                    mResetEffect = false;
                } else {
                    mEffectUriFromGallery = null;
                    Log.w(TAG, "No URI from gallery");
                    mResetEffect = true;
                }
                break;
            default:
                Log.e(TAG, "Unknown activity result sent to Camera!");
                break;
        }
    }

    @Override
    public void onEffectsUpdate(int effectId, int effectMsg) {
        if (effectMsg == EffectsRecorder.EFFECT_MSG_EFFECTS_STOPPED) {
            // Effects have shut down. Hide learning message if any,
            // and restart regular preview.
            mBgLearningMessageFrame.setVisibility(View.GONE);
            checkQualityAndStartPreview();
        } else if (effectMsg == EffectsRecorder.EFFECT_MSG_RECORDING_DONE) {
            addVideoToMediaStore();
            getThumbnail();
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
    }

    public void onCancelBgTraining(View v) {
        // Remove training message
        mBgLearningMessageFrame.setVisibility(View.GONE);
        // Write default effect out to shared prefs
        writeDefaultEffectToPrefs();
        // Tell the indicator controller to redraw based on new shared pref values
        mIndicatorControlContainer.reloadPreferences();
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
        if (exception instanceof MediaRecorderStopException) {
            Log.w(TAG, "Problem recoding video file. Removing incomplete file.");
            return;
        }
        throw new RuntimeException("Error during recording!", exception);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    public void onOverriddenPreferencesClicked() {
    }

    public void onRestorePreferencesClicked() {
        Runnable runnable = new Runnable() {
            public void run() {
                restorePreferences();
            }
        };
        MenuHelper.confirmAction(this,
                getString(R.string.confirm_restore_title),
                getString(R.string.confirm_restore_message),
                runnable);
    }

    private void restorePreferences() {
        // Reset the zoom. Zoom value is not stored in preference.
        if (mParameters.isZoomSupported()) {
            mZoomValue = 0;
            setCameraParameters();
            mZoomControl.setZoomIndex(0);
        }

        if (mIndicatorControlContainer != null) {
            mIndicatorControlContainer.dismissSettingPopup();
            CameraSettings.restorePreferences(this, mPreferences,
                    mParameters);
            mIndicatorControlContainer.reloadPreferences();
            onSharedPreferenceChanged();
        }
    }

    private boolean effectsActive() {
        return (mEffectType != EffectsRecorder.EFFECT_NONE);
    }

    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()" or preview has not started yet
        if (mPausing) return;
        synchronized (mPreferences) {
            // If mCameraDevice is not ready then we can set the parameter in
            // startPreview().
            if (mCameraDevice == null) return;

            boolean recordLocation = RecordLocationPreference.get(
                    mPreferences, getContentResolver());
            mLocationManager.recordLocation(recordLocation);

            // Check if the current effects selection has changed
            if (updateEffectSelection()) return;

            // Check if camera id is changed.
            int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
            if (mCameraId != cameraId) {
                // Restart the activity to have a crossfade animation.
                // TODO: Use SurfaceTexture to implement a better and faster
                // animation.
                if (mIsVideoCaptureIntent) {
                    // If the intent is video capture, stay in video capture mode.
                    Intent intent = getIntent();
                    intent.putExtra(RESET_EFFECT_EXTRA, false);
                    MenuHelper.gotoVideoMode(this, intent);
                } else {
                    MenuHelper.gotoVideoMode(this, false);
                }
                finish();
            } else {
                readVideoPreferences();
                showTimeLapseUI(mCaptureTimeLapse);
                // We need to restart the preview if preview size is changed.
                Size size = mParameters.getPreviewSize();
                if (size.width != mDesiredPreviewWidth
                        || size.height != mDesiredPreviewHeight) {
                    if (!effectsActive()) {
                        mCameraDevice.stopPreview();
                    } else {
                        mEffectsRecorder.release();
                    }
                    resizeForPreviewAspectRatio();
                    startPreview(); // Parameters will be set in startPreview().
                } else {
                    setCameraParameters();
                }
            }
        }
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
            return true;
        }
        if (mEffectType == EffectsRecorder.EFFECT_BACKDROPPER &&
            ((String) mEffectParameter).equals(EFFECT_BG_FROM_GALLERY)) {
            // Request video from gallery to use for background
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setDataAndType(Video.Media.EXTERNAL_CONTENT_URI,
                             "video/*");
            i.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            startActivityForResult(i, EffectsRecorder.EFFECT_BACKDROPPER);
            return true;
        }
        if (previousEffectType == EffectsRecorder.EFFECT_NONE) {
            // Stop regular preview and start effects.
            mCameraDevice.stopPreview();
            checkQualityAndStartPreview();
        } else {
            // Switch currently running effect
            mEffectsRecorder.setEffect(mEffectType, mEffectParameter);
        }
        return true;
    }

    // Verifies that the current preview view size is correct before starting
    // preview. If not, resets the surface holder and resizes the view.
    private void checkQualityAndStartPreview() {
        readVideoPreferences();
        showTimeLapseUI(mCaptureTimeLapse);
        Size size = mParameters.getPreviewSize();
        if (size.width != mDesiredPreviewWidth
                || size.height != mDesiredPreviewHeight) {
            resizeForPreviewAspectRatio();
        } else {
            // Start up preview again
            startPreview();
        }
    }

    private void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    private void showSharePopup() {
        Uri uri = mThumbnail.getUri();
        if (mSharePopup == null || !uri.equals(mSharePopup.getUri())) {
            mSharePopup = new SharePopup(this, uri, mThumbnail.getBitmap(),
                    mOrientationCompensation, mPreviewPanel);
        }
        mSharePopup.showAtLocation(mThumbnailView, Gravity.NO_GRAVITY, 0, 0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        // Check if the popup window should be dismissed first.
        if (mPopupGestureDetector != null && mPopupGestureDetector.onTouchEvent(m)) {
            return true;
        }

        return super.dispatchTouchEvent(m);
    }

    private class PopupGestureListener extends
            GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            // Check if the popup window is visible.
            View popup = mIndicatorControlContainer.getActiveSettingPopup();
            if (popup == null) return false;

            // Let popup window or indicator wheel handle the event by
            // themselves. Dismiss the popup window if users touch on other
            // areas.
            if (!Util.pointInView(e.getX(), e.getY(), popup)
                    && !Util.pointInView(e.getX(), e.getY(), mIndicatorControlContainer)) {
                mIndicatorControlContainer.dismissSettingPopup();
                // Let event fall through.
            }
            return false;
        }
    }

    private class ZoomChangeListener implements ZoomControl.OnZoomChangedListener {
        // only for immediate zoom
        @Override
        public void onZoomValueChanged(int index) {
            VideoCamera.this.onZoomValueChanged(index);
        }

        // only for smooth zoom
        @Override
        public void onZoomStateChanged(int state) {
            if (mPausing) return;

            Log.v(TAG, "zoom picker state=" + state);
            if (state == ZoomControl.ZOOM_IN) {
                VideoCamera.this.onZoomValueChanged(mZoomMax);
            } else if (state == ZoomControl.ZOOM_OUT){
                VideoCamera.this.onZoomValueChanged(0);
            } else {
                mTargetZoomValue = -1;
                if (mZoomState == ZOOM_START) {
                    mZoomState = ZOOM_STOPPING;
                    mCameraDevice.stopSmoothZoom();
                }
            }
        }
    }

    private void initializeZoom() {
        mZoomControl = (ZoomControl) findViewById(R.id.zoom_control);
        // Get the parameter to make sure we have the up-to-date zoom value.
        mParameters = mCameraDevice.getParameters();
        if (!mParameters.isZoomSupported()) return;

        mZoomMax = mParameters.getMaxZoom();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomControl.setZoomMax(mZoomMax);
        mZoomControl.setZoomIndex(mParameters.getZoom());
        mZoomControl.setSmoothZoomSupported(mSmoothZoomSupported);
        mZoomControl.setOnZoomChangeListener(new ZoomChangeListener());
        mCameraDevice.setZoomChangeListener(mZoomListener);
    }

    private final class ZoomListener
            implements android.hardware.Camera.OnZoomChangeListener {
        @Override
        public void onZoomChange(int value, boolean stopped, android.hardware.Camera camera) {
            Log.v(TAG, "Zoom changed: value=" + value + ". stopped=" + stopped);
            mZoomValue = value;

            // Update the UI when we get zoom value.
            mZoomControl.setZoomIndex(value);

            // Keep mParameters up to date. We do not getParameter again in
            // takePicture. If we do not do this, wrong zoom value will be set.
            mParameters.setZoom(value);

            if (stopped && mZoomState != ZOOM_STOPPED) {
                if (mTargetZoomValue != -1 && value != mTargetZoomValue) {
                    mCameraDevice.startSmoothZoom(mTargetZoomValue);
                    mZoomState = ZOOM_START;
                } else {
                    mZoomState = ZOOM_STOPPED;
                }
            }
        }
    }

    private void onZoomValueChanged(int index) {
        // Not useful to change zoom value when the activity is paused.
        if (mPausing) return;

        if (mSmoothZoomSupported) {
            if (mTargetZoomValue != index && mZoomState != ZOOM_STOPPED) {
                mTargetZoomValue = index;
                if (mZoomState == ZOOM_START) {
                    mZoomState = ZOOM_STOPPING;
                    mCameraDevice.stopSmoothZoom();
                }
            } else if (mZoomState == ZOOM_STOPPED && mZoomValue != index) {
                mTargetZoomValue = index;
                mCameraDevice.startSmoothZoom(index);
                mZoomState = ZOOM_START;
            }
        } else {
            mZoomValue = index;
            setCameraParameters();
        }
    }

    private void initializeVideoSnapshot() {
        if (mParameters.isVideoSnapshotSupported() && !mIsVideoCaptureIntent) {
            findViewById(R.id.camera_preview).setOnTouchListener(this);
        }
    }

    void showVideoSnapshotUI(boolean enabled) {
        if (mParameters.isVideoSnapshotSupported() && !mIsVideoCaptureIntent) {
            mPreviewFrameLayout.showBorder(enabled);
            mIndicatorControlContainer.enableZoom(!enabled);
            mShutterButton.setEnabled(!enabled);
        }
    }

    // Preview area is touched. Take a picture.
    @Override
    public boolean onTouch(View v, MotionEvent e) {
        if (mMediaRecorderRecording && effectsActive()) {
            Toast.makeText(this, getResources().getString(
                    R.string.disable_video_snapshot_hint), Toast.LENGTH_LONG).show();
            return false;
        }

        if (mPausing || mSnapshotInProgress
                || !mMediaRecorderRecording || effectsActive()) {
            return false;
        }

        // Set rotation and gps data.
        Util.setRotationParameter(mParameters, mCameraId, mOrientation);
        Location loc = mLocationManager.getCurrentLocation();
        Util.setGpsParameters(mParameters, loc);
        mCameraDevice.setParameters(mParameters);

        Log.v(TAG, "Video snapshot start");
        mCameraDevice.takePicture(null, null, null, new JpegPictureCallback(loc));
        showVideoSnapshotUI(true);
        mSnapshotInProgress = true;
        return true;
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
            // Create a thumbnail whose width is equal or bigger than that of the preview.
            int ratio = (int) Math.ceil((double) mParameters.getPictureSize().width
                    / mPreviewFrameLayout.getWidth());
            int inSampleSize = Integer.highestOneBit(ratio);
            mThumbnail = Thumbnail.createThumbnail(data, orientation, inSampleSize, uri);
            if (mThumbnail != null) {
                mThumbnailView.setBitmap(mThumbnail.getBitmap());
            }
            // Share popup may still have the reference to the old thumbnail. Clear it.
            mSharePopup = null;
            Util.broadcastNewPicture(this, uri);
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
}
