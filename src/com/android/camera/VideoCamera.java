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

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class VideoCamera extends Activity implements View.OnClickListener,
    ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback, MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {

    private static final String TAG = "videocamera";

    private static final boolean DEBUG = true;
    private static final boolean DEBUG_SUPPRESS_AUDIO_RECORDING = DEBUG && false;

    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final long NO_STORAGE_ERROR = -1L;
    private static final long CANNOT_STAT_ERROR = -2L;
    private static final long LOW_STORAGE_THRESHOLD = 512L * 1024L;
    private static final long SHARE_FILE_LENGTH_LIMIT = 3L * 1024L * 1024L;

    private static final int STORAGE_STATUS_OK = 0;
    private static final int STORAGE_STATUS_LOW = 1;
    private static final int STORAGE_STATUS_NONE = 2;

    public static final int MENU_SETTINGS = 6;
    public static final int MENU_GALLERY_PHOTOS = 7;
    public static final int MENU_GALLERY_VIDEOS = 8;
    public static final int MENU_SAVE_GALLERY_PHOTO = 34;
    public static final int MENU_SAVE_PLAY_VIDEO = 35;
    public static final int MENU_SAVE_SELECT_VIDEO = 36;
    public static final int MENU_SAVE_NEW_VIDEO = 37;

    SharedPreferences mPreferences;

    private static final float VIDEO_ASPECT_RATIO = 176.0f / 144.0f;
    VideoPreview mVideoPreview;
    SurfaceHolder mSurfaceHolder = null;
    ImageView mVideoFrame;

    private boolean mIsVideoCaptureIntent;
    // mLastPictureButton and mThumbController
    // are non-null only if isVideoCaptureIntent() is true;
    private ImageView mLastPictureButton;
    private ThumbnailController mThumbController;

    private static final int MAX_RECORDING_DURATION_MS = 10 * 60 * 1000;

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
    private long mCurrentVideoFileLength = 0L;
    private Uri mCurrentVideoUri;
    private ContentValues mCurrentVideoValues;

    boolean mPausing = false;

    static ContentResolver mContentResolver;

    int mCurrentZoomIndex = 0;

    private ShutterButton mShutterButton;
    private TextView mRecordingTimeView;
    private boolean mRecordingTimeCountsDown = false;

    ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    View mPostPictureAlert;
    LocationManager mLocationManager = null;

    private Handler mHandler = new MainHandler();

    /** This Handler is used to post message back onto the main thread of the application */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case CLEAR_SCREEN_DELAY: {
                    clearScreenOnFlag();
                    break;
                }

                case UPDATE_RECORD_TIME: {
                    if (mMediaRecorderRecording) {
                        long now = SystemClock.uptimeMillis();
                        long delta = now - mRecordingStartTime;

                        // Starting a minute before reaching the max duration
                        // limit, we'll countdown the remaining time instead.
                        boolean countdown_remaining_time =
                            (delta >= MAX_RECORDING_DURATION_MS - 60000);

                        if (countdown_remaining_time) {
                            delta = Math.max(0, MAX_RECORDING_DURATION_MS - delta);
                        }

                        long seconds = (delta + 500) / 1000;  // round to nearest
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

                        if (mRecordingTimeCountsDown != countdown_remaining_time) {
                            // Avoid setting the color on every update, do it only
                            // when it needs changing.

                            mRecordingTimeCountsDown = countdown_remaining_time;

                            int color = getResources().getColor(
                                    countdown_remaining_time ? R.color.recording_time_remaining_text
                                                             : R.color.recording_time_elapsed_text);

                            mRecordingTimeView.setTextColor(color);
                        }

                        // Work around a limitation of the T-Mobile G1: The T-Mobile
                        // hardware blitter can't pixel-accurately scale and clip at the same time,
                        // and the SurfaceFlinger doesn't attempt to work around this limitation.
                        // In order to avoid visual corruption we must manually refresh the entire
                        // surface view when changing any overlapping view's contents.
                        mVideoPreview.invalidate();
                        mHandler.sendEmptyMessageDelayed(UPDATE_RECORD_TIME, 1000);
                    }
                    break;
                }

                default:
                    Log.v(TAG, "Unhandled message: " + msg.what);
                  break;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                updateAndShowStorageHint(false);
                stopVideoRecording();
                initializeVideo();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                updateAndShowStorageHint(true);
                initializeVideo();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
                // handled in ACTION_MEDIA_EJECT
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(VideoCamera.this, getResources().getString(R.string.wait), 5000);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                updateAndShowStorageHint(true);
            }
        }
    };

    static private String createName(long dateTaken) {
        return DateFormat.format("yyyy-MM-dd kk.mm.ss", dateTaken).toString();
    }

    /** Called with the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mContentResolver = getContentResolver();

        //setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.video_camera);

        mVideoPreview = (VideoPreview) findViewById(R.id.camera_preview);
        mVideoPreview.setAspectRatio(VIDEO_ASPECT_RATIO);

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mVideoPreview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mPostPictureAlert = findViewById(R.id.post_picture_panel);

        int[] ids = new int[]{R.id.play, R.id.share, R.id.discard,
                R.id.cancel, R.id.attach};
        for (int id : ids) {
            findViewById(id).setOnClickListener(this);
        }

        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnShutterButtonListener(this);
        mRecordingTimeView = (TextView) findViewById(R.id.recording_time);
        mVideoFrame = (ImageView) findViewById(R.id.video_frame);
        mIsVideoCaptureIntent = isVideoCaptureIntent();
        if (!mIsVideoCaptureIntent) {
            mLastPictureButton = (ImageView) findViewById(R.id.last_picture_button);
            mLastPictureButton.setOnClickListener(this);
            Drawable frame = getResources().getDrawable(R.drawable.frame_thumbnail);
            mThumbController = new ThumbnailController(mLastPictureButton,
                    frame, mContentResolver);
            mThumbController.loadData(ImageManager.getLastVideoThumbPath());
        }
    }

    private void startShareVideoActivity() {
        if (mCurrentVideoFileLength > SHARE_FILE_LENGTH_LIMIT) {
            Toast.makeText(VideoCamera.this,
                    R.string.too_large_to_attach, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("video/3gpp");
        intent.putExtra(Intent.EXTRA_STREAM, mCurrentVideoUri);
        try {
            startActivity(Intent.createChooser(intent, getText(R.string.sendVideo)));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(VideoCamera.this, R.string.no_way_to_share_video, Toast.LENGTH_SHORT).show();
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.gallery:
                MenuHelper.gotoCameraVideoGallery(this);
                break;

            case R.id.attach:
                doReturnToCaller(true);
                break;

            case R.id.cancel:
                doReturnToCaller(false);
                break;

            case R.id.discard: {
                Runnable deleteCallback = new Runnable() {
                    public void run() {
                        discardCurrentVideoAndStartPreview();
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

            case R.id.last_picture_button: {
                stopVideoRecordingAndShowAlert();
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
                        initializeVideo();
                    }
                } else if (isAlertVisible()) {
                    if (mIsVideoCaptureIntent) {
                        discardCurrentVideoAndStartPreview();
                    } else {
                        hideAlertAndStartVideoRecording();
                    }
                } else {
                    startVideoRecording();
                }
                break;
        }
    }

    private void doPlayCurrentVideo() {
        Log.e(TAG, "Playing current video: " + mCurrentVideoUri);
        Intent intent = new Intent(Intent.ACTION_VIEW, mCurrentVideoUri);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    private void discardCurrentVideoAndStartPreview() {
        deleteCurrentVideo();
        hideAlertAndStartPreview();
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
                ? STORAGE_STATUS_LOW : STORAGE_STATUS_OK;
    }

    @Override
    public void onResume() {
        super.onResume();

        setScreenTimeoutLong();

        mPausing = false;

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        mStorageStatus = getStorageStatus(true);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                showStorageHint();
            }
        }, 200);

        initializeVideo();
    }

    @Override
    public void onStop() {
        setScreenTimeoutSystemDefault();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        if (mMediaRecorderRecording) {
            if (mIsVideoCaptureIntent) {
                stopVideoRecordingAndShowAlert();
            } else {
                stopVideoRecordingAndGetThumbnail();
            }
        } else {
            stopVideoRecording();
        }

        mPausing = true;

        unregisterReceiver(mReceiver);
        setScreenTimeoutSystemDefault();

        if (!mIsVideoCaptureIntent) {
            mThumbController.storeData(ImageManager.getLastVideoThumbPath());
        }

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        setScreenTimeoutLong();

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mMediaRecorderRecording) {
                    mShutterButton.performClick();
                    return true;
                } else if(isAlertVisible()) {
                    hideAlertAndStartPreview();
                    return true;
                }
                break;
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
        switch(keyCode) {
        case KeyEvent.KEYCODE_CAMERA:
            mShutterButton.setPressed(false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mPausing) {
            // We're pausing, the screen is off and we already stopped
            // video recording. We don't want to start the camera again
            // in this case in order to conserve power.
            // The fact that surfaceChanged is called _after_ an onPause appears
            // to be legitimate since in that case the lockscreen always returns
            // to portrait orientation possibly triggering the notification.
            return;
        }

        stopVideoRecording();
        initializeVideo();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }

    void gotoGallery() {
        MenuHelper.gotoCameraVideoGallery(this);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        for (int i = 1; i <= MenuHelper.MENU_ITEM_MAX; i++) {
            if (i != MenuHelper.GENERIC_ITEM) {
                menu.setGroupVisible(i, false);
            }
        }

        menu.setGroupVisible(MenuHelper.VIDEO_MODE_ITEM, true);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mIsVideoCaptureIntent) {
            // No options menu for attach mode.
            return false;
        } else {
            addBaseMenuItems(menu);
            int menuFlags = MenuHelper.INCLUDE_ALL & ~MenuHelper.INCLUDE_ROTATE_MENU
                    & ~MenuHelper.INCLUDE_DETAILS_MENU;
            MenuHelper.addImageMenuItems(
                    menu,
                    menuFlags,
                    false,
                    VideoCamera.this,
                    mHandler,

                    // Handler for deletion
                    new Runnable() {
                        public void run() {
                            // What do we do here?
                            // mContentResolver.delete(uri, null, null);
                        }
                    },
                    new MenuHelper.MenuInvoker() {
                        public void run(final MenuHelper.MenuCallback cb) {
                        }
                    });

            MenuItem gallery = menu.add(MenuHelper.IMAGE_SAVING_ITEM, MENU_SAVE_GALLERY_PHOTO, 0,
                    R.string.camera_gallery_photos_text).setOnMenuItemClickListener(
                            new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    gotoGallery();
                    return true;
                }
            });
            gallery.setIcon(android.R.drawable.ic_menu_gallery);
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
     * @return number of bytes available, or an ERROR code.
     */
    private static long getAvailableStorage() {
        try {
            if (!ImageManager.hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {
                String storageDirectory = Environment.getExternalStorageDirectory().toString();
                StatFs stat = new StatFs(storageDirectory);
                return ((long)stat.getAvailableBlocks() * (long)stat.getBlockSize());
            }
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many
            // free bytes exist.  It might be zero but just leave it
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

    // initializeVideo() starts preview and prepare media recorder.
    // Returns false if initializeVideo fails
    private boolean initializeVideo() {
        Log.v(TAG, "initializeVideo");

        // We will call initializeVideo() again when the alert is hidden.
        if (isAlertVisible()) return false;

        Intent intent = getIntent();
        Bundle myExtras = intent.getExtras();

        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mCameraVideoFileDescriptor = mContentResolver.
                        openFileDescriptor(saveUri, "rw").getFileDescriptor();
                    mCurrentVideoUri = saveUri;
                }
                catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
        }
        releaseMediaRecorder();

        if (mSurfaceHolder == null) {
            Log.v(TAG, "SurfaceHolder is null");
            return false;
        }

        mMediaRecorder = new MediaRecorder();

        if (DEBUG_SUPPRESS_AUDIO_RECORDING) {
            Log.v(TAG, "DEBUG_SUPPRESS_AUDIO_RECORDING is true.");
        } else {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

        mMediaRecorder.setMaxDuration(MAX_RECORDING_DURATION_MS);

        if (mStorageStatus != STORAGE_STATUS_OK) {
            mMediaRecorder.setOutputFile("/dev/null");
        } else {
            // We try Uri in intent first. If it doesn't work, use our own instead.
            if (mCameraVideoFileDescriptor != null) {
                mMediaRecorder.setOutputFile(mCameraVideoFileDescriptor);
            } else {
                createVideoPath();
                mMediaRecorder.setOutputFile(mCameraVideoFilename);
            }
        }

        boolean videoQualityHigh = getBooleanPreference(CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.DEFAULT_VIDEO_QUALITY_VALUE);

        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality = intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            videoQualityHigh = (extraVideoQuality > 0);
        }

        // Use the same frame rate for both, since internally
        // if the frame rate is too large, it can cause camera to become
        // unstable. We need to fix the MediaRecorder to disable the support
        // of setting frame rate for now.
        mMediaRecorder.setVideoFrameRate(20);
        if (videoQualityHigh) {
            mMediaRecorder.setVideoSize(352,288);
        } else {
            mMediaRecorder.setVideoSize(176,144);
        }
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
        if (!DEBUG_SUPPRESS_AUDIO_RECORDING) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        }
        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        long remaining = getAvailableStorage();
        // remaining >= LOW_STORAGE_THRESHOLD at this point, reserve a quarter
        // of that to make it more likely that recording can complete successfully.
        try {
            mMediaRecorder.setMaxFileSize(remaining - LOW_STORAGE_THRESHOLD / 4);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            //    on the size restriction.
        }

        try {
            mMediaRecorder.prepare();
        } catch (IOException exception) {
            Log.e(TAG, "prepare failed for " + mCameraVideoFilename);
            releaseMediaRecorder();
            // TODO: add more exception handling logic here
            return false;
        }
        mMediaRecorderRecording = false;

        if (!mIsVideoCaptureIntent && !mThumbController.isUriValid()) {
            updateLastVideo();
        }

        if (!mIsVideoCaptureIntent) {
            mThumbController.updateDisplayIfNeeded();
        }

        return true;
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
        values.put(Video.Media.DESCRIPTION, "");
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
        if (! f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, this, false);
        {
            MenuItem gallery = menu.add(MenuHelper.IMAGE_MODE_ITEM, MENU_GALLERY_PHOTOS, 0, R.string.camera_gallery_photos_text).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    gotoGallery();
                    return true;
                }
            });
            gallery.setIcon(android.R.drawable.ic_menu_gallery);
            mGalleryItems.add(gallery);
        }
        {
            MenuItem gallery = menu.add(MenuHelper.VIDEO_MODE_ITEM, MENU_GALLERY_VIDEOS, 0, R.string.camera_gallery_photos_text).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    gotoGallery();
                    return true;
                }
            });
            gallery.setIcon(android.R.drawable.ic_menu_gallery);
            mGalleryItems.add(gallery);
        }

        MenuItem item = menu.add(MenuHelper.GENERIC_ITEM, MENU_SETTINGS, 0, R.string.settings).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = new Intent();
                intent.setClass(VideoCamera.this, CameraSettings.class);
                startActivity(intent);
                return true;
            }
        });
        item.setIcon(android.R.drawable.ic_menu_preferences);
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
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            mShutterButton.performClick();
            updateAndShowStorageHint(true);
        }
    }

    /*
     * Make sure we're not recording music playing in the background, ask
     * the MediaPlaybackService to pause playback.
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
            if (mMediaRecorder == null && initializeVideo() == false ) {
                Log.e(TAG, "Initialize video (MediaRecorder) failed.");
                return;
            }

            pauseAudioPlayback();

            try {
                mMediaRecorder.setOnErrorListener(this);
                mMediaRecorder.setOnInfoListener(this);
                mMediaRecorder.start();   // Recording is now started
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start media recorder. ", e);
                return;
            }
            mMediaRecorderRecording = true;
            mRecordingStartTime = SystemClock.uptimeMillis();
            updateRecordingIndicator(true);
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            mHandler.sendEmptyMessage(UPDATE_RECORD_TIME);
            setScreenTimeoutInfinite();
            hideLastPictureButton();
        }
    }

    private void updateRecordingIndicator(boolean showRecording) {
        int drawableId = showRecording ? R.drawable.ic_camera_bar_indicator_record
            : R.drawable.ic_camera_indicator_video;
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
        int[] pickIds = {R.id.attach, R.id.cancel};
        int[] normalIds = {R.id.gallery, R.id.share, R.id.discard};
        int[] alwaysOnIds = {R.id.play};
        int[] hideIds = pickIds;
        int[] connectIds = normalIds;
        if (mIsVideoCaptureIntent) {
            hideIds = normalIds;
            connectIds = pickIds;
        }
        for(int id : hideIds) {
            mPostPictureAlert.findViewById(id).setVisibility(View.GONE);
        }
        ActionMenuButton shareButton =
                (ActionMenuButton) mPostPictureAlert.findViewById(R.id.share);
        shareButton.setRestricted(
                mCurrentVideoFileLength > SHARE_FILE_LENGTH_LIMIT);
        connectAndFadeIn(connectIds);
        connectAndFadeIn(alwaysOnIds);
        hideLastPictureButton();
        mPostPictureAlert.setVisibility(View.VISIBLE);

        // There are two cases we are here:
        // (1) We are in a capture video intent, and we are reviewing the video
        //     we just taken.
        // (2) The thumbnail button is clicked: we review the video associated
        //     with the thumbnail.
        // For the second case, we copy the associated URI and filename to
        // mCurrentVideoUri and mCurrentVideoFilename, so the video frame shown
        // and the target for actions (play, delete, ...) will be correct.

        if (!mIsVideoCaptureIntent) {
            mCurrentVideoUri = mThumbController.getUri();
            mCurrentVideoFilename = getDataPath(mCurrentVideoUri);
        }

        String path = mCurrentVideoFilename;
        if (path != null) {
            Bitmap videoFrame = ImageManager.createVideoThumbnail(path);
            mVideoFrame.setImageBitmap(videoFrame);
            mVideoFrame.setVisibility(View.VISIBLE);
        }
    }

    private void hideAlert() {
        mVideoFrame.setVisibility(View.INVISIBLE);
        mPostPictureAlert.setVisibility(View.INVISIBLE);
        showLastPictureButton();
    }

    private void connectAndFadeIn(int[] connectIds) {
        for(int id : connectIds) {
            View view = mPostPictureAlert.findViewById(id);
            view.setOnClickListener(this);
            Animation animation = new AlphaAnimation(0F, 1F);
            animation.setDuration(500);
            view.startAnimation(animation);
        }
    }

    private boolean isAlertVisible() {
        return mPostPictureAlert.getVisibility() == View.VISIBLE;
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
                try {
                    mCurrentVideoFileLength = new File(mCurrentVideoFilename).length();
                } catch (RuntimeException e) {
                    Log.e(TAG, "get file length fail: " + e.getMessage());
                    mCurrentVideoFileLength = 0;
                }
                Log.v(TAG, "Setting current video filename: " + mCurrentVideoFilename);
                needToRegisterRecording = true;
                mMediaRecorderRecording = false;
            }
            releaseMediaRecorder();
            updateRecordingIndicator(false);
            mRecordingTimeView.setVisibility(View.GONE);
            setScreenTimeoutLong();
        }
        if (needToRegisterRecording && mStorageStatus == STORAGE_STATUS_OK) {
            registerVideo();
        }

        mCameraVideoFilename = null;
        mCameraVideoFileDescriptor = null;
    }

    private void setScreenTimeoutSystemDefault() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        clearScreenOnFlag();
    }

    private void setScreenTimeoutLong() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        setScreenOnFlag();
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void setScreenTimeoutInfinite() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        setScreenOnFlag();
    }

    private void clearScreenOnFlag() {
        Window w = getWindow();
        final int keepScreenOnFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if ((w.getAttributes().flags & keepScreenOnFlag) != 0) {
            w.clearFlags(keepScreenOnFlag);
        }
    }

    private void setScreenOnFlag() {
        Window w = getWindow();
        final int keepScreenOnFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if ((w.getAttributes().flags & keepScreenOnFlag) == 0) {
            w.addFlags(keepScreenOnFlag);
        }
    }

    private void hideAlertAndStartPreview() {
        hideAlert();
        initializeVideo();
    }

    private void hideAlertAndStartVideoRecording() {
        hideAlert();
        startVideoRecording();
    }

    private void acquireVideoThumb() {
        Bitmap videoFrame = ImageManager.createVideoThumbnail(mCurrentVideoFilename);
        mThumbController.setData(mCurrentVideoUri, videoFrame);
    }

    private void showLastPictureButton() {
        if (!mIsVideoCaptureIntent) {
            mLastPictureButton.setVisibility(View.VISIBLE);
        }
    }

    private void hideLastPictureButton() {
        if (!mIsVideoCaptureIntent) {
            mLastPictureButton.setVisibility(View.INVISIBLE);
        }
    }

    private static ImageManager.DataLocation dataLocation() {
        return ImageManager.DataLocation.EXTERNAL;
    }

    private void updateLastVideo() {
        ImageManager.IImageList list = ImageManager.instance().allImages(
            this,
            mContentResolver,
            dataLocation(),
            ImageManager.INCLUDE_VIDEOS,
            ImageManager.SORT_ASCENDING,
            ImageManager.CAMERA_IMAGE_BUCKET_ID);
        int count = list.getCount();
        if (count > 0) {
            ImageManager.IImage image = list.getImageAt(count-1);
            Uri uri = image.fullSizeImageUri();
            mThumbController.setData(uri, image.miniThumbBitmap());
        } else {
            mThumbController.setData(null, null);
        }
        list.deactivate();
    }

    private static final String[] DATA_PATH_PROJECTION = new String[] {
        "_data"
    };

    private String getDataPath(Uri uri) {
        Cursor c = null;
        try {
            c = mContentResolver.query(uri, DATA_PATH_PROJECTION, null, null, null);
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            } else {
                return null;
            }
        } finally {
            if (c != null) c.close();
        }
    }
}
