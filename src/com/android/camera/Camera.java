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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Config;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

public class Camera extends Activity implements View.OnClickListener,
    ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback {

    private static final String TAG = "camera";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_TIME_OPERATIONS = DEBUG && false;

    private static final int CROP_MSG = 1;
    private static final int KEEP = 2;
    private static final int RESTART_PREVIEW = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;
    private static final int FOCUS_BEEP_VOLUME = 100;

    public static final int MENU_SWITCH_TO_VIDEO = 0;
    public static final int MENU_SWITCH_TO_CAMERA = 1;
    public static final int MENU_FLASH_SETTING = 2;
    public static final int MENU_FLASH_AUTO = 3;
    public static final int MENU_FLASH_ON = 4;
    public static final int MENU_FLASH_OFF = 5;
    public static final int MENU_SETTINGS = 6;
    public static final int MENU_GALLERY_PHOTOS = 7;
    public static final int MENU_GALLERY_VIDEOS = 8;
    public static final int MENU_SAVE_SELECT_PHOTOS = 30;
    public static final int MENU_SAVE_NEW_PHOTO = 31;
    public static final int MENU_SAVE_GALLERY_PHOTO = 34;
    public static final int MENU_SAVE_GALLERY_VIDEO_PHOTO = 35;
    public static final int MENU_SAVE_CAMERA_DONE = 36;
    public static final int MENU_SAVE_CAMERA_VIDEO_DONE = 37;

    private Toast mToast;
    private OrientationEventListener mOrientationListener;
    private int mLastOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private SharedPreferences mPreferences;

    private static final int IDLE = 1;
    private static final int SNAPSHOT_IN_PROGRESS = 2;
    private static final int SNAPSHOT_COMPLETED = 3;

    private int mStatus = IDLE;
    private static final String sTempCropFilename = "crop-temp";

    private android.hardware.Camera mCameraDevice;
    private android.hardware.Camera.Parameters mParameters;
    private VideoPreview mSurfaceView;
    private SurfaceHolder mSurfaceHolder = null;
    private View mBlackout = null;

    private int mOriginalViewFinderWidth, mOriginalViewFinderHeight;
    private int mViewFinderWidth, mViewFinderHeight;
    private boolean mPreviewing = false;

    private MediaPlayer mClickSound;

    private Capturer mCaptureObject;
    private ImageCapture mImageCapture = null;

    private boolean mPausing = false;

    private boolean mIsFocusing = false;
    private boolean mIsFocused = false;
    private boolean mIsFocusButtonPressed = false;
    private boolean mCaptureOnFocus = false;

    private static ContentResolver mContentResolver;
    private boolean mDidRegister = false;

    private ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();

    private boolean mMenuSelectionMade;

    private ImageView mLastPictureButton;
    private LayerDrawable mVignette;
    private Animation mShowLastPictureButtonAnimation = new AlphaAnimation(0F, 1F);
    private boolean mShouldShowLastPictureButton;
    private TransitionDrawable mThumbnailTransition;
    private Drawable[] mThumbnails;
    private boolean mShouldTransitionThumbnails;
    private Uri mLastPictureUri;
    private Bitmap mLastPictureThumb;
    private LocationManager mLocationManager = null;

    private ShutterButton mShutterButton;

    private Animation mFocusBlinkAnimation;
    private View mFocusIndicator;
    private ToneGenerator mFocusToneGenerator;


    private ShutterCallback mShutterCallback = new ShutterCallback();
    private RawPictureCallback mRawPictureCallback = new RawPictureCallback();
    private AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();
    private long mShutterPressTime;
    private int mPicturesRemaining;

    private boolean mKeepAndRestartPreview;

    // mPostCaptureAlert is non-null only if isImageCaptureIntent() is true.
    private View mPostCaptureAlert;


    private Handler mHandler = new MainHandler();
    private ProgressDialog mSavingProgress;

    private interface Capturer {
        Uri getLastCaptureUri();
        void onSnap();
        void dismissFreezeFrame(boolean keep);
        void cancelSave();
        void cancelAutoDismiss();
        void setDone(boolean wait);
    }

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
                    if (mSavingProgress != null) {
                        mSavingProgress.cancel();
                        mSavingProgress = null;
                    }

                    mKeepAndRestartPreview = true;

                    if (msg.obj != null) {
                        mHandler.post((Runnable)msg.obj);
                    }
                    break;
                }

                case RESTART_PREVIEW: {
                    if (mStatus == SNAPSHOT_IN_PROGRESS) {
                        // We are still in the processing of taking the picture, wait.
                        // This is is strange.  Why are we polling?
                        // TODO remove polling
                        mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, 100);
                    } else if (mStatus == SNAPSHOT_COMPLETED){
                        mCaptureObject.dismissFreezeFrame(true);
                        hidePostCaptureAlert();
                    }
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }
            }
        }
    };

    LocationListener [] mLocationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                // SD card available
                updateStorageHint(calculatePicturesRemaining());
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED) ||
                    action.equals(Intent.ACTION_MEDIA_CHECKING)) {
                // SD card unavailable
                mPicturesRemaining = MenuHelper.NO_STORAGE_ERROR;
                updateStorageHint(mPicturesRemaining);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(Camera.this, getResources().getString(R.string.wait), 5000);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                updateStorageHint();
            }
        }
    };

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;
        boolean mValid = false;
        String mProvider;

        public LocationListener(String provider) {
            mProvider = provider;
            mLastLocation = new Location(mProvider);
        }

        public void onLocationChanged(Location newLocation) {
            if (newLocation.getLatitude() == 0.0 && newLocation.getLongitude() == 0.0) {
                // Hack to filter out 0.0,0.0 locations
                return;
            }
            mLastLocation.set(newLocation);
            mValid = true;
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
            mValid = false;
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                mValid = false;
            }
        }

        public Location current() {
            return mValid ? mLastLocation : null;
        }
    };

    private long mRawPictureCallbackTime;

    private boolean mImageSavingItem = false;

    private final class ShutterCallback implements android.hardware.Camera.ShutterCallback {
        public void onShutter() {
            if (DEBUG_TIME_OPERATIONS) {
                long now = System.currentTimeMillis();
                Log.v(TAG, "********** Total shutter lag " + (now - mShutterPressTime) + " ms");
            }
            if (mClickSound != null) {
                mClickSound.start();
            }
        }
    };

    private final class RawPictureCallback implements PictureCallback {
        public void onPictureTaken(byte [] rawData, android.hardware.Camera camera) {
            if (Config.LOGV)
                Log.v(TAG, "got RawPictureCallback...");
            mRawPictureCallbackTime = System.currentTimeMillis();
            mBlackout.setVisibility(View.GONE);
        }
    };

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        public void onPictureTaken(byte [] jpegData, android.hardware.Camera camera) {
            if (Config.LOGV)
                Log.v(TAG, "got JpegPictureCallback...");

            mImageCapture.storeImage(jpegData, camera, mLocation);

            mStatus = SNAPSHOT_COMPLETED;

            if (mKeepAndRestartPreview) {
                long delay = 1500 - (System.currentTimeMillis() - mRawPictureCallbackTime);
                mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, Math.max(delay, 0));
            }
        }
    };

    private final class AutoFocusCallback implements android.hardware.Camera.AutoFocusCallback {
        public void onAutoFocus(boolean focused, android.hardware.Camera camera) {
            mIsFocusing = false;
            mIsFocused = focused;
            if (focused) {
                if (mCaptureOnFocus && mCaptureObject != null) {
                    // No need to play the AF sound if we're about to play the shutter sound
                    mCaptureObject.onSnap();
                    clearFocus();
                } else {
                    ToneGenerator tg = mFocusToneGenerator;
                    if (tg != null)
                       tg.startTone(ToneGenerator.TONE_PROP_BEEP2);
                }
                mCaptureOnFocus = false;
            }
            updateFocusIndicator();
        }
    };

    private class ImageCapture implements Capturer {

        private boolean mCancel = false;
        private boolean mCapturing = false;

        private Uri mLastContentUri;
        private ImageManager.IAddImage_cancelable mAddImageCancelable;

        Bitmap mCaptureOnlyBitmap;

        /** These member variables are used for various debug timings */
        private long mThreadTimeStart;
        private long mThreadTimeEnd;
        private long mWallTimeStart;
        private long mWallTimeEnd;


        public ImageCapture() {
        }

        /**
         * This method sets whether or not we are capturing a picture. This method must be called
         * with the ImageCapture.this lock held.
         */
        public void setCapturingLocked(boolean capturing) {
            mCapturing = capturing;
        }

        /*
         * Tell the ImageCapture thread to exit when possible.
         */
        public void setDone(boolean wait) {
        }

        /*
         * Tell the image capture thread to not "dismiss" the current
         * capture when the current image is stored, etc.
         */
        public void cancelAutoDismiss() {
        }

        public void dismissFreezeFrame(boolean keep) {
            if (keep) {
                cancelSavingNotification();
            } else {
                Toast.makeText(Camera.this, R.string.camera_tossing, Toast.LENGTH_SHORT).show();
            }

            if (mStatus == SNAPSHOT_IN_PROGRESS) {
                // If we are still in the process of taking a picture, then just post a message.
                mHandler.sendEmptyMessage(RESTART_PREVIEW);
            } else {
                restartPreview();
            }
        }

        private void startTiming() {
            mWallTimeStart = SystemClock.elapsedRealtime();
            mThreadTimeStart = Debug.threadCpuTimeNanos();
        }

        private void stopTiming() {
            mThreadTimeEnd = Debug.threadCpuTimeNanos();
            mWallTimeEnd = SystemClock.elapsedRealtime();
        }

        private void storeImage(byte[] data, Location loc) {
            try {
                if (DEBUG_TIME_OPERATIONS) {
                    startTiming();
                }
                long dateTaken = System.currentTimeMillis();
                String name = createName(dateTaken) + ".jpg";
                mLastContentUri = ImageManager.instance().addImage(
                        Camera.this,
                        mContentResolver,
                        name,
                        "",
                        dateTaken,
                        // location for the database goes here
                        loc,
                        0,   // the dsp will use the right orientation so don't "double set it"
                        ImageManager.CAMERA_IMAGE_BUCKET_NAME,
                        name);

                if (mLastContentUri == null) {
                    // this means we got an error
                    mCancel = true;
                }
                if (!mCancel) {
                    mAddImageCancelable = ImageManager.instance().storeImage(mLastContentUri,
                            Camera.this, mContentResolver, 0, null, data);
                    mAddImageCancelable.get();
                    mAddImageCancelable = null;
                }

                if (DEBUG_TIME_OPERATIONS) {
                    stopTiming();
                    Log.d(TAG, "Storing image took " + (mWallTimeEnd - mWallTimeStart) + " ms. " +
                            "Thread time was " + ((mThreadTimeEnd - mThreadTimeStart) / 1000000) +
                            " ms.");
                }
            } catch (Exception ex) {
                Log.e(TAG, "Exception while compressing image.", ex);
            }
        }

        public void storeImage(byte[] data, android.hardware.Camera camera, Location loc) {
            boolean captureOnly = isImageCaptureIntent();

            if (!captureOnly) {
                storeImage(data, loc);
                sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", mLastContentUri));
                setLastPictureThumb(data, mCaptureObject.getLastCaptureUri());
                dismissFreezeFrame(true);
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;

                if (DEBUG_TIME_OPERATIONS) {
                    startTiming();
                }

                mCaptureOnlyBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

                if (DEBUG_TIME_OPERATIONS) {
                    stopTiming();
                    Log.d(TAG, "Decoded mCaptureOnly bitmap (" + mCaptureOnlyBitmap.getWidth() +
                            "x" + mCaptureOnlyBitmap.getHeight() + " ) in " +
                            (mWallTimeEnd - mWallTimeStart) + " ms. Thread time was " +
                            ((mThreadTimeEnd - mThreadTimeStart) / 1000000) + " ms.");
                }

                showPostCaptureAlert();
                cancelAutomaticPreviewRestart();
            }

            mCapturing = false;
            if (mPausing) {
                closeCamera();
            }
        }

        /*
         * Tells the image capture thread to abort the capture of the
         * current image.
         */
        public void cancelSave() {
            if (!mCapturing) {
                return;
            }

            mCancel = true;

            if (mAddImageCancelable != null) {
                mAddImageCancelable.cancel();
            }
            dismissFreezeFrame(false);
        }

        /*
         * Initiate the capture of an image.
         */
        public void initiate(boolean captureOnly) {
            if (mCameraDevice == null) {
                return;
            }

            mCancel = false;
            mCapturing = true;

            capture(captureOnly);
        }

        public Uri getLastCaptureUri() {
            return mLastContentUri;
        }

        public Bitmap getLastBitmap() {
            return mCaptureOnlyBitmap;
        }

        private void capture(boolean captureOnly) {
            mPreviewing = false;
            mCaptureOnlyBitmap = null;

            final int latchedOrientation = ImageManager.roundOrientation(mLastOrientation + 90);

            Boolean recordLocation = mPreferences.getBoolean("pref_camera_recordlocation_key", false);
            Location loc = recordLocation ? getCurrentLocation() : null;
            // Quality 75 has visible artifacts, and quality 90 looks great but the files begin to
            // get large. 85 is a good compromise between the two.
            mParameters.set("jpeg-quality", 85);
            mParameters.set("rotation", latchedOrientation);

            mParameters.remove("gps-latitude");
            mParameters.remove("gps-longitude");
            mParameters.remove("gps-altitude");
            mParameters.remove("gps-timestamp");

            if (loc != null) {
                double lat = loc.getLatitude();
                double lon = loc.getLongitude();
                boolean hasLatLon = (lat != 0.0d) || (lon != 0.0d);

                if (hasLatLon) {
                    String latString = String.valueOf(lat);
                    String lonString = String.valueOf(lon);
                    mParameters.set("gps-latitude",  latString);
                    mParameters.set("gps-longitude", lonString);
                    if (loc.hasAltitude())
                        mParameters.set("gps-altitude",  String.valueOf(loc.getAltitude()));
                    if (loc.getTime() != 0) {
                        // Location.getTime() is UTC in milliseconds.
                        // gps-timestamp is UTC in seconds.
                        long utcTimeSeconds = loc.getTime() / 1000;
                        mParameters.set("gps-timestamp", String.valueOf(utcTimeSeconds));
                    }
                } else {
                    loc = null;
                }
            }


            Size pictureSize = mParameters.getPictureSize();

            // resize the SurfaceView to the aspect-ratio of the still image
            // and so that we can see the full image that was taken
            mSurfaceView.setAspectRatio(pictureSize.width, pictureSize.height);

            mCameraDevice.setParameters(mParameters);

            mCameraDevice.takePicture(mShutterCallback, mRawPictureCallback, new JpegPictureCallback(loc));
            // Prepare the sound to play in shutter callback.
            if (mClickSound != null) {
                mClickSound.seekTo(0);
            }

            mBlackout.setVisibility(View.VISIBLE);
            // Comment this out for now until we can decode the preview frame. This currently
            // just animates black-on-black because the surface flinger blacks out the surface
            // when the camera driver detaches the buffers.
            if (false) {
                Animation a = new android.view.animation.TranslateAnimation(mBlackout.getWidth(), 0 , 0, 0);
                a.setDuration(450);
                a.startNow();
                mBlackout.setAnimation(a);
            }
        }

        public void onSnap() {
            // If we are already in the middle of taking a snapshot then we should just save
            // the image after we have returned from the camera service.
            if (mStatus == SNAPSHOT_IN_PROGRESS || mStatus == SNAPSHOT_COMPLETED) {
                mKeepAndRestartPreview = true;
                mHandler.sendEmptyMessage(RESTART_PREVIEW);
                return;
            }

            // Don't check the filesystem here, we can't afford the latency. Instead, check the
            // cached value which was calculated when the preview was restarted.
            if (DEBUG_TIME_OPERATIONS) mShutterPressTime = System.currentTimeMillis();
            if (mPicturesRemaining < 1) {
                updateStorageHint(mPicturesRemaining);
                return;
            }

            mStatus = SNAPSHOT_IN_PROGRESS;

            mKeepAndRestartPreview = true;

            boolean getContentAction = isImageCaptureIntent();
            if (getContentAction) {
                mImageCapture.initiate(true);
            } else {
                mImageCapture.initiate(false);
            }
        }
    }

    private void setLastPictureThumb(byte[] data, Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 16;

        Bitmap lastPictureThumb = BitmapFactory.decodeByteArray(data, 0, data.length, options);

        setLastPictureThumb(lastPictureThumb, uri);
    }

    private void setLastPictureThumb(Bitmap lastPictureThumb, Uri uri) {

        final int PADDING_WIDTH = 2;
        final int PADDING_HEIGHT = 2;
        LayoutParams layoutParams = mLastPictureButton.getLayoutParams();
        // Make the mini-thumbnail size smaller than the button size so that the image corners
        // don't peek out from the rounded corners of the frame_thumbnail graphic:
        final int miniThumbWidth = layoutParams.width - 2 * PADDING_WIDTH;
        final int miniThumbHeight = layoutParams.height - 2 * PADDING_HEIGHT;

        lastPictureThumb = ImageManager.extractMiniThumb(lastPictureThumb,
                miniThumbWidth, miniThumbHeight);

        Drawable[] vignetteLayers = new Drawable[2];
        vignetteLayers[1] = getResources().getDrawable(R.drawable.frame_thumbnail);
        if (mThumbnails == null) {
            mThumbnails = new Drawable[2];
            mThumbnails[1] = new BitmapDrawable(lastPictureThumb);
            vignetteLayers[0] = mThumbnails[1];
        } else {
            mThumbnails[0] = mThumbnails[1];
            mThumbnails[1] = new BitmapDrawable(lastPictureThumb);
            mThumbnailTransition = new TransitionDrawable(mThumbnails);
            mShouldTransitionThumbnails = true;
            vignetteLayers[0] = mThumbnailTransition;
        }

        mVignette = new LayerDrawable(vignetteLayers);
        mVignette.setLayerInset(0, PADDING_WIDTH, PADDING_HEIGHT,
                PADDING_WIDTH, PADDING_HEIGHT);
        mLastPictureButton.setImageDrawable(mVignette);

        if (mLastPictureButton.getVisibility() != View.VISIBLE) {
            mShouldShowLastPictureButton = true;
        }
        mLastPictureThumb = lastPictureThumb;
        mLastPictureUri = uri;
    }

    static private String createName(long dateTaken) {
        return DateFormat.format("yyyy-MM-dd kk.mm.ss", dateTaken).toString();
    }

    static public Matrix GetDisplayMatrix(Bitmap b, ImageView v) {
        Matrix m = new Matrix();
        float bw = (float)b.getWidth();
        float bh = (float)b.getHeight();
        float vw = (float)v.getWidth();
        float vh = (float)v.getHeight();
        float scale, x, y;
        if (bw*vh > vw*bh) {
            scale = vh / bh;
            x = (vw - scale*bw)*0.5F;
            y = 0;
        } else {
            scale = vw / bw;
            x = 0;
            y = (vh - scale*bh)*0.5F;
        }
        m.setScale(scale, scale, 0.5F, 0.5F);
        m.postTranslate(x, y);
        return m;
    }

    private void postAfterKeep(final Runnable r) {
        Resources res = getResources();

        if (mSavingProgress != null) {
            mSavingProgress = ProgressDialog.show(this, res.getString(R.string.savingImage),
                    res.getString(R.string.wait));
        }

        Message msg = mHandler.obtainMessage(KEEP);
        msg.obj = r;
        msg.sendToTarget();
    }

    /** Called with the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // To reduce startup time, we open camera device in another thread.
        // We make sure the camera is opened at the end of onCreate.
        Thread openCameraThread = new Thread(new Runnable() {
            public void run() {
                mCameraDevice = android.hardware.Camera.open();
            }
        });
        openCameraThread.start();

        // To reduce startup time, we run some service creation code in another thread.
        // We make sure the services are loaded at the end of onCreate().
        Thread loadServiceThread = new Thread(new Runnable() {
            public void run() {
                mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                mOrientationListener = new OrientationEventListener(Camera.this) {
                    public void onOrientationChanged(int orientation) {
                        mLastOrientation = orientation;
                    }
                };
            }
        });
        loadServiceThread.start();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mContentResolver = getContentResolver();

        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.camera);

        mSurfaceView = (VideoPreview) findViewById(R.id.camera_preview);

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mBlackout = findViewById(R.id.blackout);

        if (!isImageCaptureIntent())  {
            mLastPictureButton = (ImageView) findViewById(R.id.last_picture_button);
            mLastPictureButton.setOnClickListener(this);
            loadLastThumb();
        }

        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnShutterButtonListener(this);

        try {
            mClickSound = new MediaPlayer();
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.camera_click);

            mClickSound.setDataSource(afd.getFileDescriptor(),
                             afd.getStartOffset(),
                             afd.getLength());

            if (mClickSound != null) {
                mClickSound.setAudioStreamType(AudioManager.STREAM_ALARM);
                mClickSound.prepare();
            }
        } catch (Exception ex) {
            Log.w(TAG, "Couldn't create click sound", ex);
        }

        mFocusIndicator = findViewById(R.id.focus_indicator);
        mFocusBlinkAnimation = AnimationUtils.loadAnimation(this, R.anim.auto_focus_blink);
        mFocusBlinkAnimation.setRepeatCount(Animation.INFINITE);
        mFocusBlinkAnimation.setRepeatMode(Animation.REVERSE);

        // We load the post_picture_panel layout only if it is needed.
        if (isImageCaptureIntent()) {
            ViewGroup cameraView = (ViewGroup)findViewById(R.id.camera);
            getLayoutInflater().inflate(R.layout.post_picture_panel,
                                        cameraView);
            mPostCaptureAlert = findViewById(R.id.post_picture_panel);
        }

        // Make sure the services are loaded.
        try {
            openCameraThread.join();
            loadServiceThread.join();
        } catch (InterruptedException ex) {
        }

        ImageManager.ensureOSXCompatibleFolder();
    }

    @Override
    public void onStart() {
        super.onStart();

        Thread t = new Thread(new Runnable() {
            public void run() {
                final boolean storageOK = calculatePicturesRemaining() > 0;

                if (!storageOK) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            updateStorageHint(mPicturesRemaining);
                        }
                    });
                }
            }
        });
        t.start();
    }

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.last_picture_button:
            viewLastImage();
            break;
        case R.id.attach:
            doAttach();
            break;
        case R.id.cancel:
            doCancel();
        }
    }

    private void doAttach() {
        Bitmap bitmap = mImageCapture.getLastBitmap();
        mCaptureObject.setDone(true);

        String cropValue = null;
        Uri saveUri = null;

        Bundle myExtras = getIntent().getExtras();
        if (myExtras != null) {
            saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            cropValue = myExtras.getString("crop");
        }


        if (cropValue == null) {
            /*
             * First handle the no crop case -- just return the value.  If the caller
             * specifies a "save uri" then write the data to it's stream.  Otherwise,
             * pass back a scaled down version of the bitmap directly in the extras.
             */
            if (saveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(saveUri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream);
                    outputStream.close();

                    setResult(RESULT_OK);
                    finish();
                } catch (IOException ex) {
                    //
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException ex) {

                        }
                    }
                }
            } else {
                float scale = .5F;
                Matrix m = new Matrix();
                m.setScale(scale, scale);

                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        m, true);

                setResult(RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
                finish();
            }
        }
        else {
            /*
             * Save the image to a temp file and invoke the cropper
             */
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = openFileOutput(sTempCropFilename, 0);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, tempStream);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            } catch (IOException ex) {
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            } finally {
                if (tempStream != null) {
                    try {
                        tempStream.close();
                    } catch (IOException ex) {

                    }
                }
            }

            Bundle newExtras = new Bundle();
            if (cropValue.equals("circle"))
                newExtras.putString("circleCrop", "true");
            if (saveUri != null)
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, saveUri);
            else
                newExtras.putBoolean("return-data", true);

            Intent cropIntent = new Intent();
            cropIntent.setClass(Camera.this, CropImage.class);
            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            startActivityForResult(cropIntent, CROP_MSG);
        }
    }

    private void doCancel() {
        setResult(RESULT_CANCELED, new Intent());
        finish();
    }

    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        switch (button.getId()) {
            case R.id.shutter_button:
                doFocus(pressed);
                break;
        }
    }

    public void onShutterButtonClick(ShutterButton button) {
        switch (button.getId()) {
            case R.id.shutter_button:
                doSnap(false);
                break;
        }
    }

    private void updateStorageHint() {
      updateStorageHint(MenuHelper.calculatePicturesRemaining());
    }

    private OnScreenHint mStorageHint;

    private void updateStorageHint(int remaining) {
        String noStorageText = null;

        if (remaining == MenuHelper.NO_STORAGE_ERROR) {
            String state = Environment.getExternalStorageState();
            if (state == Environment.MEDIA_CHECKING) {
                noStorageText = getString(R.string.preparing_sd);
            } else {
                noStorageText = getString(R.string.no_storage);
            }
        } else if (remaining < 1) {
            noStorageText = getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, noStorageText);
            } else {
                mStorageHint.setText(noStorageText);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);

        mPausing = false;
        mOrientationListener.enable();

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        mDidRegister = true;

        mImageCapture = new ImageCapture();

        restartPreview();

        if (mPreferences.getBoolean("pref_camera_recordlocation_key", false))
            startReceivingLocationUpdates();

        updateFocusIndicator();

        try {
            mFocusToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, FOCUS_BEEP_VOLUME);
        } catch (RuntimeException e) {
            Log.w(TAG, "Exception caught while creating local tone generator: " + e);
            mFocusToneGenerator = null;
        }

        mBlackout.setVisibility(View.GONE);
    }

    private ImageManager.DataLocation dataLocation() {
        return ImageManager.DataLocation.EXTERNAL;
    }

    private static final int BUFSIZE = 4096;

    // Stores the thumbnail and URI of last-picture-taken to SD card, so we can
    // load it the next time the Camera app starts.
    private void storeLastThumb() {
        if (mLastPictureUri != null && mLastPictureThumb != null) {
            try {
                FileOutputStream f = new FileOutputStream(ImageManager.getLastThumbPath());
                try {
                    BufferedOutputStream b = new BufferedOutputStream(f, BUFSIZE);
                    try {
                        DataOutputStream d = new DataOutputStream(b);
                        try {
                            d.writeUTF(mLastPictureUri.toString());
                            mLastPictureThumb.compress(Bitmap.CompressFormat.PNG, 100, d);
                        } finally {
                            d.close();
                            b = null;
                            f = null;
                        }
                    } finally {
                        if (b != null) {
                            b.close();
                            f = null;
                        }
                    }
                } finally {
                    if (f != null) {
                        f.close();
                    }
                }
            } catch (IOException e) {
            }
        }
    }

    // Loads the thumbnail and URI of last-picture-taken from SD card.
    private void loadLastThumb() {
        try {
            FileInputStream f = new FileInputStream(ImageManager.getLastThumbPath());
            try {
                BufferedInputStream b = new BufferedInputStream(f, BUFSIZE);
                try {
                    DataInputStream d = new DataInputStream(b);
                    try {
                        Uri lastUri = Uri.parse(d.readUTF());
                        Bitmap lastThumb = BitmapFactory.decodeStream(d);
                        setLastPictureThumb(lastThumb, lastUri);
                    } finally {
                        d.close();
                        b = null;
                        f = null;
                    }
                } finally {
                    if (b != null) {
                        b.close();
                        f = null;
                    }
                }
            } finally {
                if (f != null) {
                    f.close();
                }
            }
        } catch (IOException e) {
        }
    }

    @Override
    public void onStop() {
        keep();
        stopPreview();
        closeCamera();
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        super.onStop();
    }

    @Override
    protected void onPause() {
        keep();

        mPausing = true;
        mOrientationListener.disable();

        stopPreview();

        if (!mImageCapture.mCapturing) {
            closeCamera();
        }
        if (mDidRegister) {
            unregisterReceiver(mReceiver);
            mDidRegister = false;
        }
        stopReceivingLocationUpdates();

        if (mFocusToneGenerator != null) {
            mFocusToneGenerator.release();
            mFocusToneGenerator = null;
        }

        storeLastThumb();
        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CROP_MSG: {
                Intent intent = new Intent();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                setResult(resultCode, intent);
                finish();

                File path = getFileStreamPath(sTempCropFilename);
                path.delete();

                break;
            }
        }
    }

    private void autoFocus() {
        updateFocusIndicator();
        if (!mIsFocusing) {
            if (mCameraDevice != null) {
                mIsFocusing = true;
                mIsFocused = false;
                mCameraDevice.autoFocus(mAutoFocusCallback);
            }
        }
    }

    private void clearFocus() {
        mIsFocusing = false;
        mIsFocused = false;
        mIsFocusButtonPressed = false;
    }

    private void updateFocusIndicator() {
        mHandler.post(new Runnable() {
            public void run() {
                if (mIsFocusing || !mIsFocusButtonPressed) {
                    mFocusIndicator.setVisibility(View.GONE);
                    mFocusIndicator.clearAnimation();
                } else {
                    if (mIsFocused) {
                        mFocusIndicator.setVisibility(View.VISIBLE);
                        mFocusIndicator.clearAnimation();
                    } else {
                        mFocusIndicator.setVisibility(View.VISIBLE);
                        mFocusIndicator.startAnimation(mFocusBlinkAnimation);
                    }
                }
            }
        });
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mStatus == SNAPSHOT_IN_PROGRESS) {
                    // ignore backs while we're taking a picture
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_FOCUS:
                if (event.getRepeatCount() == 0) {
                    doFocus(true);
                }
                return true;
            case KeyEvent.KEYCODE_CAMERA:
                if (event.getRepeatCount() == 0) {
                    doSnap(false);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move the
                // focus to the shutter button and press it.
                if (event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After the shutter button
                    // gets the focus, doFocus() will be called again but it is fine.
                    doFocus(true);
                    if (mShutterButton.isInTouchMode()) {
                        mShutterButton.requestFocusFromTouch();
                    } else {
                        mShutterButton.requestFocus();
                    }
                    mShutterButton.setPressed(true);
                }
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                doFocus(false);
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void doSnap(boolean needAutofocus) {
        // The camera operates in focus-priority mode, meaning that we take a picture
        // when focusing completes, and only if it completes successfully. If the user
        // has half-pressed the shutter and already locked focus, we can take the photo
        // right away, otherwise we need to start AF.
        if (mIsFocused || !mPreviewing) {
            // doesn't get set until the idler runs
            if (mCaptureObject != null) {
                mCaptureObject.onSnap();
            }
            clearFocus();
            updateFocusIndicator();
        } else {
            // Half pressing the shutter (i.e. the focus button event) will already have
            // requested AF for us, so just request capture on focus here. If AF has
            // already failed, we don't want to trigger it again.
            mCaptureOnFocus = true;
            if (needAutofocus && !mIsFocusButtonPressed) {
                // But we do need to start AF for DPAD_CENTER
                autoFocus();
            }
        }
    }

    private void doFocus(boolean pressed) {
        if (pressed) {
            mIsFocusButtonPressed = true;
            mCaptureOnFocus = false;
            if (mPreviewing) {
                autoFocus();
            } else if (mCaptureObject != null) {
                // Save and restart preview
                mCaptureObject.onSnap();
            }
        } else {
            clearFocus();
            updateFocusIndicator();
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // if we're creating the surface, start the preview as well.
        boolean preview = holder.isCreating();
        setViewFinder(w, h, preview);
        mCaptureObject = mImageCapture;
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        mSurfaceHolder = null;
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.release();
            mCameraDevice = null;
            mPreviewing = false;
        }
    }

    private boolean ensureCameraDevice() {
        if (mCameraDevice == null) {
            mCameraDevice = android.hardware.Camera.open();
        }
        return mCameraDevice != null;
    }
    
    private boolean isLastPictureValid() {
        boolean isValid = true;
        if (mLastPictureUri == null)  return false;
        try {
            mContentResolver.openFileDescriptor(mLastPictureUri, "r").close();
        }
        catch (Exception ex) {
            isValid = false;
            Log.e(TAG, ex.toString());
        }
        return isValid;
    }
    
    private void updateLastImage() {
        ImageManager.IImageList list = ImageManager.instance().allImages(
            this,
            mContentResolver,
            dataLocation(),
            ImageManager.INCLUDE_IMAGES,
            ImageManager.SORT_ASCENDING,
            ImageManager.CAMERA_IMAGE_BUCKET_ID);
        int count = list.getCount();
        if (count > 0) {
            ImageManager.IImage image = list.getImageAt(count-1);
            mLastPictureUri = image.fullSizeImageUri();
            Log.v(TAG, "updateLastImage: count="+ count + 
                ", lastPictureUri="+mLastPictureUri);
            setLastPictureThumb(image.miniThumbBitmap(), mLastPictureUri);
        } else {
            mLastPictureUri = null;
        }
        list.deactivate();
    }

    private void restartPreview() {
        VideoPreview surfaceView = mSurfaceView;

        // make sure the surfaceview fills the whole screen when previewing
        surfaceView.setAspectRatio(VideoPreview.DONT_CARE);
        setViewFinder(mOriginalViewFinderWidth, mOriginalViewFinderHeight, true);
        mStatus = IDLE;

        // Calculate this in advance of each shot so we don't add to shutter latency. It's true that
        // someone else could write to the SD card in the mean time and fill it, but that could have
        // happened between the shutter press and saving the JPEG too.
        // TODO: The best longterm solution is to write a reserve file of maximum JPEG size, always
        // let the user take a picture, and delete that file if needed to save the new photo.
        calculatePicturesRemaining();

        if (!isLastPictureValid()) {
            updateLastImage();
        }
    
        if (mShouldShowLastPictureButton) {
            mShouldShowLastPictureButton = false;
            mLastPictureButton.setVisibility(View.VISIBLE);
            Animation a = mShowLastPictureButtonAnimation;
            a.setDuration(500);
            mLastPictureButton.setAnimation(a);
        }

        if (mShouldTransitionThumbnails) {
            mShouldTransitionThumbnails = false;
            mThumbnailTransition.startTransition(500);
        }
    }

    private void setViewFinder(int w, int h, boolean startPreview) {
        if (mPausing)
            return;

        if (mPreviewing &&
                w == mViewFinderWidth &&
                h == mViewFinderHeight) {
            return;
        }

        if (!ensureCameraDevice())
            return;

        if (mSurfaceHolder == null)
            return;

        if (isFinishing())
            return;

        if (mPausing)
            return;

        // remember view finder size
        mViewFinderWidth = w;
        mViewFinderHeight = h;
        if (mOriginalViewFinderHeight == 0) {
            mOriginalViewFinderWidth = w;
            mOriginalViewFinderHeight = h;
        }

        if (startPreview == false)
            return;

        /*
         * start the preview if we're asked to...
         */

        // we want to start the preview and we're previewing already,
        // stop the preview first (this will blank the screen).
        if (mPreviewing)
            stopPreview();

        // this blanks the screen if the surface changed, no-op otherwise
        try {
            mCameraDevice.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException exception) {
            mCameraDevice.release();
            mCameraDevice = null;
            // TODO: add more exception handling logic here
            return;
        }

        // request the preview size, the hardware may not honor it,
        // if we depended on it we would have to query the size again
        mParameters = mCameraDevice.getParameters();
        mParameters.setPreviewSize(w, h);
        try {
            mCameraDevice.setParameters(mParameters);
        } catch (IllegalArgumentException e) {
            // Ignore this error, it happens in the simulator.
        }


        final long wallTimeStart = SystemClock.elapsedRealtime();
        final long threadTimeStart = Debug.threadCpuTimeNanos();

        final Object watchDogSync = new Object();
        Thread watchDog = new Thread(new Runnable() {
            public void run() {
                int next_warning = 1;
                while (true) {
                    try {
                        synchronized (watchDogSync) {
                            watchDogSync.wait(1000);
                        }
                    } catch (InterruptedException ex) {
                        //
                    }
                    if (mPreviewing)
                        break;

                    int delay = (int) (SystemClock.elapsedRealtime() - wallTimeStart) / 1000;
                    if (delay >= next_warning) {
                        if (delay < 120) {
                            Log.e(TAG, "preview hasn't started yet in " + delay + " seconds");
                        } else {
                            Log.e(TAG, "preview hasn't started yet in " + (delay / 60) + " minutes");
                        }
                        if (next_warning < 60) {
                            next_warning <<= 1;
                            if (next_warning == 16) {
                                next_warning = 15;
                            }
                        } else {
                            next_warning += 60;
                        }
                    }
                }
            }
        });

        watchDog.start();

        if (Config.LOGV)
            Log.v(TAG, "calling mCameraDevice.startPreview");
        try {
            mCameraDevice.startPreview();
        } catch (Throwable e) {
            // TODO: change Throwable to IOException once android.hardware.Camera.startPreview
            // properly declares that it throws IOException.
        }
        mPreviewing = true;

        synchronized (watchDogSync) {
            watchDogSync.notify();
        }

        long threadTimeEnd = Debug.threadCpuTimeNanos();
        long wallTimeEnd = SystemClock.elapsedRealtime();
        if ((wallTimeEnd - wallTimeStart) > 3000) {
            Log.w(TAG, "startPreview() to " + (wallTimeEnd - wallTimeStart) + " ms. Thread time was"
                    + (threadTimeEnd - threadTimeStart) / 1000000 + " ms.");
        }
    }

    private void stopPreview() {
        if (mCameraDevice != null && mPreviewing) {
            mCameraDevice.stopPreview();
        }
        mPreviewing = false;
    }

    void gotoGallery() {
        MenuHelper.gotoCameraImageGallery(this);
    }

    private void viewLastImage() {
        Uri targetUri = mLastPictureUri;
        if (targetUri != null && isLastPictureValid()) {
            targetUri = targetUri.buildUpon().
                appendQueryParameter("bucketId", ImageManager.CAMERA_IMAGE_BUCKET_ID).build();
            Intent intent = new Intent(Intent.ACTION_VIEW, targetUri);
            intent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            intent.putExtra(MediaStore.EXTRA_FULL_SCREEN, true);
            intent.putExtra(MediaStore.EXTRA_SHOW_ACTION_ICONS, true);
            intent.putExtra("com.android.camera.ReviewMode", true);

            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException ex) {
                // ignore.
            }
        } else {
            Log.e(TAG, "Can't view last image.");
        }
    }

    void keep() {
        cancelSavingNotification();
        if (mCaptureObject != null) {
            mCaptureObject.dismissFreezeFrame(true);
        }
    };

    void toss() {
        cancelSavingNotification();
        if (mCaptureObject != null) {
            mCaptureObject.cancelSave();
        }
    };

    private ImageManager.IImage getImageForURI(Uri uri) {
        ImageManager.IImageList list = ImageManager.instance().allImages(
                this,
                mContentResolver,
                dataLocation(),
                ImageManager.INCLUDE_IMAGES,
                ImageManager.SORT_ASCENDING);
        ImageManager.IImage image = list.getImageForUri(uri);
        list.deactivate();
        return image;
    }


    private void startReceivingLocationUpdates() {
        if (mLocationManager != null) {
            try {
                mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000,
                        0F,
                        mLocationListeners[1]);
            } catch (java.lang.SecurityException ex) {
                // ok
            } catch (IllegalArgumentException ex) {
                if (Config.LOGD) {
                    Log.d(TAG, "provider does not exist " + ex.getMessage());
                }
            }
            try {
                mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000,
                        0F,
                        mLocationListeners[0]);
            } catch (java.lang.SecurityException ex) {
                // ok
            } catch (IllegalArgumentException ex) {
                if (Config.LOGD) {
                    Log.d(TAG, "provider does not exist " + ex.getMessage());
                }
            }
        }
    }

    private void stopReceivingLocationUpdates() {
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    // ok
                }
            }
        }
    }

    private Location getCurrentLocation() {
        Location l = null;

        // go in best to worst order
        for (int i = 0; i < mLocationListeners.length; i++) {
            l = mLocationListeners[i].current();
            if (l != null)
                break;
        }

        return l;
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        if (mImageSavingItem && !mMenuSelectionMade) {
            // save the image if we presented the "advanced" menu
            // which happens if "menu" is pressed while in
            // SNAPSHOT_IN_PROGRESS  or SNAPSHOT_COMPLETED modes
            keep();
            mHandler.sendEmptyMessage(RESTART_PREVIEW);
        }
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            if (mStatus == SNAPSHOT_IN_PROGRESS) {
                cancelAutomaticPreviewRestart();
                mMenuSelectionMade = false;
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        mMenuSelectionMade = false;

        for (int i = 1; i <= MenuHelper.MENU_ITEM_MAX; i++) {
            if (i != MenuHelper.GENERIC_ITEM) {
                menu.setGroupVisible(i, false);
            }
        }

        if (mStatus == SNAPSHOT_IN_PROGRESS || mStatus == SNAPSHOT_COMPLETED) {
            menu.setGroupVisible(MenuHelper.IMAGE_SAVING_ITEM, true);
            mImageSavingItem = true;
        } else {
            menu.setGroupVisible(MenuHelper.IMAGE_MODE_ITEM, true);
            mImageSavingItem = false;
        }

        if (mCaptureObject != null)
            mCaptureObject.cancelAutoDismiss();

        return true;
    }

    private void cancelAutomaticPreviewRestart() {
        mKeepAndRestartPreview = false;
        mHandler.removeMessages(RESTART_PREVIEW);
    }

    private boolean isImageCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action));
    }

    private void showPostCaptureAlert() {
        if (isImageCaptureIntent()) {
            mPostCaptureAlert.setVisibility(View.VISIBLE);
            int[] pickIds = {R.id.attach, R.id.cancel};
            for(int id : pickIds) {
                View view = mPostCaptureAlert.findViewById(id);
                view.setOnClickListener(this);
                Animation animation = new AlphaAnimation(0F, 1F);
                animation.setDuration(500);
                view.setAnimation(animation);
            }
        }
    }

    private void hidePostCaptureAlert() {
        if (isImageCaptureIntent()) {
            mPostCaptureAlert.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (isImageCaptureIntent()) {
            // No options menu for attach mode.
            return false;
        } else {
            addBaseMenuItems(menu);
            MenuHelper.addImageMenuItems(
                    menu,
                    MenuHelper.INCLUDE_ALL & ~MenuHelper.INCLUDE_ROTATE_MENU,
                    true,
                    Camera.this,
                    mHandler,

                    // Handler for deletion
                    new Runnable() {
                        public void run() {
                            if (mCaptureObject != null) {
                                mCaptureObject.cancelSave();
                                Uri uri = mCaptureObject.getLastCaptureUri();
                                if (uri != null) {
                                    mContentResolver.delete(uri, null, null);
                                }
                            }
                        }
                    },
                    new MenuHelper.MenuInvoker() {
                        public void run(final MenuHelper.MenuCallback cb) {
                            mMenuSelectionMade = true;
                            postAfterKeep(new Runnable() {
                                public void run() {
                                    cb.run(mSelectedImageGetter.getCurrentImageUri(), mSelectedImageGetter.getCurrentImage());
                                    if (mCaptureObject != null)
                                        mCaptureObject.dismissFreezeFrame(true);
                                }
                            });
                        }
                    });

            MenuItem gallery = menu.add(MenuHelper.IMAGE_SAVING_ITEM, MENU_SAVE_GALLERY_PHOTO, 0, R.string.camera_gallery_photos_text).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
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
        }
        return true;
    }

    SelectedImageGetter mSelectedImageGetter =
        new SelectedImageGetter() {
            public ImageManager.IImage getCurrentImage() {
                return getImageForURI(getCurrentImageUri());
            }
            public Uri getCurrentImageUri() {
                keep();
                return mCaptureObject.getLastCaptureUri();
            }
        };

    private int calculatePicturesRemaining() {
        mPicturesRemaining = MenuHelper.calculatePicturesRemaining();
        return mPicturesRemaining;
    }

    private void addBaseMenuItems(Menu menu) {
        MenuHelper.addSwitchModeMenuItem(menu, this, true);
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
                intent.setClass(Camera.this, CameraSettings.class);
                startActivity(intent);
                return true;
            }
        });
        item.setIcon(android.R.drawable.ic_menu_preferences);
    }
}

