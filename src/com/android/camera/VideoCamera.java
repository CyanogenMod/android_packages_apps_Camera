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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.media.ThumbnailUtil;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.camera.PreviewFrameLayout.OnSizeChangedListener;
import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * The Camcorder activity.
 */
public class VideoCamera extends Activity implements View.OnClickListener,
        ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback,
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener,
        Switcher.OnSwitchListener, OnSharedPreferenceChangeListener,
        OnScreenSettings.OnVisibilityChangedListener,
        PreviewFrameLayout.OnSizeChangedListener  {

    private static final String TAG = "videocamera";

    private static final int INIT_RECORDER = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final long NO_STORAGE_ERROR = -1L;
    private static final long CANNOT_STAT_ERROR = -2L;
    private static final long LOW_STORAGE_THRESHOLD = 512L * 1024L;

    private static final int STORAGE_STATUS_OK = 0;
    private static final int STORAGE_STATUS_LOW = 1;
    private static final int STORAGE_STATUS_NONE = 2;

    private static final boolean SWITCH_CAMERA = true;
    private static final boolean SWITCH_VIDEO = false;

    private SharedPreferences mPreferences;

    private PreviewFrameLayout mPreviewFrameLayout;
    private SurfaceView mVideoPreview;
    private SurfaceHolder mSurfaceHolder = null;
    private ImageView mVideoFrame;

    private boolean mIsVideoCaptureIntent;
    // mLastPictureButton and mThumbController
    // are non-null only if mIsVideoCaptureIntent is true.
    private ImageView mLastPictureButton;
    private ThumbnailController mThumbController;
    private boolean mStartPreviewFail = false;

    private int mStorageStatus = STORAGE_STATUS_OK;

    private MediaRecorder mMediaRecorder;
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
    private IconIndicator mWhitebalanceIndicator;

    private MediaRecorderProfile mProfile;

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;

    boolean mPausing = false;
    boolean mPreviewing = false; // True if preview is started.

    private ContentResolver mContentResolver;

    private ShutterButton mShutterButton;
    private TextView mRecordingTimeView;
    private View mGripper;
    private Switcher mSwitcher;
    private boolean mRecordingTimeCountsDown = false;

    private final ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    private final Handler mHandler = new MainHandler();
    private Parameters mParameters;
    private OnScreenSettings mSettings;

    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

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
                updateAndShowStorageHint(false);
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                updateAndShowStorageHint(true);
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

    private static String createName(long dateTaken) {
        return DateFormat.format("yyyy-MM-dd kk.mm.ss", dateTaken).toString();
    }

    private void showCameraBusyAndFinish() {
        Resources ress = getResources();
        Util.showFatalErrorAndFinish(VideoCamera.this,
                ress.getString(R.string.camera_error_title),
                ress.getString(R.string.cannot_connect_camera));
    }

    /** Called with the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        CameraSettings.upgradePreferences(mPreferences);

        readVideoPreferences();

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

        mVideoPreview = (SurfaceView) findViewById(R.id.camera_preview);
        mVideoFrame = (ImageView) findViewById(R.id.video_frame);

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mVideoPreview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mIsVideoCaptureIntent = isVideoCaptureIntent();
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
        mGripper = findViewById(R.id.btn_gripper);
        mGripper.setOnTouchListener(new GripperTouchListener());

        mWhitebalanceIndicator =
                (IconIndicator) findViewById(R.id.whitebalance_icon);

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
        removeUnsupportedIndicators();
    }

    private void removeUnsupportedIndicators() {
        if (mParameters.getSupportedWhiteBalance() == null) {
            mWhitebalanceIndicator.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mIsVideoCaptureIntent) {
            mSwitcher.setSwitch(SWITCH_VIDEO);
        }
    }

    private void startShareVideoActivity() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("video/3gpp");
        intent.putExtra(Intent.EXTRA_STREAM, mCurrentVideoUri);
        try {
            startActivity(Intent.createChooser(intent,
                    getText(R.string.sendVideo)));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(VideoCamera.this, R.string.no_way_to_share_video,
                    Toast.LENGTH_SHORT).show();
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
                stopVideoRecording();
                doReturnToCaller(false);
                break;
            case R.id.discard: {
                Runnable deleteCallback = new Runnable() {
                    public void run() {
                        discardCurrentVideoAndInitRecorder();
                    }
                };
                MenuHelper.deleteVideo(this, deleteCallback);
                break;
            }
            case R.id.share: {
                startShareVideoActivity();
                break;
            }
            case R.id.play: {
                doPlayCurrentVideo();
                break;
            }
            case R.id.review_thumbnail: {
                stopVideoRecordingAndShowReview();
                break;
            }
        }
    }

    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        // Do nothing (everything happens in onShutterButtonClick).
    }

    public void onShutterButtonClick(ShutterButton button) {
        switch (button.getId()) {
            case R.id.shutter_button:
                if (mMediaRecorderRecording) {
                    if (mIsVideoCaptureIntent) {
                        stopVideoRecordingAndShowAlert();
                    } else {
                        stopVideoRecordingAndGetThumbnail();
                        initializeRecorder();
                    }
                } else if (mMediaRecorder != null) {
                    // If the click comes before recorder initialization, it is
                    // ignored. If users click the button during initialization,
                    // the event is put in the queue and record will be started
                    // eventually.
                    startVideoRecording();
                }
                break;
        }
    }

    private void doPlayCurrentVideo() {
        Log.v(TAG, "Playing current video: " + mCurrentVideoUri);
        Intent intent = new Intent(Intent.ACTION_VIEW, mCurrentVideoUri);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
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
        /* Wysie: Commented out for now
        boolean videoQualityHigh =
                getBooleanPreference(CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.DEFAULT_VIDEO_QUALITY_VALUE);
        */

        int videoQuality = getIntPreference(CameraSettings.KEY_VIDEO_QUALITY, CameraSettings.DEFAULT_VIDEO_QUALITY_VALUE);
        
        // Set video quality.
        Intent intent = getIntent();
        /* Wysie: Commented out for now
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
                    intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            videoQualityHigh = (extraVideoQuality > 0);
        }
        */

        // Set video duration limit. The limit is read from the preference,
        // unless it is specified in the intent.
        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            int seconds =
                    intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mMaxVideoDurationInMs = 1000 * seconds;
        } else {
            int minutes = getIntPreference(CameraSettings.KEY_VIDEO_DURATION,
                            CameraSettings.DEFAULT_VIDEO_DURATION_VALUE);
            if (minutes == -1) {
                // This is a special case: the value -1 means we want to use the
                // device-dependent duration for MMS messages. The value is
                // represented in seconds.
                mMaxVideoDurationInMs =
                        1000 * CameraSettings.MMS_VIDEO_DURATION;
            } else {
                // 1 minute = 60000ms
                mMaxVideoDurationInMs = 60000 * minutes;
            }
        }

        mProfile = new MediaRecorderProfile(videoQuality);
    }

    private void resizeForPreviewAspectRatio() {
        mPreviewFrameLayout.setAspectRatio(
                (double) mProfile.mVideoWidth / mProfile.mVideoHeight);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPausing = false;

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
        Log.v(TAG, "startPreview");
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

        try {
            mCameraDevice.startPreview();
            mPreviewing = true;
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }

        // If setPreviewDisplay has been set with a valid surface, unlock now.
        // If surface is null, unlock later. Otherwise, setPreviewDisplay in
        // surfaceChanged will fail.
        if (mSurfaceHolder != null) {
            mCameraDevice.unlock();
        }
    }

    private void closeCamera() {
        Log.v(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        // If we don't lock the camera, release() will fail.
        mCameraDevice.lock();
        CameraHolder.instance().release();
        mCameraDevice = null;
        mPreviewing = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        mPausing = true;

        if (mSettings != null && mSettings.isVisible()) {
            mSettings.setVisible(false);
        }

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
            mShutterButton.performClick();
            return;
        }
        super.onBackPressed();
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
                    mShutterButton.performClick();
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
            case KeyEvent.KEYCODE_MENU:
                if (this.mIsVideoCaptureIntent) {
                    showOnScreenSettings();
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
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

    private void doReturnToCaller(boolean success) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (success) {
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

    private android.hardware.Camera mCameraDevice;

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
        mMediaRecorder.setOutputFormat(mProfile.mOutputFormat);
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);

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

        // Use the same frame rate for both, since internally
        // if the frame rate is too large, it can cause camera to become
        // unstable. We need to fix the MediaRecorder to disable the support
        // of setting frame rate for now.
        mMediaRecorder.setVideoFrameRate(mProfile.mVideoFps);
        mMediaRecorder.setVideoSize(
                mProfile.mVideoWidth, mProfile.mVideoHeight);
        mMediaRecorder.setParameters(String.format(
                "video-param-encoding-bitrate=%d", mProfile.mVideoBitrate));
        mMediaRecorder.setParameters(String.format(
                "audio-param-encoding-bitrate=%d", mProfile.mAudioBitrate));
        mMediaRecorder.setParameters(String.format(
                "audio-param-number-of-channels=%d", mProfile.mAudioChannels));
        mMediaRecorder.setParameters(String.format(
                "audio-param-sampling-rate=%d", mProfile.mAudioSamplingRate));
        mMediaRecorder.setVideoEncoder(mProfile.mVideoEncoder);
        mMediaRecorder.setAudioEncoder(mProfile.mAudioEncoder);
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

        // Update the last video thumbnail.
        if (!mIsVideoCaptureIntent) {
            if (!mThumbController.isUriValid()) {
                updateLastVideo();
            }
            mThumbController.updateDisplayIfNeeded();
        }
    }

    private void releaseMediaRecorder() {
        Log.v(TAG, "Releasing media recorder.");
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private int getIntPreference(String key, int defaultValue) {
        String s = mPreferences.getString(key, "");
        int result = defaultValue;
        try {
            result = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            // Ignore, result is already the default value.
        }
        return result;
    }

    private boolean getBooleanPreference(String key, boolean defaultValue) {
        return getIntPreference(key, defaultValue ? 1 : 0) != 0;
    }

    private void createVideoPath() {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String displayName = title + ".3gp"; // Used when emailing.
        String cameraDirPath = ImageManager.CAMERA_IMAGE_BUCKET_NAME;
        File cameraDir = new File(cameraDirPath);
        cameraDir.mkdirs();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                getString(R.string.video_file_name_format));
        Date date = new Date(dateTaken);
        String filepart = dateFormat.format(date);
        String filename = cameraDirPath + "/" + filepart + ".3gp";
        ContentValues values = new ContentValues(7);
        values.put(Video.Media.TITLE, title);
        values.put(Video.Media.DISPLAY_NAME, displayName);
        values.put(Video.Media.DATE_TAKEN, dateTaken);
        values.put(Video.Media.MIME_TYPE, "video/3gpp");
        values.put(Video.Media.DATA, filename);
        mCameraVideoFilename = filename;
        Log.v(TAG, "Current camera video filename: " + mCameraVideoFilename);
        mCurrentVideoValues = values;
    }

    private void registerVideo() {
        if (mCameraVideoFileDescriptor == null) {
            Uri videoTable = Uri.parse("content://media/external/video/media");
            mCurrentVideoValues.put(Video.Media.SIZE,
                    new File(mCurrentVideoFilename).length());
            mCurrentVideoUri = mContentResolver.insert(videoTable,
                    mCurrentVideoValues);
            Log.v(TAG, "Current video URI: " + mCurrentVideoUri);
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

    private void addBaseMenuItems(Menu menu) {
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

        MenuItem item = menu.add(Menu.NONE, Menu.NONE,
                MenuHelper.POSITION_CAMERA_SETTING, R.string.settings)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                showOnScreenSettings();
                return true;
            }});
        item.setIcon(android.R.drawable.ic_menu_preferences);
    }

    private void showOnScreenSettings() {
        if (mSettings == null) {
            mSettings = new OnScreenSettings(
                    findViewById(R.id.camera_preview));
            CameraSettings helper = new CameraSettings(this, mParameters);
            PreferenceScreen screen = helper
                    .getPreferenceScreen(R.xml.video_preferences);
            if (mIsVideoCaptureIntent) {
                screen = filterPreferenceScreenByIntent(screen);
            }

            mSettings.setPreferenceScreen(screen);
            mSettings.setOnVisibilityChangedListener(this);
        }
        mSettings.setVisible(true);
    }

    private PreferenceScreen filterPreferenceScreenByIntent(
            PreferenceScreen screen) {
        Intent intent = getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_QUALITY);
        }

        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            CameraSettings.removePreferenceFromScreen(screen,
                    CameraSettings.KEY_VIDEO_DURATION);
        }
        return screen;
    }

    private class GripperTouchListener implements View.OnTouchListener {
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_UP:
                    showOnScreenSettings();
                    return true;
            }
            return false;
        }
    }

    public void onVisibilityChanged(boolean visible) {
        // At this point, we are not recording.
        mGripper.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        if (visible) {
            releaseMediaRecorder();
            mPreferences.registerOnSharedPreferenceChangeListener(this);
        } else {
            initializeRecorder();
            mPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
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
            mShutterButton.performClick();
        } else if (what
                == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            mShutterButton.performClick();
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
            mMediaRecorderRecording = true;
            mRecordingStartTime = SystemClock.uptimeMillis();
            updateRecordingIndicator(false);
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            updateRecordingTime();
            keepScreenOn();
            mGripper.setVisibility(View.INVISIBLE);
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

    private void stopVideoRecordingAndShowAlert() {
        stopVideoRecording();
        showAlert();
    }

    private void showAlert() {
        fadeOut(findViewById(R.id.shutter_button));
        if (mCurrentVideoFilename != null) {
            mVideoFrame.setImageBitmap(
                    ThumbnailUtil.createVideoThumbnail(mCurrentVideoFilename));
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

    private void stopVideoRecordingAndShowReview() {
        stopVideoRecording();
        if (mThumbController.isUriValid()) {
            Uri targetUri = mThumbController.getUri();
            Intent intent = new Intent(this, ReviewImage.class);
            intent.setData(targetUri);
            intent.putExtra(MediaStore.EXTRA_FULL_SCREEN, true);
            intent.putExtra(MediaStore.EXTRA_SHOW_ACTION_ICONS, true);
            intent.putExtra("com.android.camera.ReviewMode", true);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                Log.e(TAG, "review video fail", ex);
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
            mGripper.setVisibility(View.VISIBLE);
        }
        if (needToRegisterRecording && mStorageStatus == STORAGE_STATUS_OK) {
            registerVideo();
        }

        mCameraVideoFilename = null;
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
        Bitmap videoFrame = ThumbnailUtil.createVideoThumbnail(mCurrentVideoFilename);
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

        // Work around a limitation of the T-Mobile G1: The T-Mobile
        // hardware blitter can't pixel-accurately scale and clip at the
        // same time, and the SurfaceFlinger doesn't attempt to work around
        // this limitation. In order to avoid visual corruption we must
        // manually refresh the entire surface view when changing any
        // overlapping view's contents.
        mVideoPreview.invalidate();
        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, next_update_delay);
    }

    private void setCameraParameters() {
        mParameters = mCameraDevice.getParameters();

        mParameters.setPreviewSize(mProfile.mVideoWidth, mProfile.mVideoHeight);
        mParameters.setPreviewFrameRate(mProfile.mVideoFps);

        // Set white balance parameter.
        String whiteBalance = Parameters.WHITE_BALANCE_AUTO;
        if (mParameters.getSupportedWhiteBalance() != null) {
            whiteBalance = mPreferences.getString(
                    CameraSettings.KEY_WHITE_BALANCE,
                    getString(R.string.pref_camera_whitebalance_default));
            mParameters.setWhiteBalance(whiteBalance);
        }

        // Set color effect parameter.
        if (mParameters.getSupportedColorEffects() != null) {
            String colorEffect = mPreferences.getString(
                    CameraSettings.KEY_COLOR_EFFECT,
                    getString(R.string.pref_camera_coloreffect_default));
            mParameters.setColorEffect(colorEffect);
        }

        mCameraDevice.setParameters(mParameters);

        final String finalWhiteBalance = whiteBalance;

        // It can be execute from the startPreview thread, so we post it
        // to the main UI thread
        mHandler.post(new Runnable() {
            public void run() {
                mWhitebalanceIndicator.setMode(finalWhiteBalance);
            }
        });
    }

    public boolean onSwitchChanged(Switcher source, boolean onOff) {
        if (onOff == SWITCH_CAMERA) {
            MenuHelper.gotoCameraMode(this);
            finish();
        }
        return true;
    }

    public void onSharedPreferenceChanged(
            SharedPreferences preferences, String key) {
        // ignore the events after "onPause()" or preview has not started yet
        if (mPausing) return;

        if (CameraSettings.KEY_VIDEO_DURATION.equals(key)
                || CameraSettings.KEY_VIDEO_QUALITY.equals(key)) {
            readVideoPreferences();
        }

        // If mCameraDevice is not ready then we can set the parameter in
        // startPreview().
        if (mCameraDevice == null) return;

        // We need to restart the preview if preview size is changed.
        Size size = mParameters.getPreviewSize();
        if (size.width != mProfile.mVideoWidth
                || size.height != mProfile.mVideoHeight) {
            // It is assumed media recorder is released before
            // onSharedPreferenceChanged, so we can close the camera here.
            closeCamera();
            try {
                resizeForPreviewAspectRatio();
                startPreview(); // Parameters will be set in startPreview().
            } catch (CameraHardwareException e) {
                showCameraBusyAndFinish();
            }
        } else {
            try {
                // We need to lock the camera before writing parameters.
                mCameraDevice.lock();
            } catch (RuntimeException e) {
                // When preferences are added for the first time, this method
                // will be called. But OnScreenSetting is not displayed yet and
                // media recorder still owns the camera. Lock will fail and we
                // just ignore it.
                return;
            }
            setCameraParameters();
            mCameraDevice.unlock();
        }
    }

    public void onSizeChanged() {
        if (mSettings != null) {
            mSettings.updateLayout();
        }

    }
}

//
// DefaultHashMap is a HashMap which returns a default value if the specified
// key is not found.
//
@SuppressWarnings("serial")
class DefaultHashMap<K, V> extends HashMap<K, V> {
    private V mDefaultValue;

    public void putDefault(V defaultValue) {
        mDefaultValue = defaultValue;
    }

    @Override
    public V get(Object key) {
        V value = super.get(key);
        return (value == null) ? mDefaultValue : value;
    }
}

//
// MediaRecorderProfile reads from system properties to determine the proper
// values for various parameters for MediaRecorder.
//
class MediaRecorderProfile {

    @SuppressWarnings("unused")
    private static final String TAG = "MediaRecorderProfile";
    //public final boolean mHiQuality;
    public final int mQuality;
    public final int mOutputFormat;
    public final int mVideoEncoder;
    public final int mAudioEncoder;
    public final int mVideoWidth;
    public final int mVideoHeight;
    public final int mVideoFps;
    public final int mVideoBitrate;
    public final int mAudioBitrate;
    public final int mAudioChannels;
    public final int mAudioSamplingRate;

    MediaRecorderProfile(int quality) {
        //mHiQuality = hiQuality;
        mQuality = quality;

        mOutputFormat = getFromTable("ro.media.enc.hprof.file.format", //HIGH
                                     "ro.media.enc.mprof.file.format", //SD
                                     "ro.media.enc.lprof.file.format", //MMS
                                     OUTPUT_FORMAT_TABLE);

        mVideoEncoder = getFromTable("ro.media.enc.hprof.codec.vid",
                                     "ro.media.enc.mprof.codec.vid",
                                     "ro.media.enc.lprof.codec.vid",
                                     VIDEO_ENCODER_TABLE);

        mAudioEncoder = getFromTable("ro.media.enc.hprof.codec.aud",
                                     "ro.media.enc.mprof.codec.aud",
                                     "ro.media.enc.lprof.codec.aud",
                                     AUDIO_ENCODER_TABLE);

        mVideoWidth = getInt("ro.media.enc.hprof.vid.width",
                             "ro.media.enc.mprof.vid.width",
                             "ro.media.enc.lprof.vid.width",
                             1280, 352, 176);

        mVideoHeight = getInt("ro.media.enc.hprof.vid.height",
                              "ro.media.enc.mprof.vid.height",
                              "ro.media.enc.lprof.vid.height",
                              720, 288, 144);

        mVideoFps = getInt("ro.media.enc.hprof.vid.fps",
                           "ro.media.enc.mprof.vid.fps",
                           "ro.media.enc.lprof.vid.fps",
                           30, 20, 20);

        mVideoBitrate = getInt("ro.media.enc.hprof.vid.bps",
                               "ro.media.enc.mprof.vid.bps",
                               "ro.media.enc.lprof.vid.bps",
                               500000, 360000, 192000);

        mAudioBitrate = getInt("ro.media.enc.hprof.aud.bps",
                               "ro.media.enc.mprof.aud.bps",
                               "ro.media.enc.lprof.aud.bps",
                               23450, 23450, 23450);

        mAudioChannels = getInt("ro.media.enc.hprof.aud.ch",
                                "ro.media.enc.mprof.aud.ch",
                                "ro.media.enc.lprof.aud.ch",
                                1, 1, 1);

        mAudioSamplingRate = getInt("ro.media.enc.hprof.aud.hz",
                                    "ro.media.enc.mprof.aud.hz",
                                    "ro.media.enc.lprof.aud.hz",
                                    8000, 8000, 8000);
    }

    private int getFromTable(String highKey, String medKey, String lowKey,
                DefaultHashMap<String, Integer> table) {
                
        String s = SystemProperties.get(medKey); // Default to medium
        
        switch (mQuality) {
            case CameraSettings.VIDEO_QUAL_LOW:
                s = SystemProperties.get(lowKey);
                break;
            case CameraSettings.VIDEO_QUAL_MEDIUM:
                s = SystemProperties.get(medKey);
                break;
            case CameraSettings.VIDEO_QUAL_HIGH:
                s = SystemProperties.get(highKey);
                break;
        }
        
        return table.get(s);
    }

    private int getInt(String highKey, String medKey, String lowKey,
                        int highDefault, int medDefault, int lowDefault) {
        
        String key = medKey; // Default to medium
        int defaultValue = medDefault; // Default to medium
        
        switch (mQuality) {
            case CameraSettings.VIDEO_QUAL_LOW:
                key = lowKey;
                defaultValue = lowDefault;
                break;
            case CameraSettings.VIDEO_QUAL_MEDIUM:
                key = medKey;
                defaultValue = medDefault;
                break;
            case CameraSettings.VIDEO_QUAL_HIGH:
                key = highKey;
                defaultValue = highDefault;
                break;        
        }
        
        return SystemProperties.getInt(key, defaultValue);
    }

    private static final DefaultHashMap<String, Integer>
            OUTPUT_FORMAT_TABLE = new DefaultHashMap<String, Integer>();
    private static final DefaultHashMap<String, Integer>
            VIDEO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();
    private static final DefaultHashMap<String, Integer>
            AUDIO_ENCODER_TABLE = new DefaultHashMap<String, Integer>();

    static {
        OUTPUT_FORMAT_TABLE.put("3gp", MediaRecorder.OutputFormat.THREE_GPP);
        OUTPUT_FORMAT_TABLE.put("mp4", MediaRecorder.OutputFormat.MPEG_4);
        OUTPUT_FORMAT_TABLE.putDefault(MediaRecorder.OutputFormat.DEFAULT);

        VIDEO_ENCODER_TABLE.put("h263", MediaRecorder.VideoEncoder.H263);
        VIDEO_ENCODER_TABLE.put("h264", MediaRecorder.VideoEncoder.H264);
        VIDEO_ENCODER_TABLE.put("m4v", MediaRecorder.VideoEncoder.MPEG_4_SP);
        VIDEO_ENCODER_TABLE.putDefault(MediaRecorder.VideoEncoder.DEFAULT);

        AUDIO_ENCODER_TABLE.put("amrnb", MediaRecorder.AudioEncoder.AMR_NB);
        AUDIO_ENCODER_TABLE.put("amrwb", MediaRecorder.AudioEncoder.AMR_WB);
        AUDIO_ENCODER_TABLE.put("aac", MediaRecorder.AudioEncoder.AAC);
        AUDIO_ENCODER_TABLE.put("aacplus", MediaRecorder.AudioEncoder.AAC_PLUS);
        AUDIO_ENCODER_TABLE.put("eaacplus",
                MediaRecorder.AudioEncoder.EAAC_PLUS);
        AUDIO_ENCODER_TABLE.putDefault(MediaRecorder.AudioEncoder.DEFAULT);
    }
}
