/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.filterfw.GraphEnvironment;
import android.filterfw.core.Filter;
import android.filterfw.core.GLEnvironment;
import android.filterfw.core.GraphRunner;
import android.filterfw.core.GraphRunner.OnRunnerDoneListener;
import android.filterfw.geometry.Point;
import android.filterfw.geometry.Quad;
import android.filterpacks.videoproc.BackDropperFilter;
import android.filterpacks.videoproc.BackDropperFilter.LearningDoneListener;
import android.filterpacks.videosink.MediaEncoderFilter.OnRecordingDoneListener;
import android.filterpacks.videosrc.SurfaceTextureSource.SurfaceTextureSourceListener;
import android.filterpacks.videosrc.SurfaceTextureTarget;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;


/**
 * Encapsulates the mobile filter framework components needed to record video with
 * effects applied. Modeled after MediaRecorder.
 */
public class EffectsRecorder {

    public static final int  EFFECT_NONE        = 0;
    public static final int  EFFECT_GOOFY_FACE  = 1;
    public static final int  EFFECT_BACKDROPPER = 2;

    public static final int  EFFECT_GF_SQUEEZE     = 0;
    public static final int  EFFECT_GF_BIG_EYES    = 1;
    public static final int  EFFECT_GF_BIG_MOUTH   = 2;
    public static final int  EFFECT_GF_SMALL_MOUTH = 3;
    public static final int  EFFECT_GF_BIG_NOSE    = 4;
    public static final int  EFFECT_GF_SMALL_EYES  = 5;
    public static final int  NUM_OF_GF_EFFECTS = EFFECT_GF_SMALL_EYES + 1;

    public static final int  EFFECT_MSG_STARTED_LEARNING = 0;
    public static final int  EFFECT_MSG_DONE_LEARNING    = 1;
    public static final int  EFFECT_MSG_SWITCHING_EFFECT = 2;
    public static final int  EFFECT_MSG_EFFECTS_STOPPED  = 3;
    public static final int  EFFECT_MSG_RECORDING_DONE   = 4;
    public static final int  EFFECT_MSG_PREVIEW_RUNNING  = 5;

    private Context mContext;
    private Handler mHandler;

    private Camera mCameraDevice;
    private CamcorderProfile mProfile;
    private double mCaptureRate = 0;
    private SurfaceTexture mPreviewSurfaceTexture;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private MediaRecorder.OnInfoListener mInfoListener;
    private MediaRecorder.OnErrorListener mErrorListener;

    private String mOutputFile;
    private FileDescriptor mFd;
    private int mOrientationHint = 0;
    private long mMaxFileSize = 0;
    private int mMaxDurationMs = 0;
    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int mCameraDisplayOrientation;

    private int mEffect = EFFECT_NONE;
    private int mCurrentEffect = EFFECT_NONE;
    private EffectsListener mEffectsListener;

    private Object mEffectParameter;

    private GraphEnvironment mGraphEnv;
    private int mGraphId;
    private GraphRunner mRunner = null;
    private GraphRunner mOldRunner = null;

    private SurfaceTexture mTextureSource;

    private static final int STATE_CONFIGURE              = 0;
    private static final int STATE_WAITING_FOR_SURFACE    = 1;
    private static final int STATE_STARTING_PREVIEW       = 2;
    private static final int STATE_PREVIEW                = 3;
    private static final int STATE_RECORD                 = 4;
    private static final int STATE_RELEASED               = 5;
    private int mState = STATE_CONFIGURE;

    private boolean mLogVerbose = Log.isLoggable(TAG, Log.VERBOSE);
    private static final String TAG = "EffectsRecorder";
    private MediaActionSound mCameraSound;

    /** Determine if a given effect is supported at runtime
     * Some effects require libraries not available on all devices
     */
    public static boolean isEffectSupported(int effectId) {
        switch (effectId) {
            case EFFECT_GOOFY_FACE:
                return Filter.isAvailable(
                    "com.google.android.filterpacks.facedetect.GoofyRenderFilter");
            case EFFECT_BACKDROPPER:
                return Filter.isAvailable("android.filterpacks.videoproc.BackDropperFilter");
            default:
                return false;
        }
    }

    public EffectsRecorder(Context context) {
        if (mLogVerbose) Log.v(TAG, "EffectsRecorder created (" + this + ")");
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mCameraSound = new MediaActionSound();
        mCameraSound.load(MediaActionSound.START_VIDEO_RECORDING);
        mCameraSound.load(MediaActionSound.STOP_VIDEO_RECORDING);
    }

    public synchronized void setCamera(Camera cameraDevice) {
        switch (mState) {
            case STATE_PREVIEW:
                throw new RuntimeException("setCamera cannot be called while previewing!");
            case STATE_RECORD:
                throw new RuntimeException("setCamera cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setCamera called on an already released recorder!");
            default:
                break;
        }

        mCameraDevice = cameraDevice;
    }

    public void setProfile(CamcorderProfile profile) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setProfile cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setProfile called on an already released recorder!");
            default:
                break;
        }
        mProfile = profile;
    }

    public void setOutputFile(String outputFile) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setOutputFile cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setOutputFile called on an already released recorder!");
            default:
                break;
        }

        mOutputFile = outputFile;
        mFd = null;
    }

    public void setOutputFile(FileDescriptor fd) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setOutputFile cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setOutputFile called on an already released recorder!");
            default:
                break;
        }

        mOutputFile = null;
        mFd = fd;
    }

    /**
     * Sets the maximum filesize (in bytes) of the recording session.
     * This will be passed on to the MediaEncoderFilter and then to the
     * MediaRecorder ultimately. If zero or negative, the MediaRecorder will
     * disable the limit
    */
    public synchronized void setMaxFileSize(long maxFileSize) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setMaxFileSize cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException(
                    "setMaxFileSize called on an already released recorder!");
            default:
                break;
        }
        mMaxFileSize = maxFileSize;
    }

    /**
    * Sets the maximum recording duration (in ms) for the next recording session
    * Setting it to zero (the default) disables the limit.
    */
    public synchronized void setMaxDuration(int maxDurationMs) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setMaxDuration cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException(
                    "setMaxDuration called on an already released recorder!");
            default:
                break;
        }
        mMaxDurationMs = maxDurationMs;
    }


    public void setCaptureRate(double fps) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setCaptureRate cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException(
                    "setCaptureRate called on an already released recorder!");
            default:
                break;
        }

        if (mLogVerbose) Log.v(TAG, "Setting time lapse capture rate to " + fps + " fps");
        mCaptureRate = fps;
    }

    public void setPreviewSurfaceTexture(SurfaceTexture previewSurfaceTexture,
                                  int previewWidth,
                                  int previewHeight) {
        if (mLogVerbose) Log.v(TAG, "setPreviewSurfaceTexture(" + this + ")");
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException(
                    "setPreviewSurfaceTexture cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException(
                    "setPreviewSurfaceTexture called on an already released recorder!");
            default:
                break;
        }

        mPreviewSurfaceTexture= previewSurfaceTexture;
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;

        switch (mState) {
            case STATE_WAITING_FOR_SURFACE:
                startPreview();
                break;
            case STATE_STARTING_PREVIEW:
            case STATE_PREVIEW:
                initializeEffect(true);
                break;
        }
    }

    public void setEffect(int effect, Object effectParameter) {
        if (mLogVerbose) Log.v(TAG,
                               "setEffect: effect ID " + effect +
                               ", parameter " + effectParameter.toString() );
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setEffect cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setEffect called on an already released recorder!");
            default:
                break;
        }

        mEffect = effect;
        mEffectParameter = effectParameter;

        if (mState == STATE_PREVIEW ||
                mState == STATE_STARTING_PREVIEW) {
            initializeEffect(false);
        }
    }

    public interface EffectsListener {
        public void onEffectsUpdate(int effectId, int effectMsg);
        public void onEffectsError(Exception exception, String filePath);
    }

    public void setEffectsListener(EffectsListener listener) {
        mEffectsListener = listener;
    }

    private void setFaceDetectOrientation() {
        if (mCurrentEffect == EFFECT_GOOFY_FACE) {
            Filter rotateFilter = mRunner.getGraph().getFilter("rotate");
            Filter metaRotateFilter = mRunner.getGraph().getFilter("metarotate");
            rotateFilter.setInputValue("rotation", mOrientationHint);
            int reverseDegrees = (360 - mOrientationHint) % 360;
            metaRotateFilter.setInputValue("rotation", reverseDegrees);
        }
    }

    private void setRecordingOrientation() {
        if ( mState != STATE_RECORD && mRunner != null) {
            Point bl = new Point(0, 0);
            Point br = new Point(1, 0);
            Point tl = new Point(0, 1);
            Point tr = new Point(1, 1);
            Quad recordingRegion;
            if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                // The back camera is not mirrored, so use a identity transform
                recordingRegion = new Quad(bl, br, tl, tr);
            } else {
                // Recording region needs to be tweaked for front cameras, since they
                // mirror their preview
                if (mOrientationHint == 0 || mOrientationHint == 180) {
                    // Horizontal flip in landscape
                    recordingRegion = new Quad(br, bl, tr, tl);
                } else {
                    // Horizontal flip in portrait
                    recordingRegion = new Quad(tl, tr, bl, br);
                }
            }
            Filter recorder = mRunner.getGraph().getFilter("recorder");
            recorder.setInputValue("inputRegion", recordingRegion);
        }
    }
    public void setOrientationHint(int degrees) {
        switch (mState) {
            case STATE_RELEASED:
                throw new RuntimeException(
                        "setOrientationHint called on an already released recorder!");
            default:
                break;
        }
        if (mLogVerbose) Log.v(TAG, "Setting orientation hint to: " + degrees);
        mOrientationHint = degrees;
        setFaceDetectOrientation();
        setRecordingOrientation();
    }

    public void setCameraDisplayOrientation(int orientation) {
        if (mState != STATE_CONFIGURE) {
            throw new RuntimeException(
                "setCameraDisplayOrientation called after configuration!");
        }
        mCameraDisplayOrientation = orientation;
    }

    public void setCameraFacing(int facing) {
        switch (mState) {
            case STATE_RELEASED:
                throw new RuntimeException(
                    "setCameraFacing called on alrady released recorder!");
            default:
                break;
        }
        mCameraFacing = facing;
        setRecordingOrientation();
    }

    public void setOnInfoListener(MediaRecorder.OnInfoListener infoListener) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setInfoListener cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException(
                    "setInfoListener called on an already released recorder!");
            default:
                break;
        }
        mInfoListener = infoListener;
    }

    public void setOnErrorListener(MediaRecorder.OnErrorListener errorListener) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setErrorListener cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException(
                    "setErrorListener called on an already released recorder!");
            default:
                break;
        }
        mErrorListener = errorListener;
    }

    private void initializeFilterFramework() {
        mGraphEnv = new GraphEnvironment();
        mGraphEnv.createGLEnvironment();

        int videoFrameWidth = mProfile.videoFrameWidth;
        int videoFrameHeight = mProfile.videoFrameHeight;
        if (mCameraDisplayOrientation == 90 || mCameraDisplayOrientation == 270) {
            int tmp = videoFrameWidth;
            videoFrameWidth = videoFrameHeight;
            videoFrameHeight = tmp;
        }

        mGraphEnv.addReferences(
                "textureSourceCallback", mSourceReadyCallback,
                "recordingWidth", videoFrameWidth,
                "recordingHeight", videoFrameHeight,
                "recordingProfile", mProfile,
                "learningDoneListener", mLearningDoneListener,
                "recordingDoneListener", mRecordingDoneListener);
        mRunner = null;
        mGraphId = -1;
        mCurrentEffect = EFFECT_NONE;
    }

    private synchronized void initializeEffect(boolean forceReset) {
        if (forceReset ||
            mCurrentEffect != mEffect ||
            mCurrentEffect == EFFECT_BACKDROPPER) {

            mGraphEnv.addReferences(
                    "previewSurfaceTexture", mPreviewSurfaceTexture,
                    "previewWidth", mPreviewWidth,
                    "previewHeight", mPreviewHeight,
                    "orientation", mOrientationHint);
            if (mState == STATE_PREVIEW ||
                    mState == STATE_STARTING_PREVIEW) {
                // Switching effects while running. Inform video camera.
                sendMessage(mCurrentEffect, EFFECT_MSG_SWITCHING_EFFECT);
            }

            switch (mEffect) {
                case EFFECT_GOOFY_FACE:
                    mGraphId = mGraphEnv.loadGraph(mContext, R.raw.goofy_face);
                    break;
                case EFFECT_BACKDROPPER:
                    sendMessage(EFFECT_BACKDROPPER, EFFECT_MSG_STARTED_LEARNING);
                    mGraphId = mGraphEnv.loadGraph(mContext, R.raw.backdropper);
                    break;
                default:
                    throw new RuntimeException("Unknown effect ID" + mEffect + "!");
            }
            mCurrentEffect = mEffect;

            mOldRunner = mRunner;
            mRunner = mGraphEnv.getRunner(mGraphId, GraphEnvironment.MODE_ASYNCHRONOUS);
            mRunner.setDoneCallback(mRunnerDoneCallback);
            if (mLogVerbose) {
                Log.v(TAG, "New runner: " + mRunner
                      + ". Old runner: " + mOldRunner);
            }
            if (mState == STATE_PREVIEW ||
                    mState == STATE_STARTING_PREVIEW) {
                // Switching effects while running. Stop existing runner.
                // The stop callback will take care of starting new runner.
                mCameraDevice.stopPreview();
                try {
                    mCameraDevice.setPreviewTexture(null);
                } catch(IOException e) {
                    throw new RuntimeException("Unable to connect camera to effect input", e);
                }
                mOldRunner.stop();
            }
        }

        switch (mCurrentEffect) {
            case EFFECT_GOOFY_FACE:
                tryEnableVideoStabilization(true);
                Filter goofyFilter = mRunner.getGraph().getFilter("goofyrenderer");
                goofyFilter.setInputValue("currentEffect",
                                          ((Integer)mEffectParameter).intValue());
                break;
            case EFFECT_BACKDROPPER:
                tryEnableVideoStabilization(false);
                Filter backgroundSrc = mRunner.getGraph().getFilter("background");
                backgroundSrc.setInputValue("sourceUrl", mEffectParameter);
                // For front camera, the background video needs to be mirrored in the
                // backdropper filter
                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    Filter replacer = mRunner.getGraph().getFilter("replacer");
                    replacer.setInputValue("mirrorBg", true);
                    if (mLogVerbose) Log.v(TAG, "Setting the background to be mirrored");
                }
                break;
            default:
                break;
        }
        setFaceDetectOrientation();
        setRecordingOrientation();
    }

    public synchronized void startPreview() {
        if (mLogVerbose) Log.v(TAG, "Starting preview (" + this + ")");

        switch (mState) {
            case STATE_STARTING_PREVIEW:
            case STATE_PREVIEW:
                // Already running preview
                Log.w(TAG, "startPreview called when already running preview");
                return;
            case STATE_RECORD:
                throw new RuntimeException("Cannot start preview when already recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setEffect called on an already released recorder!");
            default:
                break;
        }

        if (mEffect == EFFECT_NONE) {
            throw new RuntimeException("No effect selected!");
        }
        if (mEffectParameter == null) {
            throw new RuntimeException("No effect parameter provided!");
        }
        if (mProfile == null) {
            throw new RuntimeException("No recording profile provided!");
        }
        if (mPreviewSurfaceTexture == null) {
            if (mLogVerbose) Log.v(TAG, "Passed a null surface; waiting for valid one");
            mState = STATE_WAITING_FOR_SURFACE;
            return;
        }
        if (mCameraDevice == null) {
            throw new RuntimeException("No camera to record from!");
        }

        if (mLogVerbose) Log.v(TAG, "Initializing filter framework and running the graph.");
        initializeFilterFramework();

        initializeEffect(true);

        mState = STATE_STARTING_PREVIEW;
        mRunner.run();
        // Rest of preview startup handled in mSourceReadyCallback
    }

    private SurfaceTextureSourceListener mSourceReadyCallback =
            new SurfaceTextureSourceListener() {
        @Override
        public void onSurfaceTextureSourceReady(SurfaceTexture source) {
            if (mLogVerbose) Log.v(TAG, "SurfaceTexture ready callback received");
            synchronized(EffectsRecorder.this) {
                mTextureSource = source;

                if (mState == STATE_CONFIGURE) {
                    // Stop preview happened while the runner was doing startup tasks
                    // Since we haven't started anything up, don't do anything
                    // Rest of cleanup will happen in onRunnerDone
                    if (mLogVerbose) Log.v(TAG, "Ready callback: Already stopped, skipping.");
                    return;
                }
                if (mState == STATE_RELEASED) {
                    // EffectsRecorder has been released, so don't touch the camera device
                    // or anything else
                    if (mLogVerbose) Log.v(TAG, "Ready callback: Already released, skipping.");
                    return;
                }
                if (source == null) {
                    if (mLogVerbose) {
                        Log.v(TAG, "Ready callback: source null! Looks like graph was closed!");
                    }
                    if (mState == STATE_PREVIEW ||
                            mState == STATE_STARTING_PREVIEW ||
                            mState == STATE_RECORD) {
                        // A null source here means the graph is shutting down
                        // unexpectedly, so we need to turn off preview before
                        // the surface texture goes away.
                        if (mLogVerbose) {
                            Log.v(TAG, "Ready callback: State: " + mState + ". stopCameraPreview");
                        }

                        stopCameraPreview();
                    }
                    return;
                }

                // Lock AE/AWB to reduce transition flicker
                tryEnable3ALocks(true);

                mCameraDevice.stopPreview();
                if (mLogVerbose) Log.v(TAG, "Runner active, connecting effects preview");
                try {
                    mCameraDevice.setPreviewTexture(mTextureSource);
                } catch(IOException e) {
                    throw new RuntimeException("Unable to connect camera to effect input", e);
                }

                mCameraDevice.startPreview();

                // Unlock AE/AWB after preview started
                tryEnable3ALocks(false);

                mState = STATE_PREVIEW;

                if (mLogVerbose) Log.v(TAG, "Start preview/effect switch complete");

                // Sending a message to listener that preview is complete
                sendMessage(mCurrentEffect, EFFECT_MSG_PREVIEW_RUNNING);
            }
        }
    };

    private LearningDoneListener mLearningDoneListener =
            new LearningDoneListener() {
        @Override
        public void onLearningDone(BackDropperFilter filter) {
            if (mLogVerbose) Log.v(TAG, "Learning done callback triggered");
            // Called in a processing thread, so have to post message back to UI
            // thread
            sendMessage(EFFECT_BACKDROPPER, EFFECT_MSG_DONE_LEARNING);
            enable3ALocks(true);
        }
    };

    // A callback to finalize the media after the recording is done.
    private OnRecordingDoneListener mRecordingDoneListener =
            new OnRecordingDoneListener() {
        // Forward the callback to the VideoCamera object (as an asynchronous event).
        @Override
        public void onRecordingDone() {
            if (mLogVerbose) Log.v(TAG, "Recording done callback triggered");
            sendMessage(EFFECT_NONE, EFFECT_MSG_RECORDING_DONE);
        }
    };

    public synchronized void startRecording() {
        if (mLogVerbose) Log.v(TAG, "Starting recording (" + this + ")");

        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("Already recording, cannot begin anew!");
            case STATE_RELEASED:
                throw new RuntimeException(
                    "startRecording called on an already released recorder!");
            default:
                break;
        }

        if ((mOutputFile == null) && (mFd == null)) {
            throw new RuntimeException("No output file name or descriptor provided!");
        }

        if (mState == STATE_CONFIGURE) {
            startPreview();
        }

        Filter recorder = mRunner.getGraph().getFilter("recorder");
        if (mFd != null) {
            recorder.setInputValue("outputFileDescriptor", mFd);
        } else {
            recorder.setInputValue("outputFile", mOutputFile);
        }
        // It is ok to set the audiosource without checking for timelapse here
        // since that check will be done in the MediaEncoderFilter itself
        recorder.setInputValue("audioSource", MediaRecorder.AudioSource.CAMCORDER);

        recorder.setInputValue("recordingProfile", mProfile);
        recorder.setInputValue("orientationHint", mOrientationHint);
        // Important to set the timelapseinterval to 0 if the capture rate is not >0
        // since the recorder does not get created every time the recording starts.
        // The recorder infers whether the capture is timelapsed based on the value of
        // this interval
        boolean captureTimeLapse = mCaptureRate > 0;
        if (captureTimeLapse) {
            double timeBetweenFrameCapture = 1 / mCaptureRate;
            recorder.setInputValue("timelapseRecordingIntervalUs",
                    (long) (1000000 * timeBetweenFrameCapture));
        } else {
            recorder.setInputValue("timelapseRecordingIntervalUs", 0L);
        }

        if (mInfoListener != null) {
            recorder.setInputValue("infoListener", mInfoListener);
        }
        if (mErrorListener != null) {
            recorder.setInputValue("errorListener", mErrorListener);
        }
        recorder.setInputValue("maxFileSize", mMaxFileSize);
        recorder.setInputValue("maxDurationMs", mMaxDurationMs);
        recorder.setInputValue("recording", true);
        mCameraSound.play(MediaActionSound.START_VIDEO_RECORDING);
        mState = STATE_RECORD;
    }

    public synchronized void stopRecording() {
        if (mLogVerbose) Log.v(TAG, "Stop recording (" + this + ")");

        switch (mState) {
            case STATE_CONFIGURE:
            case STATE_STARTING_PREVIEW:
            case STATE_PREVIEW:
                Log.w(TAG, "StopRecording called when recording not active!");
                return;
            case STATE_RELEASED:
                throw new RuntimeException("stopRecording called on released EffectsRecorder!");
            default:
                break;
        }
        Filter recorder = mRunner.getGraph().getFilter("recorder");
        recorder.setInputValue("recording", false);
        mCameraSound.play(MediaActionSound.STOP_VIDEO_RECORDING);
        mState = STATE_PREVIEW;
    }

    // Called to tell the filter graph that the display surfacetexture is not valid anymore.
    // So the filter graph should not hold any reference to the surface created with that.
    public synchronized void disconnectDisplay() {
        if (mLogVerbose) Log.v(TAG, "Disconnecting the graph from the " +
            "SurfaceTexture");
        SurfaceTextureTarget display = (SurfaceTextureTarget)
            mRunner.getGraph().getFilter("display");
        display.disconnect(mGraphEnv.getContext());
    }

    // The VideoCamera will call this to notify that the camera is being
    // released to the outside world. This call should happen after the
    // stopRecording call. Else, the effects may throw an exception.
    // With the recording stopped, the stopPreview call will not try to
    // release the camera again.
    // This must be called in onPause() if the effects are ON.
    public synchronized void disconnectCamera() {
        if (mLogVerbose) Log.v(TAG, "Disconnecting the effects from Camera");
        stopCameraPreview();
        mCameraDevice = null;
    }

    // In a normal case, when the disconnect is not called, we should not
    // set the camera device to null, since on return callback, we try to
    // enable 3A locks, which need the cameradevice.
    public synchronized void stopCameraPreview() {
        if (mLogVerbose) Log.v(TAG, "Stopping camera preview.");
        if (mCameraDevice == null) {
            Log.d(TAG, "Camera already null. Nothing to disconnect");
            return;
        }
        mCameraDevice.stopPreview();
        try {
            mCameraDevice.setPreviewTexture(null);
        } catch(IOException e) {
            throw new RuntimeException("Unable to disconnect camera");
        }
    }

    // Stop and release effect resources
    public synchronized void stopPreview() {
        if (mLogVerbose) Log.v(TAG, "Stopping preview (" + this + ")");
        switch (mState) {
            case STATE_CONFIGURE:
                Log.w(TAG, "StopPreview called when preview not active!");
                return;
            case STATE_RELEASED:
                throw new RuntimeException("stopPreview called on released EffectsRecorder!");
            default:
                break;
        }

        if (mState == STATE_RECORD) {
            stopRecording();
        }

        mCurrentEffect = EFFECT_NONE;

        // This will not do anything if the camera has already been disconnected.
        stopCameraPreview();

        mState = STATE_CONFIGURE;
        mOldRunner = mRunner;
        mRunner.stop();
        mRunner = null;
        // Rest of stop and release handled in mRunnerDoneCallback
    }

    // Try to enable/disable video stabilization if supported; otherwise return false
    // It is called from a synchronized block.
    boolean tryEnableVideoStabilization(boolean toggle) {
        if (mLogVerbose) Log.v(TAG, "tryEnableVideoStabilization.");
        if (mCameraDevice == null) {
            Log.d(TAG, "Camera already null. Not enabling video stabilization.");
            return false;
        }
        Camera.Parameters params = mCameraDevice.getParameters();

        String vstabSupported = params.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            if (mLogVerbose) Log.v(TAG, "Setting video stabilization to " + toggle);
            params.set("video-stabilization", toggle ? "true" : "false");
            mCameraDevice.setParameters(params);
            return true;
        }
        if (mLogVerbose) Log.v(TAG, "Video stabilization not supported");
        return false;
    }

    // Try to enable/disable 3A locks if supported; otherwise return false
    synchronized boolean tryEnable3ALocks(boolean toggle) {
        if (mLogVerbose) Log.v(TAG, "tryEnable3ALocks");
        if (mCameraDevice == null) {
            Log.d(TAG, "Camera already null. Not tryenabling 3A locks.");
            return false;
        }
        Camera.Parameters params = mCameraDevice.getParameters();
        if (params.isAutoExposureLockSupported() &&
            params.isAutoWhiteBalanceLockSupported() ) {
            params.setAutoExposureLock(toggle);
            params.setAutoWhiteBalanceLock(toggle);
            mCameraDevice.setParameters(params);
            return true;
        }
        return false;
    }

    // Try to enable/disable 3A locks if supported; otherwise, throw error
    // Use this when locks are essential to success
    synchronized void enable3ALocks(boolean toggle) {
        if (mLogVerbose) Log.v(TAG, "Enable3ALocks");
        if (mCameraDevice == null) {
            Log.d(TAG, "Camera already null. Not enabling 3A locks.");
            return;
        }
        Camera.Parameters params = mCameraDevice.getParameters();
        if (!tryEnable3ALocks(toggle)) {
            throw new RuntimeException("Attempt to lock 3A on camera with no locking support!");
        }
    }

    private OnRunnerDoneListener mRunnerDoneCallback =
            new OnRunnerDoneListener() {
        @Override
        public void onRunnerDone(int result) {
            synchronized(EffectsRecorder.this) {
                if (mLogVerbose) {
                    Log.v(TAG,
                          "Graph runner done (" + EffectsRecorder.this
                          + ", mRunner " + mRunner
                          + ", mOldRunner " + mOldRunner + ")");
                }
                if (result == GraphRunner.RESULT_ERROR) {
                    // Handle error case
                    Log.e(TAG, "Error running filter graph!");
                    Exception e = null;
                    if (mRunner != null) {
                        e = mRunner.getError();
                    } else if (mOldRunner != null) {
                        e = mOldRunner.getError();
                    }
                    raiseError(e);
                }
                if (mOldRunner != null) {
                    // Tear down old graph if available
                    if (mLogVerbose) Log.v(TAG, "Tearing down old graph.");
                    GLEnvironment glEnv = mGraphEnv.getContext().getGLEnvironment();
                    if (glEnv != null && !glEnv.isActive()) {
                        glEnv.activate();
                    }
                    mOldRunner.getGraph().tearDown(mGraphEnv.getContext());
                    if (glEnv != null && glEnv.isActive()) {
                        glEnv.deactivate();
                    }
                    mOldRunner = null;
                }
                if (mState == STATE_PREVIEW ||
                        mState == STATE_STARTING_PREVIEW) {
                    // Switching effects, start up the new runner
                    if (mLogVerbose) {
                        Log.v(TAG, "Previous effect halted. Running graph again. state: " + mState);
                    }
                    tryEnable3ALocks(false);
                    // In case of an error, the graph restarts from beginning and in case
                    // of the BACKDROPPER effect, the learner re-learns the background.
                    // Hence, we need to show the learning dialogue to the user
                    // to avoid recording before the learning is done. Else, the user
                    // could start recording before the learning is done and the new
                    // background comes up later leading to an end result video
                    // with a heterogeneous background.
                    // For BACKDROPPER effect, this path is also executed sometimes at
                    // the end of a normal recording session. In such a case, the graph
                    // does not restart and hence the learner does not re-learn. So we
                    // do not want to show the learning dialogue then.
                    if (result == GraphRunner.RESULT_ERROR &&
                            mCurrentEffect == EFFECT_BACKDROPPER) {
                        sendMessage(EFFECT_BACKDROPPER, EFFECT_MSG_STARTED_LEARNING);
                    }
                    mRunner.run();
                } else if (mState != STATE_RELEASED) {
                    // Shutting down effects
                    if (mLogVerbose) Log.v(TAG, "Runner halted, restoring direct preview");
                    tryEnable3ALocks(false);
                    sendMessage(EFFECT_NONE, EFFECT_MSG_EFFECTS_STOPPED);
                } else {
                    // STATE_RELEASED - camera will be/has been released as well, do nothing.
                }
            }
        }
    };

    // Indicates that all camera/recording activity needs to halt
    public synchronized void release() {
        if (mLogVerbose) Log.v(TAG, "Releasing (" + this + ")");

        switch (mState) {
            case STATE_RECORD:
            case STATE_STARTING_PREVIEW:
            case STATE_PREVIEW:
                stopPreview();
                // Fall-through
            default:
                if (mCameraSound != null) {
                    mCameraSound.release();
                    mCameraSound = null;
                }
                mState = STATE_RELEASED;
                break;
        }
    }

    private void sendMessage(final int effect, final int msg) {
        if (mEffectsListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mEffectsListener.onEffectsUpdate(effect, msg);
                }
            });
        }
    }

    private void raiseError(final Exception exception) {
        if (mEffectsListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mFd != null) {
                        mEffectsListener.onEffectsError(exception, null);
                    } else {
                        mEffectsListener.onEffectsError(exception, mOutputFile);
                    }
                }
            });
        }
    }

}
