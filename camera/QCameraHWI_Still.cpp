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

/*#error uncomment this for compiler test!*/

#define LOG_NDEBUG 0
#define LOG_NDDEBUG 0
#define LOG_NIDEBUG 0
#define LOG_TAG "QCameraHWI_Still"
#include <utils/Log.h>
#include <utils/threads.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <media/mediarecorder.h>
#include <math.h>
#include "QCameraHAL.h"
#include "QCameraHWI.h"

#define THUMBNAIL_DEFAULT_WIDTH 512
#define THUMBNAIL_DEFAULT_HEIGHT 384

/* following code implement the still image capture & encoding logic of this class*/
namespace android {

typedef enum {
    SNAPSHOT_STATE_ERROR,
    SNAPSHOT_STATE_UNINIT,
    SNAPSHOT_STATE_CH_ACQUIRED,
    SNAPSHOT_STATE_BUF_NOTIF_REGD,
    SNAPSHOT_STATE_BUF_INITIALIZED,
    SNAPSHOT_STATE_INITIALIZED,
    SNAPSHOT_STATE_IMAGE_CAPTURE_STRTD,
    SNAPSHOT_STATE_YUV_RECVD,
    SNAPSHOT_STATE_JPEG_ENCODING,
    SNAPSHOT_STATE_JPEG_ENCODE_DONE,
    SNAPSHOT_STATE_JPEG_COMPLETE_ENCODE_DONE,

    /*Add any new state above*/
    SNAPSHOT_STATE_MAX
} snapshot_state_type_t;


//-----------------------------------------------------------------------
// Constants
//----------------------------------------------------------------------
static const int PICTURE_FORMAT_JPEG = 1;
static const int PICTURE_FORMAT_RAW = 2;
static const int POSTVIEW_SMALL_HEIGHT = 144;

// ---------------------------------------------------------------------------
/* static functions*/
// ---------------------------------------------------------------------------



/* TBD: Temp: to be removed*/
static pthread_mutex_t g_s_mutex;
static int g_status = 0;
static pthread_cond_t g_s_cond_v;

static void mm_app_snapshot_done()
{
  pthread_mutex_lock(&g_s_mutex);
  g_status = TRUE;
  pthread_cond_signal(&g_s_cond_v);
  pthread_mutex_unlock(&g_s_mutex);
}

static void mm_app_snapshot_wait()
{
        pthread_mutex_lock(&g_s_mutex);
        if(FALSE == g_status) pthread_cond_wait(&g_s_cond_v, &g_s_mutex);
        pthread_mutex_unlock(&g_s_mutex);
    g_status = FALSE;
}

static int mm_app_dump_snapshot_frame(char *filename,
                                      const void *buffer,
                                      uint32_t len)
{
    char bufp[128];
    int file_fdp;
    int rc = 0;

    file_fdp = open(filename, O_RDWR | O_CREAT, 0777);

    if (file_fdp < 0) {
        rc = -1;
        goto end;
    }
    write(file_fdp,
        (const void *)buffer, len);
    close(file_fdp);
end:
    return rc;
}

/* Callback received when a frame is available after snapshot*/
static void snapshot_notify_cb(mm_camera_ch_data_buf_t *recvd_frame,
                               void *user_data)
{
    QCameraStream_Snapshot *pme = (QCameraStream_Snapshot *)user_data;

    ALOGD("%s: E", __func__);

    if (pme != NULL) {
        pme->receiveRawPicture(recvd_frame);
    }
    else{
        ALOGW("%s: Snapshot obj NULL in callback", __func__);
    }

    ALOGD("%s: X", __func__);

}

/* Once we give frame for encoding, we get encoded jpeg image
   fragments by fragment. We'll need to store them in a buffer
   to form complete JPEG image */
static void snapshot_jpeg_fragment_cb(uint8_t *ptr,
                                      uint32_t size,
                                      void *user_data)
{
    QCameraStream_Snapshot *pme = (QCameraStream_Snapshot *)user_data;

    ALOGE("%s: E",__func__);
    if (pme != NULL) {
        pme->receiveJpegFragment(ptr,size);
    }
    else
        ALOGW("%s: Receive jpeg fragment cb obj Null", __func__);

    ALOGD("%s: X",__func__);
}

/* This callback is received once the complete JPEG encoding is done */
static void snapshot_jpeg_cb(jpeg_event_t event, void *user_data)
{
    QCameraStream_Snapshot *pme = (QCameraStream_Snapshot *)user_data;
    ALOGE("%s: E ",__func__);

    if (event != JPEG_EVENT_DONE) {
        if (event == JPEG_EVENT_THUMBNAIL_DROPPED) {
            ALOGE("%s: Error in thumbnail encoding (event: %d)!!!",
                 __func__, event);
            ALOGD("%s: X",__func__);
            return;
        }
        else {
            ALOGE("%s: Error (event: %d) while jpeg encoding!!!",
                 __func__, event);
        }
    }

    if (pme != NULL) {
       pme->receiveCompleteJpegPicture(event);
       ALOGE(" Completed issuing JPEG callback");
       /* deinit only if we are done taking requested number of snapshots */
       if (pme->getSnapshotState() == SNAPSHOT_STATE_JPEG_COMPLETE_ENCODE_DONE) {
           ALOGE(" About to issue deinit callback");
       /* If it's ZSL Mode, we don't deinit now. We'll stop the polling thread and
          deinit the channel/buffers only when we change the mode from zsl to
          non-zsl. */
           if (!(pme->isZSLMode())) {
               pme->stop();
           }
           /* If we've just taken a picture with the flash by switching from ZSL mode to
              Non-ZSL mode, switch back to ZSL mode */
           if(pme->mHalCamCtrl->mZslFlashEnable) {
               pme->mHalCamCtrl->mZslFlashEnable=0;
               pme->mHalCamCtrl->changeMode((camera_mode_t)(CAMERA_SUPPORT_MODE_ZSL | CAMERA_SUPPORT_MODE_2D));
           }
        }
    }
    else
        ALOGW("%s: Receive jpeg cb Obj Null", __func__);


    ALOGD("%s: X",__func__);

}

// ---------------------------------------------------------------------------
/* private functions*/
// ---------------------------------------------------------------------------

void QCameraStream_Snapshot::
receiveJpegFragment(uint8_t *ptr, uint32_t size)
{
    ALOGE("%s: E", __func__);
#if 0
    if (mJpegHeap != NULL) {
        ALOGE("%s: Copy jpeg...", __func__);
        memcpy((uint8_t *)mJpegHeap->mHeap->base()+ mJpegOffset, ptr, size);
        mJpegOffset += size;
    }
    else {
        ALOGE("%s: mJpegHeap is NULL!", __func__);
    }
    #else
    if(mHalCamCtrl->mJpegMemory.camera_memory[0] != NULL && ptr != NULL && size > 0) {
        memcpy((uint8_t *)((uint32_t)mHalCamCtrl->mJpegMemory.camera_memory[0]->data + mJpegOffset), ptr, size);
        mJpegOffset += size;


        /*
                memcpy((uint8_t *)((uint32_t)mHalCamCtrl->mJpegMemory.camera_memory[0]->data + mJpegOffset), ptr, size);
                mJpegOffset += size;
        */
    } else {
        ALOGE("%s: mJpegHeap is NULL!", __func__);
    }


    #endif

    ALOGD("%s: X", __func__);
}


void QCameraStream_Snapshot::
receiveCompleteJpegPicture(jpeg_event_t event)
{
    int msg_type = CAMERA_MSG_COMPRESSED_IMAGE;
    ALOGE("%s: E", __func__);
    camera_memory_t *encodedMem = NULL;
    camera_data_callback jpg_data_cb = NULL;
    bool fail_cb_flag = false;

    //Mutex::Autolock l(&snapshotLock);
    if(!mActive && !isLiveSnapshot()) {
        ALOGE("Cancel Picture");
        goto end;
    }

    mStopCallbackLock.lock( );
    if(mCurrentFrameEncoded!=NULL && isLiveSnapshot()){
        ALOGE("<DEBUG>: Calling buf done for liveshot buffer");
        cam_evt_buf_done(mCameraId, mCurrentFrameEncoded);
        free(mCurrentFrameEncoded);
        mCurrentFrameEncoded=NULL;
    }
    /* If for some reason jpeg heap is NULL we'll just return */

    ALOGE("%s: Calling upperlayer callback to store JPEG image", __func__);

    msg_type = CAMERA_MSG_COMPRESSED_IMAGE;
    mHalCamCtrl->dumpFrameToFile(mHalCamCtrl->mJpegMemory.camera_memory[0]->data, mJpegOffset, (char *)"marvin", (char *)"jpg", 0);
    if (mHalCamCtrl->mDataCb && (mHalCamCtrl->mMsgEnabled & msg_type)) {
        // Create camera_memory_t object backed by the same physical
        // memory but with actual bitstream size.
        camera_memory_t *encodedMem = mHalCamCtrl->mGetMemory(
            mHalCamCtrl->mJpegMemory.fd[0], mJpegOffset, 1,
            mHalCamCtrl);
        if (!encodedMem || !encodedMem->data) {
            ALOGE("%s: mGetMemory failed.\n", __func__);
            goto end;
        }
        memcpy(encodedMem->data, mHalCamCtrl->mJpegMemory.camera_memory[0]->data, mJpegOffset );
//        ALOGE("%s: Calling mDataCb %x",__func__, mHalCamCtrl->mDataCb);
      mStopCallbackLock.unlock();
      if(mActive || isLiveSnapshot()){
          jpg_data_cb  = mHalCamCtrl->mDataCb;
      }
      if (jpg_data_cb != NULL) {
        jpg_data_cb (msg_type,
                             encodedMem, 0, NULL,
                             mHalCamCtrl->mCallbackCookie);
        encodedMem->release( encodedMem );
        jpg_data_cb = NULL;
      }
    } else {
      ALOGW("%s: JPEG callback was cancelled--not delivering image.", __func__);
    }

    //reset jpeg_offset
    mJpegOffset = 0;
    mm_jpeg_encoder_join();

    /* Tell lower layer that we are done with this buffer.
       If it's live snapshot, we don't need to call it. Recording
       object will take care of it */
    if (!isLiveSnapshot()) {
        ALOGD("%s: Calling buf done for frame id %d buffer: %x", __func__,
             mCurrentFrameEncoded->snapshot.main.idx,
             (unsigned int)mCurrentFrameEncoded->snapshot.main.frame->buffer);
        cam_evt_buf_done(mCameraId, mCurrentFrameEncoded);
    }

end:

    /* free the resource we allocated to maintain the structure */
    //mm_camera_do_munmap(main_fd, (void *)main_buffer_addr, mSnapshotStreamBuf.frame_len);
    if(! isLiveSnapshot() ) {
        if(mCurrentFrameEncoded) {
            free(mCurrentFrameEncoded);
            mCurrentFrameEncoded = NULL;
        }
    }
    setSnapshotState(SNAPSHOT_STATE_JPEG_ENCODE_DONE);

    mNumOfRecievedJPEG++;
    mHalCamCtrl->resetExifData();

    /* Before leaving check the jpeg queue. If it's not empty give the available
       frame for encoding*/
    if (!mSnapshotQueue.isEmpty()) {
        ALOGI("%s: JPEG Queue not empty. Dequeue and encode.", __func__);
        mm_camera_ch_data_buf_t* buf =
            (mm_camera_ch_data_buf_t *)mSnapshotQueue.dequeue();
        //encodeDisplayAndSave(buf, 1);
        if ( NO_ERROR != encodeDisplayAndSave(buf, 1)){
          fail_cb_flag = true;
        }
    }
    else
    {

        /* getRemainingSnapshots call will give us number of snapshots still
           remaining after flushing current zsl buffer once*/
        int remaining_cb = 0;//mHalCamCtrl->getRemainingSnapshots();
        if (!remaining_cb) {
            ALOGD("%s: Complete JPEG Encoding Done!", __func__);
            setSnapshotState(SNAPSHOT_STATE_JPEG_COMPLETE_ENCODE_DONE);
            mBurstModeFlag = false;
            /* in case of zsl, we need to reset some of the zsl attributes */
            if (isZSLMode()){
                ALOGD("%s: Resetting the ZSL attributes", __func__);
                setZSLChannelAttribute();
            }
        }
    }


    ALOGD("%s: X", __func__);
}


status_t QCameraStream_Snapshot::
configSnapshotDimension(cam_ctrl_dimension_t* dim)
{
    bool matching = true;
    cam_format_t img_format;
    status_t ret = NO_ERROR;
    ALOGD("%s: E", __func__);

    ALOGD("%s:Passed picture size: %d X %d", __func__,
         dim->picture_width, dim->picture_height);
    ALOGD("%s:Passed postview size: %d X %d", __func__,
         dim->ui_thumbnail_width, dim->ui_thumbnail_height);

    /* First check if the picture resolution is the same, if not, change it*/
    mHalCamCtrl->getPictureSize(&mPictureWidth, &mPictureHeight);
    ALOGD("%s: Picture size received: %d x %d", __func__,
         mPictureWidth, mPictureHeight);
    /*Current VFE software design requires picture size >= display size for ZSL*/
    if (isZSLMode()){
        mPostviewWidth = dim->display_width;
        mPostviewHeight = dim->display_height;
    } else {
        mPostviewWidth = mHalCamCtrl->mParameters.getInt(CameraParameters::KEY_JPEG_THUMBNAIL_WIDTH);
        mPostviewHeight =  mHalCamCtrl->mParameters.getInt(CameraParameters::KEY_JPEG_THUMBNAIL_HEIGHT);
    }
    if (isLiveSnapshot()){
        mPostviewWidth = THUMBNAIL_DEFAULT_WIDTH;
        mPostviewHeight = THUMBNAIL_DEFAULT_HEIGHT;
    }
    /*If application requested thumbnail size to be (0,0) 
       then configure second outout to a default size.
       Jpeg encoder will drop thumbnail as reflected in encodeParams.
    */
    mDropThumbnail = false;
    if (mPostviewWidth == 0 && mPostviewHeight == 0) {
         mPostviewWidth = THUMBNAIL_DEFAULT_WIDTH;
         mPostviewHeight = THUMBNAIL_DEFAULT_HEIGHT;
         mDropThumbnail = true;
    }

    ALOGD("%s: Postview size received: %d x %d", __func__,
         mPostviewWidth, mPostviewHeight);

    matching = (mPictureWidth == dim->picture_width) &&
        (mPictureHeight == dim->picture_height);
    matching &= (dim->ui_thumbnail_width == mPostviewWidth) &&
        (dim->ui_thumbnail_height == mPostviewHeight);

    /* picture size currently set do not match with the one wanted
       by user.*/
    if (!matching) {
        if (mPictureWidth < mPostviewWidth || mPictureHeight < mPostviewHeight) {
            //Changes to Handle VFE limitation.
            mActualPictureWidth = mPictureWidth;
            mActualPictureHeight = mPictureHeight;
            mPictureWidth = mPostviewWidth;
            mPictureHeight = mPostviewHeight;
            mJpegDownscaling = TRUE;
        }else{
            mJpegDownscaling = FALSE;
        }
        dim->picture_width  = mPictureWidth;
        dim->picture_height = mPictureHeight;
        dim->ui_thumbnail_height = mThumbnailHeight = mPostviewHeight;
        dim->ui_thumbnail_width = mThumbnailWidth = mPostviewWidth;
    }
    #if 0
    img_format = mHalCamCtrl->getPreviewFormat();
    if (img_format) {
        matching &= (img_format == dim->main_img_format);
        if (!matching) {
            dim->main_img_format = img_format;
            dim->thumb_format = img_format;
        }
    }
    #endif
    if (!matching) {
         ALOGD("%s: Image Sizes before set parm call: main: %dx%d thumbnail: %dx%d",
              __func__,
              dim->picture_width, dim->picture_height,
              dim->ui_thumbnail_width, dim->ui_thumbnail_height);

        ret = cam_config_set_parm(mCameraId, MM_CAMERA_PARM_DIMENSION,dim);
        if (NO_ERROR != ret) {
            ALOGE("%s: error - can't config snapshot parms!", __func__);
            ret = FAILED_TRANSACTION;
            goto end;
        }
    }
    /* set_parm will return corrected dimension based on aspect ratio and
       ceiling size */
    mPictureWidth = dim->picture_width;
    mPictureHeight = dim->picture_height;
    mPostviewHeight = mThumbnailHeight = dim->ui_thumbnail_height;
    mPostviewWidth = mThumbnailWidth = dim->ui_thumbnail_width;
    mPictureFormat= dim->main_img_format;
    mThumbnailFormat = dim->thumb_format;

    ALOGD("%s: Image Format: %d", __func__, dim->main_img_format);
    ALOGD("%s: Image Sizes: main: %dx%d thumbnail: %dx%d", __func__,
         dim->picture_width, dim->picture_height,
         dim->ui_thumbnail_width, dim->ui_thumbnail_height);
end:
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::
initRawSnapshotChannel(cam_ctrl_dimension_t *dim,
                       int num_of_snapshots)
{
    status_t ret = NO_ERROR;
    mm_camera_ch_image_fmt_parm_t fmt;
    mm_camera_channel_attr_t ch_attr;

    mm_camera_raw_streaming_type_t raw_stream_type =
        MM_CAMERA_RAW_STREAMING_CAPTURE_SINGLE;

    ALOGD("%s: E", __func__);

    /* Initialize stream - set format, acquire channel */
    /*TBD: Currently we only support single raw capture*/
    ALOGE("num_of_snapshots = %d",num_of_snapshots);
    if (num_of_snapshots == 1) {
        raw_stream_type = MM_CAMERA_RAW_STREAMING_CAPTURE_SINGLE;
    }

    /* Set channel attribute */
    ALOGD("%s: Set Raw Snapshot Channel attribute", __func__);
    memset(&ch_attr, 0, sizeof(ch_attr));
    ch_attr.type = MM_CAMERA_CH_ATTR_RAW_STREAMING_TYPE;
    ch_attr.raw_streaming_mode = raw_stream_type;

    if( NO_ERROR !=
        cam_ops_ch_set_attr(mCameraId, MM_CAMERA_CH_RAW, &ch_attr)) {
        ALOGD("%s: Failure setting Raw channel attribute.", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    memset(&fmt, 0, sizeof(mm_camera_ch_image_fmt_parm_t));
    fmt.ch_type = MM_CAMERA_CH_RAW;
    fmt.def.fmt = CAMERA_BAYER_SBGGR10;
    fmt.def.dim.width = dim->raw_picture_width;
    fmt.def.dim.height = dim->raw_picture_height;


    ALOGV("%s: Raw snapshot channel fmt: %d", __func__,
         fmt.def.fmt);
    ALOGV("%s: Raw snapshot resolution: %dX%d", __func__,
         dim->raw_picture_width, dim->raw_picture_height);

    ALOGD("%s: Set Raw Snapshot channel image format", __func__);
    ret = cam_config_set_parm(mCameraId, MM_CAMERA_PARM_CH_IMAGE_FMT, &fmt);
    if (NO_ERROR != ret) {
        ALOGE("%s: Set Raw Snapshot Channel format err=%d\n", __func__, ret);
        ret = FAILED_TRANSACTION;
        goto end;
    }

end:
    if (ret != NO_ERROR) {
        handleError();
    }
    ALOGE("%s: X", __func__);
    return ret;

}

status_t QCameraStream_Snapshot::
setZSLChannelAttribute(void)
{
    status_t ret = NO_ERROR;
    mm_camera_channel_attr_t ch_attr;
    ALOGD("%s: E", __func__);

    memset(&ch_attr, 0, sizeof(mm_camera_channel_attr_t));
    ch_attr.type = MM_CAMERA_CH_ATTR_BUFFERING_FRAME;
    ch_attr.buffering_frame.look_back = mHalCamCtrl->getZSLBackLookCount();
    ch_attr.buffering_frame.water_mark = mHalCamCtrl->getZSLQueueDepth();
    ALOGE("%s: ZSL queue_depth = %d, back_look_count = %d", __func__,
         ch_attr.buffering_frame.water_mark,
         ch_attr.buffering_frame.look_back);
    if( NO_ERROR !=
        cam_ops_ch_set_attr(mCameraId, MM_CAMERA_CH_SNAPSHOT, &ch_attr)) {
        ALOGD("%s: Failure setting ZSL channel attribute.", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }
end:
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::
initSnapshotFormat(cam_ctrl_dimension_t *dim)
{
    status_t ret = NO_ERROR;
    mm_camera_ch_image_fmt_parm_t fmt;

    ALOGD("%s: E", __func__);

    /* For ZSL mode we'll need to set channel attribute */
    if (isZSLMode()) {
        ret = setZSLChannelAttribute();
        if (ret != NO_ERROR) {
            goto end;
        }
    }

    memset(&fmt, 0, sizeof(mm_camera_ch_image_fmt_parm_t));
    fmt.ch_type = MM_CAMERA_CH_SNAPSHOT;
    fmt.snapshot.main.fmt = dim->main_img_format;
    fmt.snapshot.main.dim.width = dim->picture_width;
    fmt.snapshot.main.dim.height = dim->picture_height;

    fmt.snapshot.thumbnail.fmt = dim->thumb_format;
    fmt.snapshot.thumbnail.dim.width = dim->ui_thumbnail_width;
    fmt.snapshot.thumbnail.dim.height = dim->ui_thumbnail_height;

    ALOGV("%s: Snapshot channel fmt = main: %d thumbnail: %d", __func__,
         dim->main_img_format, dim->thumb_format);
    ALOGV("%s: Snapshot channel resolution = main: %dX%d  thumbnail: %dX%d",
         __func__, dim->picture_width, dim->picture_height,
         dim->ui_thumbnail_width, dim->ui_thumbnail_height);

    ALOGD("%s: Set Snapshot channel image format", __func__);
    ret = cam_config_set_parm(mCameraId, MM_CAMERA_PARM_CH_IMAGE_FMT, &fmt);
    if (NO_ERROR != ret) {
        ALOGE("%s: Set Snapshot Channel format err=%d\n", __func__, ret);
        ret = FAILED_TRANSACTION;
        goto end;
    }

end:
    if (ret != NO_ERROR) {
        handleError();
    }
    ALOGE("%s: X", __func__);
    return ret;

}

void QCameraStream_Snapshot::
deinitSnapshotChannel(mm_camera_channel_type_t ch_type)
{
    ALOGD("%s: E", __func__);

    /* unreg buf notify*/
    if (getSnapshotState() >= SNAPSHOT_STATE_BUF_NOTIF_REGD){
        if (NO_ERROR != cam_evt_register_buf_notify(mCameraId,
                        ch_type, NULL,(mm_camera_register_buf_cb_type_t)NULL,0, this)) {
            ALOGE("%s: Failure to unregister buf notification", __func__);
        }
    }

    if (getSnapshotState() >= SNAPSHOT_STATE_CH_ACQUIRED) {
        ALOGD("%s: Release snapshot channel", __func__);
        cam_ops_ch_release(mCameraId, ch_type);
    }

    ALOGD("%s: X",__func__);
}

status_t QCameraStream_Snapshot::
initRawSnapshotBuffers(cam_ctrl_dimension_t *dim, int num_of_buf)
{
    status_t ret = NO_ERROR;
    struct msm_frame *frame;
    uint32_t frame_len;
    uint8_t num_planes;
    uint32_t planes[VIDEO_MAX_PLANES];
    mm_camera_reg_buf_t reg_buf;

    ALOGD("%s: E", __func__);
    memset(&reg_buf,  0,  sizeof(mm_camera_reg_buf_t));
    memset(&mSnapshotStreamBuf, 0, sizeof(mSnapshotStreamBuf));

    if ((num_of_buf == 0) || (num_of_buf > MM_CAMERA_MAX_NUM_FRAMES)) {
        ALOGE("%s: Invalid number of buffers (=%d) requested!", __func__, num_of_buf);
        ret = BAD_VALUE;
        goto end;
    }

    reg_buf.def.buf.mp = new mm_camera_mp_buf_t[num_of_buf];
    if (!reg_buf.def.buf.mp) {
      ALOGE("%s Error allocating memory for mplanar struct ", __func__);
      ret = NO_MEMORY;
      goto end;
    }
    memset(reg_buf.def.buf.mp, 0, num_of_buf * sizeof(mm_camera_mp_buf_t));

    /* Get a frame len for buffer to be allocated*/
    frame_len = mm_camera_get_msm_frame_len(CAMERA_BAYER_SBGGR10,
                                            myMode,
                                            dim->raw_picture_width,
                                            dim->raw_picture_height,
                                            OUTPUT_TYPE_S,
                                            &num_planes, planes);
    ALOGE("Got frame_len=%d",frame_len);

    if (mHalCamCtrl->initHeapMem(&mHalCamCtrl->mRawMemory, num_of_buf,
                                        frame_len, 0, planes[0], MSM_PMEM_RAW_MAINIMG,
                                        &mSnapshotStreamBuf, &reg_buf.def,
                                        num_planes, planes) < 0) {
        ret = NO_MEMORY;
        goto end;
    }

    /* register the streaming buffers for the channel*/
    reg_buf.ch_type = MM_CAMERA_CH_RAW;
    reg_buf.def.num = mSnapshotStreamBuf.num;

    ret = cam_config_prepare_buf(mCameraId, &reg_buf);
    if(ret != NO_ERROR) {
        ALOGV("%s:reg snapshot buf err=%d\n", __func__, ret);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    /* If we have reached here successfully, we have allocated buffer.
       Set state machine.*/
    setSnapshotState(SNAPSHOT_STATE_BUF_INITIALIZED);

end:
    /* If it's error, we'll need to do some needful */
    if (ret != NO_ERROR) {
        handleError();
    }
    if (reg_buf.def.buf.mp)
      delete []reg_buf.def.buf.mp;
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::deinitRawSnapshotBuffers(void)
{
    int ret = NO_ERROR;

    ALOGD("%s: E", __func__);

    /* deinit buffers only if we have already allocated */
    if (getSnapshotState() >= SNAPSHOT_STATE_BUF_INITIALIZED ){

        ALOGD("%s: Unpreparing Snapshot Buffer", __func__);
        ret = cam_config_unprepare_buf(mCameraId, MM_CAMERA_CH_RAW);
        if(ret != NO_ERROR) {
            ALOGE("%s:Unreg Raw snapshot buf err=%d\n", __func__, ret);
            ret = FAILED_TRANSACTION;
            goto end;
        }
        mHalCamCtrl->releaseHeapMem(&mHalCamCtrl->mRawMemory);
    }

end:
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::
initSnapshotBuffers(cam_ctrl_dimension_t *dim, int num_of_buf)
{
    status_t ret = NO_ERROR;
    struct msm_frame *frame;
    uint32_t frame_len, y_off, cbcr_off;
    uint8_t num_planes;
    uint32_t planes[VIDEO_MAX_PLANES];
    mm_camera_reg_buf_t reg_buf;
    int rotation = 0;

    ALOGD("%s: E", __func__);
    memset(&reg_buf,  0,  sizeof(mm_camera_reg_buf_t));
    memset(&mSnapshotStreamBuf, 0, sizeof(mSnapshotStreamBuf));

    if ((num_of_buf == 0) || (num_of_buf > MM_CAMERA_MAX_NUM_FRAMES)) {
        ALOGE("%s: Invalid number of buffers (=%d) requested!",
             __func__, num_of_buf);
        ret = BAD_VALUE;
        goto end;
    }

    ALOGD("%s: Mode: %d Num_of_buf: %d ImageSizes: main: %dx%d thumb: %dx%d",
         __func__, myMode, num_of_buf,
         dim->picture_width, dim->picture_height,
         dim->ui_thumbnail_width, dim->ui_thumbnail_height);

    reg_buf.snapshot.main.buf.mp = new mm_camera_mp_buf_t[num_of_buf];
    if (!reg_buf.snapshot.main.buf.mp) {
          ALOGE("%s Error allocating memory for mplanar struct ", __func__);
          ret = NO_MEMORY;
          goto end;
    }
    memset(reg_buf.snapshot.main.buf.mp, 0,
      num_of_buf * sizeof(mm_camera_mp_buf_t));

    if (!isFullSizeLiveshot()) {
      reg_buf.snapshot.thumbnail.buf.mp = new mm_camera_mp_buf_t[num_of_buf];
      if (!reg_buf.snapshot.thumbnail.buf.mp) {
        ALOGE("%s Error allocating memory for mplanar struct ", __func__);
        ret = NO_MEMORY;
        goto end;
      }
      memset(reg_buf.snapshot.thumbnail.buf.mp, 0,
        num_of_buf * sizeof(mm_camera_mp_buf_t));
    }
    /* Number of buffers to be set*/
    /* Set the JPEG Rotation here since get_buffer_offset needs
     * the value of rotation.*/
    mHalCamCtrl->setJpegRotation(isZSLMode());
    rotation = mHalCamCtrl->getJpegRotation();
    mm_jpeg_encoder_get_buffer_offset( dim->picture_width, dim->picture_height,&y_off, &cbcr_off, &frame_len,&num_planes, planes);
	if (mHalCamCtrl->initHeapMem (&mHalCamCtrl->mJpegMemory, 1, frame_len, 0, 0/*cbcr_off*/,
                                  MSM_PMEM_MAX, NULL, NULL, num_planes, planes) < 0) {
		ALOGE("%s: Error allocating JPEG memory", __func__);
		ret = NO_MEMORY;
		goto end;
	}

	if (mHalCamCtrl->initHeapMem(&mHalCamCtrl->mSnapshotMemory, num_of_buf,
	   frame_len, y_off, cbcr_off, MSM_PMEM_MAINIMG, &mSnapshotStreamBuf,
                                 &reg_buf.snapshot.main, num_planes, planes) < 0) {
				ret = NO_MEMORY;
				goto end;
	};
    mm_jpeg_encoder_get_buffer_offset( dim->ui_thumbnail_width, dim->ui_thumbnail_height,
			&y_off, &cbcr_off, &frame_len,
			&num_planes, planes);
    if (mHalCamCtrl->initHeapMem(&mHalCamCtrl->mThumbnailMemory, num_of_buf,
		    frame_len, y_off, cbcr_off, MSM_PMEM_THUMBNAIL, &mPostviewStreamBuf,
		    &reg_buf.snapshot.thumbnail, num_planes, planes) < 0) {
	    ret = NO_MEMORY;
	    goto end;
    }

    /* register the streaming buffers for the channel*/
    reg_buf.ch_type = MM_CAMERA_CH_SNAPSHOT;
    reg_buf.snapshot.main.num = mSnapshotStreamBuf.num;

    if (!isFullSizeLiveshot())
        reg_buf.snapshot.thumbnail.num = mPostviewStreamBuf.num;
    else
        reg_buf.snapshot.thumbnail.num = 0;

    if(!isLiveSnapshot()) {
        ret = cam_config_prepare_buf(mCameraId, &reg_buf);
        if(ret != NO_ERROR) {
            ALOGV("%s:reg snapshot buf err=%d\n", __func__, ret);
            ret = FAILED_TRANSACTION;
            goto end;
        }
    }

    /* If we have reached here successfully, we have allocated buffer.
       Set state machine.*/
    setSnapshotState(SNAPSHOT_STATE_BUF_INITIALIZED);
end:
    if (ret != NO_ERROR) {
        handleError();
    }
    if (reg_buf.snapshot.main.buf.mp)
      delete []reg_buf.snapshot.main.buf.mp;
    if (reg_buf.snapshot.thumbnail.buf.mp)
      delete []reg_buf.snapshot.thumbnail.buf.mp;
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::
deinitSnapshotBuffers(void)
{
    int ret = NO_ERROR;

    ALOGD("%s: E", __func__);

    /* Deinit only if we have already initialized*/
    if (getSnapshotState() >= SNAPSHOT_STATE_BUF_INITIALIZED ){
        if(!isLiveSnapshot()) {
            ALOGD("%s: Unpreparing Snapshot Buffer", __func__);
            ret = cam_config_unprepare_buf(mCameraId, MM_CAMERA_CH_SNAPSHOT);
            if(ret != NO_ERROR) {
                ALOGE("%s:unreg snapshot buf err=%d\n", __func__, ret);
                ret = FAILED_TRANSACTION;
                goto end;
            }
        }
        /* Clear main and thumbnail heap*/
        mHalCamCtrl->releaseHeapMem(&mHalCamCtrl->mSnapshotMemory);
        if (!isFullSizeLiveshot())
          mHalCamCtrl->releaseHeapMem(&mHalCamCtrl->mThumbnailMemory);
        mHalCamCtrl->releaseHeapMem(&mHalCamCtrl->mJpegMemory);
    }
end:
    ALOGD("%s: X", __func__);
    return ret;
}

void QCameraStream_Snapshot::deInitBuffer(void)
{
    mm_camera_channel_type_t ch_type;

    ALOGI("%s: E", __func__);

    if( getSnapshotState() == SNAPSHOT_STATE_UNINIT) {
        ALOGD("%s: Already deinit'd!", __func__);
        return;
    }

    if (mSnapshotFormat == PICTURE_FORMAT_RAW) {
        /* deinit buffer */
        deinitRawSnapshotBuffers();
    }
    else
    {
        deinitSnapshotBuffers();
    }


    /* deinit jpeg buffer if allocated */
    if(mJpegHeap != NULL) mJpegHeap.clear();
    mJpegHeap = NULL;

    /* memset some global structure */
    memset(&mSnapshotStreamBuf, 0, sizeof(mSnapshotStreamBuf));
    memset(&mPostviewStreamBuf, 0, sizeof(mPostviewStreamBuf));
    mSnapshotQueue.flush();
    mWDNQueue.flush();

    mNumOfSnapshot = 0;
    mNumOfRecievedJPEG = 0;
    setSnapshotState(SNAPSHOT_STATE_UNINIT);

    ALOGD("%s: X", __func__);
}

/*Temp: to be removed once event handling is enabled in mm-camera.
  We need an event - one event for
  stream-off to disable OPS_SNAPSHOT*/
void QCameraStream_Snapshot::runSnapshotThread(void *data)
{
    ALOGD("%s: E", __func__);

    if (mSnapshotFormat == PICTURE_FORMAT_RAW) {
       /* TBD: Temp: Needs to be removed once event handling is enabled.
          We cannot call mm-camera interface to stop snapshot from callback
          function as it causes deadlock. Hence handling it here temporarily
          in this thread. Later mm-camera intf will give us event in separate
          thread context */
        mm_app_snapshot_wait();
        /* Send command to stop snapshot polling thread*/
        stop();
    }
    ALOGD("%s: X", __func__);
}

/*Temp: to be removed once event handling is enabled in mm-camera*/
static void *snapshot_thread(void *obj)
{
    QCameraStream_Snapshot *pme = (QCameraStream_Snapshot *)obj;
    ALOGD("%s: E", __func__);
    if (pme != 0) {
        pme->runSnapshotThread(obj);
    }
    else ALOGW("not starting snapshot thread: the object went away!");
    ALOGD("%s: X", __func__);
    return NULL;
}

/*Temp: to be removed later*/
static pthread_t mSnapshotThread;

status_t QCameraStream_Snapshot::initJPEGSnapshot(int num_of_snapshots)
{
    status_t ret = NO_ERROR;
    cam_ctrl_dimension_t dim;
    mm_camera_op_mode_type_t op_mode;

    ALOGV("%s: E", __func__);

    if (isFullSizeLiveshot())
      goto end;

    ALOGD("%s: Get current dimension", __func__);
    /* Query mm_camera to get current dimension */
    memset(&dim, 0, sizeof(cam_ctrl_dimension_t));
    ret = cam_config_get_parm(mCameraId,
                              MM_CAMERA_PARM_DIMENSION, &dim);
    if (NO_ERROR != ret) {
        ALOGE("%s: error - can't get preview dimension!", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    /* Set camera op mode to MM_CAMERA_OP_MODE_CAPTURE */
    ALOGD("Setting OP_MODE_CAPTURE");
    op_mode = MM_CAMERA_OP_MODE_CAPTURE;
    if( NO_ERROR != cam_config_set_parm(mCameraId,
            MM_CAMERA_PARM_OP_MODE, &op_mode)) {
        ALOGE("%s: MM_CAMERA_OP_MODE_CAPTURE failed", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    /* config the parmeters and see if we need to re-init the stream*/
    ALOGD("%s: Configure Snapshot Dimension", __func__);
    ret = configSnapshotDimension(&dim);
    if (ret != NO_ERROR) {
        ALOGE("%s: Setting snapshot dimension failed", __func__);
        goto end;
    }

    /* Initialize stream - set format, acquire channel */
    ret = initSnapshotFormat(&dim);
    if (NO_ERROR != ret) {
        ALOGE("%s: error - can't init nonZSL stream!", __func__);
        goto end;
    }

    ret = initSnapshotBuffers(&dim, num_of_snapshots);
    if ( NO_ERROR != ret ){
        ALOGE("%s: Failure allocating memory for Snapshot buffers", __func__);
        goto end;
    }

end:
    /* Based on what state we are in, we'll need to handle error -
       like deallocating memory if we have already allocated */
    if (ret != NO_ERROR) {
        handleError();
    }
    ALOGV("%s: X", __func__);
    return ret;

}

status_t QCameraStream_Snapshot::initRawSnapshot(int num_of_snapshots)
{
    status_t ret = NO_ERROR;
    cam_ctrl_dimension_t dim;
    bool initSnapshot = false;
    mm_camera_op_mode_type_t op_mode;

    ALOGV("%s: E", __func__);

    /* Set camera op mode to MM_CAMERA_OP_MODE_CAPTURE */
    ALOGD("%s: Setting OP_MODE_CAPTURE", __func__);
    op_mode = MM_CAMERA_OP_MODE_CAPTURE;
    if( NO_ERROR != cam_config_set_parm(mCameraId,
            MM_CAMERA_PARM_OP_MODE, &op_mode)) {
        ALOGE("%s: MM_CAMERA_OP_MODE_CAPTURE failed", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    /* For raw snapshot, we do not know the dimension as it
       depends on sensor to sensor. We call getDimension which will
       give us raw width and height */
    memset(&dim, 0, sizeof(cam_ctrl_dimension_t));
    ret = cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION, &dim);
    if (MM_CAMERA_OK != ret) {
      ALOGE("%s: error - can't get dimension!", __func__);
      ALOGE("%s: X", __func__);
      goto end;
    }
    ALOGD("%s: Raw Snapshot dimension: %dx%d", __func__,
         dim.raw_picture_width,
         dim.raw_picture_height);


    ret = initRawSnapshotChannel(&dim, num_of_snapshots);
    if (NO_ERROR != ret) {
        ALOGE("%s: error - can't init nonZSL stream!", __func__);
        goto end;
    }

    ret = initRawSnapshotBuffers(&dim, num_of_snapshots);
    if ( NO_ERROR != ret ){
        ALOGE("%s: Failure allocating memory for Raw Snapshot buffers",
             __func__);
        goto end;
    }
    setSnapshotState(SNAPSHOT_STATE_INITIALIZED);

end:
    if (ret != NO_ERROR) {
        handleError();
    }
    ALOGV("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::initFullLiveshot(void)
{
    status_t ret = NO_ERROR;
    cam_ctrl_dimension_t dim;
    bool matching = true;

    memset(&dim, 0, sizeof(cam_ctrl_dimension_t));
    ret = cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION, &dim);
    if (MM_CAMERA_OK != ret) {
      ALOGE("%s: error - can't get dimension!", __func__);
      return ret;
    }
#if 1
    /* First check if the picture resolution is the same, if not, change it*/
    mHalCamCtrl->getPictureSize(&mPictureWidth, &mPictureHeight);
    ALOGD("%s: Picture size received: %d x %d", __func__,
         mPictureWidth, mPictureHeight);

    mThumbnailWidth = dim.display_width;
    mThumbnailHeight = dim.display_height;
    matching = (mPictureWidth == dim.picture_width) &&
        (mPictureHeight == dim.picture_height);


    if (!matching) {
        dim.picture_width  = mPictureWidth;
        dim.picture_height = mPictureHeight;
        dim.ui_thumbnail_height = mThumbnailHeight;
        dim.ui_thumbnail_width = mThumbnailWidth;
    }
    ALOGD("%s: Picture size to set: %d x %d", __func__,
         dim.picture_width, dim.picture_height);
    ret = cam_config_set_parm(mCameraId, MM_CAMERA_PARM_DIMENSION,&dim);
#endif
    /* Initialize stream - set format, acquire channel */
    ret = initSnapshotFormat(&dim);
    if (NO_ERROR != ret) {
        ALOGE("%s: error - can't init nonZSL stream!", __func__);
        return ret;
    }
    ret = initSnapshotBuffers(&dim, 1);
    if ( NO_ERROR != ret ){
        ALOGE("%s: Failure allocating memory for Snapshot buffers", __func__);
        return ret;
    }

    return ret;
}

status_t QCameraStream_Snapshot::initZSLSnapshot(void)
{
    status_t ret = NO_ERROR;
    cam_ctrl_dimension_t dim;
    mm_camera_op_mode_type_t op_mode;

    ALOGV("%s: E", __func__);

    ALOGD("%s: Get current dimension", __func__);
    /* Query mm_camera to get current dimension */
    memset(&dim, 0, sizeof(cam_ctrl_dimension_t));
    ret = cam_config_get_parm(mCameraId,
                              MM_CAMERA_PARM_DIMENSION, &dim);
    if (NO_ERROR != ret) {
        ALOGE("%s: error - can't get preview dimension!", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    /* config the parmeters and see if we need to re-init the stream*/
    ALOGD("%s: Configure Snapshot Dimension", __func__);
    ret = configSnapshotDimension(&dim);
    if (ret != NO_ERROR) {
        ALOGE("%s: Setting snapshot dimension failed", __func__);
        goto end;
    }

    /* Initialize stream - set format, acquire channel */
    ret = initSnapshotFormat(&dim);
    if (NO_ERROR != ret) {
        ALOGE("%s: error - can't init nonZSL stream!", __func__);
        goto end;
    }

    /* For ZSL we'll have to allocate buffers for internal queue
       maintained by mm-camera lib plus around 3 buffers used for
       data handling by lower layer.*/

    ret = initSnapshotBuffers(&dim, mHalCamCtrl->getZSLQueueDepth() + 3);
    if ( NO_ERROR != ret ){
        ALOGE("%s: Failure allocating memory for Snapshot buffers", __func__);
        goto end;
    }

end:
    /* Based on what state we are in, we'll need to handle error -
       like deallocating memory if we have already allocated */
    if (ret != NO_ERROR) {
        handleError();
    }
    ALOGV("%s: X", __func__);
    return ret;

}

status_t QCameraStream_Snapshot::
takePictureJPEG(void)
{
    status_t ret = NO_ERROR;

    ALOGD("%s: E", __func__);

    /* Take snapshot */
    ALOGD("%s: Call MM_CAMERA_OPS_SNAPSHOT", __func__);
    if (NO_ERROR != cam_ops_action(mCameraId,
                                              TRUE,
                                              MM_CAMERA_OPS_SNAPSHOT,
                                              this)) {
           ALOGE("%s: Failure taking snapshot", __func__);
           ret = FAILED_TRANSACTION;
           goto end;
    }

    /* TBD: Temp: to be removed once event callback
       is implemented in mm-camera lib  */
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_create(&mSnapshotThread,&attr,
                   snapshot_thread, (void *)this);

end:
    if (ret != NO_ERROR) {
        handleError();
    }

    ALOGD("%s: X", __func__);
    return ret;

}

status_t QCameraStream_Snapshot::
takePictureRaw(void)
{
    status_t ret = NO_ERROR;

    ALOGD("%s: E", __func__);

    /* Take snapshot */
    ALOGD("%s: Call MM_CAMERA_OPS_SNAPSHOT", __func__);
    if (NO_ERROR != cam_ops_action(mCameraId,
                                  TRUE,
                                  MM_CAMERA_OPS_RAW,
                                  this)) {
           ALOGE("%s: Failure taking snapshot", __func__);
           ret = FAILED_TRANSACTION;
           goto end;
    }

    /* TBD: Temp: to be removed once event callback
       is implemented in mm-camera lib  */
    /* Wait for snapshot frame callback to return*/
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_create(&mSnapshotThread,&attr,
                   snapshot_thread, (void *)this);

end:
    if (ret != NO_ERROR) {
        handleError();
    }
    ALOGD("%s: X", __func__);
    return ret;

}

/* This is called from vide stream object */
status_t QCameraStream_Snapshot::
takePictureLiveshot(mm_camera_ch_data_buf_t* recvd_frame,
                    cam_ctrl_dimension_t *dim,
                    int frame_len)
{
    status_t ret = NO_ERROR;
    common_crop_t crop_info;
    uint32_t aspect_ratio;

    ALOGI("%s: E", __func__);

    /* set flag to indicate we are doing livesnapshot */
    setModeLiveSnapshot(true);

    ALOGI("%s:Passed picture size: %d X %d", __func__,
         dim->picture_width, dim->picture_height);
    ALOGI("%s:Passed thumbnail size: %d X %d", __func__,
         dim->ui_thumbnail_width, dim->ui_thumbnail_height);

    mPictureWidth = dim->picture_width;
    mPictureHeight = dim->picture_height;
    mThumbnailWidth = dim->ui_thumbnail_width;
    mThumbnailHeight = dim->ui_thumbnail_height;
    mPictureFormat = dim->main_img_format;
    mThumbnailFormat = dim->thumb_format;

    memset(&crop_info, 0, sizeof(common_crop_t));
    crop_info.in1_w = mPictureWidth;
    crop_info.in1_h = mPictureHeight;
    /* For low power live snapshot the thumbnail output size is set to default size.
       In case of live snapshot video buffer = thumbnail buffer. For higher resolutions
       the thumnail will be dropped if its more than 64KB. To avoid thumbnail drop
       set thumbnail as configured by application. This will be a size lower than video size*/
    mDropThumbnail = false;
    if(mThumbnailWidth == 0 &&  mThumbnailHeight == 0) {
        ALOGE("Live Snapshot thumbnail will be dropped as indicated by application");
        mDropThumbnail = true;
    }
    crop_info.out1_w = mThumbnailWidth;
    crop_info.out1_h = mThumbnailHeight;
    ret = encodeData(recvd_frame, &crop_info, frame_len, 0);
    if (ret != NO_ERROR) {
        ALOGE("%s: Failure configuring JPEG encoder", __func__);

        /* Failure encoding this frame. Just notify upper layer
           about it.*/
        if(mHalCamCtrl->mDataCb &&
            (mHalCamCtrl->mMsgEnabled & MEDIA_RECORDER_MSG_COMPRESSED_IMAGE)) {
            /* get picture failed. Give jpeg callback with NULL data
             * to the application to restore to preview mode
             */
        }
        setModeLiveSnapshot(false);
        goto end;
    }

end:
    ALOGI("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::
takePictureZSL(void)
{
    status_t ret = NO_ERROR;
    mm_camera_ops_parm_get_buffered_frame_t param;

    ALOGE("%s: E", __func__);

    memset(&param, 0, sizeof(param));
    param.ch_type = MM_CAMERA_CH_SNAPSHOT;

    /* Take snapshot */
    ALOGE("%s: Call MM_CAMERA_OPS_GET_BUFFERED_FRAME", __func__);
    if (NO_ERROR != cam_ops_action(mCameraId,
                                          TRUE,
                                          MM_CAMERA_OPS_GET_BUFFERED_FRAME,
                                          &param)) {
           ALOGE("%s: Failure getting zsl frame(s)", __func__);
           ret = FAILED_TRANSACTION;
           goto end;
    }

    /* TBD: Temp: to be removed once event callback
       is implemented in mm-camera lib  */
/*    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_create(&mSnapshotThread,&attr,
                   snapshot_thread, (void *)this);
*/
end:
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::
startStreamZSL(void)
{
    status_t ret = NO_ERROR;

    ALOGD("%s: E", __func__);

    /* Start ZSL - it'll start queuing the frames */
    ALOGD("%s: Call MM_CAMERA_OPS_ZSL", __func__);
    if (NO_ERROR != cam_ops_action(mCameraId,
                                          TRUE,
                                          MM_CAMERA_OPS_ZSL,
                                          this)) {
           ALOGE("%s: Failure starting ZSL stream", __func__);
           ret = FAILED_TRANSACTION;
           goto end;
    }

end:
    ALOGD("%s: X", __func__);
    return ret;

}

status_t  QCameraStream_Snapshot::
encodeData(mm_camera_ch_data_buf_t* recvd_frame,
           common_crop_t *crop_info,
           int frame_len,
           bool enqueued)
{
    status_t ret = NO_ERROR;
    cam_ctrl_dimension_t dimension;
    struct msm_frame *postviewframe;
    struct msm_frame *mainframe;
    common_crop_t crop;
    cam_point_t main_crop_offset;
    cam_point_t thumb_crop_offset;
    int width, height;
    uint8_t *thumbnail_buf;
    uint32_t thumbnail_fd;

//    omx_jpeg_encode_params encode_params;

    /* If it's the only frame, we directly pass to encoder.
       If not, we'll queue it and check during next jpeg .
       Also, if the queue isn't empty then we need to queue this
       one too till its turn comes (only if it's not already
       queued up there)*/
    if((getSnapshotState() == SNAPSHOT_STATE_JPEG_ENCODING) ||
       (!mSnapshotQueue.isEmpty() && !enqueued)){ /*busy and new buffer*/
        /* encoding is going on. Just queue the frame for now.*/
        ALOGE("%s: JPEG encoding in progress."
             "Enqueuing frame id(%d) for later processing.", __func__,
             recvd_frame->snapshot.main.idx);
        mSnapshotQueue.enqueue((void *)recvd_frame);
    } else {  /*not busy and new buffer (first job)*/
        postviewframe = recvd_frame->snapshot.thumbnail.frame;
        /* No thumbnail for full size liveshot */
        if (!isFullSizeLiveshot())
            postviewframe = recvd_frame->snapshot.thumbnail.frame;
        mainframe = recvd_frame->snapshot.main.frame;
        cam_config_get_parm(mHalCamCtrl->mCameraId, MM_CAMERA_PARM_DIMENSION, &dimension);
        ALOGV("%s: main_fmt =%d, tb_fmt =%d", __func__, dimension.main_img_format, dimension.thumb_format);

        dimension.orig_picture_dx = mPictureWidth;
        dimension.orig_picture_dy = mPictureHeight;

        if(!mDropThumbnail) {
            if(isZSLMode()) {
                ALOGI("Setting input thumbnail size to previewWidth= %d   previewheight= %d in ZSL mode",
                     mHalCamCtrl->previewWidth, mHalCamCtrl->previewHeight);
                dimension.thumbnail_width = width = mHalCamCtrl->previewWidth;
                dimension.thumbnail_height = height = mHalCamCtrl->previewHeight;
            } else {
                dimension.thumbnail_width = width = mThumbnailWidth;
                dimension.thumbnail_height = height = mThumbnailHeight;
            }
         } else {
            dimension.thumbnail_width = width = 0;
            dimension.thumbnail_height = height = 0;
        }

        dimension.main_img_format = mPictureFormat;
        dimension.thumb_format = mThumbnailFormat;

        /*TBD: Move JPEG handling to the mm-camera library */
        ALOGD("Setting callbacks, initializing encoder and start encoding.");
        ALOGD(" Passing my obj: %x", (unsigned int) this);
        set_callbacks(snapshot_jpeg_fragment_cb, snapshot_jpeg_cb, this) ; //, mHalCamCtrl->mJpegMemory.camera_memory[0]->data, &mJpegOffset);
        mm_jpeg_encoder_init();
        mm_jpeg_encoder_setMainImageQuality(mHalCamCtrl->getJpegQuality());

        ALOGD("%s: Dimension to encode: main: %dx%d thumbnail: %dx%d", __func__,
             dimension.orig_picture_dx, dimension.orig_picture_dy,
             dimension.thumbnail_width, dimension.thumbnail_height);

        /*TBD: Pass 0 as cropinfo for now as v4l2 doesn't provide
          cropinfo. It'll be changed later.*/
        memset(&crop,0,sizeof(common_crop_t));
        memset(&main_crop_offset,0,sizeof(cam_point_t));
        memset(&thumb_crop_offset,0,sizeof(cam_point_t));

        /* Setting crop info */

        /*Main image*/
        crop.in2_w=mCrop.snapshot.main_crop.width;// dimension.picture_width
        crop.in2_h=mCrop.snapshot.main_crop.height;// dimension.picture_height;
        if (!mJpegDownscaling) {
            crop.out2_w = mPictureWidth;
            crop.out2_h = mPictureHeight;
        } else {
            crop.out2_w = mActualPictureWidth;
            crop.out2_h = mActualPictureHeight;
            if (!crop.in2_w || !crop.in2_h) {
                crop.in2_w = mPictureWidth;
                crop.in2_h = mPictureHeight;
            }
        }
        main_crop_offset.x=mCrop.snapshot.main_crop.left;
        main_crop_offset.y=mCrop.snapshot.main_crop.top;
        /*Thumbnail image*/
        if (!isFullSizeLiveshot()) {
          crop.in1_w=mCrop.snapshot.thumbnail_crop.width; //dimension.thumbnail_width;
          crop.in1_h=mCrop.snapshot.thumbnail_crop.height; // dimension.thumbnail_height;
          if((width != 0) && (height != 0)) {
              crop.out1_w=mThumbnailWidth;
              crop.out1_h=mThumbnailHeight;
          } else {
              crop.out1_w = 0;
              crop.out1_h = 0;
          }
          thumb_crop_offset.x=mCrop.snapshot.thumbnail_crop.left;
          thumb_crop_offset.y=mCrop.snapshot.thumbnail_crop.top;
//          ALOGD("mm_jpeg_encoder_encode: %x %x %x, %x %x %", postviewframe->buffer,postviewframe->fd,postviewframe->phy_offset,mainframe->buffer,mainframe->fd,mainframe->phy_offset);
          mHalCamCtrl->setExifTags();
          mHalCamCtrl->initExifData();
          if (!mm_jpeg_encoder_encode((const cam_ctrl_dimension_t *)&dimension,
                          (uint8_t *)postviewframe->buffer,
                          postviewframe->fd,
                          postviewframe->phy_offset,
                          (uint8_t *)mainframe->buffer,
                          mainframe->fd,
                          mainframe->phy_offset,
                          &crop,
                          mHalCamCtrl->getExifData(),
                          mHalCamCtrl->getExifTableNumEntries(),
                          -1,
                          &main_crop_offset,
                          &thumb_crop_offset)){
              ALOGE("%s: Failure! JPEG encoder returned error.", __func__);
              ret = FAILED_TRANSACTION;
              goto end;
            }
        }

        /* Save the pointer to the frame sent for encoding. we'll need it to
           tell kernel that we are done with the frame.*/
        mCurrentFrameEncoded = recvd_frame;
        setSnapshotState(SNAPSHOT_STATE_JPEG_ENCODING);
    }

end:
    ALOGD("%s: X", __func__);
    return ret;
}

/* Called twice - 1st to play shutter sound and 2nd to configure
   overlay/surfaceflinger for postview */
void QCameraStream_Snapshot::notifyShutter(common_crop_t *crop,
                                           bool mPlayShutterSoundOnly)
{
    //image_rect_type size;
    int32_t ext1 = 0, ext2 = 0;
    int32_t shutterMsg;

    camera_notify_callback         notifyCb;
    ALOGD("%s: E", __func__);

    mStopCallbackLock.lock();
    ext2 = mPlayShutterSoundOnly;
    ALOGD("%s: Calling callback to play shutter sound", __func__);
    notifyCb =  mHalCamCtrl->mNotifyCb;
    shutterMsg = mHalCamCtrl->mMsgEnabled & CAMERA_MSG_SHUTTER;
    mStopCallbackLock.unlock();

    if( notifyCb ) {
      notifyCb(CAMERA_MSG_SHUTTER, ext1, ext2,
                                 mHalCamCtrl->mCallbackCookie);
      if(mPlayShutterSoundOnly) {
        /* At this point, invoke Notify Callback to play shutter sound only.
         * We want to call notify callback again when we have the
         * yuv picture ready. This is to reduce blanking at the time
         * of displaying postview frame. Using ext2 to indicate whether
         * to play shutter sound only or register the postview buffers.
         */
         ext2 = mPlayShutterSoundOnly;
        ALOGD("%s: Calling callback to play shutter sound", __func__);
        notifyCb(CAMERA_MSG_SHUTTER, ext1, ext2,
                   mHalCamCtrl->mCallbackCookie);
        return;
    }


    if (shutterMsg ) {
        mDisplayHeap = mPostviewHeap;
        if (crop != NULL && (crop->in1_w != 0 && crop->in1_h != 0)) {
            ALOGD("%s: Size from cropinfo: %dX%d", __func__,
                 crop->in1_w, crop->in1_h);
        }
        else {
            ALOGD("%s: Size from global: %dX%d", __func__,
                 mPostviewWidth, mPostviewHeight);
        }
        /*if(strTexturesOn == true) {
            size.width = mPictureWidth;
            size.height = mPictureHeight;
        }*/
        /* Now, invoke Notify Callback to unregister preview buffer
         * and register postview buffer with surface flinger. Set ext2
         * as 0 to indicate not to play shutter sound.
         */

        notifyCb(CAMERA_MSG_SHUTTER, ext1, ext2,
                                     mHalCamCtrl->mCallbackCookie);
      }
    }
    ALOGD("%s: X", __func__);
}

status_t  QCameraStream_Snapshot::
encodeDisplayAndSave(mm_camera_ch_data_buf_t* recvd_frame,
                     bool enqueued)
{
    status_t ret = NO_ERROR;
    struct msm_frame *postview_frame;
    int buf_index = 0;
    ssize_t offset_addr = 0;
    common_crop_t dummy_crop;
    /* send frame for encoding */
    ALOGE("%s: Send frame for encoding", __func__);
    /*TBD: Pass 0 as cropinfo for now as v4l2 doesn't provide
      cropinfo. It'll be changed later.*/
    if(!mActive) {
        ALOGE("Cancel Picture.. Stop is called");
        return NO_ERROR;
    }
    if(isZSLMode()){
      ALOGE("%s: set JPEG rotation in ZSL mode", __func__);
      mHalCamCtrl->setJpegRotation(isZSLMode());
    }

    memset(&dummy_crop,0,sizeof(common_crop_t));
    mPictureFormat=CAMERA_YUV_420_NV12;
    mThumbnailFormat=CAMERA_YUV_420_NV12;
    ret = encodeData(recvd_frame, &dummy_crop, mSnapshotStreamBuf.frame_len,
                     enqueued);
    if (ret != NO_ERROR) {
        ALOGE("%s: Failure configuring JPEG encoder", __func__);

        goto end;
    }

    /* Display postview image*/
    /* If it's burst mode, we won't be displaying postview of all the captured
       images - only the first one */
    ALOGD("%s: Burst mode flag  %d", __func__, mBurstModeFlag);

end:
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::receiveRawPicture(mm_camera_ch_data_buf_t* recvd_frame)
{
    int buf_index = 0;
    common_crop_t crop;
    int rc = NO_ERROR;

    camera_notify_callback         notifyCb;
    camera_data_callback           dataCb, jpgDataCb;

    ALOGD("%s: E ", __func__);
    mStopCallbackLock.lock( );
    if(!mActive) {
        mStopCallbackLock.unlock();
        ALOGD("%s: Stop receiving raw pic ", __func__);
        return NO_ERROR;
    }

    mHalCamCtrl->dumpFrameToFile(recvd_frame->snapshot.main.frame, HAL_DUMP_FRM_MAIN);
    if (!isFullSizeLiveshot())
        mHalCamCtrl->dumpFrameToFile(recvd_frame->snapshot.thumbnail.frame,
                                     HAL_DUMP_FRM_THUMBNAIL);

    /* If it's raw snapshot, we just want to tell upperlayer to save the image*/
    if(mSnapshotFormat == PICTURE_FORMAT_RAW) {
        ALOGD("%s: Call notifyShutter 2nd time in case of RAW", __func__);
        mStopCallbackLock.unlock();
        if(!mHalCamCtrl->mShutterSoundPlayed) {
            notifyShutter(&crop, TRUE);
        }
        notifyShutter(&crop, FALSE);
        mHalCamCtrl->mShutterSoundPlayed = FALSE;

        mStopCallbackLock.lock( );
        ALOGD("%s: Sending Raw Snapshot Callback to Upperlayer", __func__);
        buf_index = recvd_frame->def.idx;

        if (mHalCamCtrl->mDataCb && mActive &&
            (mHalCamCtrl->mMsgEnabled & CAMERA_MSG_COMPRESSED_IMAGE)){
          dataCb = mHalCamCtrl->mDataCb;
        } else {
          dataCb = NULL;
        }
        mStopCallbackLock.unlock();

        if(dataCb) {
            dataCb(
                CAMERA_MSG_COMPRESSED_IMAGE,
                mHalCamCtrl->mRawMemory.camera_memory[buf_index], 0, NULL,
                mHalCamCtrl->mCallbackCookie);
        }
        /* TBD: Temp: To be removed once event handling is enabled */
        mm_app_snapshot_done();
    } else {
        /*TBD: v4l2 doesn't have support to provide cropinfo along with
          frame. We'll need to query.*/
        memset(&crop, 0, sizeof(common_crop_t));

        /*maftab*/
        #if 0
        crop.in1_w=mCrop.snapshot.thumbnail_crop.width;
        crop.in1_h=mCrop.snapshot.thumbnail_crop.height;
        crop.out1_w=mThumbnailWidth;
        crop.out1_h=mThumbnailHeight;
        #endif

        ALOGD("%s: Call notifyShutter 2nd time", __func__);
        mStopCallbackLock.unlock();
        if(!mHalCamCtrl->mShutterSoundPlayed) {
            notifyShutter(&crop, TRUE);
        }
        notifyShutter(&crop, FALSE);
        mHalCamCtrl->mShutterSoundPlayed = FALSE;

        /* The recvd_frame structre we receive from lower library is a local
           variable. So we'll need to save this structure so that we won't
           be later pointing to garbage data when that variable goes out of
           scope */
        mm_camera_ch_data_buf_t* frame =
            (mm_camera_ch_data_buf_t *)malloc(sizeof(mm_camera_ch_data_buf_t));
        if (frame == NULL) {
            ALOGE("%s: Error allocating memory to save received_frame structure.", __func__);
            cam_evt_buf_done(mCameraId, recvd_frame);
            return BAD_VALUE;
        }
        memcpy(frame, recvd_frame, sizeof(mm_camera_ch_data_buf_t));

        mStopCallbackLock.lock();

        // only in ZSL mode and Wavelet Denoise is enabled, we will send frame to deamon to do WDN
        if (isZSLMode() && mHalCamCtrl->isWDenoiseEnabled()) {
            if(mIsDoingWDN){
                mWDNQueue.enqueue((void *)frame);
                ALOGD("%s: Wavelet denoise is going on, queue frame", __func__);
                rc = NO_ERROR;
            } else {
                ALOGD("%s: Start Wavelet denoise", __func__);
                mIsDoingWDN = TRUE; // set the falg to TRUE because we are going to do WDN

                // No WDN is going on so far, we will start it here
                rc = doWaveletDenoise(frame);
                if ( NO_ERROR != rc ) {
                    ALOGE("%s: Error while doing wavelet denoise", __func__);
                    mIsDoingWDN = FALSE;
                }
            }
        }
        else {
            rc = encodeDisplayAndSave(frame, 0);
        }

        // send upperlayer callback for raw image (data or notify, not both)
        if((mHalCamCtrl->mDataCb) && (mHalCamCtrl->mMsgEnabled & CAMERA_MSG_RAW_IMAGE)){
          dataCb = mHalCamCtrl->mDataCb;
        } else {
          dataCb = NULL;
        }
        if((mHalCamCtrl->mNotifyCb) && (mHalCamCtrl->mMsgEnabled & CAMERA_MSG_RAW_IMAGE_NOTIFY)){
          notifyCb = mHalCamCtrl->mNotifyCb;
        } else {
          notifyCb = NULL;
        }

        if (rc != NO_ERROR)
        {
            ALOGE("%s: Error while encoding/displaying/saving image", __func__);
            cam_evt_buf_done(mCameraId, recvd_frame);

            if(mHalCamCtrl->mDataCb &&
                (mHalCamCtrl->mMsgEnabled & CAMERA_MSG_COMPRESSED_IMAGE)) {
                /* get picture failed. Give jpeg callback with NULL data
                 * to the application to restore to preview mode
                 */
                jpgDataCb = mHalCamCtrl->mDataCb;
            } else {
              jpgDataCb = NULL;
           	}
            ALOGE("%s: encode err so data cb", __func__);
            mStopCallbackLock.unlock();
            if (dataCb) {
              dataCb(CAMERA_MSG_RAW_IMAGE, mHalCamCtrl->mSnapshotMemory.camera_memory[0],
                                   1, NULL, mHalCamCtrl->mCallbackCookie);
            }
            if (notifyCb) {
              notifyCb(CAMERA_MSG_RAW_IMAGE_NOTIFY, 0, 0, mHalCamCtrl->mCallbackCookie);
            }
            if (jpgDataCb) {
              jpgDataCb(CAMERA_MSG_COMPRESSED_IMAGE,
                                       NULL, 0, NULL,
                                       mHalCamCtrl->mCallbackCookie);
            }

            if (frame != NULL) {
                free(frame);
            }
        } else {

          mStopCallbackLock.unlock();
          if (dataCb) {
            dataCb(CAMERA_MSG_RAW_IMAGE, mHalCamCtrl->mSnapshotMemory.camera_memory[0],
                                 1, NULL, mHalCamCtrl->mCallbackCookie);
          }
          if (notifyCb) {
            notifyCb(CAMERA_MSG_RAW_IMAGE_NOTIFY, 0, 0, mHalCamCtrl->mCallbackCookie);
          }
        }
    }

    ALOGD("%s: X", __func__);
    return NO_ERROR;
}

//-------------------------------------------------------------------
// Helper Functions
//-------------------------------------------------------------------
void QCameraStream_Snapshot::handleError()
{
    mm_camera_channel_type_t ch_type;
    ALOGD("%s: E", __func__);

    /* Depending upon the state we'll have to
       handle error */
    switch(getSnapshotState()) {
    case SNAPSHOT_STATE_JPEG_ENCODING:
        if(mJpegHeap != NULL) mJpegHeap.clear();
        mJpegHeap = NULL;

    case SNAPSHOT_STATE_YUV_RECVD:
    case SNAPSHOT_STATE_IMAGE_CAPTURE_STRTD:
        stopPolling();
    case SNAPSHOT_STATE_INITIALIZED:
    case SNAPSHOT_STATE_BUF_INITIALIZED:
        if (mSnapshotFormat == PICTURE_FORMAT_JPEG) {
            deinitSnapshotBuffers();
        }else
        {
            deinitRawSnapshotBuffers();
        }
    case SNAPSHOT_STATE_BUF_NOTIF_REGD:
    case SNAPSHOT_STATE_CH_ACQUIRED:
        if (mSnapshotFormat == PICTURE_FORMAT_JPEG) {
            deinitSnapshotChannel(MM_CAMERA_CH_SNAPSHOT);
        }else
        {
            deinitSnapshotChannel(MM_CAMERA_CH_RAW);
        }
    default:
        /* Set the state to ERROR */
        setSnapshotState(SNAPSHOT_STATE_ERROR);
        break;
    }

    ALOGD("%s: X", __func__);
}

void QCameraStream_Snapshot::setSnapshotState(int state)
{
    ALOGD("%s: Setting snapshot state to: %d",
         __func__, state);
    mSnapshotState = state;
}

int QCameraStream_Snapshot::getSnapshotState()
{
    return mSnapshotState;
}

void QCameraStream_Snapshot::setModeLiveSnapshot(bool value)
{
    mModeLiveSnapshot = value;
}

bool QCameraStream_Snapshot::isLiveSnapshot(void)
{
    return mModeLiveSnapshot;
}
bool QCameraStream_Snapshot::isZSLMode()
{
    return (myMode & CAMERA_ZSL_MODE);
}

void QCameraStream_Snapshot::setFullSizeLiveshot(bool value)
{
    mFullLiveshot = value;
}

bool QCameraStream_Snapshot::isFullSizeLiveshot()
{
    return mFullLiveshot;
}

//------------------------------------------------------------------
// Constructor and Destructor
//------------------------------------------------------------------
QCameraStream_Snapshot::
QCameraStream_Snapshot(int cameraId, camera_mode_t mode)
  : QCameraStream(cameraId,mode),
    mSnapshotFormat(PICTURE_FORMAT_JPEG),
    mPictureWidth(0), mPictureHeight(0),
    mPictureFormat(CAMERA_YUV_420_NV21),
    mPostviewWidth(0), mPostviewHeight(0),
    mThumbnailWidth(0), mThumbnailHeight(0),
    mThumbnailFormat(CAMERA_YUV_420_NV21),
    mJpegOffset(0),
    mSnapshotState(SNAPSHOT_STATE_UNINIT),
    mNumOfSnapshot(0),
    mModeLiveSnapshot(false),
    mBurstModeFlag(false),
    mActualPictureWidth(0),
    mActualPictureHeight(0),
    mJpegDownscaling(false),
    mJpegHeap(NULL),
    mDisplayHeap(NULL),
    mPostviewHeap(NULL),
    mCurrentFrameEncoded(NULL),
    mJpegSessionId(0),
    mFullLiveshot(false),
    mDropThumbnail(false)
  {
    ALOGV("%s: E", __func__);

    /*initialize snapshot queue*/
    mSnapshotQueue.init();

    /*initialize WDN queue*/
    mWDNQueue.init();
    mIsDoingWDN = FALSE;

    memset(&mSnapshotStreamBuf, 0, sizeof(mSnapshotStreamBuf));
    memset(&mPostviewStreamBuf, 0, sizeof(mPostviewStreamBuf));
    mSnapshotBufferNum = 0;
    mMainSize = 0;
    mThumbSize = 0;
    for(int i = 0; i < mMaxSnapshotBufferCount; i++) {
        mMainfd[i] = 0;
        mThumbfd[i] = 0;
        mCameraMemoryPtrMain[i] = NULL;
        mCameraMemoryPtrThumb[i] = NULL;
    }
    /*load the jpeg lib*/
    ALOGV("%s: X", __func__);
  }


QCameraStream_Snapshot::~QCameraStream_Snapshot() {
    ALOGV("%s: E", __func__);

    /* deinit snapshot queue */
    if (mSnapshotQueue.isInitialized()) {
        mSnapshotQueue.deinit();
    }
    ALOGV("%s: X", __func__);

}

//------------------------------------------------------------------
// Public Members
//------------------------------------------------------------------
status_t QCameraStream_Snapshot::init()
{
    status_t ret = NO_ERROR;
    mm_camera_op_mode_type_t op_mode;

    ALOGV("%s: E", __func__);

    /* Check the state. If we have already started snapshot
       process just return*/
    if (getSnapshotState() != SNAPSHOT_STATE_UNINIT) {
        ret = isZSLMode() ? NO_ERROR : INVALID_OPERATION;
        ALOGE("%s: Trying to take picture while snapshot is in progress",
             __func__);
        goto end;
    }


    mNumOfRecievedJPEG = 0;
    mInit = true;

end:
    /*if (ret == NO_ERROR) {
        setSnapshotState(SNAPSHOT_STATE_INITIALIZED);
    }*/
    ALOGV("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::start(void) {
    status_t ret = NO_ERROR;

    ALOGV("%s: E", __func__);

    Mutex::Autolock lock(mStopCallbackLock);

    /* Keep track of number of snapshots to take - in case of
       multiple snapshot/burst mode */
    mNumOfSnapshot = mHalCamCtrl->getNumOfSnapshots();
    if (mNumOfSnapshot == 0) {
        /* If by chance returned value is 0, we'll just take one snapshot */
        mNumOfSnapshot = 1;
    }
    ALOGD("%s: Number of images to be captured: %d", __func__, mNumOfSnapshot);

    if(mHalCamCtrl->isRawSnapshot()) {
        ALOGD("%s: Acquire Raw Snapshot Channel", __func__);
        ret = cam_ops_ch_acquire(mCameraId, MM_CAMERA_CH_RAW);
        if (NO_ERROR != ret) {
            ALOGE("%s: Failure Acquiring Raw Snapshot Channel error =%d\n",
                 __func__, ret);
            ret = FAILED_TRANSACTION;
            goto end;
        }
        /* Snapshot channel is acquired */
        setSnapshotState(SNAPSHOT_STATE_CH_ACQUIRED);
        ALOGD("%s: Register buffer notification. My object: %x",
             __func__, (unsigned int) this);
        (void) cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_RAW,
                                        snapshot_notify_cb,
                                        MM_CAMERA_REG_BUF_CB_INFINITE,
                                        0,
                                        this);
        /* Set the state to buffer notification completed */
        setSnapshotState(SNAPSHOT_STATE_BUF_NOTIF_REGD);
    }else{
        ALOGD("%s: Acquire Snapshot Channel", __func__);
        ret = cam_ops_ch_acquire(mCameraId, MM_CAMERA_CH_SNAPSHOT);
        if (NO_ERROR != ret) {
            ALOGE("%s: Failure Acquiring Snapshot Channel error =%d\n", __func__, ret);
            ret = FAILED_TRANSACTION;
            goto end;
        }
        /* Snapshot channel is acquired */
        setSnapshotState(SNAPSHOT_STATE_CH_ACQUIRED);
        ALOGD("%s: Register buffer notification. My object: %x",
             __func__, (unsigned int) this);
        (void) cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_SNAPSHOT,
                                        snapshot_notify_cb,
                                        MM_CAMERA_REG_BUF_CB_INFINITE,
                                        0,
                                        this);
        /* Set the state to buffer notification completed */
        setSnapshotState(SNAPSHOT_STATE_BUF_NOTIF_REGD);
    }

    if (isZSLMode()) {
        prepareHardware();
        ret = initZSLSnapshot();
        if(ret != NO_ERROR) {
            ALOGE("%s : Error while Initializing ZSL snapshot",__func__);
            goto end;
        }
        /* In case of ZSL, start will only start snapshot stream and
           continuously queue the frames in a queue. When user clicks
           shutter we'll call get buffer from the queue and pass it on */
        ret = startStreamZSL();
        goto end;
    }

    if (isFullSizeLiveshot())
      ret = initFullLiveshot();

    /* Check if it's a raw snapshot or JPEG*/
    if(mHalCamCtrl->isRawSnapshot()) {
        mSnapshotFormat = PICTURE_FORMAT_RAW;
        ret = initRawSnapshot(mNumOfSnapshot);
    }else{
        //JPEG
        mSnapshotFormat = PICTURE_FORMAT_JPEG;
        ret = initJPEGSnapshot(mNumOfSnapshot);
    }
    if(ret != NO_ERROR) {
        ALOGE("%s : Error while Initializing snapshot",__func__);
        goto end;
    }

    mHalCamCtrl->setExifTags();
    mHalCamCtrl->initExifData();

    if (mSnapshotFormat == PICTURE_FORMAT_RAW) {
        ret = takePictureRaw();
        goto end;
    }
    else{
        ret = takePictureJPEG();
        goto end;
    }

end:
    if (ret == NO_ERROR) {
        setSnapshotState(SNAPSHOT_STATE_IMAGE_CAPTURE_STRTD);
        mActive = true;
    }
    ALOGV("%s: X", __func__);
    return ret;
  }

void QCameraStream_Snapshot::stopPolling(void)
{
    mm_camera_ops_type_t ops_type;

    if (mSnapshotFormat == PICTURE_FORMAT_JPEG) {
        ops_type = isZSLMode() ? MM_CAMERA_OPS_ZSL : MM_CAMERA_OPS_SNAPSHOT;
    }else
        ops_type = MM_CAMERA_OPS_RAW;

    if( NO_ERROR != cam_ops_action(mCameraId, FALSE,
                                          ops_type, this)) {
        ALOGE("%s: Failure stopping snapshot", __func__);
    }
    // need to stop the preview here or it causes an iommu page fault
    if ((myMode & CAMERA_ZSL_MODE)  && !mHalCamCtrl->mZslFlashEnable)
        cam_ops_action(mCameraId, FALSE,MM_CAMERA_OPS_PREVIEW, this);

}

void QCameraStream_Snapshot::stop(void)
{
    mm_camera_ops_type_t ops_type;
    status_t ret = NO_ERROR;

    ALOGV("%s: E", __func__);
//    Mutex::Autolock l(&snapshotLock);

    if(!mActive) {
      ALOGV("%s: Not Active return now", __func__);
      return;
    }
    mActive = false;
    Mutex::Autolock lock(mStopCallbackLock);
    if (getSnapshotState() != SNAPSHOT_STATE_UNINIT) {
        /* Stop polling for further frames */
        stopPolling();
        if(getSnapshotState() == SNAPSHOT_STATE_JPEG_ENCODING) {
            ALOGV("Destroy Jpeg Instance");
            mm_jpeg_encoder_cancel();
        }

        /* Depending upon current state, we'll need to allocate-deallocate-deinit*/
        deInitBuffer();
    }

    if(mSnapshotFormat == PICTURE_FORMAT_RAW) {
        ret= QCameraStream::deinitChannel(mCameraId, MM_CAMERA_CH_RAW);
        if(ret != MM_CAMERA_OK) {
          ALOGE("%s:Deinit RAW channel failed=%d\n", __func__, ret);
        }
        (void)cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_RAW,
                                            NULL,
                                            (mm_camera_register_buf_cb_type_t)NULL,
                                            0,
                                            NULL);
    } else {
        ret= QCameraStream::deinitChannel(mCameraId, MM_CAMERA_CH_SNAPSHOT);
        if(ret != MM_CAMERA_OK) {
          ALOGE("%s:Deinit Snapshot channel failed=%d\n", __func__, ret);
        }
        (void)cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_SNAPSHOT,
                                            NULL,
                                            (mm_camera_register_buf_cb_type_t)NULL,
                                            0,
                                            NULL);
    }

    ALOGV("%s: X", __func__);

}

void QCameraStream_Snapshot::release()
{
    status_t ret = NO_ERROR;
    ALOGV("%s: E", __func__);
    //Mutex::Autolock l(&snapshotLock);

    if(isLiveSnapshot()) {
        deInitBuffer();
    }
    if(!mInit){
        ALOGE("%s : Stream not Initalized",__func__);
        return;
    }

    if(mActive) {
      this->stop();
      mActive = FALSE;
    }

    /* release is generally called in case of explicit call from
       upper-layer during disconnect. So we need to deinit everything
       whatever state we are in */

    //deinit();
    mInit = false;
    ALOGV("%s: X", __func__);

}

void QCameraStream_Snapshot::prepareHardware()
{
    ALOGV("%s: E", __func__);

    /* Prepare snapshot*/
    cam_ops_action(mCameraId,
                          TRUE,
                          MM_CAMERA_OPS_PREPARE_SNAPSHOT,
                          this);
    ALOGV("%s: X", __func__);
}

sp<IMemoryHeap> QCameraStream_Snapshot::getRawHeap() const
{
    return ((mDisplayHeap != NULL) ? mDisplayHeap->mHeap : NULL);
}

QCameraStream*
QCameraStream_Snapshot::createInstance(int cameraId,
                                      camera_mode_t mode)
{

  QCameraStream* pme = new QCameraStream_Snapshot(cameraId, mode);

  return pme;
}

void QCameraStream_Snapshot::deleteInstance(QCameraStream *p)
{
  if (p){
    p->release();
    delete p;
    p = NULL;
  }
}

void QCameraStream_Snapshot::notifyWDenoiseEvent(cam_ctrl_status_t status, void * cookie)
{
    camera_notify_callback         notifyCb;
    camera_data_callback           dataCb, jpgDataCb;
    int rc = NO_ERROR;
    mm_camera_ch_data_buf_t *frame = (mm_camera_ch_data_buf_t *)cookie;

    ALOGI("%s: WDN Done status (%d) received",__func__,status);
    Mutex::Autolock lock(mStopCallbackLock);
    if (frame == NULL) {
        ALOGE("%s: cookie is returned NULL", __func__);
    } else {
        // first unmapping the fds
        sendWDenoiseUnMappingBuf(MSM_V4L2_EXT_CAPTURE_MODE_MAIN, frame->snapshot.main.idx);
        sendWDenoiseUnMappingBuf(MSM_V4L2_EXT_CAPTURE_MODE_THUMBNAIL, frame->snapshot.thumbnail.idx);

        // then do JPEG encoding
        rc = encodeDisplayAndSave(frame, 0);
    }

    // send upperlayer callback for raw image (data or notify, not both)
    if((mHalCamCtrl->mDataCb) && (mHalCamCtrl->mMsgEnabled & CAMERA_MSG_RAW_IMAGE)){
      dataCb = mHalCamCtrl->mDataCb;
    } else {
      dataCb = NULL;
    }
    if((mHalCamCtrl->mNotifyCb) && (mHalCamCtrl->mMsgEnabled & CAMERA_MSG_RAW_IMAGE_NOTIFY)){
      notifyCb = mHalCamCtrl->mNotifyCb;
    } else {
      notifyCb = NULL;
    }
    if(mHalCamCtrl->mDataCb &&
        (mHalCamCtrl->mMsgEnabled & CAMERA_MSG_COMPRESSED_IMAGE)) {
        /* get picture failed. Give jpeg callback with NULL data
         * to the application to restore to preview mode
         */
        jpgDataCb = mHalCamCtrl->mDataCb;
    } else {
      jpgDataCb = NULL;
    }

    // launch next WDN if there is more in WDN Queue
    lauchNextWDenoiseFromQueue();

    mStopCallbackLock.unlock();

    if (rc != NO_ERROR)
    {
        ALOGE("%s: Error while encoding/displaying/saving image", __func__);
        if (frame) {
            cam_evt_buf_done(mCameraId, frame);
        }

        if (dataCb) {
          dataCb(CAMERA_MSG_RAW_IMAGE, mHalCamCtrl->mSnapshotMemory.camera_memory[0],
                               1, NULL, mHalCamCtrl->mCallbackCookie);
        }
        if (notifyCb) {
          notifyCb(CAMERA_MSG_RAW_IMAGE_NOTIFY, 0, 0, mHalCamCtrl->mCallbackCookie);
        }
        if (jpgDataCb) {
          jpgDataCb(CAMERA_MSG_COMPRESSED_IMAGE,
                                   NULL, 0, NULL,
                                   mHalCamCtrl->mCallbackCookie);
        }

        if (frame != NULL) {
            free(frame);
        }
    }
}

void QCameraStream_Snapshot::lauchNextWDenoiseFromQueue()
{
    do {
        mm_camera_ch_data_buf_t *frame = NULL;
        if ( mWDNQueue.isEmpty() ||
             (NULL == (frame = (mm_camera_ch_data_buf_t *)mWDNQueue.dequeue())) ) {
            // set the flag back to FALSE when no WDN going on
            mIsDoingWDN = FALSE;
            break;
        }

        if ( NO_ERROR != doWaveletDenoise(frame) ) {
            ALOGE("%s: Error while doing wavelet denoise", __func__);
            if (frame != NULL) {
                free(frame);
            }
        } else {
            // we sent out req for WDN, so we can break here
            ALOGD("%s: Send out req for doing wavelet denoise, return here", __func__);
            break;
        }
    } while (TRUE);
}

uint32_t QCameraStream_Snapshot::fillFrameInfo
(
    int                         ext_mode,
    mm_camera_frame_map_type *  frame_info,
    mm_camera_ch_data_buf_t *   rcvd_frame,
    cam_ctrl_dimension_t *      dim
)
{
    uint32_t rc = NO_ERROR;
    frame_info->ext_mode = ext_mode;
    switch (ext_mode) {
    case MSM_V4L2_EXT_CAPTURE_MODE_MAIN:
        frame_info->frame_idx = rcvd_frame->snapshot.main.idx;
        frame_info->fd = rcvd_frame->snapshot.main.frame->fd;
        frame_info->size = dim->picture_frame_offset.mp[0].len+dim->picture_frame_offset.mp[1].len;
        break;
    case MSM_V4L2_EXT_CAPTURE_MODE_THUMBNAIL:
        frame_info->frame_idx = rcvd_frame->snapshot.thumbnail.idx;
        frame_info->fd = rcvd_frame->snapshot.thumbnail.frame->fd;
        frame_info->size = dim->thumb_frame_offset.mp[0].len+dim->thumb_frame_offset.mp[1].len;
        break;
    default:
        ALOGE("%s: error - wrong ext_mode (%d)!", __func__, ext_mode);
        rc = BAD_VALUE;
        break;
    }

    return rc;
}

status_t QCameraStream_Snapshot::doWaveletDenoise(mm_camera_ch_data_buf_t* frame)
{
    status_t ret = NO_ERROR;

/*
    cam_sock_packet_t packet;
    cam_ctrl_dimension_t dim;

    ALOGD("%s: E", __func__);


    // get dim on the fly
    memset(&dim, 0, sizeof(cam_ctrl_dimension_t));
    ret = cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION, &dim);
    if (NO_ERROR != ret) {
        ALOGE("%s: error - can't get dimension!", __func__);
        return FAILED_TRANSACTION;
    }

    // send main frame mapping through domain socket
    if (NO_ERROR != sendWDenoiseMappingBuf(MSM_V4L2_EXT_CAPTURE_MODE_MAIN, frame, &dim)) {
        ALOGE("%s: sending main frame mapping buf msg Failed", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    // send thumbnail frame mapping through domain socket
    if (NO_ERROR != sendWDenoiseMappingBuf(MSM_V4L2_EXT_CAPTURE_MODE_THUMBNAIL, frame, &dim)) {
        ALOGE("%s: sending thumbnail frame mapping buf msg Failed", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

    // ask deamon to start wdn operation
    if (NO_ERROR != sendWDenoiseStartMsg(frame)) {
        ALOGE("%s: sending thumbnail frame mapping buf msg Failed", __func__);
        ret = FAILED_TRANSACTION;
        goto end;
    }

end:
*/
    ALOGD("%s: X", __func__);
    return ret;
}

status_t QCameraStream_Snapshot::sendWDenoiseStartMsg(mm_camera_ch_data_buf_t * frame)
{
/*
    cam_sock_packet_t packet;
    memset(&packet, 0, sizeof(cam_sock_packet_t));
    packet.msg_type = CAM_SOCK_MSG_TYPE_WDN_START;
    packet.payload.wdn_start.cookie = (unsigned long)frame;
    packet.payload.wdn_start.num_frames = MM_MAX_WDN_NUM;
    packet.payload.wdn_start.ext_mode[0] = MSM_V4L2_EXT_CAPTURE_MODE_MAIN;
    packet.payload.wdn_start.ext_mode[1] = MSM_V4L2_EXT_CAPTURE_MODE_THUMBNAIL;
    packet.payload.wdn_start.frame_idx[0] = frame->snapshot.main.idx;
    packet.payload.wdn_start.frame_idx[1] = frame->snapshot.thumbnail.idx;
    if ( cam_ops_sendmsg(mCameraId, &packet, sizeof(packet), 0) <= 0 ) {
        ALOGE("%s: sending start wavelet denoise msg failed", __func__);
        return FAILED_TRANSACTION;
    }
*/
    return NO_ERROR;
}

status_t QCameraStream_Snapshot::sendWDenoiseMappingBuf
(
    int                         ext_mode,
    mm_camera_ch_data_buf_t *   rcvd_frame,
    cam_ctrl_dimension_t *      dim
)
{
/*
    cam_sock_packet_t packet;
    memset(&packet, 0, sizeof(cam_sock_packet_t));
    packet.msg_type = CAM_SOCK_MSG_TYPE_FD_MAPPING;
    if (NO_ERROR != fillFrameInfo(ext_mode, &(packet.payload.frame_fd_map), rcvd_frame, dim)) {
        ALOGE("%s: Failure filling wdn frame info", __func__);
        return FAILED_TRANSACTION;
    }
    if ( cam_ops_sendmsg(mCameraId, &packet, sizeof(cam_sock_packet_t), packet.payload.frame_fd_map.fd) <= 0 ) {
        ALOGE("%s: sending frame mapping buf msg Failed", __func__);
        return FAILED_TRANSACTION;
    }
*/
    return NO_ERROR;
}

status_t QCameraStream_Snapshot::sendWDenoiseUnMappingBuf(int ext_mode, int idx)
{
/*
    cam_sock_packet_t packet;
    memset(&packet, 0, sizeof(cam_sock_packet_t));
    packet.msg_type = CAM_SOCK_MSG_TYPE_FD_UNMAPPING;
    packet.payload.frame_fd_unmap.ext_mode = ext_mode;
    packet.payload.frame_fd_unmap.frame_idx = idx;
    if ( cam_ops_sendmsg(mCameraId, &packet, sizeof(cam_sock_packet_t), 0) <= 0 ) {
        ALOGE("%s: sending frame unmapping buf msg Failed", __func__);
        return FAILED_TRANSACTION;
    }
*/
    return NO_ERROR;
}

}; // namespace android

