/*
** Copyright (c) 2011 Code Aurora Forum. All rights reserved.
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
#define LOG_NIDEBUG 0
#define LOG_TAG __FILE__
#include <utils/Log.h>
#include <utils/threads.h>


#include "QCameraStream.h"

/* QCameraStream class implementation goes here*/
/* following code implement the control logic of this class*/

namespace android {

StreamQueue::StreamQueue(){
    mInitialized = false;
}

StreamQueue::~StreamQueue(){
    flush();
}

void StreamQueue::init(){
    Mutex::Autolock l(&mQueueLock);
    mInitialized = true;
    mQueueWait.signal();
}

void StreamQueue::deinit(){
    Mutex::Autolock l(&mQueueLock);
    mInitialized = false;
    mQueueWait.signal();
}

bool StreamQueue::isInitialized(){
   Mutex::Autolock l(&mQueueLock);
   return mInitialized;
}

bool StreamQueue::enqueue(
                 void * element){
    Mutex::Autolock l(&mQueueLock);
    if(mInitialized == false)
        return false;

    mContainer.add(element);
    mQueueWait.signal();
    return true;
}

bool StreamQueue::isEmpty(){
    return (mInitialized && mContainer.isEmpty());
}
void* StreamQueue::dequeue(){

    void *frame;
    mQueueLock.lock();
    while(mInitialized && mContainer.isEmpty()){
        mQueueWait.wait(mQueueLock);
    }

    if(!mInitialized){
        mQueueLock.unlock();
        return NULL;
    }

    frame = mContainer.itemAt(0);
    mContainer.removeAt(0);
    mQueueLock.unlock();
    return frame;
}

void StreamQueue::flush(){
    Mutex::Autolock l(&mQueueLock);
    mContainer.clear();
}


// ---------------------------------------------------------------------------
// QCameraStream
// ---------------------------------------------------------------------------

/* initialize a streaming channel*/
status_t QCameraStream::initChannel(int cameraId,
                                    uint32_t ch_type_mask)
{
#if 0
    int rc = MM_CAMERA_OK;
    int i;
    status_t ret = NO_ERROR;
    int width = 0;  /* width of channel      */
    int height = 0; /* height of channel */
    cam_ctrl_dimension_t dim;
    mm_camera_ch_image_fmt_parm_t fmt;

    memset(&dim, 0, sizeof(cam_ctrl_dimension_t));
    rc = cam_config_get_parm(cameraId, MM_CAMERA_PARM_DIMENSION, &dim);
    if (MM_CAMERA_OK != rc) {
      ALOGE("%s: error - can't get camera dimension!", __func__);
      ALOGE("%s: X", __func__);
      return BAD_VALUE;
    }

    if(MM_CAMERA_CH_PREVIEW_MASK & ch_type_mask) {
        rc = cam_ops_ch_acquire(cameraId, MM_CAMERA_CH_PREVIEW);
        ALOGV("%s:ch_acquire MM_CAMERA_CH_PREVIEW, rc=%d\n",__func__, rc);

        if(MM_CAMERA_OK != rc) {
                ALOGE("%s: preview channel acquir error =%d\n", __func__, rc);
                ALOGE("%s: X", __func__);
                return BAD_VALUE;
        }
        else{
            memset(&fmt, 0, sizeof(mm_camera_ch_image_fmt_parm_t));
            fmt.ch_type = MM_CAMERA_CH_PREVIEW;
            fmt.def.fmt = CAMERA_YUV_420_NV12; //dim.prev_format;
            fmt.def.dim.width = dim.display_width;
            fmt.def.dim.height =  dim.display_height;
            ALOGV("%s: preview channel fmt = %d", __func__,
                     dim.prev_format);
            ALOGV("%s: preview channel resolution = %d X %d", __func__,
                     dim.display_width, dim.display_height);

            rc = cam_config_set_parm(cameraId, MM_CAMERA_PARM_CH_IMAGE_FMT, &fmt);
            ALOGV("%s: preview MM_CAMERA_PARM_CH_IMAGE_FMT rc = %d\n", __func__, rc);
            if(MM_CAMERA_OK != rc) {
                    ALOGE("%s:set preview channel format err=%d\n", __func__, ret);
                    ALOGE("%s: X", __func__);
                    ret = BAD_VALUE;
            }
        }
    }


    if(MM_CAMERA_CH_VIDEO_MASK & ch_type_mask)
    {
        rc = cam_ops_ch_acquire(cameraId, MM_CAMERA_CH_VIDEO);
        ALOGV("%s:ch_acquire MM_CAMERA_CH_VIDEO, rc=%d\n",__func__, rc);

        if(MM_CAMERA_OK != rc) {
                ALOGE("%s: video channel acquir error =%d\n", __func__, rc);
                ALOGE("%s: X", __func__);
                ret = BAD_VALUE;
        }
        else {
            memset(&fmt, 0, sizeof(mm_camera_ch_image_fmt_parm_t));
            fmt.ch_type = MM_CAMERA_CH_VIDEO;
            fmt.video.video.fmt = CAMERA_YUV_420_NV12; //dim.enc_format;
            fmt.video.video.dim.width = dim.video_width;
            fmt.video.video.dim.height = dim.video_height;
            ALOGV("%s: video channel fmt = %d", __func__,
                     dim.enc_format);
            ALOGV("%s: video channel resolution = %d X %d", __func__,
                 dim.video_width, dim.video_height);

            rc = cam_config_set_parm(cameraId,  MM_CAMERA_PARM_CH_IMAGE_FMT, &fmt);

            ALOGV("%s: video MM_CAMERA_PARM_CH_IMAGE_FMT rc = %d\n", __func__, rc);
            if(MM_CAMERA_OK != rc) {
                ALOGE("%s:set video channel format err=%d\n", __func__, rc);
                ALOGE("%s: X", __func__);
                ret= BAD_VALUE;
            }
        }

  } /*MM_CAMERA_CH_VIDEO*/
#endif

    int rc = MM_CAMERA_OK;
    status_t ret = NO_ERROR;
    mm_camera_op_mode_type_t op_mode=MM_CAMERA_OP_MODE_VIDEO;
    int i;

    ALOGV("QCameraStream::initChannel : E");
    if(MM_CAMERA_CH_PREVIEW_MASK & ch_type_mask){
        rc = cam_ops_ch_acquire(cameraId, MM_CAMERA_CH_PREVIEW);
        ALOGV("%s:ch_acquire MM_CAMERA_CH_PREVIEW, rc=%d\n",__func__, rc);
        if(MM_CAMERA_OK != rc) {
                ALOGE("%s: preview channel acquir error =%d\n", __func__, rc);
                ALOGE("%s: X", __func__);
                return BAD_VALUE;
        }
        /*Callback register*/
        /* register a notify into the mmmm_camera_t object*/
       /* ret = cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_PREVIEW,
                                                preview_notify_cb,
                                                this);
        ALOGV("Buf notify MM_CAMERA_CH_PREVIEW, rc=%d\n",rc);*/
    }else if(MM_CAMERA_CH_VIDEO_MASK & ch_type_mask){
        rc = cam_ops_ch_acquire(cameraId, MM_CAMERA_CH_VIDEO);
        ALOGV("%s:ch_acquire MM_CAMERA_CH_VIDEO, rc=%d\n",__func__, rc);
        if(MM_CAMERA_OK != rc) {
                ALOGE("%s: preview channel acquir error =%d\n", __func__, rc);
                ALOGE("%s: X", __func__);
                return BAD_VALUE;
        }
        /*Callback register*/
        /* register a notify into the mmmm_camera_t object*/
        /*ret = cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_VIDEO,
                                                record_notify_cb,
                                                this);
        ALOGV("Buf notify MM_CAMERA_CH_VIDEO, rc=%d\n",rc);*/
    }
    setFormat(ch_type_mask);
    ret = (MM_CAMERA_OK==rc)? NO_ERROR : BAD_VALUE;
    ALOGV("%s: X, ret = %d", __func__, ret);
    return ret;
}

status_t QCameraStream::deinitChannel(int cameraId,
                                    mm_camera_channel_type_t ch_type)
{

    int rc = MM_CAMERA_OK;

    ALOGV("%s: E, channel = %d\n", __func__, ch_type);

    if (MM_CAMERA_CH_MAX <= ch_type) {
        ALOGE("%s: X: BAD_VALUE", __func__);
        return BAD_VALUE;
    }

    cam_ops_ch_release(cameraId, ch_type);

    ALOGV("%s: X, channel = %d\n", __func__, ch_type);
    return NO_ERROR;
}

status_t QCameraStream::setMode(int enable) {
  ALOGE("%s :myMode %x ", __func__, myMode);
  if (enable) {
      myMode = (camera_mode_t)(myMode | CAMERA_ZSL_MODE);
  } else {
      myMode = (camera_mode_t)(myMode & ~CAMERA_ZSL_MODE);
  }
  return NO_ERROR;
}

status_t QCameraStream::setFormat(uint8_t ch_type_mask)
{
    int rc = MM_CAMERA_OK;
    status_t ret = NO_ERROR;
    int width = 0;  /* width of channel      */
    int height = 0; /* height of channel */
    cam_ctrl_dimension_t dim;
    mm_camera_ch_image_fmt_parm_t fmt;

    ALOGE("%s: E",__func__);

    memset(&dim, 0, sizeof(cam_ctrl_dimension_t));
    rc = cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION, &dim);
    if (MM_CAMERA_OK != rc) {
      ALOGE("%s: error - can't get camera dimension!", __func__);
      ALOGE("%s: X", __func__);
      return BAD_VALUE;
    }

    memset(&fmt, 0, sizeof(mm_camera_ch_image_fmt_parm_t));
    if(MM_CAMERA_CH_PREVIEW_MASK & ch_type_mask){
        fmt.ch_type = MM_CAMERA_CH_PREVIEW;
        fmt.def.fmt = CAMERA_YUV_420_NV12; //dim.prev_format;
        fmt.def.dim.width = dim.display_width;
        fmt.def.dim.height =  dim.display_height;
    }else if(MM_CAMERA_CH_VIDEO_MASK & ch_type_mask){
        fmt.ch_type = MM_CAMERA_CH_VIDEO;
        fmt.video.video.fmt = CAMERA_YUV_420_NV21; //dim.enc_format;
        fmt.video.video.dim.width = dim.video_width;
        fmt.video.video.dim.height = dim.video_height;
    }/*else if(MM_CAMERA_CH_SNAPSHOT_MASK & ch_type_mask){
        if(mHalCamCtrl->isRawSnapshot()) {
            fmt.ch_type = MM_CAMERA_CH_RAW;
            fmt.def.fmt = CAMERA_BAYER_SBGGR10;
            fmt.def.dim.width = dim.raw_picture_width;
            fmt.def.dim.height = dim.raw_picture_height;
        }else{
            //Jpeg???
            fmt.ch_type = MM_CAMERA_CH_SNAPSHOT;
            fmt.snapshot.main.fmt = dim.main_img_format;
            fmt.snapshot.main.dim.width = dim.picture_width;
            fmt.snapshot.main.dim.height = dim.picture_height;

            fmt.snapshot.thumbnail.fmt = dim.thumb_format;
            fmt.snapshot.thumbnail.dim.width = dim.ui_thumbnail_width;
            fmt.snapshot.thumbnail.dim.height = dim.ui_thumbnail_height;
        }
    }*/

    rc = cam_config_set_parm(mCameraId, MM_CAMERA_PARM_CH_IMAGE_FMT, &fmt);
    ALOGV("%s: Stream MM_CAMERA_PARM_CH_IMAGE_FMT %d %d rc = %d\n", __func__, fmt.ch_type, fmt.video.video.fmt, rc);
    if(MM_CAMERA_OK != rc) {
        ALOGE("%s:set stream channel format err=%d\n", __func__, ret);
        ALOGE("%s: X", __func__);
        ret = BAD_VALUE;
    }
    ALOGE("%s: X",__func__);
    return ret;
}

QCameraStream::QCameraStream (){
    mInit = false;
    mActive = false;
    /* memset*/
    memset(&mCrop, 0, sizeof(mm_camera_ch_crop_t));
}

QCameraStream::QCameraStream (int cameraId, camera_mode_t mode)
              :mCameraId(cameraId),
               myMode(mode)
{
    mInit = false;
    mActive = false;

    /* memset*/
    memset(&mCrop, 0, sizeof(mm_camera_ch_crop_t));
}

QCameraStream::~QCameraStream () {;}


status_t QCameraStream::init() {
    return NO_ERROR;
}

status_t QCameraStream::start() {
    return NO_ERROR;
}

void QCameraStream::stop() {
    return;
}

void QCameraStream::release() {
    return;
}

void QCameraStream::setHALCameraControl(QCameraHardwareInterface* ctrl) {

    /* provide a frame data user,
    for the  queue monitor thread to call the busy queue is not empty*/
    mHalCamCtrl = ctrl;
}

}; // namespace android
