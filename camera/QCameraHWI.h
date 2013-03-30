/*
** Copyright (c) 2011-2012 Code Aurora Forum. All rights reserved.
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

#ifndef ANDROID_HARDWARE_QCAMERA_HARDWARE_INTERFACE_H
#define ANDROID_HARDWARE_QCAMERA_HARDWARE_INTERFACE_H


#include <utils/threads.h>
//#include <camera/CameraHardwareInterface.h>
#include <hardware/camera.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryHeapPmem.h>
#include <utils/threads.h>
#include <cutils/properties.h>
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
#include <linux/msm_ion.h>
#include <camera.h>
//#include <camera_defs_i.h>
#include <mm_camera_interface2.h>

#include "mm_jpeg_encoder.h"

} //extern C

#include "QCameraHWI_Mem.h"
#include "QCameraStream.h"

//Error codes
#define  NOT_FOUND -1
#define MAX_ZOOM_RATIOS 62
#define QCIF_WIDTH      176
#define QCIF_HEIGHT     144
#define D1_WIDTH        720
#define D1_HEIGHT       480

#ifdef Q12
#undef Q12
#endif

#define Q12 4096
#define QCAMERA_PARM_ENABLE   1
#define QCAMERA_PARM_DISABLE  0

#define QCIF_WIDTH      176
#define QCIF_HEIGHT     144
#define D1_WIDTH        720
#define D1_HEIGHT       480

struct str_map {
    const char *const desc;
    int val;
};

struct preview_format_info_t {
   int Hal_format;
   cam_format_t mm_cam_format;
   cam_pad_format_t padding;
   int num_planar;
};

typedef enum {
  CAMERA_STATE_UNINITED,
  CAMERA_STATE_READY,
  CAMERA_STATE_PREVIEW_START_CMD_SENT,
  CAMERA_STATE_PREVIEW_STOP_CMD_SENT,
  CAMERA_STATE_PREVIEW,
  CAMERA_STATE_RECORD_START_CMD_SENT,  /*5*/
  CAMERA_STATE_RECORD_STOP_CMD_SENT,
  CAMERA_STATE_RECORD,
  CAMERA_STATE_SNAP_START_CMD_SENT,
  CAMERA_STATE_SNAP_STOP_CMD_SENT,
  CAMERA_STATE_SNAP_CMD_ACKED,  /*10 - snapshot comd acked, snapshot not done yet*/
  CAMERA_STATE_ZSL_START_CMD_SENT,
  CAMERA_STATE_ZSL,
  CAMERA_STATE_AF_START_CMD_SENT,
  CAMERA_STATE_AF_STOP_CMD_SENT,
  CAMERA_STATE_ERROR, /*15*/

  /*Add any new state above*/
  CAMERA_STATE_MAX
} HAL_camera_state_type_t;

enum {
  BUFFER_NOT_OWNED,
  BUFFER_UNLOCKED,
  BUFFER_LOCKED,
};

typedef enum {
  HAL_DUMP_FRM_PREVIEW = 1,
  HAL_DUMP_FRM_VIDEO = 1<<1,
  HAL_DUMP_FRM_MAIN = 1<<2,
  HAL_DUMP_FRM_THUMBNAIL = 1<<3,

  /*8 bits mask*/
  HAL_DUMP_FRM_MAX = 1 << 8
} HAL_cam_dump_frm_type_t;

#define HAL_DUMP_FRM_MASK_ALL ( HAL_DUMP_FRM_PREVIEW + HAL_DUMP_FRM_VIDEO + \
    HAL_DUMP_FRM_MAIN + HAL_DUMP_FRM_THUMBNAIL)
#define QCAMERA_HAL_PREVIEW_STOPPED    0
#define QCAMERA_HAL_PREVIEW_START      1
#define QCAMERA_HAL_PREVIEW_STARTED    2
#define QCAMERA_HAL_RECORDING_STARTED  3
#define QCAMERA_HAL_TAKE_PICTURE       4


typedef struct {
     int                     buffer_count;
	 buffer_handle_t        *buffer_handle[MM_CAMERA_MAX_NUM_FRAMES];
	 struct private_handle_t *private_buffer_handle[MM_CAMERA_MAX_NUM_FRAMES];
	 int                     stride[MM_CAMERA_MAX_NUM_FRAMES];
	 uint32_t                addr_offset[MM_CAMERA_MAX_NUM_FRAMES];
	 uint8_t                 local_flag[MM_CAMERA_MAX_NUM_FRAMES];
     int                     enqueued_flag[MM_CAMERA_MAX_NUM_FRAMES];
	 camera_memory_t        *camera_memory[MM_CAMERA_MAX_NUM_FRAMES];
} QCameraHalMemory_t;


typedef struct {
     int                     buffer_count;
     uint32_t                size;
     uint32_t                y_offset;
     uint32_t                cbcr_offset;
	 int                     fd[MM_CAMERA_MAX_NUM_FRAMES];
	 int                     local_flag[MM_CAMERA_MAX_NUM_FRAMES];
	 camera_memory_t*        camera_memory[MM_CAMERA_MAX_NUM_FRAMES];
     camera_memory_t*        metadata_memory[MM_CAMERA_MAX_NUM_FRAMES];
     int main_ion_fd[MM_CAMERA_MAX_NUM_FRAMES];
     struct ion_allocation_data alloc[MM_CAMERA_MAX_NUM_FRAMES];
     struct ion_fd_data ion_info_fd[MM_CAMERA_MAX_NUM_FRAMES];
} QCameraHalHeap_t;

typedef struct {
  int32_t msg_type;
  int32_t ext1;
  int32_t ext2;
  void    *cookie;
} argm_notify_t;

typedef struct {
  int32_t                  msg_type;
  camera_memory_t         *data;
  unsigned int             index;
  camera_frame_metadata_t *metadata;
  void                    *cookie;
} argm_data_cb_t;

typedef struct {
  camera_notify_callback notifyCb;
  camera_data_callback   dataCb;
  argm_notify_t argm_notify;
  argm_data_cb_t        argm_data_cb;
} app_notify_cb_t;

/* camera_area_t
 * rectangle with weight to store the focus and metering areas.
 * x1, y1, x2, y2: from -1000 to 1000
 * weight: 0 to 1000
 */
typedef struct {
    int x1, y1, x2, y2;
    int weight;
} camera_area_t;

//EXIF globals
static const char ExifAsciiPrefix[] = { 0x41, 0x53, 0x43, 0x49, 0x49, 0x0, 0x0, 0x0 };          // "ASCII\0\0\0"
static const char ExifUndefinedPrefix[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };   // "\0\0\0\0\0\0\0\0"

//EXIF detfines
#define MAX_EXIF_TABLE_ENTRIES           14
#define GPS_PROCESSING_METHOD_SIZE       101
#define FOCAL_LENGTH_DECIMAL_PRECISION   100
#define EXIF_ASCII_PREFIX_SIZE           8   //(sizeof(ExifAsciiPrefix))

typedef struct{
    //GPS tags
    rat_t       latitude[3];
    rat_t       longitude[3];
    char        lonRef[2];
    char        latRef[2];
    rat_t       altitude;
    rat_t       gpsTimeStamp[3];
    char        gpsDateStamp[20];
    char        gpsProcessingMethod[EXIF_ASCII_PREFIX_SIZE+GPS_PROCESSING_METHOD_SIZE];
    //Other tags
    char        dateTime[20];
    rat_t       focalLength;
    uint16_t    flashMode;
    uint16_t    isoSpeed;

    bool        mAltitude;
    bool        mLongitude;
    bool        mLatitude;
    bool        mTimeStamp;
    bool        mGpsProcess;

    int         mAltitude_ref;
    long        mGPSTimestamp;

} exif_values_t;


namespace android {

class QCameraStream;

class QCameraHardwareInterface : public virtual RefBase {
public:

    QCameraHardwareInterface(int  cameraId, int mode);

    /** Set the ANativeWindow to which preview frames are sent */
    int setPreviewWindow(preview_stream_ops_t* window);

    /** Set the notification and data callbacks */
    void setCallbacks(camera_notify_callback notify_cb,
            camera_data_callback data_cb,
            camera_data_timestamp_callback data_cb_timestamp,
            camera_request_memory get_memory,
            void *user);

    /**
     * The following three functions all take a msg_type, which is a bitmask of
     * the messages defined in include/ui/Camera.h
     */

    /**
     * Enable a message, or set of messages.
     */
    void enableMsgType(int32_t msg_type);

    /**
     * Disable a message, or a set of messages.
     *
     * Once received a call to disableMsgType(CAMERA_MSG_VIDEO_FRAME), camera
     * HAL should not rely on its client to call releaseRecordingFrame() to
     * release video recording frames sent out by the cameral HAL before and
     * after the disableMsgType(CAMERA_MSG_VIDEO_FRAME) call. Camera HAL
     * clients must not modify/access any video recording frame after calling
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME).
     */
    void disableMsgType(int32_t msg_type);

    /**
     * Query whether a message, or a set of messages, is enabled.  Note that
     * this is operates as an AND, if any of the messages queried are off, this
     * will return false.
     */
    int msgTypeEnabled(int32_t msg_type);

    /**
     * Start preview mode.
     */
    int startPreview();
    int startPreview2();

    /**
     * Stop a previously started preview.
     */
    void stopPreview();

    /**
     * Returns true if preview is enabled.
     */
    int previewEnabled();


    /**
     * Request the camera HAL to store meta data or real YUV data in the video
     * buffers sent out via CAMERA_MSG_VIDEO_FRAME for a recording session. If
     * it is not called, the default camera HAL behavior is to store real YUV
     * data in the video buffers.
     *
     * This method should be called before startRecording() in order to be
     * effective.
     *
     * If meta data is stored in the video buffers, it is up to the receiver of
     * the video buffers to interpret the contents and to find the actual frame
     * data with the help of the meta data in the buffer. How this is done is
     * outside of the scope of this method.
     *
     * Some camera HALs may not support storing meta data in the video buffers,
     * but all camera HALs should support storing real YUV data in the video
     * buffers. If the camera HAL does not support storing the meta data in the
     * video buffers when it is requested to do do, INVALID_OPERATION must be
     * returned. It is very useful for the camera HAL to pass meta data rather
     * than the actual frame data directly to the video encoder, since the
     * amount of the uncompressed frame data can be very large if video size is
     * large.
     *
     * @param enable if true to instruct the camera HAL to store
     *        meta data in the video buffers; false to instruct
     *        the camera HAL to store real YUV data in the video
     *        buffers.
     *
     * @return OK on success.
     */
    int storeMetaDataInBuffers(int enable);

    /**
     * Start record mode. When a record image is available, a
     * CAMERA_MSG_VIDEO_FRAME message is sent with the corresponding
     * frame. Every record frame must be released by a camera HAL client via
     * releaseRecordingFrame() before the client calls
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME). After the client calls
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME), it is the camera HAL's
     * responsibility to manage the life-cycle of the video recording frames,
     * and the client must not modify/access any video recording frames.
     */
    int startRecording();

    /**
     * Stop a previously started recording.
     */
    void stopRecording();

    /**
     * Returns true if recording is enabled.
     */
    int recordingEnabled();

    /**
     * Release a record frame previously returned by CAMERA_MSG_VIDEO_FRAME.
     *
     * It is camera HAL client's responsibility to release video recording
     * frames sent out by the camera HAL before the camera HAL receives a call
     * to disableMsgType(CAMERA_MSG_VIDEO_FRAME). After it receives the call to
     * disableMsgType(CAMERA_MSG_VIDEO_FRAME), it is the camera HAL's
     * responsibility to manage the life-cycle of the video recording frames.
     */
    void releaseRecordingFrame(const void *opaque);

    /**
     * Start auto focus, the notification callback routine is called with
     * CAMERA_MSG_FOCUS once when focusing is complete. autoFocus() will be
     * called again if another auto focus is needed.
     */
    int autoFocus();

    /**
     * Cancels auto-focus function. If the auto-focus is still in progress,
     * this function will cancel it. Whether the auto-focus is in progress or
     * not, this function will return the focus position to the default.  If
     * the camera does not support auto-focus, this is a no-op.
     */
    int cancelAutoFocus();

    /**
     * Take a picture.
     */
    int takePicture();

    /**
     * Cancel a picture that was started with takePicture. Calling this method
     * when no picture is being taken is a no-op.
     */
    int cancelPicture();

    /**
     * Set the camera parameters. This returns BAD_VALUE if any parameter is
     * invalid or not supported.
     */
    int setParameters(const char *parms);

    //status_t setParameters(const CameraParameters& params);
    /** Retrieve the camera parameters.  The buffer returned by the camera HAL
        must be returned back to it with put_parameters, if put_parameters
        is not NULL.
     */
    int getParameters(char **parms);

    /** The camera HAL uses its own memory to pass us the parameters when we
        call get_parameters.  Use this function to return the memory back to
        the camera HAL, if put_parameters is not NULL.  If put_parameters
        is NULL, then you have to use free() to release the memory.
    */
    void putParameters(char *);

    /**
     * Send command to camera driver.
     */
    int sendCommand(int32_t cmd, int32_t arg1, int32_t arg2);

    /**
     * Release the hardware resources owned by this object.  Note that this is
     * *not* done in the destructor.
     */
    void release();

    /**
     * Dump state of the camera hardware
     */
    int dump(int fd);

    //virtual sp<IMemoryHeap> getPreviewHeap() const;
    //virtual sp<IMemoryHeap> getRawHeap() const;


    status_t    takeLiveSnapshot();
    status_t    takeFullSizeLiveshot();
    bool        canTakeFullSizeLiveshot();

    //virtual status_t          getBufferInfo( sp<IMemory>& Frame,
    //size_t *alignedSize);
    void         getPictureSize(int *picture_width, int *picture_height) const;
    void         getPreviewSize(int *preview_width, int *preview_height) const;
    cam_format_t getPreviewFormat() const;

    cam_pad_format_t getPreviewPadding() const;

    //bool     useOverlay(void);
    //virtual status_t setOverlay(const sp<Overlay> &overlay);

    void        encodeData();

    void processEvent(mm_camera_event_t *);
    int  getJpegQuality() const;
    int  getNumOfSnapshots(void) const;
    int  getThumbSizesFromAspectRatio(uint32_t aspect_ratio,
                                     int *picture_width,
                                     int *picture_height);
    bool isRawSnapshot();
    bool mShutterSoundPlayed;
    void                dumpFrameToFile(struct msm_frame*, HAL_cam_dump_frm_type_t);

    static QCameraHardwareInterface *createInstance(int, int);
	//QCameraHardwareInterface(int cameraId, int mode);
    status_t setZSLLookBack(int mode, int value);
    void getZSLLookBack(int *mode, int *value);
    void setZSLEmptyQueueFlag(bool flag);
    void getZSLEmptyQueueFlag(bool *flag);
	int getZSLQueueDepth(void) const;
	int getZSLBackLookCount(void) const;
    //QCameraHardwareInterface(int  cameraId, int mode);
    ~QCameraHardwareInterface();
    int initHeapMem(QCameraHalHeap_t *heap,
				int num_of_buf,
				int pmem_type,
				int frame_len,
				int cbcr_off,
				int y_off,
				mm_cameara_stream_buf_t *StreamBuf,
                                mm_camera_buf_def_t *buf_def,
                                uint8_t num_planes,
                                uint32_t *planes);

    int releaseHeapMem( QCameraHalHeap_t *heap);
    int allocate_ion_memory(QCameraHalHeap_t *p_camera_memory, int cnt, int ion_type);
    int deallocate_ion_memory(QCameraHalHeap_t *p_camera_memory, int cnt);
    void dumpFrameToFile(const void * data, uint32_t size, char* name, char* ext, int index);
    preview_format_info_t  getPreviewFormatInfo( );
    bool isCameraReady();
    exif_tags_info_t* getExifData(){ return mExifData; }
    void resetExifData();
    void initExifData();
    int getExifTableNumEntries() { return mExifTableNumEntries; }
    void changeMode(camera_mode_t mode);
    int mZslFlashEnable;

private:
    int16_t  zoomRatios[MAX_ZOOM_RATIOS];
    bool mUseOverlay;

    void initDefaultParameters();
    bool getMaxPictureDimension(mm_camera_dimension_t *dim);

    status_t updateFocusDistances(const char *focusmode);

    bool native_set_parms(mm_camera_parm_type_t type, uint16_t length, void *value);
    bool native_set_parms( mm_camera_parm_type_t type, uint16_t length, void *value, int *result);

    void hasAutoFocusSupport();
    void debugShowPreviewFPS() const;
    //void prepareSnapshotAndWait();

    bool isPreviewRunning();
    bool isRecordingRunning();
    bool isSnapshotRunning();

    void processChannelEvent(mm_camera_ch_event_t *, app_notify_cb_t *);
    void processPreviewChannelEvent(mm_camera_ch_event_type_t channelEvent, app_notify_cb_t *);
    void processRecordChannelEvent(mm_camera_ch_event_type_t channelEvent, app_notify_cb_t *);
    void processSnapshotChannelEvent(mm_camera_ch_event_type_t channelEvent, app_notify_cb_t *);
    void processCtrlEvent(mm_camera_ctrl_event_t *, app_notify_cb_t *);
    void processStatsEvent(mm_camera_stats_event_t *, app_notify_cb_t *);
    void processInfoEvent(mm_camera_info_event_t *event, app_notify_cb_t *);
    void processprepareSnapshotEvent(cam_ctrl_status_t *);
    void roiEvent(fd_roi_t roi, app_notify_cb_t *);
    void zoomEvent(cam_ctrl_status_t *status, app_notify_cb_t *);
    void autofocusevent(cam_ctrl_status_t *status, app_notify_cb_t *);
    void handleZoomEventForPreview(app_notify_cb_t *);
    void handleZoomEventForSnapshot(void);
    status_t autoFocusEvent(cam_ctrl_status_t *, app_notify_cb_t *);

    void filterPictureSizes();
    bool supportsSceneDetection();
    bool supportsSelectableZoneAf();
    bool supportsFaceDetection();
    bool supportsRedEyeReduction();
    bool preview_parm_config (cam_ctrl_dimension_t* dim,CameraParameters& parm);

    void stopPreviewInternal();
    void stopRecordingInternal();
    //void stopPreviewZSL();
    status_t cancelPictureInternal();
    //status_t startPreviewZSL();
    void pausePreviewForSnapshot();
    void pausePreviewForZSL();
    status_t resumePreviewAfterSnapshot();

    status_t runFaceDetection();

    status_t          setParameters(const CameraParameters& params);
    CameraParameters&  getParameters() ;

    status_t setCameraMode(const CameraParameters& params);
    status_t setPictureSizeTable(void);
    status_t setPreviewSizeTable(void);
    status_t setPreviewSize(const CameraParameters& params);
    status_t setJpegThumbnailSize(const CameraParameters& params);
    status_t setPreviewFpsRange(const CameraParameters& params);
    status_t setPreviewFrameRate(const CameraParameters& params);
    status_t setPreviewFrameRateMode(const CameraParameters& params);
    status_t setVideoSize(const CameraParameters& params);
    status_t setPictureSize(const CameraParameters& params);
    status_t setJpegQuality(const CameraParameters& params);
    status_t setNumOfSnapshot(const CameraParameters& params);
    status_t setJpegRotation(int isZSL);
    int getJpegRotation(void);
    int getISOSpeedValue();
    status_t setAntibanding(const CameraParameters& params);
    status_t setEffect(const CameraParameters& params);
    status_t setExposureCompensation(const CameraParameters &params);
    status_t setAutoExposure(const CameraParameters& params);
    status_t setWhiteBalance(const CameraParameters& params);
    status_t setFlash(const CameraParameters& params);
    status_t setGpsLocation(const CameraParameters& params);
    status_t setRotation(const CameraParameters& params);
    status_t setZoom(const CameraParameters& params);
    status_t setFocusMode(const CameraParameters& params);
    status_t setBrightness(const CameraParameters& params);
    status_t setSkinToneEnhancement(const CameraParameters& params);
    status_t setOrientation(const CameraParameters& params);
    status_t setLensshadeValue(const CameraParameters& params);
    status_t setMCEValue(const CameraParameters& params);
    status_t setISOValue(const CameraParameters& params);
    status_t setPictureFormat(const CameraParameters& params);
    status_t setSharpness(const CameraParameters& params);
    status_t setContrast(const CameraParameters& params);
    status_t setSaturation(const CameraParameters& params);
    status_t setWaveletDenoise(const CameraParameters& params);
    status_t setSceneMode(const CameraParameters& params);
    status_t setContinuousAf(const CameraParameters& params);
    status_t setFaceDetection(const char *str);
    status_t setSceneDetect(const CameraParameters& params);
    status_t setStrTextures(const CameraParameters& params);
    status_t setPreviewFormat(const CameraParameters& params);
    status_t setSelectableZoneAf(const CameraParameters& params);
    status_t setOverlayFormats(const CameraParameters& params);
    status_t setHighFrameRate(const CameraParameters& params);
    status_t setRedeyeReduction(const CameraParameters& params);
    status_t setAEBracket(const CameraParameters& params);
    status_t setFaceDetect(const CameraParameters& params);
    status_t setDenoise(const CameraParameters& params);
    status_t setAecAwbLock(const CameraParameters & params);
    status_t setHistogram(int histogram_en);
    status_t setRecordingHint(const CameraParameters& params);
    status_t setFocusAreas(const CameraParameters& params);
    status_t setMeteringAreas(const CameraParameters& params);
    status_t setFullLiveshot(void);
    status_t setDISMode(void);
    status_t setCaptureBurstExp(void);
    void takePicturePrepareHardware( );

    isp3a_af_mode_t getAutoFocusMode(const CameraParameters& params);
    bool isValidDimension(int w, int h);

    String8 create_values_str(const str_map *values, int len);

    void setMyMode(int mode);
    bool isZSLMode();
    bool isWDenoiseEnabled();
    void wdenoiseEvent(cam_ctrl_status_t status, void *cookie);
    bool isLowPowerCamcorder();
    void freePictureTable(void);

    int32_t createPreview();
    int32_t createRecord();
    int32_t createSnapshot();

    int getHDRMode();
    //EXIF
    void addExifTag(exif_tag_id_t tagid, exif_tag_type_t type,
                        uint32_t count, uint8_t copy, void *data);
    void setExifTags();
    void setExifTagsGPS();
    void parseGPSCoordinate(const char *latlonString, rat_t* coord);

    int           mCameraId;
    camera_mode_t myMode;

    CameraParameters    mParameters;
    //sp<Overlay>         mOverlay;
    int32_t             mMsgEnabled;

    camera_notify_callback         mNotifyCb;
    camera_data_callback           mDataCb;
    camera_data_timestamp_callback mDataCbTimestamp;
    camera_request_memory          mGetMemory;
    void                           *mCallbackCookie;

    //sp<MemoryHeapBase>  mPreviewHeap;  //@Guru : Need to remove
    sp<AshmemPool>      mMetaDataHeap;

    mutable Mutex       mLock;
    //mutable Mutex       eventLock;
    Mutex         mCallbackLock;
    Mutex         mPreviewMemoryLock;
    Mutex         mRecordingMemoryLock;
    Mutex         mAutofocusLock;
    Mutex         mMetaDataWaitLock;
    pthread_mutex_t     mAsyncCmdMutex;
    pthread_cond_t      mAsyncCmdWait;

    QCameraStream       *mStreamDisplay;
    QCameraStream       *mStreamRecord;
    QCameraStream       *mStreamSnap;
	QCameraStream       *mStreamLiveSnap;

    cam_ctrl_dimension_t mDimension;
    int  previewWidth, previewHeight;
    int  videoWidth, videoHeight;
    int  maxSnapshotWidth, maxSnapshotHeight;
    int  mPreviewFormat;
    int  mFps;
    int  mDebugFps;
    int  mBrightness;
    int  mSkinToneEnhancement;
    int  mDenoiseValue;
    int  mHJR;
    int  mRotation;
    int  mTargetSmoothZoom;
    int  mSmoothZoomStep;
    int  mMaxZoom;
    int  mCurrentZoom;
    int  mSupportedPictureSizesCount;
    int  mFaceDetectOn;
    int  mDumpFrmCnt;
    int  mDumpSkipCnt;

    unsigned int mPictureSizeCount;
    unsigned int mPreviewSizeCount;

    bool mAutoFocusRunning;
    bool mMultiTouch;
    bool mHasAutoFocusSupport;
    bool mInitialized;
    bool mDisEnabled;
    bool strTexturesOn;
    bool mIs3DModeOn;
    bool mSmoothZoomRunning;
    bool mPreparingSnapshot;
    bool mParamStringInitialized;
    bool mZoomSupported;
    bool mSendMetaData;
    bool mFullLiveshotEnabled;
    bool mRecordingHint;
    int mHdrMode;

/*for histogram*/
    int            mStatsOn;
    int            mCurrentHisto;
    bool           mSendData;
    sp<AshmemPool> mStatHeap;
    camera_memory_t *mStatsMapped[3];
    int32_t        mStatSize;

    bool mZslLookBackMode;
    int mZslLookBackValue;
	int mHFRLevel;
    bool mZslEmptyQueueFlag;
    String8 mEffectValues;
    String8 mIsoValues;
    String8 mSceneModeValues;
    String8 mSceneDetectValues;
    String8 mFocusModeValues;
    String8 mSelectableZoneAfValues;
    String8 mAutoExposureValues;
    String8 mWhitebalanceValues;
    String8 mAntibandingValues;
    String8 mFrameRateModeValues;
    String8 mTouchAfAecValues;
    String8 mPreviewSizeValues;
    String8 mPictureSizeValues;
    String8 mFlashValues;
    String8 mLensShadeValues;
    String8 mMceValues;
    String8 mHistogramValues;
    String8 mSkinToneEnhancementValues;
    String8 mPictureFormatValues;
    String8 mDenoiseValues;
    String8 mZoomRatioValues;
    String8 mPreviewFrameRateValues;
    String8 mPreviewFormatValues;
    String8 mFaceDetectionValues;
    String8 mHfrValues;
    String8 mHfrSizeValues;
    String8 mRedeyeReductionValues;
    String8 denoise_value;
    String8 mFpsRangesSupportedValues;
    String8 mZslValues;

    friend class QCameraStream;
    friend class QCameraStream_record;
    friend class QCameraStream_preview;
    friend class QCameraStream_Snapshot;

    camera_size_type* mPictureSizes;
    camera_size_type* mPreviewSizes;
    const camera_size_type * mPictureSizesPtr;
    HAL_camera_state_type_t mCameraState;

     /* Temporary - can be removed after Honeycomb*/
#ifdef USE_ION
    sp<IonPool>  mPostPreviewHeap;
#else
    sp<PmemPool> mPostPreviewHeap;
#endif
     mm_cameara_stream_buf_t mPrevForPostviewBuf;
	 int mStoreMetaDataInFrame;
	 preview_stream_ops_t *mPreviewWindow;
     Mutex                mStateLock;
	 int                  mPreviewState;
     QCameraHalMemory_t   mPreviewMemory;
     QCameraHalHeap_t     mSnapshotMemory;
     QCameraHalHeap_t     mThumbnailMemory;
     QCameraHalHeap_t     mRecordingMemory;
     QCameraHalHeap_t     mJpegMemory;
     QCameraHalHeap_t     mRawMemory;
	 camera_frame_metadata_t mMetadata;
	 camera_face_t           mFace[MAX_ROI];
     preview_format_info_t  mPreviewFormatInfo;
     friend void liveshot_callback(mm_camera_ch_data_buf_t *frame,void *user_data);

     //EXIF
     exif_tags_info_t       mExifData[MAX_EXIF_TABLE_ENTRIES];  //Exif tags for JPEG encoder
     exif_values_t          mExifValues;                        //Exif values in usable format
     int                    mExifTableNumEntries;            //NUmber of entries in mExifData
};

}; // namespace android

#endif
