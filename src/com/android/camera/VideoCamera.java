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
import java.util.ArrayList;
import java.io.IOException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

public class VideoCamera extends Activity implements View.OnClickListener, SurfaceHolder.Callback {

    private static final String TAG = "videocamera";

    private static final boolean DEBUG = true;
    private static final boolean DEBUG_SUPPRESS_AUDIO_RECORDING = DEBUG && true;
    private static final boolean DEBUG_DO_NOT_REUSE_MEDIA_RECORDER = DEBUG && true;

    private static final int KEEP = 2;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int RESTART_PREVIEW = 6;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;
    private static final int POST_PICTURE_ALERT_TIMEOUT = 6 * 1000;

    private static final int NO_STORAGE_ERROR = -1;
    private static final int CANNOT_STAT_ERROR = -2;

    public static final int MENU_SWITCH_TO_VIDEO = 0;
    public static final int MENU_SWITCH_TO_CAMERA = 1;
    public static final int MENU_SETTINGS = 6;
    public static final int MENU_GALLERY_PHOTOS = 7;
    public static final int MENU_GALLERY_VIDEOS = 8;
    public static final int MENU_SAVE_SELECT_PHOTOS = 30;
    public static final int MENU_SAVE_NEW_PHOTO = 31;
    public static final int MENU_SAVE_SELECTVIDEO = 32;
    public static final int MENU_SAVE_TAKE_NEW_VIDEO = 33;
    public static final int MENU_SAVE_GALLERY_PHOTO = 34;
    public static final int MENU_SAVE_GALLERY_VIDEO_PHOTO = 35;
    public static final int MENU_SAVE_CAMERA_DONE = 36;
    public static final int MENU_SAVE_CAMERA_VIDEO_DONE = 37;

    Toast mToast;
    SharedPreferences mPreferences;

    private static final float VIDEO_ASPECT_RATIO = 176.0f / 144.0f;
    VideoPreview mVideoPreview;
    SurfaceHolder mSurfaceHolder = null;
    ImageView mBlackout = null;
    ImageView mVideoFrame;
    Bitmap mVideoFrameBitmap;

    private MediaRecorder mMediaRecorder;
    private boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;

    boolean mPausing = false;

    static ContentResolver mContentResolver;
    boolean mDidRegister = false;

    int mCurrentZoomIndex = 0;

    private ImageView mModeIndicatorView;
    private ImageView mRecordingIndicatorView;
    private TextView mRecordingTimeView;

    ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    View mPostPictureAlert;
    LocationManager mLocationManager = null;

    private int mPicturesRemaining;

    private Handler mHandler = new MainHandler();

    private void cancelSavingNotification() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }
    }

    /** This Handler is used to post message back onto the main thread of the application */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case KEEP: {
                    keep();

                    if (msg.obj != null) {
                        mHandler.post((Runnable)msg.obj);
                    }
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case UPDATE_RECORD_TIME: {
                    if (mMediaRecorderRecording) {
                        long now = SystemClock.uptimeMillis();
                        long delta = now - mRecordingStartTime;
                        long seconds = delta / 1000;
                        long minutes = seconds / 60;
                        long remainderSeconds = seconds - (minutes * 60);

                        String secondsString = Long.toString(remainderSeconds);
                        if (secondsString.length() < 2) {
                            secondsString = "0" + secondsString;
                        }
                        String minutesString = Long.toString(minutes);
                        if (minutesString.length() < 2) {
                            minutesString = "0" + minutesString;
                        }
                        String text = minutesString + ":" + secondsString;
                        mRecordingTimeView.setText(text);
                        mHandler.sendEmptyMessageDelayed(UPDATE_RECORD_TIME, 1000);
                    }
                    break;
                }

                case RESTART_PREVIEW:
                    hideVideoFrameAndStartPreview();
                    break;

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
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                // SD card available
                // TODO put up a "please wait" message
                // TODO also listen for the media scanner finished message
                showStorageToast();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
                showStorageToast();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(VideoCamera.this, getResources().getString(R.string.wait), 5000);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                showStorageToast();
            }
        }
    };

    static private String createName(long dateTaken) {
        return DateFormat.format("yyyy-MM-dd kk.mm.ss", dateTaken).toString();
    }

    private void postAfterKeep(final Runnable r) {
        Message msg = mHandler.obtainMessage(KEEP);
        msg.obj = r;
        msg.sendToTarget();
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

        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.video_camera);

        mVideoPreview = (VideoPreview) findViewById(R.id.camera_preview);
        mVideoPreview.setAspectRatio(VIDEO_ASPECT_RATIO);

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mVideoPreview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mBlackout = (ImageView) findViewById(R.id.blackout);
        mBlackout.setBackgroundDrawable(new ColorDrawable(0xFF000000));

        mPostPictureAlert = findViewById(R.id.post_picture_panel);
        View b;

        b = findViewById(R.id.play);
        b.setOnClickListener(this);

        b = findViewById(R.id.share);
        b.setOnClickListener(this);

        b = findViewById(R.id.discard);
        b.setOnClickListener(this);

        mModeIndicatorView = (ImageView) findViewById(R.id.mode_indicator);
        mRecordingIndicatorView = (ImageView) findViewById(R.id.recording_indicator);
        mRecordingTimeView = (TextView) findViewById(R.id.recording_time);
        mVideoFrame = (ImageView) findViewById(R.id.video_frame);
    }

    @Override
    public void onStart() {
        super.onStart();

        final View hintView = findViewById(R.id.hint_toast);
        if (hintView != null)
            hintView.setVisibility(View.GONE);

        Thread t = new Thread(new Runnable() {
            public void run() {
                final boolean storageOK = calculatePicturesRemaining() > 0;
                if (hintView == null)
                    return;

                if (storageOK) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            hintView.setVisibility(View.VISIBLE);
                        }
                    });
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            Animation a = new android.view.animation.AlphaAnimation(1F, 0F);
                            a.setDuration(500);
                            a.startNow();
                            hintView.setAnimation(a);
                            hintView.setVisibility(View.GONE);
                        }
                    }, 3000);
                } else {
                    mHandler.post(new Runnable() {
                        public void run() {
                            hintView.setVisibility(View.GONE);
                            showStorageToast();
                        }
                    });
                }
            }
        });
        t.start();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.discard: {
                File f = new File(mCurrentVideoFilename);
                f.delete();
                mContentResolver.delete(mCurrentVideoUri, null, null);

                hideVideoFrameAndStartPreview();
                break;
            }

            case R.id.share: {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("video/3gpp");
                intent.putExtra(Intent.EXTRA_STREAM, mCurrentVideoUri);
                try {
                    startActivity(Intent.createChooser(intent, getText(R.string.sendVideo)));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(VideoCamera.this, R.string.no_way_to_share_video, Toast.LENGTH_SHORT).show();
                }

                break;
            }

            case R.id.play: {
                Intent intent = new Intent(Intent.ACTION_VIEW, mCurrentVideoUri);
                try {
                    startActivity(intent);
                } catch (android.content.ActivityNotFoundException ex) {
                    Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
                }
                break;
            }
        }
    }

    private void showStorageToast() {
        String noStorageText = null;
        int remaining = calculatePicturesRemaining();

        if (remaining == NO_STORAGE_ERROR) {
            noStorageText = getString(R.string.no_storage);
        } else if (remaining < 1) {
            noStorageText = getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
            Toast.makeText(this, noStorageText, 5000).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);

        mPausing = false;

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        mDidRegister = true;

        mBlackout.setVisibility(View.INVISIBLE);
        if (mVideoFrameBitmap == null) {
            initializeVideo();
        } else {
            showPostRecordingAlert();
        }
    }

    @Override
    public void onStop() {
        Log.v(TAG, "onStop");
        stopVideoRecording();
        keep();
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        stopVideoRecording();
        keep();
        hidePostPictureAlert();

        mPausing = true;

        if (mDidRegister) {
            unregisterReceiver(mReceiver);
            mDidRegister = false;
        }

        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mMediaRecorderRecording) {
                    Log.v(TAG, "onKeyBack");
                    stopVideoRecordingAndDisplayDialog();
                    return true;
                } else if(isPostRecordingAlertVisible()) {
                    hideVideoFrameAndStartPreview();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_FOCUS:
                return true;
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    if (!mMediaRecorderRecording) {
                        startVideoRecording();
                    } else {
                        stopVideoRecordingAndDisplayDialog();
                    }
                    return true;
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (mMediaRecorderRecording) {
                    stopVideoRecordingAndDisplayDialog();
                    return true;
                }
                hideVideoFrameAndStartPreview();
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        cancelRestartPreviewTimeout();
        return super.onTrackballEvent(event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
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
        Uri target = Video.Media.INTERNAL_CONTENT_URI;
        Intent intent = new Intent(Intent.ACTION_VIEW, target);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not start gallery activity", e);
        }
    }

    void keep() {
        cancelSavingNotification();
    };

    void toss() {
        cancelSavingNotification();
    };

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

        addBaseMenuItems(menu);
        MenuHelper.addImageMenuItems(
                menu,
                MenuHelper.INCLUDE_ALL & ~MenuHelper.INCLUDE_ROTATE_MENU,
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
                postAfterKeep(new Runnable() {
                    public void run() {
                        gotoGallery();
                    }
                });
                return true;
            }
        });
        gallery.setIcon(android.R.drawable.ic_menu_gallery);
        return true;
    }

    private int calculatePicturesRemaining() {
        try {
            if (!ImageManager.hasStorage()) {
                mPicturesRemaining = NO_STORAGE_ERROR;
            } else {
                String storageDirectory = Environment.getExternalStorageDirectory().toString();
                StatFs stat = new StatFs(storageDirectory);
                float remaining = ((float)stat.getAvailableBlocks() * (float)stat.getBlockSize()) / 400000F;
                mPicturesRemaining = (int)remaining;
            }
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many
            // pictures are remaining.  it might be zero but just leave it
            // blank since we really don't know.
            mPicturesRemaining = CANNOT_STAT_ERROR;
        }
        return mPicturesRemaining;
    }

    private void initializeVideo() {
        Log.v(TAG, "initializeVideo");
        releaseMediaRecorder();

        if (mSurfaceHolder == null) {
            Log.v(TAG, "SurfaceHolder is null");
            return;
        }

        mMediaRecorder = new MediaRecorder();

        if (DEBUG_SUPPRESS_AUDIO_RECORDING) {
            Log.v(TAG, "DEBUG_SUPPRESS_AUDIO_RECORDING is true.");
        } else {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        Log.v(TAG, "before setOutputFile");
        createVideoPath();
        mMediaRecorder.setOutputFile(mCurrentVideoFilename);
        Boolean videoQualityLow = getIntPreference("pref_camera_videoquality_key") == 0;

        // Use the same frame rate for both, since internally
        // if the frame rate is too large, it can cause camera to become
        // unstable. We need to fix the MediaRecorder to disable the support
        // of setting frame rate for now.
        mMediaRecorder.setVideoFrameRate(20);
        if (videoQualityLow) {
            mMediaRecorder.setVideoSize(176,144);
        } else {
            mMediaRecorder.setVideoSize(352,288);
        }
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
        if (!DEBUG_SUPPRESS_AUDIO_RECORDING) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        }
        Log.v(TAG, "before setPreviewDisplay");
        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        try {
            mMediaRecorder.prepare();
        } catch (IOException exception) {
            Log.e(TAG, "prepare failed for " + mCurrentVideoFilename);
            releaseMediaRecorder();
            // TODO: add more exception handling logic here
            return;
        }
        mMediaRecorderRecording = false;
    }

    private void releaseMediaRecorder() {
        Log.v(TAG, "Releasing media recorder.");
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private void restartPreview() {
        if (DEBUG_DO_NOT_REUSE_MEDIA_RECORDER) {
            Log.v(TAG, "DEBUG_DO_NOT_REUSE_MEDIA_RECORDER recreating mMediaRecorder.");
            initializeVideo();
        } else {
            try {
                mMediaRecorder.prepare();
            } catch (IOException exception) {
                Log.e(TAG, "prepare failed for " + mCurrentVideoFilename);
                releaseMediaRecorder();
                // TODO: add more exception handling logic here
            }
        }
    }

    private int getIntPreference(String key) {
        String s = mPreferences.getString(key, "0");
        return Integer.parseInt(s);
    }

    private void createVideoPath() {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String displayName = title + ".3gp"; // Used when emailing.
        String filename = ImageManager.CAMERA_IMAGE_BUCKET_NAME + "/"
            + Long.toString(dateTaken) + ".3gp";
        ContentValues values = new ContentValues(7);
        values.put(Video.Media.TITLE, title);
        values.put(Video.Media.DISPLAY_NAME, displayName);
        values.put(Video.Media.DESCRIPTION, "");
        values.put(Video.Media.DATE_TAKEN, dateTaken);
        values.put(Video.Media.MIME_TYPE, "video/3gpp");
        values.put(Video.Media.DATA, filename);
        Uri videoTable = Uri.parse("content://media/external/video/media");
        Uri item = mContentResolver.insert(videoTable, values);
        mCurrentVideoFilename = filename;
        mCurrentVideoUri = item;
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

    private void startVideoRecording() {
        Log.v(TAG, "startVideoRecording");
        if (!mMediaRecorderRecording) {

            // Check mMediaRecorder to see whether it is initialized or not.
            if (mMediaRecorder == null) {
                initializeVideo();
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            try {
                mMediaRecorder.start();   // Recording is now started
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start media recorder. ", e);
                return;
            }
            mMediaRecorderRecording = true;
            mRecordingStartTime = SystemClock.uptimeMillis();
            mModeIndicatorView.setVisibility(View.GONE);
            mRecordingIndicatorView.setVisibility(View.VISIBLE);
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            mHandler.sendEmptyMessage(UPDATE_RECORD_TIME);
        }
    }

    private void stopVideoRecordingAndDisplayDialog() {
        Log.v(TAG, "stopVideoRecordingAndDisplayDialog");
        if (mMediaRecorderRecording) {
            stopVideoRecording();
            acquireAndShowVideoFrame();
            showPostRecordingAlert();
        }
    }

    private void showPostRecordingAlert() {
        cancelRestartPreviewTimeout();
        mPostPictureAlert.setVisibility(View.VISIBLE);
        mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, POST_PICTURE_ALERT_TIMEOUT);
    }

    private void hidePostPictureAlert() {
        cancelRestartPreviewTimeout();
        mPostPictureAlert.setVisibility(View.INVISIBLE);
    }

    private void cancelRestartPreviewTimeout() {
        mHandler.removeMessages(RESTART_PREVIEW);
    }

    private boolean isPostRecordingAlertVisible() {
        return mPostPictureAlert.getVisibility() == View.VISIBLE;
    }

    private void stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        if (mMediaRecorderRecording || mMediaRecorder != null) {
            if (mMediaRecorderRecording) {
                mMediaRecorder.stop();
            }
            releaseMediaRecorder();
            mMediaRecorderRecording = false;
            mModeIndicatorView.setVisibility(View.VISIBLE);
            mRecordingIndicatorView.setVisibility(View.GONE);
            mRecordingTimeView.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void hideVideoFrameAndStartPreview() {
        hidePostPictureAlert();
        hideVideoFrame();
        restartPreview();
    }

    private void acquireAndShowVideoFrame() {
        recycleVideoFrameBitmap();
        mVideoFrameBitmap = createVideoThumbnail(mCurrentVideoFilename);
        mVideoFrame.setImageBitmap(mVideoFrameBitmap);
        mVideoFrame.setVisibility(View.VISIBLE);
    }

    private void hideVideoFrame() {
        recycleVideoFrameBitmap();
        mVideoFrame.setVisibility(View.GONE);
    }

    private void recycleVideoFrameBitmap() {
        if (mVideoFrameBitmap != null) {
            mVideoFrame.setImageDrawable(null);
            mVideoFrameBitmap.recycle();
            mVideoFrameBitmap = null;
        }
    }

    private Bitmap createVideoThumbnail(String filePath) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setMode(MediaMetadataRetriever.MODE_CAPTURE_FRAME_ONLY);
            retriever.setDataSource(filePath);
            bitmap = retriever.captureFrame();
        } finally {
            retriever.release();
        }
        return bitmap;
    }

}

