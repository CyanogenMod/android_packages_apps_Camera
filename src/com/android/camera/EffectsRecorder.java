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
import android.filterfw.core.GraphRunner;
import android.filterfw.core.GraphRunner.OnRunnerDoneListener;
import android.filterpacks.videosrc.SurfaceTextureSource.SurfaceTextureSourceListener;
import android.filterpacks.videoproc.BackDropperFilter;
import android.filterpacks.videoproc.BackDropperFilter.LearningDoneListener;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.lang.Runnable;

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
    public static final int  EFFECT_MSG_STOPPING_EFFECT  = 2;

    private Context mContext;
    private Handler mHandler;
    private boolean mReleased;

    private Camera mCameraDevice;
    private CamcorderProfile mProfile;
    private SurfaceHolder mPreviewSurfaceHolder;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private MediaRecorder.OnInfoListener mInfoListener;
    private MediaRecorder.OnErrorListener mErrorListener;

    private String mOutputFile;
    private int mOrientationHint = 0;

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
    private static final int STATE_PREVIEW                = 2;
    private static final int STATE_RECORD                 = 3;
    private static final int STATE_RELEASED               = 4;
    private int mState = STATE_CONFIGURE;

    private boolean mLogVerbose = Log.isLoggable(TAG, Log.VERBOSE);
    private static final String TAG = "effectsrecorder";

    /** Determine if a given effect is supported at runtime
     * Some effects require libraries not available on all devices
     */
    public static boolean isEffectSupported(int effectId) {
        switch (effectId) {
            case EFFECT_GOOFY_FACE:
                return Filter.isAvailable("com.google.android.filterpacks.facedetect.GoofyRenderFilter");
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
    }

    public void setCamera(Camera cameraDevice) {
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
    }

    public void setPreviewDisplay(SurfaceHolder previewSurfaceHolder,
                                  int previewWidth,
                                  int previewHeight) {
        if (mLogVerbose) Log.v(TAG, "setPreviewDisplay (" + this + ")");
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setPreviewDisplay cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setPreviewDisplay called on an already released recorder!");
            default:
                break;
        }

        mPreviewSurfaceHolder = previewSurfaceHolder;
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;

        switch (mState) {
            case STATE_WAITING_FOR_SURFACE:
                startPreview();
                break;
            case STATE_PREVIEW:
                initializeEffect(true);
                break;
        }
    }

    public void setEffect(int effect, Object effectParameter) {
        if (mLogVerbose) Log.v(TAG,
                               "Setting effect ID to " + effect +
                               ", parameter to " + effectParameter.toString() );
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

        if (mState == STATE_PREVIEW) {
            initializeEffect(false);
        }
    }

    public interface EffectsListener {
        public void onEffectsUpdate(int effectId, int effectMsg);
    }

    public void setEffectsListener(EffectsListener listener) {
        mEffectsListener = listener;
    }

    private void setFaceDetectOrientation(int degrees) {
        if (mCurrentEffect == EFFECT_GOOFY_FACE) {
            Filter rotateFilter = mRunner.getGraph().getFilter("rotate");
            Filter metaRotateFilter = mRunner.getGraph().getFilter("metarotate");
            rotateFilter.setInputValue("rotation", degrees);
            int reverseDegrees = (360 - degrees) % 360;
            metaRotateFilter.setInputValue("rotation", reverseDegrees);
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
        setFaceDetectOrientation(degrees);
    }

    public void setOnInfoListener(MediaRecorder.OnInfoListener infoListener) {
        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("setInfoListener cannot be called while recording!");
            case STATE_RELEASED:
                throw new RuntimeException("setInfoListener called on an already released recorder!");
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
                throw new RuntimeException("setErrorListener called on an already released recorder!");
            default:
                break;
        }
        mErrorListener = errorListener;
    }

    private void initializeFilterFramework() {
        mGraphEnv = new GraphEnvironment();
        mGraphEnv.createGLEnvironment();

        mGraphEnv.addReferences(
                "textureSourceCallback", mSourceReadyCallback,
                "recordingWidth", mProfile.videoFrameWidth,
                "recordingHeight", mProfile.videoFrameHeight,
                "recordingProfile", mProfile,
                "audioSource", MediaRecorder.AudioSource.CAMCORDER,
                "learningDoneListener", mLearningDoneListener);

        mRunner = null;
        mGraphId = -1;
        mCurrentEffect = EFFECT_NONE;
    }

    private synchronized void initializeEffect(boolean forceReset) {
        if (forceReset ||
            mCurrentEffect != mEffect ||
            mCurrentEffect == EFFECT_BACKDROPPER) {
            mGraphEnv.addReferences(
                    "previewSurface", mPreviewSurfaceHolder.getSurface(),
                    "previewWidth", mPreviewWidth,
                    "previewHeight", mPreviewHeight);
            if (mState == STATE_PREVIEW) {
                // Switching effects while running. Inform video camera.
                sendMessage(mCurrentEffect, EFFECT_MSG_STOPPING_EFFECT);
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

            if (mState == STATE_PREVIEW) {
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
                Filter goofyFilter = mRunner.getGraph().getFilter("goofyrenderer");
                goofyFilter.setInputValue("currentEffect",
                                          ((Integer)mEffectParameter).intValue());
                break;
            case EFFECT_BACKDROPPER:
                Filter backgroundSrc = mRunner.getGraph().getFilter("background");
                backgroundSrc.setInputValue("sourceUrl",
                                            (String)mEffectParameter);
                break;
            default:
                break;
        }
        setFaceDetectOrientation(mOrientationHint);
    }

    public synchronized void startPreview() {
        if (mLogVerbose) Log.v(TAG, "Starting preview (" + this + ")");

        switch (mState) {
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
        if (mPreviewSurfaceHolder == null) {
            if (mLogVerbose) Log.v(TAG, "Passed a null surface holder; waiting for valid one");
            mState = STATE_WAITING_FOR_SURFACE;
            return;
        }
        if (mCameraDevice == null) {
            throw new RuntimeException("No camera to record from!");
        }

        if (mLogVerbose) Log.v(TAG, "Initializing filter graph");

        initializeFilterFramework();

        initializeEffect(true);

        if (mLogVerbose) Log.v(TAG, "Starting filter graph");

        mRunner.run();
        // Rest of preview startup handled in mSourceReadyCallback
    }

    private SurfaceTextureSourceListener mSourceReadyCallback =
            new SurfaceTextureSourceListener() {
        public void onSurfaceTextureSourceReady(SurfaceTexture source) {
            if (mLogVerbose) Log.v(TAG, "SurfaceTexture ready callback received");
            synchronized(EffectsRecorder.this) {
                mTextureSource = source;

                // When shutting down a graph, we receive a null SurfaceTexture to
                // indicate that. Don't want to connect up the camera in that case.
                if (source == null) return;

                if (mState == STATE_RELEASED) return;

                mCameraDevice.stopPreview();
                if (mLogVerbose) Log.v(TAG, "Runner active, connecting effects preview");
                try {
                    mCameraDevice.setPreviewTexture(mTextureSource);
                } catch(IOException e) {
                    throw new RuntimeException("Unable to connect camera to effect input", e);
                }

                // Lock AE/AWB to reduce transition flicker
                tryEnable3ALocks(true);

                mCameraDevice.startPreview();

                // Unlock AE/AWB after preview started
                tryEnable3ALocks(false);

                mState = STATE_PREVIEW;

                if (mLogVerbose) Log.v(TAG, "Start preview/effect switch complete");
            }
        }
    };

    private LearningDoneListener mLearningDoneListener =
            new LearningDoneListener() {
        public void onLearningDone(BackDropperFilter filter) {
            if (mLogVerbose) Log.v(TAG, "Learning done callback triggered");
            // Called in a processing thread, so have to post message back to UI
            // thread
            sendMessage(EFFECT_BACKDROPPER, EFFECT_MSG_DONE_LEARNING);
            enable3ALocks(true);
        }
    };

    public synchronized void startRecording() {
        if (mLogVerbose) Log.v(TAG, "Starting recording (" + this + ")");

        switch (mState) {
            case STATE_RECORD:
                throw new RuntimeException("Already recording, cannot begin anew!");
            case STATE_RELEASED:
                throw new RuntimeException("startRecording called on an already released recorder!");
            default:
                break;
        }

        if (mOutputFile == null) {
            throw new RuntimeException("No output file name provided!");
        }

        if (mState == STATE_CONFIGURE) {
            startPreview();
        }
        Filter recorder = mRunner.getGraph().getFilter("recorder");
        recorder.setInputValue("outputFile", mOutputFile);
        recorder.setInputValue("orientationHint", mOrientationHint);
        if (mInfoListener != null) {
            recorder.setInputValue("infoListener", mInfoListener);
        }
        if (mErrorListener != null) {
            recorder.setInputValue("errorListener", mErrorListener);
        }
        recorder.setInputValue("recording", true);
        mState = STATE_RECORD;
    }

    public synchronized void stopRecording() {
        if (mLogVerbose) Log.v(TAG, "Stop recording (" + this + ")");

        switch (mState) {
            case STATE_CONFIGURE:
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
        mState = STATE_PREVIEW;
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

        sendMessage(mCurrentEffect, EFFECT_MSG_STOPPING_EFFECT);

        mCurrentEffect = EFFECT_NONE;

        mCameraDevice.stopPreview();
        try {
            mCameraDevice.setPreviewTexture(null);
        } catch(IOException e) {
            throw new RuntimeException("Unable to connect camera to effect input", e);
        }

        mState = STATE_CONFIGURE;
        mOldRunner = mRunner;
        mRunner.stop();

        // Rest of stop and release handled in mRunnerDoneCallback
    }

    // Try to enable/disable 3A locks if supported; otherwise return false
    boolean tryEnable3ALocks(boolean toggle) {
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
    void enable3ALocks(boolean toggle) {
        Camera.Parameters params = mCameraDevice.getParameters();
        if (!tryEnable3ALocks(toggle)) {
            throw new RuntimeException("Attempt to lock 3A on camera with no locking support!");
        }
    }

    private OnRunnerDoneListener mRunnerDoneCallback =
            new OnRunnerDoneListener() {
        public void onRunnerDone(int result) {
            synchronized(EffectsRecorder.this) {
                if (mOldRunner != null) {
                    if (mLogVerbose) Log.v(TAG, "Tearing down old graph.");
                    mOldRunner.getGraph().tearDown(mGraphEnv.getContext());
                    mOldRunner = null;
                }
                if (mState == STATE_PREVIEW) {
                    // Switching effects, start up the new runner
                    if (mLogVerbose) Log.v(TAG, "Previous effect halted, starting new effect.");
                    tryEnable3ALocks(false);
                    mRunner.run();
                } else if (mState != STATE_RELEASED) {
                    // Shutting down effects
                    if (mLogVerbose) Log.v(TAG, "Runner halted, restoring direct preview");
                    try {
                        mCameraDevice.setPreviewDisplay(mPreviewSurfaceHolder);
                    } catch(IOException e) {
                        throw new RuntimeException("Unable to connect camera to preview display", e);
                    }
                    mCameraDevice.startPreview();
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
            case STATE_PREVIEW:
                stopPreview();
                // Fall-through
            default:
                mState = STATE_RELEASED;
                break;
        }
    }

    private void sendMessage(final int effect, final int msg) {
        if (mEffectsListener != null) {
            mHandler.post(new Runnable() {
                public void run() {
                    mEffectsListener.onEffectsUpdate(effect,
                                                     msg);
                }
            });
        }
    }
}
