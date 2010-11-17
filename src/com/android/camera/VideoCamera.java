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

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.ui.CamcorderHeadUpDisplay;
import com.android.camera.ui.GLRootView;
import com.android.camera.ui.GLView;
import com.android.camera.ui.HeadUpDisplay;
import com.android.camera.ui.ZoomController;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.CameraSwitch;
import android.media.CamcorderProfile;
import android.media.EncoderCapabilities;
import android.media.EncoderCapabilities.AudioEncoderCap;
import android.media.EncoderCapabilities.VideoEncoderCap;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;


/**
 * The Camcorder activity.
 */
public class VideoCamera extends BaseCamera implements
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener,
        PreviewFrameLayout.OnSizeChangedListener {

    private static final String TAG = "videocamera";

    private static final int INIT_RECORDER = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int ENABLE_SHUTTER_BUTTON = 6;
    private static final int RELOAD_HUD = 7;

    private static final int SCREEN_DELAY = 1000;

    // The brightness settings used when it is set to automatic in the system.
    // The reason why it is set to 0.7 is just because 1.0 is too bright.
    private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;

    private static final long NO_STORAGE_ERROR = -1L;
    private static final long CANNOT_STAT_ERROR = -2L;
    private static final long LOW_STORAGE_THRESHOLD = 512L * 1024L;

    private static final int STORAGE_STATUS_OK = 0;
    private static final int STORAGE_STATUS_LOW = 1;
    private static final int STORAGE_STATUS_NONE = 2;

    private static final boolean SWITCH_CAMERA = true;
    private static final boolean SWITCH_VIDEO = false;

    private static final long SHUTTER_BUTTON_TIMEOUT = 500L; // 500ms

    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped.
     * TODO: consider publishing by moving into MediaStore.
     */
    private final static String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    private OrientationEventListener mOrientationListener;
    private int mLastOrientation = 0;  // No rotation (landscape) by default.

    private PreviewFrameLayout mPreviewFrameLayout;
    private SurfaceHolder mSurfaceHolder = null;
    private ImageView mVideoFrame;
    private GLRootView mGLRootView;

    private boolean mIsVideoCaptureIntent;
    private boolean mQuickCapture;
    // mLastPictureButton and mThumbController
    // are non-null only if mIsVideoCaptureIntent is true.
    private ImageView mLastPictureButton;
    private ThumbnailController mThumbController;
    private boolean mStartPreviewFail = false;

    private int mStorageStatus = STORAGE_STATUS_OK;

    private boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private String mCameraVideoFilename;
    private FileDescriptor mCameraVideoFileDescriptor;

    // The video file that has already been recorded, and that is being
    // examined by the user.
    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;
    private ContentValues mCurrentVideoValues;

    private CamcorderProfile mProfile;
    private int mVideoEncoder;
    private int mAudioEncoder;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoBitrate;
    private String mOutputFormat = "3gp";
    private int mVideoFps = 30;


    //
    // DefaultHashMap is a HashMap which returns a default value if the specified
    // key is not found.
    //
    @SuppressWarnings("serial")

    static class DefaultHashMap<K, V> extends HashMap<K, V> {
        private V mDefaultValue;

        public void putDefault(V defaultValue) {
            mDefaultValue = defaultValue;
        }

        @Override
        public V get(Object key) {
            V value = super.get(key);
            return (value == null) ? mDefaultValue : value;
        }

        public K getKey(V toCheck) {
            Iterator<K> it = this.keySet().iterator();
            V val;
            K key;
            while(it.hasNext()) {
                key = it.next();
                val = this.get(key);
                if (val.equals(toCheck)) {
                    return key;
                }
            }
        return null;
        }
    }


    static final DefaultHashMap<String, Integer>
            OUTPUT_FORMAT_TABLE = new DefaultHashMap<String, Integer>();
    static final DefaultHashMap<String, Integer>
            VIDEO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();
    static final DefaultHashMap<String, Integer>
            AUDIO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();
    static final DefaultHashMap<String, Integer>
            VIDEOQUALITY_BITRATE_TABLE = new DefaultHashMap<String, Integer>();

    static {
        OUTPUT_FORMAT_TABLE.put("3gp", MediaRecorder.OutputFormat.THREE_GPP);
        OUTPUT_FORMAT_TABLE.put("mp4", MediaRecorder.OutputFormat.MPEG_4);
        OUTPUT_FORMAT_TABLE.putDefault(MediaRecorder.OutputFormat.DEFAULT);

        for (VideoEncoderCap encoder : EncoderCapabilities.getVideoEncoders()) {
            switch (encoder.mCodec) {
                case MediaRecorder.VideoEncoder.H263:
                    VIDEO_ENCODER_TABLE.put("h263", MediaRecorder.VideoEncoder.H263);
                    break;
                case MediaRecorder.VideoEncoder.H264:
                    VIDEO_ENCODER_TABLE.put("h264", MediaRecorder.VideoEncoder.H264);
                    break;
                case MediaRecorder.VideoEncoder.MPEG_4_SP:
                    VIDEO_ENCODER_TABLE.put("m4v", MediaRecorder.VideoEncoder.MPEG_4_SP);
                    break;
            }
        }
        VIDEO_ENCODER_TABLE.putDefault(MediaRecorder.VideoEncoder.DEFAULT);

        for (AudioEncoderCap encoder : EncoderCapabilities.getAudioEncoders()) {
            switch (encoder.mCodec) {
                case MediaRecorder.AudioEncoder.AMR_NB:
                    AUDIO_ENCODER_TABLE.put("amrnb", MediaRecorder.AudioEncoder.AMR_NB);
                    break;
                case MediaRecorder.AudioEncoder.AAC:
                    AUDIO_ENCODER_TABLE.put("aac", MediaRecorder.AudioEncoder.AAC);
                    break;
            }
        }
        AUDIO_ENCODER_TABLE.putDefault(MediaRecorder.AudioEncoder.DEFAULT);

	/*
        AUDIO_ENCODER_TABLE.put("amrwb", MediaRecorder.AudioEncoder.AMR_WB);
        AUDIO_ENCODER_TABLE.put("qcelp", MediaRecorder.AudioEncoder.QCELP);
        AUDIO_ENCODER_TABLE.put("evrc", MediaRecorder.AudioEncoder.EVRC);
        AUDIO_ENCODER_TABLE.put("aacplus", MediaRecorder.AudioEncoder.AAC_PLUS);
        AUDIO_ENCODER_TABLE.put("eaacplus", MediaRecorder.AudioEncoder.EAAC_PLUS);
	*/

        VIDEOQUALITY_BITRATE_TABLE.put("1280x720", 6000000);
        VIDEOQUALITY_BITRATE_TABLE.put("720x480",  3000000);
        VIDEOQUALITY_BITRATE_TABLE.put("800x480",  3000000);
        VIDEOQUALITY_BITRATE_TABLE.put("640x480",  1600000);
        VIDEOQUALITY_BITRATE_TABLE.put("352x288",  360000);
        VIDEOQUALITY_BITRATE_TABLE.put("320x240",  320000);
        VIDEOQUALITY_BITRATE_TABLE.put("176x144",  192000);
        VIDEOQUALITY_BITRATE_TABLE.putDefault(320000);
    }

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;

    private ContentResolver mContentResolver;

    private ShutterButton mShutterButton;
    private TextView mRecordingTimeView;
    private Switcher mSwitcher;
    private boolean mRecordingTimeCountsDown = false;

    private long mLastStabilityChange = 0;

    private final ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    private final Handler mHandler = new MainHandler();

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

                case INIT_RECORDER: {
                    initializeRecorder();
                    break;
                }

                case RELOAD_HUD: {
                    if (mHeadUpDisplay != null) {
                        finalizeHeadUpDisplay();
                        initializeHeadUpDisplay();
                    }
                    break;
                }
                default:
                    Log.v(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    private StabilityChangeListener mStabilityChangeListener = new StabilityChangeListener() {
        public void onStabilityChanged(boolean stable) {
            long now = System.currentTimeMillis();

            // 2 second interval for measuring stability
            if (!mHeadUpDisplay.isActive() && stable
                    && (now - mLastStabilityChange) > 120) {
                Log.d(TAG, "** Camera stable **");
                mLastStabilityChange = now;
                if (mMediaRecorder != null) {
                    mMediaRecorder.autoFocusCamera();
                }
                mFocusRectangle.showSuccess();
            } else if (!stable) {
                mFocusRectangle.clear();
            }
        }

    };

    private BroadcastReceiver mReceiver = null;

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                updateAndShowStorageHint(false);
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                updateAndShowStorageHint(true);
		if(mMediaRecorder != null)
			releaseMediaRecorder();
		if(mCameraVideoFilename != null)
			cleanupEmptyFile();
                initializeRecorder();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
                // handled in ACTION_MEDIA_EJECT
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(VideoCamera.this,
                        getResources().getString(R.string.wait), 5000);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                updateAndShowStorageHint(true);
            }
        }
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                getString(R.string.video_file_name_format));

        return dateFormat.format(date);
    }

    private void showCameraBusyAndFinish() {
        Resources ress = getResources();
        Util.showFatalErrorAndFinish(VideoCamera.this,
                ress.getString(R.string.camera_error_title),
                ress.getString(R.string.cannot_connect_camera));
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Window win = getWindow();

        // Overright the brightness settings if it is automatic
        int mode = Settings.System.getInt(
                getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.screenBrightness = DEFAULT_CAMERA_BRIGHTNESS;
            win.setAttributes(winParams);
        }

        mPreferences = getSharedPreferences(CameraHolder.instance().getCameraNode(), Context.MODE_PRIVATE);
        CameraSettings.upgradePreferences(mPreferences);

        readVideoPreferences();

        // Create orientation listenter. This should be done first because it
        // takes some time to get first orientation.
        mOrientationListener = new OrientationEventListener(VideoCamera.this) {
            @Override
            public void onOrientationChanged(int orientation) {
                // We keep the last known orientation. So if the user
                // first orient the camera then point the camera to
                if (orientation != ORIENTATION_UNKNOWN) {
                    orientation += 90;
                }
                orientation = ImageManager.roundOrientation(orientation);
                if (orientation != mLastOrientation) {
                    mLastOrientation = orientation;
                    if (!mIsVideoCaptureIntent) {
                        setOrientationIndicator(mLastOrientation);
                    }

                    if (mGLRootView != null) {
                        mGLRootView.queueEvent(new Runnable() {
                            public void run() {
                                mHeadUpDisplay.setOrientation(mLastOrientation);
                            }
                        });
                    }
                }
            }
        };
        mOrientationListener.enable();

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        Thread startPreviewThread = new Thread(new Runnable() {
            public void run() {
                try {
                    mStartPreviewFail = false;
                    startPreview();
                } catch (CameraHardwareException e) {
                    // In eng build, we throw the exception so that test tool
                    // can detect it and report it
                    if ("eng".equals(Build.TYPE)) {
                        throw new RuntimeException(e);
                    }
                    mStartPreviewFail = true;
                }
            }
        });
        startPreviewThread.start();

        mContentResolver = getContentResolver();

        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.video_camera);

        mPreviewFrameLayout = (PreviewFrameLayout)
                findViewById(R.id.frame_layout);
        mPreviewFrameLayout.setOnSizeChangedListener(this);
        resizeForPreviewAspectRatio();

        mSurfaceView = (SurfaceView) findViewById(R.id.camera_preview);
        mVideoFrame = (ImageView) findViewById(R.id.video_frame);
        mFocusRectangle = (FocusRectangle) findViewById(R.id.focus_rectangle);
        
        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mIsVideoCaptureIntent = isVideoCaptureIntent();
        mQuickCapture = getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mRecordingTimeView = (TextView) findViewById(R.id.recording_time);

        ViewGroup rootView = (ViewGroup) findViewById(R.id.video_camera);
        LayoutInflater inflater = this.getLayoutInflater();
        if (!mIsVideoCaptureIntent) {
            View controlBar = inflater.inflate(
                    R.layout.camera_control, rootView);
            mLastPictureButton =
                    (ImageView) controlBar.findViewById(R.id.review_thumbnail);
            mThumbController = new ThumbnailController(
                    getResources(), mLastPictureButton, mContentResolver);
            mLastPictureButton.setOnClickListener(this);
            mThumbController.loadData(ImageManager.getLastVideoThumbPath());
            mSwitcher = ((Switcher) findViewById(R.id.camera_switch));
            mSwitcher.setOnSwitchListener(this);
            mSwitcher.addTouchView(findViewById(R.id.camera_switch_set));
        } else {
            View controlBar = inflater.inflate(
                    R.layout.attach_camera_control, rootView);
            controlBar.findViewById(R.id.btn_cancel).setOnClickListener(this);
            ImageView retake =
                    (ImageView) controlBar.findViewById(R.id.btn_retake);
            retake.setOnClickListener(this);
            retake.setImageResource(R.drawable.btn_ic_review_retake_video);
            controlBar.findViewById(R.id.btn_play).setOnClickListener(this);
            controlBar.findViewById(R.id.btn_done).setOnClickListener(this);
        }

        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setImageResource(R.drawable.btn_ic_video_record);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.requestFocus();

        // Make sure preview is started.
        try {
            startPreviewThread.join();
            if (mStartPreviewFail) {
                showCameraBusyAndFinish();
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

        initializeZoom();
        initializeTouchFocus();
        if (!mSmoothZoomSupported) {
            setStabilityChangeListener(mStabilityChangeListener);
        } else {
            setStabilityChangeListener(null);
        }
    }

    private void overrideHudSettings(final String videoEncoder,
            final String audioEncoder, final String videoDuration, final String videoSize) {
            if (mHeadUpDisplay != null && mGLRootView != null) {
            mGLRootView.queueEvent(new Runnable() {
            public void run() {
                mHeadUpDisplay.overrideSettings(
                       CameraSettings.KEY_VIDEO_ENCODER, videoEncoder);
                mHeadUpDisplay.overrideSettings(
                        CameraSettings.KEY_AUDIO_ENCODER, audioEncoder);
                mHeadUpDisplay.overrideSettings(
                        CameraSettings.KEY_VIDEO_DURATION, videoDuration);
                mHeadUpDisplay.overrideSettings(
                        CameraSettings.KEY_VIDEO_SIZE, videoSize);
            }});
          }
    }

    private void updateProfileInHud() {
        String quality = mPreferences.getString(
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.DEFAULT_VIDEO_QUALITY_VALUE);
        if(!"custom".equalsIgnoreCase(quality)){
            String videoEncoder = VIDEO_ENCODER_TABLE.getKey(mProfile.videoCodec);
            String audioEncoder = AUDIO_ENCODER_TABLE.getKey(mProfile.audioCodec);
            String videoSize = mProfile.videoFrameWidth + "x" + mProfile.videoFrameHeight;
            int videoDuration = mMaxVideoDurationInMs / 1000;
            if(videoDuration >= 60)
                videoDuration = videoDuration / 60;
            else
                videoDuration = -1;
            overrideHudSettings(videoEncoder, audioEncoder, String.valueOf(videoDuration), videoSize);
       } else {
            overrideHudSettings(null, null, null, null);
       }
    }

    private void changeHeadUpDisplayState() {
        // If the camera resumes behind the lock screen, the orientation
        // will be portrait. That causes OOM when we try to allocation GPU
        // memory for the GLSurfaceView again when the orientation changes. So,
        // we delayed initialization of HeadUpDisplay until the orientation
        // becomes landscape.
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && !mPausing && mGLRootView == null) {
            initializeHeadUpDisplay();
        } else if (mGLRootView != null) {
            finalizeHeadUpDisplay();
        }
    }

    private synchronized void initializeHeadUpDisplay() {
        FrameLayout frame = (FrameLayout) findViewById(R.id.frame);
        mGLRootView = new GLRootView(this);
        frame.addView(mGLRootView);

        mHeadUpDisplay = new CamcorderHeadUpDisplay(this, mParameters);
        CameraSettings settings = new CameraSettings(this, mParameters);

        PreferenceGroup group =
                settings.getPreferenceGroup(R.xml.video_preferences);
        if (mIsVideoCaptureIntent) {
            group = filterPreferenceScreenByIntent(group);
        }
        mHeadUpDisplay.initialize(this, group);
        mGLRootView.setContentPane(mHeadUpDisplay);
        mHeadUpDisplay.setListener(new MyHeadUpDisplayListener());
        mHeadUpDisplay.setOrientation(mLastOrientation);

        if (mParameters.isZoomSupported()) {
            mHeadUpDisplay.setZoomRatios(getZoomRatios());
            mHeadUpDisplay.setZoomIndex(mZoomValue);
            mHeadUpDisplay.setZoomListener(new ZoomController.ZoomListener() {
                public void onZoomChanged(
                        final int index, float ratio, boolean isMoving) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            onZoomValueChanged(index);
                        }
                    });
                }
            });
        }
        updateProfileInHud();
    }

    private void finalizeHeadUpDisplay() {
        mHeadUpDisplay.collapse();
        ((ViewGroup) mGLRootView.getParent()).removeView(mGLRootView);
        mGLRootView = null;
    }

    private void setOrientationIndicator(int degree) {
        ((RotateImageView) findViewById(
                R.id.review_thumbnail)).setDegree(degree);
        ((RotateImageView) findViewById(
                R.id.camera_switch_icon)).setDegree(degree);
        ((RotateImageView) findViewById(
                R.id.video_switch_icon)).setDegree(degree);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mIsVideoCaptureIntent) {
            mSwitcher.setSwitch(SWITCH_VIDEO);
        }
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW, mCurrentVideoUri);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_retake:
                discardCurrentVideoAndInitRecorder();
                break;
            case R.id.btn_play:
                startPlayVideoActivity();
                break;
            case R.id.btn_done:
                doReturnToCaller(true);
                break;
            case R.id.btn_cancel:
                stopVideoRecordingAndReturn(false);
                break;
            case R.id.review_thumbnail:
                if (!mMediaRecorderRecording) viewLastVideo();
                break;
        }
    }

    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        // Do nothing (everything happens in onShutterButtonClick).
    }

    private void onStopVideoRecording(boolean valid) {
        ((CamcorderHeadUpDisplay)mHeadUpDisplay).setVideoQualityControlsEnabled(true);
        if (mIsVideoCaptureIntent) {
            if (mQuickCapture) {
                stopVideoRecordingAndReturn(valid);
            } else {
                stopVideoRecordingAndShowAlert();
            }
        } else {
            stopVideoRecordingAndGetThumbnail();
            mHandler.sendEmptyMessage(INIT_RECORDER);
        }
    }

    public void onShutterButtonClick(ShutterButton button) {
        switch (button.getId()) {
            case R.id.shutter_button:
                if (mHeadUpDisplay.collapse()) return;

                if (mMediaRecorderRecording) {
                    onStopVideoRecording(true);
                } else if (mMediaRecorder != null) {
                    // If the click comes before recorder initialization, it is
                    // ignored. If users click the button during initialization,
                    // the event is put in the queue and record will be started
                    // eventually.
                    startVideoRecording();
                }
                mShutterButton.setEnabled(false);
                mHandler.sendEmptyMessageDelayed(
                        ENABLE_SHUTTER_BUTTON, SHUTTER_BUTTON_TIMEOUT);
                break;
        }
    }

    private void discardCurrentVideoAndInitRecorder() {
        deleteCurrentVideo();
        hideAlertAndInitializeRecorder();
    }

    private OnScreenHint mStorageHint;

    private void updateAndShowStorageHint(boolean mayHaveSd) {
        mStorageStatus = getStorageStatus(mayHaveSd);
        showStorageHint();
    }

    private void showStorageHint() {
        String errorMessage = null;
        switch (mStorageStatus) {
            case STORAGE_STATUS_NONE:
                errorMessage = getString(R.string.no_storage);
                break;
            case STORAGE_STATUS_LOW:
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

    private int getStorageStatus(boolean mayHaveSd) {
        long remaining = mayHaveSd ? getAvailableStorage() : NO_STORAGE_ERROR;
        if (remaining == NO_STORAGE_ERROR) {
            return STORAGE_STATUS_NONE;
        }
        return remaining < LOW_STORAGE_THRESHOLD
                ? STORAGE_STATUS_LOW
                : STORAGE_STATUS_OK;
    }

    private void readVideoPreferences() {
        String quality = mPreferences.getString(
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.getDefaultVideoQualityValue());

        if(!"custom".equalsIgnoreCase(quality)){

            boolean videoQualityHigh = CameraSettings.getVideoQuality(quality);

            // Set video quality.
            Intent intent = getIntent();
            if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
                int extraVideoQuality =
                        intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
                videoQualityHigh = (extraVideoQuality > 0);
            }

            // Set video duration limit. The limit is read from the preference,
            // unless it is specified in the intent.
            if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
                int seconds =
                        intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
                mMaxVideoDurationInMs = 1000 * seconds;
            } else {
                mMaxVideoDurationInMs =
                        CameraSettings.getVidoeDurationInMillis(quality);
            }
            mProfile = CameraSettings.getCamcorderProfile(videoQualityHigh);
        } else {
            mProfile = null;

            String videoEncoder = mPreferences.getString(
                        CameraSettings.KEY_VIDEO_ENCODER,
                        getString(R.string.pref_camera_videoencoder_default));
            int encoderId = VIDEO_ENCODER_TABLE.get(videoEncoder);
            if (encoderId != mVideoEncoder) {
                mHandler.sendEmptyMessage(RELOAD_HUD);
            }
            mVideoEncoder = encoderId;

            String audioEncoder = mPreferences.getString(
                        CameraSettings.KEY_AUDIO_ENCODER,
                        getString(R.string.pref_camera_audioencoder_default));

            String videoQuality = mPreferences.getString(
                     CameraSettings.KEY_VIDEO_SIZE,
                        getString(R.string.pref_camera_videosize_default));

            StringTokenizer tokenizer = null;
            if(videoQuality != null &&
                        ( tokenizer = new StringTokenizer(
                              videoQuality ,"x")).countTokens() == 2){
                mVideoWidth = Integer.parseInt(tokenizer.nextToken());
                mVideoHeight = Integer.parseInt(tokenizer.nextToken());
                mVideoBitrate = VIDEOQUALITY_BITRATE_TABLE.get(videoQuality);
            } else {
                mVideoWidth = 320;
                mVideoHeight = 240;
                mVideoBitrate = 320000;
            }

            mVideoEncoder = VIDEO_ENCODER_TABLE.get(videoEncoder);
            mAudioEncoder = AUDIO_ENCODER_TABLE.get(audioEncoder);

            String minutesStr = mPreferences.getString(CameraSettings.KEY_VIDEO_DURATION,
                              CameraSettings.DEFAULT_VIDEO_DURATION_VALUE+"");

            int minutes = -1;
            try{
	        minutes = Integer.parseInt(minutesStr);
	    }catch(NumberFormatException npe){
	        // use default value continue
	        minutes = CameraSettings.DEFAULT_VIDEO_DURATION_VALUE;
        }

            if (minutes == -1) {
                // This is a special case: the value -1 means we want to use the
                // device-dependent duration for MMS messages. The value is
                // represented in seconds.

                mMaxVideoDurationInMs =
                       CameraSettings.getVidoeDurationInMillis("mms");

            } else {
                // 1 minute = 60000ms
                mMaxVideoDurationInMs = 60000 * minutes;
            }
        }
        updateProfileInHud();
    }

    private void resizeForPreviewAspectRatio() {
        if(mProfile != null){
            mPreviewFrameLayout.setAspectRatio(
                     (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
        } else {
            mPreviewFrameLayout.setAspectRatio( ((double) mVideoWidth) / mVideoHeight);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPausing = false;
        mOrientationListener.enable();
        mSurfaceView.setVisibility(View.VISIBLE);
        readVideoPreferences();
        resizeForPreviewAspectRatio();
        if (!mPreviewing && !mStartPreviewFail) {
            try {
                startPreview();
            } catch (CameraHardwareException e) {
                showCameraBusyAndFinish();
                return;
            }
        }
        changeHeadUpDisplayState();
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
        mStorageStatus = getStorageStatus(true);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                showStorageHint();
            }
        }, 200);

        if (mSurfaceHolder != null) {
            mHandler.sendEmptyMessage(INIT_RECORDER);
        }
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void startPreview() throws CameraHardwareException {
        Log.d(TAG, "startPreview");

        if (mPreviewing) {
            // After recording a video, preview is not stopped. So just return.
            return;
        }

        if (mCameraDevice == null) {
            // If the activity is paused and resumed, camera device has been
            // released and we need to open the camera.
            mCameraDevice = CameraHolder.instance().open();
        }

        mCameraDevice.lock();
        setCameraParameters();
        setPreviewDisplay(mSurfaceHolder);

        // Disable orientation effect at 720p until frame is removed
        if (CameraSettings.is720p(mParameters)) {
            mOrientationListener.disable();
        }

        try {
            mCameraDevice.startPreview();
            mPreviewing = true;
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
    }

    private void closeCamera() {
        Log.v(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        releaseMediaRecorder();
        mCameraDevice.lock();
        CameraHolder.instance().release();
        mCameraDevice = null;
        mPreviewing = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPausing = true;
        mOrientationListener.disable();
        changeHeadUpDisplayState();

        // Hide the preview now. Otherwise, the preview may be rotated during
        // onPause and it is annoying to users.
        mSurfaceView.setVisibility(View.INVISIBLE);

        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        if (mMediaRecorderRecording) {
            if (mIsVideoCaptureIntent) {
                stopVideoRecording();
                showAlert();
            } else {
                stopVideoRecordingAndGetThumbnail();
            }
        } else {
            stopVideoRecording();
        }
        closeCamera();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        resetScreenOn();

        if (!mIsVideoCaptureIntent) {
            mThumbController.storeData(ImageManager.getLastVideoThumbPath());
        }

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

        mHandler.removeMessages(INIT_RECORDER);

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
        } else if (mHeadUpDisplay == null || !mHeadUpDisplay.collapse()) {
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
                if (mMediaRecorderRecording) {
                    onStopVideoRecording(true);
                    return true;
                }
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
        Log.d(TAG, "surfaceChanged");
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            Log.d(TAG, "holder.getSurface() == null");
            return;
        }

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

        if (mMediaRecorderRecording) {
            stopVideoRecording();
        }

        // Set preview display if the surface is being created. Preview was
        // already started.
        if (holder.isCreating()) {
            setPreviewDisplay(holder);
            mCameraDevice.unlock();
            mHandler.sendEmptyMessage(INIT_RECORDER);
        } else {
            mCameraDevice.unlock();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
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
        setResult(resultCode, resultIntent);
        finish();
    }

    /**
     * Returns
     *
     * @return number of bytes available, or an ERROR code.
     */
    private static long getAvailableStorage() {
        try {
            if (!ImageManager.hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {
                String storageDirectory =
                        Environment.getExternalStorageDirectory().toString();
                StatFs stat = new StatFs(storageDirectory);
                return (long) stat.getAvailableBlocks()
                        * (long) stat.getBlockSize();
            }
        } catch (RuntimeException ex) {
            // if we can't stat the filesystem then we don't know how many
            // free bytes exist. It might be zero but just leave it
            // blank since we really don't know.
            return CANNOT_STAT_ERROR;
        }
    }

    private void cleanupEmptyFile() {
        if (mCameraVideoFilename != null) {
            File f = new File(mCameraVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mCameraVideoFilename);
                mCameraVideoFilename = null;
            }
        }
    }

    // Prepares media recorder.
    private void initializeRecorder() {
        Log.v(TAG, "initializeRecorder");
        if (mMediaRecorder != null) return;

        // We will call initializeRecorder() again when the alert is hidden.
        // If the mCameraDevice is null, then this activity is going to finish
        if (isAlertVisible() || mCameraDevice == null) return;

        Intent intent = getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mCameraVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw")
                            .getFileDescriptor();
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        mMediaRecorder = new MediaRecorder();

        mMediaRecorder.setCamera(mCameraDevice);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if(mProfile != null){
            mMediaRecorder.setProfile(mProfile);
            mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
        }
        else {

            mMediaRecorder.setOutputFormat(OUTPUT_FORMAT_TABLE.get(mOutputFormat));
            mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
            mMediaRecorder.setVideoFrameRate(mVideoFps);
            mMediaRecorder.setVideoSize(mVideoWidth, mVideoHeight);
            mMediaRecorder.setVideoEncodingBitRate(mVideoBitrate);
            mMediaRecorder.setVideoEncoder(mVideoEncoder);
            mMediaRecorder.setAudioEncoder(mAudioEncoder);
            Log.v(TAG, "Custom format " +
                       " output format " + OUTPUT_FORMAT_TABLE.get(mOutputFormat) +
                       " Video Fps " +  mVideoFps +
                       " Video Bitrate " + mVideoBitrate +
                       " Max Video Duration "  + mMaxVideoDurationInMs+
                       " Video Encoder "  + mVideoEncoder +
                       " Audio Encoder "  + mAudioEncoder);
        }

        updateAndShowStorageHint(true);
        // Set output file.
        if (mStorageStatus != STORAGE_STATUS_OK) {
            mMediaRecorder.setOutputFile("/dev/null");
        } else {
            // Try Uri in the intent first. If it doesn't exist, use our own
            // instead.
            if (mCameraVideoFileDescriptor != null) {
                mMediaRecorder.setOutputFile(mCameraVideoFileDescriptor);
            } else {
                createVideoPath();
                mMediaRecorder.setOutputFile(mCameraVideoFilename);
            }
        }

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        // Set maximum file size.
        // remaining >= LOW_STORAGE_THRESHOLD at this point, reserve a quarter
        // of that to make it more likely that recording can complete
        // successfully.
        long maxFileSize = getAvailableStorage() - LOW_STORAGE_THRESHOLD / 4;
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

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mCameraVideoFilename);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }
        mMediaRecorderRecording = false;

        resetFocusIndicator();
        if (mFocusRectangle != null) {
            mFocusRectangle.clear();
        }

        // Update the last video thumbnail.
        if (!mIsVideoCaptureIntent) {
            if (!mThumbController.isUriValid()) {
                updateLastVideo();
            }
            mThumbController.updateDisplayIfNeeded();
        }
    }

    private void releaseMediaRecorder() {
        Log.d(TAG, "releaseMediaRecorder");
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private void createVideoPath() {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String filename;
        if (mProfile == null) {
            filename = title + "." + mOutputFormat;
        } else {
            filename = title + (MediaRecorder.OutputFormat.MPEG_4 == mProfile.fileFormat ? ".mp4" : ".3gp"); // Used when emailing.
        }

        String cameraDirPath = ImageManager.CAMERA_IMAGE_BUCKET_NAME;
        String filePath = cameraDirPath + "/" + filename;
        File cameraDir = new File(cameraDirPath);
        cameraDir.mkdirs();
        ContentValues values = new ContentValues(7);
        values.put(Video.Media.TITLE, title);
        values.put(Video.Media.DISPLAY_NAME, filename);
        values.put(Video.Media.DATE_TAKEN, dateTaken);
        if (mProfile == null) {
            values.put(Video.Media.MIME_TYPE, "video/" + ("3gp".equals(mOutputFormat) ? "3gpp" : "mp4"));
        } else {
            values.put(Video.Media.MIME_TYPE, "video/" +
                    (MediaRecorder.OutputFormat.MPEG_4 == mProfile.fileFormat ? "mp4" : "3gpp"));
        }
        values.put(Video.Media.DATA, filePath);
        mCameraVideoFilename = filePath;
        Log.v(TAG, "Current camera video filename: " + mCameraVideoFilename);
        mCurrentVideoValues = values;
    }

    private void registerVideo() {
        if (mCameraVideoFileDescriptor == null) {
            Uri videoTable = Uri.parse("content://media/external/video/media");
            mCurrentVideoValues.put(Video.Media.SIZE,
                    new File(mCurrentVideoFilename).length());
            try {
                mCurrentVideoUri = mContentResolver.insert(videoTable,
                        mCurrentVideoValues);
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
        if (mCurrentVideoFilename != null) {
            deleteVideoFile(mCurrentVideoFilename);
            mCurrentVideoFilename = null;
        }
        if (mCurrentVideoUri != null) {
            mContentResolver.delete(mCurrentVideoUri, null, null);
            mCurrentVideoUri = null;
        }
        updateAndShowStorageHint(true);
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private void addBaseMenuItems(final Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, false, new Runnable() {
            public void run() {
                switchToCameraMode();
            }
        });

        if (CameraSwitch.hasCameraSwitch()) {
            MenuHelper.addSwitchDeviceMenuItem(menu, new Runnable() {
                public void run() {
                    switchCameraDevice(CameraSettings.isMainCamera());
                }
            });
        }

        MenuItem gallery = menu.add(Menu.NONE, Menu.NONE,
                MenuHelper.POSITION_GOTO_GALLERY,
                R.string.camera_gallery_photos_text)
                .setOnMenuItemClickListener(
                    new OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            gotoGallery();
                            return true;
                        }
                    });
        gallery.setIcon(android.R.drawable.ic_menu_gallery);
        mGalleryItems.add(gallery);
        mOptionsMenu = menu;
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
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecording();
            updateAndShowStorageHint(true);
        }
    }

    // from MediaRecorder.OnInfoListener
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording(true);
        } else if (what
                == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (mMediaRecorderRecording) onStopVideoRecording(true);

            // Show the toast.
            Toast.makeText(VideoCamera.this, R.string.video_reach_size_limit,
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

    private void startVideoRecording() {
        Log.v(TAG, "startVideoRecording");
        if (!mMediaRecorderRecording) {

            if (mStorageStatus != STORAGE_STATUS_OK) {
                Log.v(TAG, "Storage issue, ignore the start request");
                return;
            }

            // Check mMediaRecorder to see whether it is initialized or not.
            if (mMediaRecorder == null) {
                Log.e(TAG, "MediaRecorder is not initialized.");
                return;
            }

            pauseAudioPlayback();

            try {
                mMediaRecorder.setOnErrorListener(this);
                mMediaRecorder.setOnInfoListener(this);
                mMediaRecorder.start(); // Recording is now started
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start media recorder. ", e);
                return;
            }
            ((CamcorderHeadUpDisplay)mHeadUpDisplay).setVideoQualityControlsEnabled(false);

            mMediaRecorderRecording = true;
            mRecordingStartTime = SystemClock.uptimeMillis();
            updateRecordingIndicator(false);
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            updateRecordingTime();
            keepScreenOn();
        }
    }

    private void updateRecordingIndicator(boolean showRecording) {
        int drawableId =
                showRecording ? R.drawable.btn_ic_video_record
                        : R.drawable.btn_ic_video_record_stop;
        Drawable drawable = getResources().getDrawable(drawableId);
        mShutterButton.setImageDrawable(drawable);
    }

    private void stopVideoRecordingAndGetThumbnail() {
        stopVideoRecording();
        acquireVideoThumb();
    }

    private void stopVideoRecordingAndReturn(boolean valid) {
        stopVideoRecording();
        doReturnToCaller(valid);
    }

    private void stopVideoRecordingAndShowAlert() {
        stopVideoRecording();
        showAlert();
    }

    private void showAlert() {
        fadeOut(findViewById(R.id.shutter_button));
        if (mCurrentVideoFilename != null) {
            mVideoFrame.setImageBitmap(
                    ThumbnailUtils.createVideoThumbnail(mCurrentVideoFilename,
                             Video.Thumbnails.MINI_KIND));
            mVideoFrame.setVisibility(View.VISIBLE);
        }
        int[] pickIds = {R.id.btn_retake, R.id.btn_done, R.id.btn_play};
        for (int id : pickIds) {
            View button = findViewById(id);
            fadeIn(((View) button.getParent()));
        }
    }

    private void hideAlert() {
        mVideoFrame.setVisibility(View.INVISIBLE);
        fadeIn(findViewById(R.id.shutter_button));
        int[] pickIds = {R.id.btn_retake, R.id.btn_done, R.id.btn_play};
        for (int id : pickIds) {
            View button = findViewById(id);
            fadeOut(((View) button.getParent()));
        }
    }

    private static void fadeIn(View view) {
        view.setVisibility(View.VISIBLE);
        Animation animation = new AlphaAnimation(0F, 1F);
        animation.setDuration(500);
        view.startAnimation(animation);
    }

    private static void fadeOut(View view) {
        view.setVisibility(View.INVISIBLE);
        Animation animation = new AlphaAnimation(1F, 0F);
        animation.setDuration(500);
        view.startAnimation(animation);
    }

    private boolean isAlertVisible() {
        return this.mVideoFrame.getVisibility() == View.VISIBLE;
    }

    private void viewLastVideo() {
        Intent intent = null;
        if (mThumbController.isUriValid()) {
            intent = new Intent(Util.REVIEW_ACTION, mThumbController.getUri());
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                try {
                    intent = new Intent(Intent.ACTION_VIEW, mThumbController.getUri());
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "review video fail", e);
                }
            }
        } else {
            Log.e(TAG, "Can't view last video.");
        }
    }

    private void stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        boolean needToRegisterRecording = false;
        if (mMediaRecorderRecording || mMediaRecorder != null) {
            if (mMediaRecorderRecording && mMediaRecorder != null) {
                try {
                    mMediaRecorder.setOnErrorListener(null);
                    mMediaRecorder.setOnInfoListener(null);
                    mMediaRecorder.stop();
                } catch (RuntimeException e) {
                    Log.e(TAG, "stop fail: " + e.getMessage());
                }
                mHeadUpDisplay.setEnabled(true);
                mCurrentVideoFilename = mCameraVideoFilename;
                Log.v(TAG, "Setting current video filename: "
                        + mCurrentVideoFilename);
                needToRegisterRecording = true;
                mMediaRecorderRecording = false;
            }
            releaseMediaRecorder();
            updateRecordingIndicator(true);
            mRecordingTimeView.setVisibility(View.GONE);
            keepScreenOnAwhile();
        }
        if (needToRegisterRecording && mStorageStatus == STORAGE_STATUS_OK && mCameraVideoFilename != null) {
            registerVideo();
        }

        /*Make the videoCameraFilename null only if the respective file does not exist*/
        try {
            File f = new File(mCameraVideoFilename);
            if(!f.exists())
                mCameraVideoFilename = null;
        } catch(NullPointerException e) {
            Log.e(TAG, "StopVideoRecording: mCameraVideoFilename is null");
        }
        mCameraVideoFileDescriptor = null;
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

    private void hideAlertAndInitializeRecorder() {
        hideAlert();
        mHandler.sendEmptyMessage(INIT_RECORDER);
    }

    private void acquireVideoThumb() {
        Bitmap videoFrame = ThumbnailUtils.createVideoThumbnail(
                mCurrentVideoFilename, Video.Thumbnails.MINI_KIND);
        mThumbController.setData(mCurrentVideoUri, videoFrame);
    }

    private static ImageManager.DataLocation dataLocation() {
        return ImageManager.DataLocation.EXTERNAL;
    }

    private void updateLastVideo() {
        IImageList list = ImageManager.makeImageList(
                        mContentResolver,
                        dataLocation(),
                        ImageManager.INCLUDE_VIDEOS,
                        ImageManager.SORT_ASCENDING,
                        ImageManager.CAMERA_IMAGE_BUCKET_ID);
        int count = list.getCount();
        if (count > 0) {
            IImage image = list.getImageAt(count - 1);
            Uri uri = image.fullSizeImageUri();
            mThumbController.setData(uri, image.miniThumbBitmap());
        } else {
            mThumbController.setData(null, null);
        }
        list.close();
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

        long next_update_delay = 1000 - (delta % 1000);
        long seconds;
        if (countdownRemainingTime) {
            delta = Math.max(0, mMaxVideoDurationInMs - delta);
            seconds = (delta + 999) / 1000;
        } else {
            seconds = delta / 1000; // round to nearest
        }

        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        String secondsString = Long.toString(remainderSeconds);
        if (secondsString.length() < 2) {
            secondsString = "0" + secondsString;
        }
        String minutesString = Long.toString(remainderMinutes);
        if (minutesString.length() < 2) {
            minutesString = "0" + minutesString;
        }
        String text = minutesString + ":" + secondsString;
        if (hours > 0) {
            String hoursString = Long.toString(hours);
            if (hoursString.length() < 2) {
                hoursString = "0" + hoursString;
            }
            text = hoursString + ":" + text;
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

        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, next_update_delay);
    }

    private void setCameraParameters() {
        mParameters = mCameraDevice.getParameters();

        if(mProfile != null) {
            mParameters.setPreviewSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
            mParameters.setPreviewFrameRate(mProfile.videoFrameRate);
        } else {
            mParameters.setPreviewSize(mVideoWidth, mVideoHeight);
            mParameters.setPreviewFrameRate(mVideoFps);
        }
        updateProfileInHud();

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

        mFocusMode = mPreferences.getString(
                CameraSettings.KEY_VIDEOCAMERA_FOCUS_MODE,
                getString(R.string.pref_camera_focusmode_default));
        mParameters.setFocusMode(mFocusMode);
        if (Parameters.FOCUS_MODE_AUTO.equals(mFocusMode) && !mSmoothZoomSupported) {
            setStabilityChangeListener(mStabilityChangeListener);
        } else if ("touch".equals(mFocusMode) ||
                Parameters.FOCUS_MODE_INFINITY.equals(mFocusMode) ||
                mSmoothZoomSupported == true) {
            setStabilityChangeListener(null);
        }

        setCommonParameters();
        setWhiteBalance();

        CameraSettings.setCamMode(mParameters, CameraSettings.VIDEO_MODE);
        setCameraHardwareParameters();
    }

    protected void setCameraHardwareParameters() {
        CameraSettings.dumpParameters(mParameters);
        if (mMediaRecorder != null) {
            mMediaRecorder.setCameraParameters(mParameters.flatten());
        } else {
            mCameraDevice.setParameters(mParameters);
        }
    }

    private boolean switchToCameraMode() {
        if (isFinishing() || mMediaRecorderRecording) return false;
        MenuHelper.gotoCameraMode(this);
        finish();
        return true;
    }

    public boolean onSwitchChanged(Switcher source, boolean onOff) {
        if (onOff == SWITCH_CAMERA) {
            return switchToCameraMode();
        } else {
            return true;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);

        // If the camera resumes behind the lock screen, the orientation
        // will be portrait. That causes OOM when we try to allocation GPU
        // memory for the GLSurfaceView again when the orientation changes. So,
        // we delayed initialization of HeadUpDisplay until the orientation
        // becomes landscape.
        changeHeadUpDisplayState();
    }

    private void resetCameraParameters() {

        // We need to restart the preview if preview size is changed.
        Size size = mParameters.getPreviewSize();
        boolean sizeChanged = true;
        if(mProfile != null){
            sizeChanged = size.width != mProfile.videoFrameWidth
                            || size.height != mProfile.videoFrameHeight;
        } else {
            sizeChanged = size.width != mVideoWidth || size.height != mVideoHeight;
        }
        if (sizeChanged) {
            // It is assumed media recorder is released before
            // onSharedPreferenceChanged, so we can close the camera here.
            mCameraDevice.stopPreview();
            releaseMediaRecorder();
            closeCamera();
            try {
                resizeForPreviewAspectRatio();
                mHandler.sendEmptyMessage(RELOAD_HUD);
                startPreview(); // Parameters will be set in startPreview().
            } catch (CameraHardwareException e) {
                showCameraBusyAndFinish();
            }
        } else {
            setCameraParameters();
        }
    }

    private boolean switchCameraDevice(boolean switchToSecondary) {
        if (isFinishing() || mMediaRecorderRecording) return false;
        CameraHolder.instance().setCameraNode(switchToSecondary ?
                CameraSwitch.SWITCH_CAMERA_SECONDARY : CameraSwitch.SWITCH_CAMERA_MAIN);
        MenuHelper.gotoVideoMode(this);
        return true;
    }

    protected void onZoomValueChanged(int index) {
        mZoomValue = index;
        mParameters.setZoom(index);
        setCameraHardwareParameters();
    }

    public void onSizeChanged() {
        // TODO: update the content on GLRootView
    }

    private class MyHeadUpDisplayListener implements HeadUpDisplay.Listener {
        public void onSharedPreferencesChanged() {
            mHandler.post(new Runnable() {
                public void run() {
                    VideoCamera.this.onSharedPreferencesChanged();
                }
            });
        }

        public void onRestorePreferencesClicked() {
            mHandler.post(new Runnable() {
                public void run() {
                    VideoCamera.this.onRestorePreferencesClicked();
                }
            });
        }

        public void onPopupWindowVisibilityChanged(final int visibility) {
            mHandler.post(new Runnable() {
                public void run() {
                    VideoCamera.this.onPopupWindowVisibilityChanged(visibility);
                }
            });
        }
    }

    private void onPopupWindowVisibilityChanged(int visibility) {
        if (visibility != GLView.VISIBLE && !mPausing && mSurfaceHolder != null) {
           initializeRecorder();
         }
    }

    private void onRestorePreferencesClicked() {
        Runnable runnable = new Runnable() {
            public void run() {
                mHeadUpDisplay.restorePreferences(mParameters);
            }
        };
        MenuHelper.confirmAction(this,
                getString(R.string.confirm_restore_title),
                getString(R.string.confirm_restore_message),
                runnable);
    }

    private void onSharedPreferencesChanged() {
        // ignore the events after "onPause()" or preview has not started yet
        if (mPausing) return;
        synchronized (mPreferences) {
            readVideoPreferences();
            // If mCameraDevice is not ready then we can set the parameter in
            // startPreview().
            if (mCameraDevice == null) return;
            resetCameraParameters();
        }
    }

    @Override
    protected int getCameraMode() {
        return CameraSettings.VIDEO_MODE;
    }

}
