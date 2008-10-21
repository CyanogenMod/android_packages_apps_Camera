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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
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
import android.os.StatFs;
import android.os.SystemClock;
import android.pim.DateFormat;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.util.Config;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;

public class Camera extends Activity implements View.OnClickListener, SurfaceHolder.Callback {
    
    private static final String TAG = "camera";
    
    private static final boolean DEBUG = false;
    
    private static final int CROP_MSG = 1;
    private static final int KEEP = 2;
    private static final int RESTART_PREVIEW = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    
    private static final int SCREEN_DELAY = 2 * 60 * 1000;
    private static final int FOCUS_BEEP_VOLUME = 100;

    private static final int NO_STORAGE_ERROR = -1;
    private static final int CANNOT_STAT_ERROR = -2;
    
    public static final int MENU_SWITCH_TO_VIDEO = 0;
    public static final int MENU_FLASH_SETTING = 1;
    public static final int MENU_FLASH_AUTO = 2;
    public static final int MENU_FLASH_ON = 3;
    public static final int MENU_FLASH_OFF = 4;
    public static final int MENU_SETTINGS = 5;
    public static final int MENU_GALLERY_PHOTOS = 6;
    public static final int MENU_SAVE_SELECT_PHOTOS = 30;
    public static final int MENU_SAVE_NEW_PHOTO = 31;
    public static final int MENU_SAVE_SELECTVIDEO = 32;
    public static final int MENU_SAVE_TAKE_NEW_VIDEO = 33;
    public static final int MENU_SAVE_GALLERY_PHOTO = 34;
    public static final int MENU_SAVE_GALLERY_VIDEO_PHOTO = 35; 
    public static final int MENU_SAVE_CAMERA_DONE = 36; 
    public static final int MENU_SAVE_CAMERA_VIDEO_DONE = 37;
    
    Toast mToast;
    OrientationListener mOrientationListener;
    int mLastOrientation = OrientationListener.ORIENTATION_UNKNOWN;
    SharedPreferences mPreferences;
    
    static final int IDLE = 1;
    static final int SNAPSHOT_IN_PROGRESS = 2;
    static final int SNAPSHOT_COMPLETED = 3;
    
    int mStatus = IDLE;

    android.hardware.Camera mCameraDevice;
    SurfaceView mSurfaceView;
    SurfaceHolder mSurfaceHolder = null;
    ImageView mBlackout = null;

    int mViewFinderWidth, mViewFinderHeight;
    boolean mPreviewing = false;

    MediaPlayer mClickSound;

    Capturer mCaptureObject;
    ImageCapture mImageCapture = null;
    
    boolean mPausing = false;

    boolean mIsFocusing = false;
    boolean mIsFocused = false;
    boolean mIsFocusButtonPressed = false;
    boolean mCaptureOnFocus = false;

    static ContentResolver mContentResolver;
    boolean mDidRegister = false;

    int mCurrentZoomIndex = 0;
    
    private static final int STILL_MODE = 1;
    private static final int VIDEO_MODE = 2;
    private int mMode = STILL_MODE;

    ArrayList<MenuItem> mGalleryItems = new ArrayList<MenuItem>();
    
    boolean mMenuSelectionMade;
    
    View mPostPictureAlert;
    LocationManager mLocationManager = null;

    private Animation mFocusBlinkAnimation;
    private View mFocusIndicator;
    private ToneGenerator mFocusToneGenerator;
    
    private ShutterCallback mShutterCallback = new ShutterCallback();
    private RawPictureCallback mRawPictureCallback = new RawPictureCallback();
    private JpegPictureCallback mJpegPictureCallback = new JpegPictureCallback();
    private AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();
    private long mShutterPressTime;
    private int mPicturesRemaining;

    private boolean mKeepAndRestartPreview;

    private Handler mHandler = new MainHandler(); 
    private ProgressDialog mSavingProgress;
    
    interface Capturer {
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
                // TODO put up a "please wait" message
                // TODO also listen for the media scanner finished message
                showStorageToast();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
                showStorageToast();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(Camera.this, getResources().getString(R.string.wait), 5000);
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                showStorageToast();
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
            if (DEBUG) {
                long now = System.currentTimeMillis();
                Log.v(TAG, "********** Total shutter lag " + (now - mShutterPressTime) + " ms");
            }
            if (mClickSound != null) {
                mClickSound.seekTo(0);
                mClickSound.start();
            }
        }
    };
    
    private final class RawPictureCallback implements PictureCallback { 
        public void onPictureTaken(byte [] rawData, android.hardware.Camera camera) {
            if (Config.LOGV)
                Log.v(TAG, "got RawPictureCallback...");
            mRawPictureCallbackTime = System.currentTimeMillis();
            mBlackout.setVisibility(View.INVISIBLE);
            if (!isPickIntent() && mPreferences.getBoolean("pref_camera_postpicturemenu_key", true)) {
                mPostPictureAlert.setVisibility(View.VISIBLE);
            }
        }
    };
    
    private final class JpegPictureCallback implements PictureCallback {
        public void onPictureTaken(byte [] jpegData, android.hardware.Camera camera) {
            if (Config.LOGV)
                Log.v(TAG, "got JpegPictureCallback...");

            mImageCapture.storeImage(jpegData, camera);

            mStatus = SNAPSHOT_COMPLETED;

            if (!mPreferences.getBoolean("pref_camera_postpicturemenu_key", true)) {
                if (mKeepAndRestartPreview) {
                    long delay = 1500 - (System.currentTimeMillis() - mRawPictureCallbackTime);
                    mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, Math.max(delay, 0));
                }
                return;
            }

            if (mKeepAndRestartPreview) {
                mKeepAndRestartPreview = false;
                mPostPictureAlert.setVisibility(View.INVISIBLE);
                
                // Post this message so that we can finish processing the request. This also
                // prevents the preview from showing up before mPostPictureAlert is dismissed.
                mHandler.sendEmptyMessage(RESTART_PREVIEW);
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
                    mFocusToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
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
        
        private void storeImage(byte[] data) {
            try {
                if (DEBUG) {
                    startTiming();
                }
                    
                mLastContentUri = ImageManager.instance().addImage(
                        Camera.this,
                        mContentResolver,
                        DateFormat.format("yyyy-MM-dd kk.mm.ss", System.currentTimeMillis()).toString(), 
                        "",
                        System.currentTimeMillis(),
                        // location for the database goes here
                        null,
                        0,   // the dsp will use the right orientation so don't "double set it"
                        ImageManager.CAMERA_IMAGE_BUCKET_NAME,
                        null);

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
                
                if (DEBUG) {
                    stopTiming();
                    Log.d(TAG, "Storing image took " + (mWallTimeEnd - mWallTimeStart) + " ms. " +
                            "Thread time was " + ((mThreadTimeEnd - mThreadTimeStart) / 1000000) + 
                            " ms.");
                }
            } catch (Exception ex) {
                Log.e(TAG, "Exception while compressing image.", ex);
            }
        }
        
        public void storeImage(byte[] data, android.hardware.Camera camera) {
            boolean captureOnly = isPickIntent();
            
            if (!captureOnly) {
                storeImage(data);
                sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", mLastContentUri));
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                
                if (DEBUG) {
                    startTiming();
                }
                
                mCaptureOnlyBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                
                if (DEBUG) {
                    stopTiming();
                    Log.d(TAG, "Decoded mCaptureOnly bitmap (" + mCaptureOnlyBitmap.getWidth() +
                            "x" + mCaptureOnlyBitmap.getHeight() + " ) in " + 
                            (mWallTimeEnd - mWallTimeStart) + " ms. Thread time was " +  
                            ((mThreadTimeEnd - mThreadTimeStart) / 1000000) + " ms.");
                }
                
                openOptionsMenu();
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
            android.hardware.Camera.Parameters parameters = mCameraDevice.getParameters();
            // Quality 75 has visible artifacts, and quality 90 looks great but the files begin to
            // get large. 85 is a good compromise between the two.
            parameters.set("jpeg-quality", 85);
            parameters.set("rotation", latchedOrientation);
            if (loc != null) {
                parameters.set("gps-latitude",  String.valueOf(loc.getLatitude()));
                parameters.set("gps-longitude", String.valueOf(loc.getLongitude()));
                parameters.set("gps-altitude",  String.valueOf(loc.getAltitude()));
                parameters.set("gps-timestamp", String.valueOf(loc.getTime()));
            } else {
                parameters.remove("gps-latitude");
                parameters.remove("gps-longitude");
                parameters.remove("gps-altitude");
                parameters.remove("gps-timestamp");
            }

            Size pictureSize = parameters.getPictureSize();
            Size previewSize = parameters.getPreviewSize();

            // resize the SurfaceView to the aspect-ratio of the still image
            // and so that we can see the full image that was taken
            ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
            if (pictureSize.width*previewSize.height < previewSize.width*pictureSize.height) {
                lp.width = (pictureSize.width * previewSize.height) / pictureSize.height; 
            } else {
                lp.height = (pictureSize.height * previewSize.width) / pictureSize.width; 
            }
            mSurfaceView.requestLayout();

            mCameraDevice.setParameters(parameters);
       
            mCameraDevice.takePicture(mShutterCallback, mRawPictureCallback, mJpegPictureCallback);
            
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
                mPostPictureAlert.setVisibility(View.INVISIBLE);
                return;
            }

            // Don't check the filesystem here, we can't afford the latency. Instead, check the
            // cached value which was calculated when the preview was restarted.
            if (DEBUG) mShutterPressTime = System.currentTimeMillis();
            if (mPicturesRemaining < 1) {
                showStorageToast();
                return;
            }
            
            mStatus = SNAPSHOT_IN_PROGRESS;

            mKeepAndRestartPreview = !mPreferences.getBoolean("pref_camera_postpicturemenu_key", true);

            boolean getContentAction = isPickIntent();
            if (getContentAction) {
                mImageCapture.initiate(true);
            } else {
                mImageCapture.initiate(false);
            }
        }
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

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mContentResolver = getContentResolver();

        //setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.camera);

        mSurfaceView = (SurfaceView) findViewById(R.id.camera_preview);
        
        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceCreated / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        mBlackout = (ImageView) findViewById(R.id.blackout);
        mBlackout.setBackgroundDrawable(new ColorDrawable(0xFF000000));

        mPostPictureAlert = findViewById(R.id.post_picture_panel);
        View b;
        
        b = findViewById(R.id.save);
        b.setOnClickListener(this);
        
        b = findViewById(R.id.discard);
        b.setOnClickListener(this);
        
        b = findViewById(R.id.share);
        b.setOnClickListener(this);

        b = findViewById(R.id.setas);
        b.setOnClickListener(this);
        
        try {
            mClickSound = new MediaPlayer();
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.camera_click);

            mClickSound.setDataSource(afd.getFileDescriptor(),
                             afd.getStartOffset(),
                             afd.getLength());

            if (mClickSound != null) {
                mClickSound.setAudioStreamType(AudioManager.STREAM_SYSTEM);
                mClickSound.prepare();
            }
        } catch (Exception ex) {
            Log.w(TAG, "Couldn't create click sound", ex);
        }
        
        mOrientationListener = new OrientationListener(this) {
            public void onOrientationChanged(int orientation) {
                mLastOrientation = orientation;
            }
        };
        
        mFocusIndicator = findViewById(R.id.focus_indicator);
        mFocusBlinkAnimation = AnimationUtils.loadAnimation(this, R.anim.auto_focus_blink);
        mFocusBlinkAnimation.setRepeatCount(Animation.INFINITE);
        mFocusBlinkAnimation.setRepeatMode(Animation.REVERSE);
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
            case R.id.save: {
                mPostPictureAlert.setVisibility(View.INVISIBLE);
                postAfterKeep(null);
                break;
            }
                
            case R.id.discard: {
                if (mCaptureObject != null) {
                    mCaptureObject.cancelSave();
                    Uri uri = mCaptureObject.getLastCaptureUri();
                    if (uri != null) {
                        mContentResolver.delete(uri, null, null);
                    }
                    mCaptureObject.dismissFreezeFrame(true);
                }
                mPostPictureAlert.setVisibility(View.INVISIBLE);
                break;
            }
            
            case R.id.share: {
                mPostPictureAlert.setVisibility(View.INVISIBLE);
                postAfterKeep(new Runnable() {
                    public void run() {
                        Uri u = mCaptureObject.getLastCaptureUri();
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_SEND);
                        intent.setType("image/jpeg");
                        intent.putExtra(Intent.EXTRA_STREAM, u);
                        try {
                            startActivity(Intent.createChooser(intent, getText(R.string.sendImage)));
                        } catch (android.content.ActivityNotFoundException ex) {
                            Toast.makeText(Camera.this, R.string.no_way_to_share_video, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                break;
            }
            
            case R.id.setas: {
                mPostPictureAlert.setVisibility(View.INVISIBLE);
                postAfterKeep(new Runnable() {
                    public void run() {
                        Uri u = mCaptureObject.getLastCaptureUri();
                        Intent intent = new Intent(Intent.ACTION_ATTACH_DATA, u);
                        try {
                            startActivity(Intent.createChooser(intent, getText(R.string.setImage)));
                        } catch (android.content.ActivityNotFoundException ex) {
                            Toast.makeText(Camera.this, R.string.no_way_to_share_video, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                break;
            }
        }
    }
    
    void keepVideo() {
    };

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
        mOrientationListener.enable();

        if (isPickIntent()) {
            mMode = STILL_MODE;
        }

        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        mDidRegister = true;

        mImageCapture = new ImageCapture();

        restartPreview();
        
        if (mPreferences.getBoolean("pref_camera_recordlocation_key", false))
            startReceivingLocationUpdates();
        
        updateFocusIndicator();

        mFocusToneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, FOCUS_BEEP_VOLUME);
        
        mBlackout.setVisibility(View.INVISIBLE);
    }

    private ImageManager.DataLocation dataLocation() {
        return ImageManager.DataLocation.EXTERNAL;
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
        mPostPictureAlert.setVisibility(View.INVISIBLE);

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
        mFocusToneGenerator.release();
        mFocusToneGenerator = null;

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
                if (mStatus == SNAPSHOT_IN_PROGRESS || mStatus == SNAPSHOT_COMPLETED) {
                    if (mPostPictureAlert.getVisibility() == View.VISIBLE) {
                        keep();
                        mPostPictureAlert.setVisibility(View.INVISIBLE);
                        restartPreview();
                    }
                    // ignore backs while we're taking a picture
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_FOCUS:
                mIsFocusButtonPressed = true;
                if (event.getRepeatCount() == 0) {
                    if (mPreviewing) {
                        autoFocus();
                    } else if (mCaptureObject != null) {
                        // Save and restart preview
                        mCaptureObject.onSnap();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
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
                        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && !mIsFocusButtonPressed) {
                            // But we do need to start AF for DPAD_CENTER
                            autoFocus();
                        }
                    }
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                mPostPictureAlert.setVisibility(View.INVISIBLE);
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                clearFocus();
                updateFocusIndicator();
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mMode == STILL_MODE) {
            // if we're creating the surface, start the preview as well.
            boolean preview = holder.isCreating();
            setViewFinder(w, h, preview);
            mCaptureObject = mImageCapture;
        }
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
    
    private void restartPreview() {
        SurfaceView surfaceView = mSurfaceView;
        if (surfaceView == null || 
                surfaceView.getWidth() == 0 || surfaceView.getHeight() == 0) {
            return;
        }
        // make sure the surfaceview fills the whole screen when previewing
        ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.FILL_PARENT;
        lp.height = ViewGroup.LayoutParams.FILL_PARENT;
        surfaceView.requestLayout();
        setViewFinder(mViewFinderWidth, mViewFinderHeight, true);
        mStatus = IDLE;

        // Calculate this in advance of each shot so we don't add to shutter latency. It's true that
        // someone else could write to the SD card in the mean time and fill it, but that could have
        // happened between the shutter press and saving the JPEG too.
        // TODO: The best longterm solution is to write a reserve file of maximum JPEG size, always
        // let the user take a picture, and delete that file if needed to save the new photo.
        calculatePicturesRemaining();
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
        mCameraDevice.setPreviewDisplay(mSurfaceHolder);
        

        // request the preview size, the hardware may not honor it,
        // if we depended on it we would have to query the size again
        android.hardware.Camera.Parameters p = mCameraDevice.getParameters();
        p.setPreviewSize(w, h);
        mCameraDevice.setParameters(p);
        
        
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
        mCameraDevice.startPreview();
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
        Uri target = mMode == STILL_MODE ? Images.Media.INTERNAL_CONTENT_URI
                                                : Video.Media.INTERNAL_CONTENT_URI;
        Intent intent = new Intent(Intent.ACTION_VIEW, target);
        startActivity(intent);
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
        
        // go in worst to best order
        for (int i = 0; i < mLocationListeners.length && l == null; i++) {
            l = mLocationListeners[i].current();
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
                mKeepAndRestartPreview = false;
                mHandler.removeMessages(RESTART_PREVIEW);
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
        
        if (mMode == STILL_MODE) {
            if (mStatus == SNAPSHOT_IN_PROGRESS || mStatus == SNAPSHOT_COMPLETED) {
                menu.setGroupVisible(MenuHelper.IMAGE_SAVING_ITEM, true);
                mImageSavingItem = true;
            } else {
                menu.setGroupVisible(MenuHelper.IMAGE_MODE_ITEM, true);
                mImageSavingItem = false;
            }
        } else if (mMode == VIDEO_MODE) {
        }

        if (mCaptureObject != null)
            mCaptureObject.cancelAutoDismiss();

        return true;
    }

    private boolean isPickIntent() {
        String action = getIntent().getAction();
        return (Intent.ACTION_PICK.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE.equals(action));
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (isPickIntent()) {
            menu.add(MenuHelper.IMAGE_SAVING_ITEM, MENU_SAVE_SELECT_PHOTOS , 0, R.string.camera_selectphoto).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    Bitmap bitmap = mImageCapture.getLastBitmap();
                    mCaptureObject.setDone(true);

                    // TODO scale the image down to something ridiculous until IPC gets straightened out
                    float scale = .5F;
                    Matrix m = new Matrix();
                    m.setScale(scale, scale);

                    bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                            bitmap.getWidth(),
                            bitmap.getHeight(), 
                            m, true);
                    
                    Bundle myExtras = getIntent().getExtras();
                    String cropValue = myExtras != null ? myExtras.getString("crop") : null;
                    if (cropValue != null) {
                        Bundle newExtras = new Bundle();
                        if (cropValue.equals("circle"))
                            newExtras.putString("circleCrop", "true");
                        newExtras.putParcelable("data", bitmap);

                        Intent cropIntent = new Intent();
                        cropIntent.setClass(Camera.this, CropImage.class);
                        cropIntent.putExtras(newExtras);
                        startActivityForResult(cropIntent, CROP_MSG);
                    } else {
                        Bundle extras = new Bundle();
                        extras.putParcelable("data", bitmap);
                        setResult(RESULT_OK, new Intent("inline-data")
                                .putExtra("data", bitmap));
                        finish();
                    }
                    return true;
                }
            });
            
            menu.add(MenuHelper.IMAGE_SAVING_ITEM, MENU_SAVE_NEW_PHOTO, 0, R.string.camera_takenewphoto).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    keep();
                    return true;
                }
            });
            menu.add(MenuHelper.VIDEO_SAVING_ITEM, MENU_SAVE_TAKE_NEW_VIDEO, 0, R.string.camera_takenewvideo).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    toss();
                    return true;
                }
            });
        } else {
            addBaseMenuItems(menu);
            MenuHelper.addImageMenuItems(
                    menu,
                    MenuHelper.INCLUDE_ALL & ~MenuHelper.INCLUDE_ROTATE_MENU,
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

            mGalleryItems.add(menu.add(MenuHelper.VIDEO_SAVING_ITEM, MENU_SAVE_GALLERY_VIDEO_PHOTO, 0, R.string.camera_gallery_photos_text).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    keepVideo();
                    gotoGallery();
                    return true;
                }
            }));
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
        try {
            if (!ImageManager.instance().hasStorage()) {
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
    
    private void addBaseMenuItems(Menu menu) {
        MenuItem gallery = menu.add(MenuHelper.IMAGE_MODE_ITEM, MENU_GALLERY_PHOTOS, 0, R.string.camera_gallery_photos_text).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                gotoGallery();
                return true;
            }
        });
        gallery.setIcon(android.R.drawable.ic_menu_gallery);
        mGalleryItems.add(gallery);

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

