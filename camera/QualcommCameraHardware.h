/*
** Copyright 2008, Google Inc.
** Copyright (c) 2009-2011, Code Aurora Forum. All rights reserved.
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef ANDROID_HARDWARE_QUALCOMM_CAMERA_HARDWARE_H
#define ANDROID_HARDWARE_QUALCOMM_CAMERA_HARDWARE_H

#define ICS

//#include <camera/CameraHardwareInterface.h>
#include <utils/threads.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <stdint.h>
#include <ui/egl/android_natives.h>
#ifdef ICS
#include <hardware/camera.h>
#endif
#include <camera/Camera.h>
#include <camera/CameraParameters.h>
#include <system/window.h>
#include <system/camera.h>
#include <hardware/camera.h>
#include <gralloc_priv.h>
#include <QComOMXMetadata.h>

extern "C" {
#include <linux/android_pmem.h>
#include <linux/ion.h>
#include <camera.h>
#include <camera_defs_i.h>
#include <mm_camera_interface.h>
}

struct str_map {
    const char *const desc;
    int val;
};

struct buffer_map {
    msm_frame *frame;
    buffer_handle_t * buffer;
    int size;
    int lockState;
};

typedef enum {
    TARGET_MSM7625,
    TARGET_MSM7625A,
    TARGET_MSM7627,
    TARGET_MSM7627A,
    TARGET_QSD8250,
    TARGET_MSM7630,
    TARGET_MSM8660,
    TARGET_MAX
}targetType;

typedef enum {
    LIVESHOT_DONE,
    LIVESHOT_IN_PROGRESS,
    LIVESHOT_STOPPED
}liveshotState;
#define MIN_UNDEQUEUD_BUFFER_COUNT 2
struct target_map {
    const char *targetStr;
    targetType targetEnum;
};

enum {
    BUFFER_UNLOCKED,
    BUFFER_LOCKED
};

struct board_property{
    targetType target;
    unsigned int previewSizeMask;
    bool hasSceneDetect;
    bool hasSelectableZoneAf;
    bool hasFaceDetect;
};

namespace android {

class QualcommCameraHardware : public RefBase{
public:

    //virtual sp<IMemoryHeap> getPreviewHeap() const;
    //virtual sp<IMemoryHeap> getRawHeap() const;

    void setCallbacks(camera_notify_callback notify_cb,
                            camera_data_callback data_cb,
                            camera_data_timestamp_callback data_cb_timestamp,
                            camera_request_memory get_memory,
                            void *user);

    virtual void enableMsgType(int32_t msgType);
    virtual void disableMsgType(int32_t msgType);
    virtual bool msgTypeEnabled(int32_t msgType);

    virtual status_t dump(int fd, const Vector<String16>& args) const;
    virtual status_t startPreview();
    virtual void stopPreview();
    virtual bool previewEnabled();
    virtual status_t startRecording();
    virtual void stopRecording();
    virtual bool recordingEnabled();
    virtual void releaseRecordingFrame(const void *opaque);
    virtual status_t autoFocus();
    virtual status_t cancelAutoFocus();
    virtual status_t takePicture();
    virtual status_t takeLiveSnapshot();
    virtual status_t takeLiveSnapshotInternal();
    void set_liveshot_exifinfo();
    virtual status_t cancelPicture();
    virtual status_t setParameters(const CameraParameters& params);
    virtual CameraParameters getParameters() const;
    virtual status_t sendCommand(int32_t command, int32_t arg1, int32_t arg2);
    virtual int32_t getNumberOfVideoBuffers();
    virtual sp<IMemory> getVideoBuffer(int32_t index);
    virtual status_t getBufferInfo( sp<IMemory>& Frame, size_t *alignedSize);
    virtual void encodeData( );
#ifdef ICS
    virtual status_t set_PreviewWindow(void* param);
    virtual status_t setPreviewWindow(preview_stream_ops_t* window);
#endif
    virtual status_t setPreviewWindow(const sp<ANativeWindow>& buf) {return NO_ERROR;};
    virtual void release();

    static QualcommCameraHardware* createInstance();
    static QualcommCameraHardware* getInstance();

    void receivePreviewFrame(struct msm_frame *frame);
    void receiveLiveSnapshot(uint32_t jpeg_size);
    void receiveCameraStats(camstats_type stype, camera_preview_histogram_info* histinfo);
    void receiveRecordingFrame(struct msm_frame *frame);
    void receiveJpegPicture(status_t status, mm_camera_buffer_t *encoded_buffer);
    void jpeg_set_location();
    void receiveJpegPictureFragment(uint8_t *buf, uint32_t size);
    void notifyShutter(bool mPlayShutterSoundOnly);
    void receive_camframe_error_timeout();
    static void getCameraInfo();
    void receiveRawPicture(status_t status,struct msm_frame *postviewframe, struct msm_frame *mainframe);
    int allocate_ion_memory(int *main_ion_fd, struct ion_allocation_data* alloc,
    struct ion_fd_data* ion_info_fd, int ion_type, int size, int *memfd);
    int deallocate_ion_memory(int *main_ion_fd, struct ion_fd_data* ion_info_fd);
    virtual ~QualcommCameraHardware();
    int storeMetaDataInBuffers(int enable);

private:
    QualcommCameraHardware();
    status_t startPreviewInternal();
    status_t startRecordingInternal();
    status_t setHistogramOn();
    status_t setHistogramOff();
    status_t runFaceDetection();
    status_t setFaceDetection(const char *str);

    void stopPreviewInternal();
    friend void *auto_focus_thread(void *user);
    void runAutoFocus();
    status_t cancelAutoFocusInternal();
    bool native_set_dimension (int camfd);
    bool native_jpeg_encode (void);
    bool updatePictureDimension(const CameraParameters& params, int& width, int& height);
    bool native_set_parms(mm_camera_parm_type_t type, uint16_t length, void *value);
    bool native_set_parms( mm_camera_parm_type_t type, uint16_t length, void *value, int *result);
    bool native_zoom_image(int fd, int srcOffset, int dstOffset, common_crop_t *crop);

    status_t startInitialPreview();
    void stopInitialPreview();
    status_t getBuffersAndStartPreview();
    void relinquishBuffers();

    QualcommCameraHardware * singleton;

    /* These constants reflect the number of buffers that libmmcamera requires
       for preview and raw, and need to be updated when libmmcamera
       changes.
    */
    static const int kPreviewBufferCount = NUM_PREVIEW_BUFFERS;
    static const int kRawBufferCount = 1;
    static const int kJpegBufferCount = 1;
    static const int kTotalPreviewBufferCount = kPreviewBufferCount + MIN_UNDEQUEUD_BUFFER_COUNT;
    int numCapture;
    int numJpegReceived;
    int jpegPadding;

    CameraParameters mParameters;
    unsigned int frame_size;
    bool mCameraRunning;
    Mutex mCameraRunningLock;
    bool mPreviewInitialized;


    class MMCameraDL : public RefBase{
    private:
        static wp<MMCameraDL> instance;
        MMCameraDL();
        virtual ~MMCameraDL();
        void *libmmcamera;
        static Mutex singletonLock;
    public:
        static sp<MMCameraDL> getInstance();
        void * pointer();
    };

    // This class represents a heap which maintains several contiguous
    // buffers.  The heap may be backed by pmem (when pmem_pool contains
    // the name of a /dev/pmem* file), or by ashmem (when pmem_pool == NULL).
    struct MemPool : public RefBase {
        MemPool(int buffer_size, int num_buffers,
                int frame_size,
                const char *name);

        virtual ~MemPool()  ;// = 0;

        void completeInitialization();
        bool initialized() const {
            return mHeap != NULL && mHeap->base() != MAP_FAILED;
        }

        virtual status_t dump(int fd, const Vector<String16>& args) const;

        int mBufferSize;
        int mAlignedBufferSize;
        int mNumBuffers;
        int mFrameSize;
        sp<MemoryHeapBase> mHeap;
        sp<MemoryBase> *mBuffers;

        const char *mName;
    };
      struct DispMemPool : public MemPool {
          DispMemPool(int fd, int buffer_size,
          int num_buffers, int frame_size,
          const char *name);
          virtual ~DispMemPool();
          int mFD;
      };
      sp<DispMemPool> mPreviewHeap[kPreviewBufferCount + MIN_UNDEQUEUD_BUFFER_COUNT];

    struct AshmemPool : public MemPool {
        AshmemPool(int buffer_size, int num_buffers,
                   int frame_size,
                   const char *name);
    };

    struct PmemPool : public MemPool {
        PmemPool(const char *pmem_pool,
                 int flags, int pmem_type,
                 int buffer_size, int num_buffers,
                 int frame_size, int cbcr_offset,
                 int yoffset, const char *name);
        virtual ~PmemPool();
        int mFd;
        int mPmemType;
        int mCbCrOffset;
        int myOffset;
        int mCameraControlFd;
        uint32_t mAlignedSize;
        struct pmem_region mSize;
        sp<QualcommCameraHardware::MMCameraDL> mMMCameraDLRef;
    };
//TODO
    struct IonPool : public MemPool {
        IonPool( int ion_heap_id, int flags, int ion_type,
             int buffer_size, int num_buffers,
             int frame_size, int cbcr_offset,
             int yoffset, const char *name);
    virtual ~IonPool();
    int mFd;
    int mIonType;
    int mCbCrOffset;
    int myOffset;
    int mCameraControlFd;
    uint32_t mAlignedSize;
    sp<QualcommCameraHardware::MMCameraDL> mMMCameraDLRef;
    static const char mIonDevName[];
    };
#ifdef USE_ION
//    sp<IonPool> mPreviewHeap;
    sp<IonPool> mYV12Heap;
    sp<IonPool> mRecordHeap;
    sp<IonPool> mThumbnailHeap;
    sp<IonPool> mRawHeap;
    sp<IonPool> mDisplayHeap;
    sp<AshmemPool> mJpegHeap;
    sp<AshmemPool> mStatHeap;
    sp<AshmemPool> mMetaDataHeap;
    sp<IonPool> mRawSnapShotPmemHeap;
    sp<IonPool> mLastPreviewFrameHeap;
    sp<IonPool> mPostviewHeap;
#else
//    sp<PmemPool> mPreviewHeap;
    sp<PmemPool> mYV12Heap;
    sp<PmemPool> mRecordHeap;
    sp<PmemPool> mThumbnailHeap;
    sp<PmemPool> mRawHeap;
    sp<PmemPool> mDisplayHeap;
    sp<AshmemPool> mJpegHeap;
    sp<AshmemPool> mStatHeap;
    sp<AshmemPool> mMetaDataHeap;
    sp<PmemPool> mRawSnapShotPmemHeap;
    sp<PmemPool> mLastPreviewFrameHeap;
    sp<PmemPool> mPostviewHeap;
	sp<PmemPool> mPostViewHeap;
    sp<PmemPool> mInitialPreviewHeap;
#endif

    sp<MMCameraDL> mMMCameraDLRef;

    bool startCamera();
    bool initPreview();
    bool initRecord();
    void deinitPreview();
    bool initRaw(bool initJpegHeap);
    bool initZslBuffers(bool initJpegHeap);
    bool deinitZslBuffers();
    bool initLiveSnapshot(int videowidth, int videoheight);
    bool initRawSnapshot();
    void deinitRaw();
    void deinitRawSnapshot();
    bool mPreviewThreadRunning;
    bool createSnapshotMemory (int numberOfRawBuffers, int numberOfJpegBuffers,
                                   bool initJpegHeap, int snapshotFormat = 1 /*PICTURE_FORMAT_JPEG*/);
    Mutex mPreviewThreadWaitLock;
    Condition mPreviewThreadWait;
    friend void *preview_thread(void *user);
    friend void *openCamera(void *data);
    void runPreviewThread(void *data);
    friend void *hfr_thread(void *user);
    void runHFRThread(void *data);
    bool mHFRThreadRunning;
	int mapBuffer(msm_frame *frame);
	int mapRawBuffer(msm_frame *frame);
	int mapThumbnailBuffer(msm_frame *frame);
	int mapJpegBuffer(mm_camera_buffer_t* buffer);
        int mapvideoBuffer( msm_frame *frame);
	int mapFrame(buffer_handle_t *buffer);
    Mutex mHFRThreadWaitLock;

    class FrameQueue : public RefBase{
    private:
        Mutex mQueueLock;
        Condition mQueueWait;
        bool mInitialized;

        Vector<struct msm_frame *> mContainer;
    public:
        FrameQueue();
        virtual ~FrameQueue();
        bool add(struct msm_frame *element);
        void flush();
        struct msm_frame* get();
        void init();
        void deinit();
        bool isInitialized();
    };

    FrameQueue mPreviewBusyQueue;

    bool mFrameThreadRunning;
    Mutex mFrameThreadWaitLock;
    Condition mFrameThreadWait;
    friend void *frame_thread(void *user);
    void runFrameThread(void *data);

    //720p recording video thread
    bool mVideoThreadExit;
    bool mVideoThreadRunning;
    Mutex mVideoThreadWaitLock;
    Condition mVideoThreadWait;
    friend void *video_thread(void *user);
    void runVideoThread(void *data);

    // smooth zoom
    int mTargetSmoothZoom;
    bool mSmoothzoomThreadExit;
    bool mSmoothzoomThreadRunning;
    Mutex mSmoothzoomThreadWaitLock;
    Mutex mSmoothzoomThreadLock;
    Condition mSmoothzoomThreadWait;
    friend void *smoothzoom_thread(void *user);
    void runSmoothzoomThread(void* data);

    // For Histogram
    int mStatsOn;
    int mCurrent;
    bool mSendData;
    Mutex mStatsWaitLock;
    Condition mStatsWait;

    //For Face Detection
    int mFaceDetectOn;
    bool mSendMetaData;
    Mutex mMetaDataWaitLock;

    bool mShutterPending;
    Mutex mShutterLock;

    bool mSnapshotThreadRunning;
    Mutex mSnapshotThreadWaitLock;
    Condition mSnapshotThreadWait;
    friend void *snapshot_thread(void *user);
    void runSnapshotThread(void *data);
    Mutex mRawPictureHeapLock;
    bool mJpegThreadRunning;
    Mutex mJpegThreadWaitLock;
    Condition mJpegThreadWait;
    bool mInSnapshotMode;
    Mutex mInSnapshotModeWaitLock;
    Condition mInSnapshotModeWait;
    bool mEncodePending;
    Mutex mEncodePendingWaitLock;
    Condition mEncodePendingWait;
	bool mBuffersInitialized;

    void debugShowPreviewFPS() const;
    void debugShowVideoFPS() const;

    int mSnapshotFormat;
    bool mFirstFrame;
    void hasAutoFocusSupport();
    void filterPictureSizes();
    void filterPreviewSizes();
    static void storeTargetType();
    bool supportsSceneDetection();
    bool supportsSelectableZoneAf();
    bool supportsFaceDetection();

    void initDefaultParameters();
    bool initImageEncodeParameters(int size);
    bool initZslParameter(void);
    status_t setCameraMode(const CameraParameters& params);
    status_t setPreviewSize(const CameraParameters& params);
    status_t setJpegThumbnailSize(const CameraParameters& params);
    status_t setPreviewFpsRange(const CameraParameters& params);
    status_t setPreviewFrameRate(const CameraParameters& params);
    status_t setPreviewFrameRateMode(const CameraParameters& params);
    status_t setRecordSize(const CameraParameters& params);
    status_t setPictureSize(const CameraParameters& params);
    status_t setJpegQuality(const CameraParameters& params);
    status_t setAntibanding(const CameraParameters& params);
    status_t setEffect(const CameraParameters& params);
    status_t setRecordingHint(const CameraParameters& params);
    status_t setExposureCompensation(const CameraParameters &params);
    status_t setAutoExposure(const CameraParameters& params);
    status_t setWhiteBalance(const CameraParameters& params);
    status_t setFlash(const CameraParameters& params);
    status_t setGpsLocation(const CameraParameters& params);
    status_t setRotation(const CameraParameters& params);
    status_t setZoom(const CameraParameters& params);
    status_t setFocusMode(const CameraParameters& params);
    status_t setFocusAreas(const CameraParameters& params);
    status_t setMeteringAreas(const CameraParameters& params);
    status_t setBrightness(const CameraParameters& params);
    status_t setSkinToneEnhancement(const CameraParameters& params);
    status_t setOrientation(const CameraParameters& params);
    status_t setLensshadeValue(const CameraParameters& params);
    status_t setMCEValue(const CameraParameters& params);
    status_t setHDRImaging(const CameraParameters& params);
    status_t setExpBracketing(const CameraParameters& params);
    status_t setISOValue(const CameraParameters& params);
    status_t setPictureFormat(const CameraParameters& params);
    status_t setSharpness(const CameraParameters& params);
    status_t setContrast(const CameraParameters& params);
    status_t setSaturation(const CameraParameters& params);
    status_t setSceneMode(const CameraParameters& params);
    status_t setContinuousAf(const CameraParameters& params);
    status_t setTouchAfAec(const CameraParameters& params);
    status_t setSceneDetect(const CameraParameters& params);
    status_t setStrTextures(const CameraParameters& params);
    status_t setPreviewFormat(const CameraParameters& params);
    status_t setSelectableZoneAf(const CameraParameters& params);
    status_t setHighFrameRate(const CameraParameters& params);
    bool register_record_buffers(bool register_buffer);
    status_t setRedeyeReduction(const CameraParameters& params);
    status_t setDenoise(const CameraParameters& params);
    status_t setZslParam(const CameraParameters& params);
    status_t setSnapshotCount(const CameraParameters& params);
    void setGpsParameters();
    bool storePreviewFrameForPostview();
    bool isValidDimension(int w, int h);
    status_t updateFocusDistances(const char *focusmode);
    int mStoreMetaDataInFrame;

    Mutex mLock;
	Mutex mDisplayLock;
    Mutex mCamframeTimeoutLock;
    bool camframe_timeout_flag;
    bool mReleasedRecordingFrame;

    Mutex mParametersLock;


    Mutex mCallbackLock;
    Mutex mOverlayLock;
	Mutex mRecordLock;
	Mutex mRecordFrameLock;
	Condition mRecordWait;
    Condition mStateWait;

    /* mJpegSize keeps track of the size of the accumulated JPEG.  We clear it
       when we are about to take a picture, so at any time it contains either
       zero, or the size of the last JPEG picture taken.
    */
    uint32_t mJpegSize;
    unsigned int        mPreviewFrameSize;
    unsigned int        mRecordFrameSize;
    int                 mRawSize;
    int                 mCbCrOffsetRaw;
    int                 mJpegMaxSize;
    int32_t                 mStatSize;


    cam_ctrl_dimension_t mDimension;
    bool mAutoFocusThreadRunning;
    Mutex mAutoFocusThreadLock;

    Mutex mAfLock;

    pthread_t mFrameThread;
    pthread_t mVideoThread;
    pthread_t mPreviewThread;
    pthread_t mSnapshotThread;
    pthread_t mDeviceOpenThread;
    pthread_t mSmoothzoomThread;
    pthread_t mHFRThread;

    common_crop_t mCrop;

    bool mInitialized;

    int mBrightness;
    int mSkinToneEnhancement;
    int mHJR;
    unsigned int mThumbnailMapped[MAX_SNAPSHOT_BUFFERS];
    unsigned int mThumbnailLockState[MAX_SNAPSHOT_BUFFERS];
    int mRawfd[MAX_SNAPSHOT_BUFFERS];
    int mRawSnapshotfd;
    int mJpegfd[MAX_SNAPSHOT_BUFFERS];
    int mRecordfd[9];
    camera_memory_t *mPreviewMapped[kPreviewBufferCount + MIN_UNDEQUEUD_BUFFER_COUNT];
    camera_memory_t *mRawMapped[MAX_SNAPSHOT_BUFFERS];
    camera_memory_t *mJpegMapped[MAX_SNAPSHOT_BUFFERS];
    camera_memory_t *mRawSnapshotMapped;
    camera_memory_t *mStatsMapped[3];
    camera_memory_t *mRecordMapped[9];
    camera_memory_t *mJpegCopyMapped;
    camera_memory_t* metadata_memory[9];
    camera_memory_t *mJpegLiveSnapMapped;
    int raw_main_ion_fd[MAX_SNAPSHOT_BUFFERS];
    int raw_snapshot_main_ion_fd;
    int Jpeg_main_ion_fd[MAX_SNAPSHOT_BUFFERS];
    int record_main_ion_fd[9];
    struct ion_allocation_data raw_alloc[MAX_SNAPSHOT_BUFFERS];
    struct ion_allocation_data raw_snapshot_alloc;
    struct ion_allocation_data Jpeg_alloc[MAX_SNAPSHOT_BUFFERS];
    struct ion_allocation_data record_alloc[9];
    struct ion_fd_data raw_ion_info_fd[MAX_SNAPSHOT_BUFFERS];
    struct ion_fd_data raw_snapshot_ion_info_fd;
    struct ion_fd_data Jpeg_ion_info_fd[MAX_SNAPSHOT_BUFFERS];
    struct ion_fd_data record_ion_info_fd[9];

    struct msm_frame frames[kPreviewBufferCount + MIN_UNDEQUEUD_BUFFER_COUNT];
    struct buffer_map frame_buffer[kPreviewBufferCount + MIN_UNDEQUEUD_BUFFER_COUNT];
    struct msm_frame *recordframes;
    struct msm_frame *rawframes;
    bool *record_buffers_tracking_flag;
    bool mInPreviewCallback;
    preview_stream_ops_t* mPreviewWindow;
    android_native_buffer_t *mPostViewBuffer;
    buffer_handle_t *mThumbnailBuffer[MAX_SNAPSHOT_BUFFERS];
    bool mIs3DModeOn;

    int32_t mMsgEnabled;    // camera msg to be handled
    camera_notify_callback mNotifyCallback;
    camera_data_callback mDataCallback;
    camera_data_timestamp_callback mDataCallbackTimestamp;
    camera_request_memory mGetMemory;
    void *mCallbackCookie;  // same for all callbacks
    int mDebugFps;
    int kPreviewBufferCountActual;
    int previewWidth, previewHeight;
    int yv12framesize;
    bool mSnapshotDone;
    int maxSnapshotWidth;
    int maxSnapshotHeight;
    bool mHasAutoFocusSupport;
    int videoWidth, videoHeight;

    bool mDisEnabled;
    int mRotation;
    bool mResetWindowCrop;
    int mThumbnailWidth, mThumbnailHeight;
    status_t setVpeParameters();
    status_t setDIS();
    bool strTexturesOn;
    int mPictureWidth;
    int mPictureHeight;
    int mPostviewWidth;
    int mPostviewHeight;
	int mTotalPreviewBufferCount;
    int mDenoiseValue;
    int mZslEnable;
    int mZslPanorama;
    bool mZslFlashEnable;
    cam_3d_frame_format_t mSnapshot3DFormat;
    bool mSnapshotCancel;
    bool mHFRMode;
    Mutex mSnapshotCancelLock;
    int mActualPictWidth;
    int mActualPictHeight;
    bool mUseJpegDownScaling;
    bool mPreviewStopping;
    bool mInHFRThread;
    Mutex mPmemWaitLock;
    Condition mPmemWait;
    bool mPrevHeapDeallocRunning;
    bool mHdrMode;
    bool mExpBracketMode;

    bool mMultiTouch;

    int mRecordingState;
};

extern "C" int HAL_getNumberOfCameras();
extern "C" void HAL_getCameraInfo(int cameraId, struct CameraInfo* cameraInfo);
extern "C" QualcommCameraHardware* HAL_openCameraHardware(int cameraId);
}; // namespace android

#endif
