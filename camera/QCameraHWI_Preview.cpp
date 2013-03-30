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

#define LOG_TAG "QCameraHWI_Preview"
#include <utils/Log.h>
#include <utils/threads.h>
#include <fcntl.h>
#include <sys/mman.h>

#include "QCameraHAL.h"
#include "QCameraHWI.h"
#include <gralloc_priv.h>
#include <genlock.h>

#define UNLIKELY(exp) __builtin_expect(!!(exp), 0)

/* QCameraHWI_Preview class implementation goes here*/
/* following code implement the preview mode's image capture & display logic of this class*/

namespace android {

// ---------------------------------------------------------------------------
// Preview Callback
// ---------------------------------------------------------------------------
static void preview_notify_cb(mm_camera_ch_data_buf_t *frame,
                                void *user_data)
{
  QCameraStream_preview *pme = (QCameraStream_preview *)user_data;
  mm_camera_ch_data_buf_t *bufs_used = 0;
  ALOGV("%s: E", __func__);
  /* for peview data, there is no queue, so directly use*/
  if(pme==NULL) {
    ALOGE("%s: X : Incorrect cookie",__func__);
    /*Call buf done*/
    return;
  }

  pme->processPreviewFrame(frame);
  ALOGV("%s: X", __func__);
}

status_t QCameraStream_preview::setPreviewWindow(preview_stream_ops_t* window)
{
    status_t retVal = NO_ERROR;
    ALOGE(" %s: E ", __FUNCTION__);
    if( window == NULL) {
        ALOGW(" Setting NULL preview window ");
        /* TODO: Current preview window will be invalidated.
         * Release all the buffers back */
       // relinquishBuffers();
    }
    mDisplayLock.lock();
    mPreviewWindow = window;
    mDisplayLock.unlock();
    ALOGV(" %s : X ", __FUNCTION__ );
    return retVal;
}

status_t QCameraStream_preview::getBufferFromSurface() {
    int err = 0;
    int numMinUndequeuedBufs = 0;
  int format = 0;
  status_t ret = NO_ERROR;

    ALOGI(" %s : E ", __FUNCTION__);

    if( mPreviewWindow == NULL) {
    ALOGE("%s: mPreviewWindow = NULL", __func__);
        return INVALID_OPERATION;
  }
    cam_ctrl_dimension_t dim;

  //mDisplayLock.lock();
    cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION,&dim);

	format = mHalCamCtrl->getPreviewFormatInfo().Hal_format;
	if(ret != NO_ERROR) {
        ALOGE("%s: display format %d is not supported", __func__, dim.prev_format);
    goto end;
  }
  numMinUndequeuedBufs = 0;
  if(mPreviewWindow->get_min_undequeued_buffer_count) {
    err = mPreviewWindow->get_min_undequeued_buffer_count(mPreviewWindow, &numMinUndequeuedBufs);
    if (err != 0) {
       ALOGE("get_min_undequeued_buffer_count  failed: %s (%d)",
            strerror(-err), -err);
       ret = UNKNOWN_ERROR;
       goto end;
    }
  }
    mHalCamCtrl->mPreviewMemoryLock.lock();
    mHalCamCtrl->mPreviewMemory.buffer_count = kPreviewBufferCount + numMinUndequeuedBufs;
    if(mHalCamCtrl->isZSLMode()) {
      if(mHalCamCtrl->getZSLQueueDepth() > numMinUndequeuedBufs)
        mHalCamCtrl->mPreviewMemory.buffer_count +=
            mHalCamCtrl->getZSLQueueDepth() - numMinUndequeuedBufs;
    }
    err = mPreviewWindow->set_buffer_count(mPreviewWindow, mHalCamCtrl->mPreviewMemory.buffer_count );
    if (err != 0) {
         ALOGE("set_buffer_count failed: %s (%d)",
                    strerror(-err), -err);
         ret = UNKNOWN_ERROR;
     goto end;
    }
    err = mPreviewWindow->set_buffers_geometry(mPreviewWindow,
                dim.display_width, dim.display_height, format);
    if (err != 0) {
         ALOGE("set_buffers_geometry failed: %s (%d)",
                    strerror(-err), -err);
         ret = UNKNOWN_ERROR;
     goto end;
    }
    err = mPreviewWindow->set_usage(mPreviewWindow,
        GRALLOC_USAGE_PRIVATE_MM_HEAP |
        GRALLOC_USAGE_PRIVATE_IOMMU_HEAP |
        GRALLOC_USAGE_PRIVATE_UNCACHED);
	if(err != 0) {
        /* set_usage error out */
		ALOGE("%s: set_usage rc = %d", __func__, err);
		ret = UNKNOWN_ERROR;
		goto end;
	}
	for (int cnt = 0; cnt < mHalCamCtrl->mPreviewMemory.buffer_count; cnt++) {
		int stride;
		err = mPreviewWindow->dequeue_buffer(mPreviewWindow,
										&mHalCamCtrl->mPreviewMemory.buffer_handle[cnt],
										&mHalCamCtrl->mPreviewMemory.stride[cnt]);
		if(!err) {
          ALOGE("%s: dequeue buf hdl =%p", __func__, *mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]);
                    err = mPreviewWindow->lock_buffer(this->mPreviewWindow,
                                       mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]);
                    // lock the buffer using genlock
                    ALOGE("%s: camera call genlock_lock, hdl=%p", __FUNCTION__, (*mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]));
                    if (GENLOCK_NO_ERROR != genlock_lock_buffer((native_handle_t *)(*mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]),
                                                      GENLOCK_WRITE_LOCK, GENLOCK_MAX_TIMEOUT)) {
                       ALOGE("%s: genlock_lock_buffer(WRITE) failed", __FUNCTION__);
                       mHalCamCtrl->mPreviewMemory.local_flag[cnt] = BUFFER_UNLOCKED;
	                //mHalCamCtrl->mPreviewMemoryLock.unlock();
                       //return -EINVAL;
                   } else {
                     ALOGE("%s: genlock_lock_buffer hdl =%p", __FUNCTION__, *mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]);
                     mHalCamCtrl->mPreviewMemory.local_flag[cnt] = BUFFER_LOCKED;
                   }
		} else {
          mHalCamCtrl->mPreviewMemory.local_flag[cnt] = BUFFER_NOT_OWNED;
          ALOGE("%s: dequeue_buffer idx = %d err = %d", __func__, cnt, err);
        }

		ALOGE("%s: dequeue buf: %p\n", __func__, mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]);

		if(err != 0) {
            ALOGE("%s: dequeue_buffer failed: %s (%d)", __func__,
                    strerror(-err), -err);
            ret = UNKNOWN_ERROR;
			for(int i = 0; i < cnt; i++) {
                if (BUFFER_LOCKED == mHalCamCtrl->mPreviewMemory.local_flag[i]) {
                      ALOGE("%s: camera call genlock_unlock", __FUNCTION__);
                     if (GENLOCK_FAILURE == genlock_unlock_buffer((native_handle_t *)
                                                  (*(mHalCamCtrl->mPreviewMemory.buffer_handle[i])))) {
                        ALOGE("%s: genlock_unlock_buffer failed: hdl =%p", __FUNCTION__, (*(mHalCamCtrl->mPreviewMemory.buffer_handle[i])) );
                         //mHalCamCtrl->mPreviewMemoryLock.unlock();
                        //return -EINVAL;
                     } else {
                       mHalCamCtrl->mPreviewMemory.local_flag[i] = BUFFER_UNLOCKED;
                     }
                }
                if( mHalCamCtrl->mPreviewMemory.local_flag[i] != BUFFER_NOT_OWNED) {
                  err = mPreviewWindow->cancel_buffer(mPreviewWindow,
                                          mHalCamCtrl->mPreviewMemory.buffer_handle[i]);
                }
                mHalCamCtrl->mPreviewMemory.local_flag[i] = BUFFER_NOT_OWNED;
                ALOGE("%s: cancel_buffer: hdl =%p", __func__,  (*mHalCamCtrl->mPreviewMemory.buffer_handle[i]));
				mHalCamCtrl->mPreviewMemory.buffer_handle[i] = NULL;
			}
			goto end;
		}
		mHalCamCtrl->mPreviewMemory.private_buffer_handle[cnt] =
		    (struct private_handle_t *)(*mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]);
		mHalCamCtrl->mPreviewMemory.camera_memory[cnt] =
		    mHalCamCtrl->mGetMemory(mHalCamCtrl->mPreviewMemory.private_buffer_handle[cnt]->fd,
			mHalCamCtrl->mPreviewMemory.private_buffer_handle[cnt]->size, 1, (void *)this);
		ALOGE("%s: idx = %d, fd = %d, size = %d, offset = %d", __func__,
            cnt, mHalCamCtrl->mPreviewMemory.private_buffer_handle[cnt]->fd,
      mHalCamCtrl->mPreviewMemory.private_buffer_handle[cnt]->size,
      mHalCamCtrl->mPreviewMemory.private_buffer_handle[cnt]->offset);
  }


  memset(&mHalCamCtrl->mMetadata, 0, sizeof(mHalCamCtrl->mMetadata));
  memset(mHalCamCtrl->mFace, 0, sizeof(mHalCamCtrl->mFace));

    ALOGI(" %s : X ",__FUNCTION__);
end:
  //mDisplayLock.unlock();
  mHalCamCtrl->mPreviewMemoryLock.unlock();

    return NO_ERROR;
}

status_t QCameraStream_preview::putBufferToSurface() {
    int err = 0;
  status_t ret = NO_ERROR;

    ALOGI(" %s : E ", __FUNCTION__);

    //mDisplayLock.lock();
    mHalCamCtrl->mPreviewMemoryLock.lock();
	for (int cnt = 0; cnt < mHalCamCtrl->mPreviewMemory.buffer_count; cnt++) {
        if (cnt < mHalCamCtrl->mPreviewMemory.buffer_count) {
            if (NO_ERROR != sendUnMappingBuf(MSM_V4L2_EXT_CAPTURE_MODE_PREVIEW, cnt)) {
                ALOGE("%s: sending data Msg Failed", __func__);
            }
        }

        mHalCamCtrl->mPreviewMemory.camera_memory[cnt]->release(mHalCamCtrl->mPreviewMemory.camera_memory[cnt]);
            if (BUFFER_LOCKED == mHalCamCtrl->mPreviewMemory.local_flag[cnt]) {
                ALOGD("%s: camera call genlock_unlock", __FUNCTION__);
	        if (GENLOCK_FAILURE == genlock_unlock_buffer((native_handle_t *)
                                                    (*(mHalCamCtrl->mPreviewMemory.buffer_handle[cnt])))) {
                    ALOGE("%s: genlock_unlock_buffer failed, handle =%p", __FUNCTION__, (*(mHalCamCtrl->mPreviewMemory.buffer_handle[cnt])));
	                //mHalCamCtrl->mPreviewMemoryLock.unlock();
                    //return -EINVAL;
                } else {

                    ALOGD("%s: genlock_unlock_buffer, handle =%p", __FUNCTION__, (*(mHalCamCtrl->mPreviewMemory.buffer_handle[cnt])));
                    mHalCamCtrl->mPreviewMemory.local_flag[cnt] = BUFFER_UNLOCKED;
                }
            }
             if( mHalCamCtrl->mPreviewMemory.local_flag[cnt] != BUFFER_NOT_OWNED) {
               err = mPreviewWindow->cancel_buffer(mPreviewWindow, mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]);
               ALOGD("%s: cancel_buffer: hdl =%p", __func__,  (*mHalCamCtrl->mPreviewMemory.buffer_handle[cnt]));
             }
             mHalCamCtrl->mPreviewMemory.local_flag[cnt] = BUFFER_NOT_OWNED;

		ALOGD(" put buffer %d successfully", cnt);
	}
	memset(&mHalCamCtrl->mPreviewMemory, 0, sizeof(mHalCamCtrl->mPreviewMemory));
	mHalCamCtrl->mPreviewMemoryLock.unlock();
	//mDisplayLock.unlock();
    ALOGI(" %s : X ",__FUNCTION__);
    return NO_ERROR;
}

void QCameraStream_preview::notifyROIEvent(fd_roi_t roi)
{
    int faces_detected = roi.rect_num;
    if(faces_detected > MAX_ROI)
      faces_detected = MAX_ROI;
    ALOGI("%s, width = %d height = %d", __func__,
       mHalCamCtrl->mDimension.display_width,
       mHalCamCtrl->mDimension.display_height);
    mDisplayLock.lock();
    for (int i = 0; i < faces_detected; i++) {
       // top
       mHalCamCtrl->mFace[i].rect[0] =
           roi.faces[i].x*2000/mHalCamCtrl->mDimension.display_width - 1000;
       //right
       mHalCamCtrl->mFace[i].rect[1] =
          ((roi.faces[i].y)*2000)/mHalCamCtrl->mDimension.display_height - 1000;
      //bottom
      mHalCamCtrl->mFace[i].rect[2] =  mHalCamCtrl->mFace[i].rect[0] +
          (( roi.faces[i].dx*2000)/mHalCamCtrl->mDimension.display_width);
      //left
      mHalCamCtrl->mFace[i].rect[3] = mHalCamCtrl->mFace[i].rect[1] +
           (roi.faces[i].dy*2000)/mHalCamCtrl->mDimension.display_height;
    }
    mHalCamCtrl->mMetadata.number_of_faces = faces_detected;
    mHalCamCtrl->mMetadata.faces = mHalCamCtrl->mFace;
    mDisplayLock.unlock();
}

status_t QCameraStream_preview::initDisplayBuffers()
{
  status_t ret = NO_ERROR;
  int width = 0;  /* width of channel  */
  int height = 0; /* height of channel */
  uint32_t frame_len = 0; /* frame planner length */
  int buffer_num = 4; /* number of buffers for display */
  const char *pmem_region;
  uint8_t num_planes = 0;
  uint32_t planes[VIDEO_MAX_PLANES];

  cam_ctrl_dimension_t dim;

  ALOGE("%s:BEGIN",__func__);
  memset(&mHalCamCtrl->mMetadata, 0, sizeof(camera_frame_metadata_t));
  mHalCamCtrl->mPreviewMemoryLock.lock();
  memset(&mHalCamCtrl->mPreviewMemory, 0, sizeof(mHalCamCtrl->mPreviewMemory));
  mHalCamCtrl->mPreviewMemoryLock.unlock();
  memset(&mNotifyBuffer, 0, sizeof(mNotifyBuffer));

/* get preview size, by qury mm_camera*/
  memset(&dim, 0, sizeof(cam_ctrl_dimension_t));

  memset(&(this->mDisplayStreamBuf),0, sizeof(this->mDisplayStreamBuf));

  ret = cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION, &dim);
  if (MM_CAMERA_OK != ret) {
    ALOGE("%s: error - can't get camera dimension!", __func__);
    ALOGE("%s: X", __func__);
    return BAD_VALUE;
  }else {
    width =  dim.display_width,
    height = dim.display_height;
  }

  ret = getBufferFromSurface();
  if(ret != NO_ERROR) {
    ALOGE("%s: cannot get memory from surface texture client, ret = %d", __func__, ret);
    return ret;
  }

  /* set 4 buffers for display */
  memset(&mDisplayStreamBuf, 0, sizeof(mDisplayStreamBuf));
  mHalCamCtrl->mPreviewMemoryLock.lock();
  this->mDisplayStreamBuf.num = mHalCamCtrl->mPreviewMemory.buffer_count;
  this->myMode=myMode; /*Need to assign this in constructor after translating from mask*/
  num_planes = 2;
  planes[0] = width*height; //dim.display_frame_offset.mp[0].len;
  planes[1] = dim.display_frame_offset.mp[1].len;
  this->mDisplayStreamBuf.frame_len = planes[0]+planes[1]; //dim.display_frame_offset.frame_len;
  ALOGE("%s: planes=%d %d len=%d",__func__,planes[0],planes[1],this->mDisplayStreamBuf.frame_len);

  memset(&mDisplayBuf, 0, sizeof(mDisplayBuf));
  mDisplayBuf.preview.buf.mp = new mm_camera_mp_buf_t[mDisplayStreamBuf.num];
  if (!mDisplayBuf.preview.buf.mp) {
    ALOGE("%s Error allocating memory for mplanar struct ", __func__);
  }
  memset(mDisplayBuf.preview.buf.mp, 0,
    mDisplayStreamBuf.num * sizeof(mm_camera_mp_buf_t));

  /*allocate memory for the buffers*/
  void *vaddr = NULL;
  for(int i = 0; i < mDisplayStreamBuf.num; i++){
	  if (mHalCamCtrl->mPreviewMemory.private_buffer_handle[i] == NULL)
		  continue;
      mDisplayStreamBuf.frame[i].fd = mHalCamCtrl->mPreviewMemory.private_buffer_handle[i]->fd;
      mDisplayStreamBuf.frame[i].cbcr_off = planes[0];
      mDisplayStreamBuf.frame[i].y_off = 0;
      mDisplayStreamBuf.frame[i].path = OUTPUT_TYPE_P;
	  mHalCamCtrl->mPreviewMemory.addr_offset[i] =
	      mHalCamCtrl->mPreviewMemory.private_buffer_handle[i]->offset;
      mDisplayStreamBuf.frame[i].buffer =
          (long unsigned int)mHalCamCtrl->mPreviewMemory.camera_memory[i]->data;

    ALOGE("%s: idx = %d, fd = %d, size = %d, cbcr_offset = %d, y_offset = %d, "
      "offset = %d, vaddr = 0x%x", __func__, i, mDisplayStreamBuf.frame[i].fd,
      mHalCamCtrl->mPreviewMemory.private_buffer_handle[i]->size,
      mDisplayStreamBuf.frame[i].cbcr_off, mDisplayStreamBuf.frame[i].y_off,
      mHalCamCtrl->mPreviewMemory.addr_offset[i],
      (uint32_t)mDisplayStreamBuf.frame[i].buffer);

    if (NO_ERROR != sendMappingBuf(
                        MSM_V4L2_EXT_CAPTURE_MODE_PREVIEW,
                        i,
                        mDisplayStreamBuf.frame[i].fd,
                        mHalCamCtrl->mPreviewMemory.private_buffer_handle[i]->size)) {
      ALOGE("%s: sending mapping data Msg Failed", __func__);
    }

    mDisplayBuf.preview.buf.mp[i].frame = mDisplayStreamBuf.frame[i];
    mDisplayBuf.preview.buf.mp[i].frame_offset = mHalCamCtrl->mPreviewMemory.addr_offset[i];
    mDisplayBuf.preview.buf.mp[i].num_planes = num_planes;

    /* Plane 0 needs to be set seperately. Set other planes
     * in a loop. */
    mDisplayBuf.preview.buf.mp[i].planes[0].length = planes[0];
    mDisplayBuf.preview.buf.mp[i].planes[0].m.userptr = mDisplayStreamBuf.frame[i].fd;
    mDisplayBuf.preview.buf.mp[i].planes[0].data_offset = 0;
    mDisplayBuf.preview.buf.mp[i].planes[0].reserved[0] =
      mDisplayBuf.preview.buf.mp[i].frame_offset;
    for (int j = 1; j < num_planes; j++) {
      mDisplayBuf.preview.buf.mp[i].planes[j].length = planes[j];
      mDisplayBuf.preview.buf.mp[i].planes[j].m.userptr =
        mDisplayStreamBuf.frame[i].fd;
      mDisplayBuf.preview.buf.mp[i].planes[j].data_offset = 0;
      mDisplayBuf.preview.buf.mp[i].planes[j].reserved[0] =
        mDisplayBuf.preview.buf.mp[i].planes[j-1].reserved[0] +
        mDisplayBuf.preview.buf.mp[i].planes[j-1].length;
    }

    for (int j = 0; j < num_planes; j++)
      ALOGE("Planes: %d length: %d userptr: %lu offset: %d\n", j,
        mDisplayBuf.preview.buf.mp[i].planes[j].length,
        mDisplayBuf.preview.buf.mp[i].planes[j].m.userptr,
        mDisplayBuf.preview.buf.mp[i].planes[j].reserved[0]);
  }/*end of for loop*/

 /* register the streaming buffers for the channel*/
  mDisplayBuf.ch_type = MM_CAMERA_CH_PREVIEW;
  mDisplayBuf.preview.num = mDisplayStreamBuf.num;
  mHalCamCtrl->mPreviewMemoryLock.unlock();
  ALOGE("%s:END",__func__);
  return NO_ERROR;

end:
  if (MM_CAMERA_OK == ret ) {
    ALOGV("%s: X - NO_ERROR ", __func__);
    return NO_ERROR;
  }

    ALOGV("%s: out of memory clean up", __func__);
  /* release the allocated memory */

  ALOGV("%s: X - BAD_VALUE ", __func__);
  return BAD_VALUE;
}

status_t QCameraStream_preview::reinitDisplayBuffers()
{
    int err = NO_ERROR;
    buffer_handle_t *buffer_handle = NULL;
    int tmp_stride = 0, i = 0;
    ALOGI(" %s : E ", __FUNCTION__);

    if (mDisplayBuf.preview.buf.mp == NULL) {
        ALOGE("%s: preview.buf.mp is NULL, propbably wrong state", __FUNCTION__);
        return BAD_VALUE;
    }

    mHalCamCtrl->mPreviewMemoryLock.lock();

    while (err == NO_ERROR) {
        buffer_handle = NULL;
        tmp_stride = 0;
        err = this->mPreviewWindow->dequeue_buffer(this->mPreviewWindow,
                    &buffer_handle, &tmp_stride);
        if (err == NO_ERROR && buffer_handle != NULL) {
            ALOGD("%s: dequed buf hdl =%p", __func__, *buffer_handle);
            for(i = 0; i < mHalCamCtrl->mPreviewMemory.buffer_count; i++) {
                if(mHalCamCtrl->mPreviewMemory.buffer_handle[i] == buffer_handle) {
                    mHalCamCtrl->mPreviewMemory.local_flag[i] = BUFFER_UNLOCKED;
                    break;
                }
            }
            if (i < mHalCamCtrl->mPreviewMemory.buffer_count ) {
                err = this->mPreviewWindow->lock_buffer(this->mPreviewWindow, buffer_handle);
                ALOGD("%s: camera call genlock_lock: hdl =%p", __FUNCTION__, *buffer_handle);
                if (GENLOCK_FAILURE == genlock_lock_buffer((native_handle_t*)(*buffer_handle), GENLOCK_WRITE_LOCK,
                                                        GENLOCK_MAX_TIMEOUT)) {
                   ALOGE("%s: genlock_lock_buffer(WRITE) failed", __FUNCTION__);
                } else  {
                    mHalCamCtrl->mPreviewMemory.local_flag[i] = BUFFER_LOCKED;
                }
            }
        }
    }

    for (i=0; i<mDisplayBuf.preview.num; i++) {
        mHalCamCtrl->mPreviewMemory.enqueued_flag[i] = FALSE;
    }
    mHalCamCtrl->mPreviewMemoryLock.unlock();

    ALOGI(" %s : X ",__FUNCTION__);
    return NO_ERROR;
}

void QCameraStream_preview::dumpFrameToFile(struct msm_frame* newFrame)
{
  int32_t enabled = 0;
  int frm_num;
  uint32_t  skip_mode;
  char value[PROPERTY_VALUE_MAX];
  char buf[32];
  int w, h;
  static int count = 0;
  cam_ctrl_dimension_t dim;
  int file_fd;
  int rc = 0;
  int len;
  unsigned long addr;
  unsigned long * tmp = (unsigned long *)newFrame->buffer;
  addr = *tmp;
  status_t ret = cam_config_get_parm(mHalCamCtrl->mCameraId,
                 MM_CAMERA_PARM_DIMENSION, &dim);

  w = dim.display_width;
  h = dim.display_height;
  len = (w * h)*3/2;
  count++;
  if(count < 100) {
    snprintf(buf, sizeof(buf), "/data/mzhu%d.yuv", count);
    file_fd = open(buf, O_RDWR | O_CREAT, 0777);

    rc = write(file_fd, (const void *)addr, len);
    ALOGE("%s: file='%s', vaddr_old=0x%x, addr_map = 0x%p, len = %d, rc = %d",
          __func__, buf, (uint32_t)newFrame->buffer, (void *)addr, len, rc);
    close(file_fd);
    ALOGE("%s: dump %s, rc = %d, len = %d", __func__, buf, rc, len);
  }
}

status_t QCameraStream_preview::processPreviewFrame(mm_camera_ch_data_buf_t *frame)
{
  ALOGV("%s",__func__);
  int err = 0;
  int msgType = 0;
  int i;
  camera_memory_t *data = NULL;
  camera_frame_metadata_t *metadata = NULL;

  if(!mActive) {
    ALOGE("Preview Stopped. Returning callback");
    return NO_ERROR;
  }
  Mutex::Autolock lock(mStopCallbackLock);
  if(mHalCamCtrl==NULL) {
    ALOGE("%s: X: HAL control object not set",__func__);
    /*Call buf done*/
    return BAD_VALUE;
  }

  if (UNLIKELY(mHalCamCtrl->mDebugFps)) {
      mHalCamCtrl->debugShowPreviewFPS();
  }
  //dumpFrameToFile(frame->def.frame);
  mHalCamCtrl->dumpFrameToFile(frame->def.frame, HAL_DUMP_FRM_PREVIEW);

  mHalCamCtrl->mPreviewMemoryLock.lock();
  mNotifyBuffer[frame->def.idx] = *frame;
  // mzhu fix me, need to check meta data also.

  ALOGV("Enqueue buf handle %p\n",
  mHalCamCtrl->mPreviewMemory.buffer_handle[frame->def.idx]);
  ALOGV("%s: camera call genlock_unlock", __FUNCTION__);
    if (BUFFER_LOCKED == mHalCamCtrl->mPreviewMemory.local_flag[frame->def.idx]) {
      ALOGV("%s: genlock_unlock_buffer hdl =%p", __FUNCTION__, (*mHalCamCtrl->mPreviewMemory.buffer_handle[frame->def.idx]));
        if (GENLOCK_FAILURE == genlock_unlock_buffer((native_handle_t*)
	            (*mHalCamCtrl->mPreviewMemory.buffer_handle[frame->def.idx]))) {
            ALOGE("%s: genlock_unlock_buffer failed", __FUNCTION__);
	        //mHalCamCtrl->mPreviewMemoryLock.unlock();
            //return -EINVAL;
        } else {
            mHalCamCtrl->mPreviewMemory.local_flag[frame->def.idx] = BUFFER_UNLOCKED;
        }
    } else {
        ALOGE("%s: buffer to be enqueued is not locked", __FUNCTION__);
	    //mHalCamCtrl->mPreviewMemoryLock.unlock();
        //return -EINVAL;
    }
  err = this->mPreviewWindow->enqueue_buffer(this->mPreviewWindow,
        (buffer_handle_t *)mHalCamCtrl->mPreviewMemory.buffer_handle[frame->def.idx]);
  if(err != 0) {
    ALOGE("%s: enqueue_buffer failed, err = %d", __func__, err);
  } else {
   ALOGV("%s: enqueue_buffer hdl=%p", __func__, *mHalCamCtrl->mPreviewMemory.buffer_handle[frame->def.idx]);
    mHalCamCtrl->mPreviewMemory.local_flag[frame->def.idx] = BUFFER_NOT_OWNED;
  }
  buffer_handle_t *buffer_handle = NULL;
  int tmp_stride = 0;
  err = this->mPreviewWindow->dequeue_buffer(this->mPreviewWindow,
              &buffer_handle, &tmp_stride);
  if (err == NO_ERROR && buffer_handle != NULL) {

    ALOGV("%s: dequed buf hdl =%p", __func__, *buffer_handle);
    for(i = 0; i < mHalCamCtrl->mPreviewMemory.buffer_count; i++) {
        if(mHalCamCtrl->mPreviewMemory.buffer_handle[i] == buffer_handle) {
          mHalCamCtrl->mPreviewMemory.local_flag[i] = BUFFER_UNLOCKED;
          break;
        }
    }
     if (i < mHalCamCtrl->mPreviewMemory.buffer_count ) {
      err = this->mPreviewWindow->lock_buffer(this->mPreviewWindow, buffer_handle);
      ALOGV("%s: camera call genlock_lock: hdl =%p", __FUNCTION__, *buffer_handle);
      if (GENLOCK_FAILURE == genlock_lock_buffer((native_handle_t*)(*buffer_handle), GENLOCK_WRITE_LOCK,
                                                 GENLOCK_MAX_TIMEOUT)) {
            ALOGE("%s: genlock_lock_buffer(WRITE) failed", __FUNCTION__);
	    //mHalCamCtrl->mPreviewMemoryLock.unlock();
           // return -EINVAL;
      } else  {
        mHalCamCtrl->mPreviewMemory.local_flag[i] = BUFFER_LOCKED;

        if (mHalCamCtrl->mPreviewMemory.enqueued_flag[i]) {
            // buffer is already queued, so buf_done will take care of the enqueue
            if(MM_CAMERA_OK != cam_evt_buf_done(mCameraId, &mNotifyBuffer[i])) {
                ALOGE("BUF DONE FAILED");
            }
        } else {
            // Not enqueued before, fresh enqueue
            ALOGD("%s: Not enqueued before, fresh enqueue", __FUNCTION__);
            mm_camera_reg_buf_t reg_buf;
            memset(&reg_buf, 0, sizeof(mm_camera_reg_buf_t));
            reg_buf.ch_type = MM_CAMERA_CH_PREVIEW;
            reg_buf.preview.num = 1;
            reg_buf.preview.buf.mp = &mDisplayBuf.preview.buf.mp[i];
            if(MM_CAMERA_OK != cam_config_enqueue_buf(mCameraId, &reg_buf)) {
                ALOGE("ENQUEUE FAILED");
            } else {
                mHalCamCtrl->mPreviewMemory.enqueued_flag[i] = TRUE;
            }
        }
      }
     }
  } else
      ALOGE("%s: error in dequeue_buffer, enqueue_buffer idx = %d, no free buffer now", __func__, frame->def.idx);
  /* Save the last displayed frame. We'll be using it to fill the gap between
     when preview stops and postview start during snapshot.*/
  mLastQueuedFrame = &(mDisplayStreamBuf.frame[frame->def.idx]);
  mHalCamCtrl->mPreviewMemoryLock.unlock();

  mHalCamCtrl->mCallbackLock.lock();
  camera_data_callback pcb = mHalCamCtrl->mDataCb;
  mHalCamCtrl->mCallbackLock.unlock();
  ALOGV("Message enabled = 0x%x", mHalCamCtrl->mMsgEnabled);

  camera_memory_t *previewMem = NULL;
  int previewWidth, previewHeight;
  mHalCamCtrl->mParameters.getPreviewSize(&previewWidth, &previewHeight);

  if (pcb != NULL) {
      //Sending preview callback if corresponding Msgs are enabled
      if(mHalCamCtrl->mMsgEnabled & CAMERA_MSG_PREVIEW_FRAME) {
          msgType |=  CAMERA_MSG_PREVIEW_FRAME;
          int previewBufSize;
          /* The preview buffer size sent back in the callback should be (width*height*bytes_per_pixel)
           * As all preview formats we support, use 12 bits per pixel, buffer size = previewWidth * previewHeight * 3/2.
           * We need to put a check if some other formats are supported in future. (punits) */
          if((mHalCamCtrl->mPreviewFormat == CAMERA_YUV_420_NV21) || (mHalCamCtrl->mPreviewFormat == CAMERA_YUV_420_NV12) ||
                    (mHalCamCtrl->mPreviewFormat == CAMERA_YUV_420_YV12))
          {
              previewBufSize = previewWidth * previewHeight * 3/2;
              if(previewBufSize != mHalCamCtrl->mPreviewMemory.private_buffer_handle[frame->def.idx]->size) {
                  previewMem = mHalCamCtrl->mGetMemory(mHalCamCtrl->mPreviewMemory.private_buffer_handle[frame->def.idx]->fd,
                  previewBufSize, 1, mHalCamCtrl->mCallbackCookie);
                  if (!previewMem || !previewMem->data) {
                      ALOGE("%s: mGetMemory failed.\n", __func__);
                  } else {
                      data = previewMem;
                  }
              } else
                    data = mHalCamCtrl->mPreviewMemory.camera_memory[frame->def.idx];
          } else {
                data = mHalCamCtrl->mPreviewMemory.camera_memory[frame->def.idx];
                ALOGE("Invalid preview format, buffer size in preview callback may be wrong.");
          }
      } else {
          data = NULL;
      }

      if(mHalCamCtrl->mMsgEnabled & CAMERA_MSG_PREVIEW_METADATA){
          msgType  |= CAMERA_MSG_PREVIEW_METADATA;
          metadata = &mHalCamCtrl->mMetadata;
      } else {
          metadata = NULL;
      }
      if(msgType) {
          mStopCallbackLock.unlock();
          if(mActive)
            pcb(msgType, data, 0, metadata, mHalCamCtrl->mCallbackCookie);
          if (previewMem)
              previewMem->release(previewMem);
      }
      ALOGV("end of cb");
  }

  /* Save the last displayed frame. We'll be using it to fill the gap between
     when preview stops and postview start during snapshot.*/
  //mLastQueuedFrame = frame->def.frame;
/*
  if(MM_CAMERA_OK != cam_evt_buf_done(mCameraId, frame))
  {
      ALOGE("BUF DONE FAILED");
      return BAD_VALUE;
  }
*/
  return NO_ERROR;
}

// ---------------------------------------------------------------------------
// QCameraStream_preview
// ---------------------------------------------------------------------------

QCameraStream_preview::
QCameraStream_preview(int cameraId, camera_mode_t mode)
  : QCameraStream(cameraId,mode),
    mLastQueuedFrame(NULL),
    mbPausedBySnapshot(FALSE)
  {
    mHalCamCtrl = NULL;
    ALOGE("%s: E", __func__);
    ALOGE("%s: X", __func__);
  }
// ---------------------------------------------------------------------------
// QCameraStream_preview
// ---------------------------------------------------------------------------

QCameraStream_preview::~QCameraStream_preview() {
    ALOGV("%s: E", __func__);
	if(mActive) {
		stop();
	}
	if(mInit) {
		release();
	}
	mInit = false;
	mActive = false;
    ALOGV("%s: X", __func__);

}
// ---------------------------------------------------------------------------
// QCameraStream_preview
// ---------------------------------------------------------------------------

status_t QCameraStream_preview::init() {

  status_t ret = NO_ERROR;
  ALOGV("%s: E", __func__);

  ret = QCameraStream::initChannel (mCameraId, MM_CAMERA_CH_PREVIEW_MASK);
  if (NO_ERROR!=ret) {
    ALOGE("%s E: can't init native cammera preview ch\n",__func__);
    return ret;
  }

  ALOGE("Debug : %s : initChannel",__func__);
  /* register a notify into the mmmm_camera_t object*/
  (void) cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_PREVIEW,
                                     preview_notify_cb,
                                     MM_CAMERA_REG_BUF_CB_INFINITE,
                                     0,this);
  ALOGE("Debug : %s : cam_evt_register_buf_notify",__func__);
  buffer_handle_t *buffer_handle = NULL;
  int tmp_stride = 0;
  mInit = true;
  return ret;
}
// ---------------------------------------------------------------------------
// QCameraStream_preview
// ---------------------------------------------------------------------------

status_t QCameraStream_preview::start()
{
    ALOGV("%s: E", __func__);
    status_t ret = NO_ERROR;

    Mutex::Autolock lock(mStopCallbackLock);

    /* call start() in parent class to start the monitor thread*/
    //QCameraStream::start ();
    setFormat(MM_CAMERA_CH_PREVIEW_MASK);

    /* We do initDisplayBuffers only it's a clean start.
     * If preview is stopped because of taking picutre,
     * and resumed after snapshot is taken,
     * mbPaused will be set to TRUE already.
     */
    if (!mbPausedBySnapshot) {
        if(NO_ERROR!=initDisplayBuffers()){
            return BAD_VALUE;
        }
        ALOGE("Debug : %s : initDisplayBuffers",__func__);

        ret = cam_config_prepare_buf(mCameraId, &mDisplayBuf);
        ALOGE("Debug : %s : cam_config_prepare_buf",__func__);
        if(ret != MM_CAMERA_OK) {
            ALOGV("%s:reg preview buf err=%d\n", __func__, ret);
            ret = BAD_VALUE;
        }else {
            ret = NO_ERROR;
            /* all buffers are enqueued to kernel after cam_config_prepare_buf, 
               so set falg to TRUE */
            for (int cnt = 0; cnt < mDisplayBuf.preview.num; cnt++) {
                mHalCamCtrl->mPreviewMemory.enqueued_flag[cnt] = TRUE;
            }
        }
    } else {
        /* This is a start case resumed from snapshot */
        if(NO_ERROR!=reinitDisplayBuffers()){
            return BAD_VALUE;
        }

        /* Request buffer numbers */
        ret = cam_config_request_buf(mCameraId, &mDisplayBuf);
        if(ret != MM_CAMERA_OK) {
            ALOGE("%s:request preview buf err=%d\n", __func__, ret);
            ret = BAD_VALUE;
        }else {
            /* For each buffer that is locked by HAL, if it's not enqueued before,
             * we need to enquque to kernel
             */
            int enqueued_buf_num = 0;
            for (int cnt = 0; cnt < mHalCamCtrl->mPreviewMemory.buffer_count; cnt++) {
                if( (mHalCamCtrl->mPreviewMemory.local_flag[cnt] == BUFFER_LOCKED) &&
                    (mHalCamCtrl->mPreviewMemory.enqueued_flag[cnt] == FALSE) ) {
                    mm_camera_reg_buf_t reg_buf;
                    memset(&reg_buf, 0, sizeof(mm_camera_reg_buf_t));
                    reg_buf.ch_type = MM_CAMERA_CH_PREVIEW;
                    reg_buf.preview.num = 1;
                    reg_buf.preview.buf.mp = &mDisplayBuf.preview.buf.mp[cnt];
                    ALOGD("%s:enqueue preview buf (%d) index = %d\n", __func__, cnt, reg_buf.preview.buf.mp[0].idx);
                    ret = cam_config_enqueue_buf(mCameraId, &reg_buf);
                    if(ret != MM_CAMERA_OK) {
                        ALOGE("%s:enqueue preview buf err=%d\n", __func__, ret);
                        ret = BAD_VALUE;
                    }else {
                        ret = NO_ERROR;
                        mHalCamCtrl->mPreviewMemory.enqueued_flag[cnt] = TRUE;
                        enqueued_buf_num++;
                    }
                }
            }
            if (enqueued_buf_num < 3) {
                ALOGE("%s: enqueued preview buf number = %d , less than 3, return error\n", __func__, enqueued_buf_num);
                return BAD_VALUE;
            }
        }

        /* reset the paused flag to FALSE after preview stream started*/
        mbPausedBySnapshot = FALSE;
    }

	/* For preview, the OP_MODE we set is dependent upon whether we are
       starting camera or camcorder. For snapshot, anyway we disable preview.
       However, for ZSL we need to set OP_MODE to OP_MODE_ZSL and not
       OP_MODE_VIDEO. We'll set that for now in CamCtrl. So in case of
       ZSL we skip setting Mode here */

    if (!(myMode & CAMERA_ZSL_MODE)) {
        ALOGE("Setting OP MODE to MM_CAMERA_OP_MODE_VIDEO");
        mm_camera_op_mode_type_t op_mode=MM_CAMERA_OP_MODE_VIDEO;
        ret = cam_config_set_parm (mCameraId, MM_CAMERA_PARM_OP_MODE,
                                        &op_mode);
        ALOGE("OP Mode Set");

        if(MM_CAMERA_OK != ret) {
          ALOGE("%s: X :set mode MM_CAMERA_OP_MODE_VIDEO err=%d\n", __func__, ret);
          return BAD_VALUE;
        }
    }else {
        ALOGE("Setting OP MODE to MM_CAMERA_OP_MODE_ZSL");
        mm_camera_op_mode_type_t op_mode=MM_CAMERA_OP_MODE_ZSL;
        ret = cam_config_set_parm (mCameraId, MM_CAMERA_PARM_OP_MODE,
                                        &op_mode);
        if(MM_CAMERA_OK != ret) {
          ALOGE("%s: X :set mode MM_CAMERA_OP_MODE_ZSL err=%d\n", __func__, ret);
          return BAD_VALUE;
        }
     }

    /* call mm_camera action start(...)  */
    ALOGE("Starting Preview/Video Stream. ");
    ret = cam_ops_action(mCameraId, TRUE, MM_CAMERA_OPS_PREVIEW, 0);

    if (MM_CAMERA_OK != ret) {
      ALOGE ("%s: preview streaming start err=%d\n", __func__, ret);
      return BAD_VALUE;
    }

    ALOGE("Debug : %s : Preview streaming Started",__func__);
    ret = NO_ERROR;

    mActive =  true;
    ALOGE("%s: X", __func__);
    return NO_ERROR;
  }


// ---------------------------------------------------------------------------
// QCameraStream_preview
// ---------------------------------------------------------------------------
  void QCameraStream_preview::stop() {
    ALOGE("%s: E", __func__);
    int ret=MM_CAMERA_OK;

    if(!mActive) {
      return;
    }
    Mutex::Autolock lock(mStopCallbackLock);
    mActive =  false;
    /* unregister the notify fn from the mmmm_camera_t object*/

    ALOGI("%s: Stop the thread \n", __func__);
    /* In zsl mode this is done when the shapshot channel stops to avoid an iommu page fault*/
    if (!((myMode & CAMERA_ZSL_MODE) && !mHalCamCtrl->mZslFlashEnable))
      ret = cam_ops_action(mCameraId, FALSE, MM_CAMERA_OPS_PREVIEW, 0);
    if(MM_CAMERA_OK != ret) {
      ALOGE ("%s: camera preview stop err=%d\n", __func__, ret);
    }
    ret = cam_config_unprepare_buf(mCameraId, MM_CAMERA_CH_PREVIEW);
    if(ret != MM_CAMERA_OK) {
      ALOGE("%s:Unreg preview buf err=%d\n", __func__, ret);
      //ret = BAD_VALUE;
    }

    if (!mbPausedBySnapshot) {
        /* In case of a clean stop, we need to clean all buffers*/
        ALOGE("Debug : %s : Buffer Unprepared",__func__);
        if (mDisplayBuf.preview.buf.mp != NULL) {
            delete[] mDisplayBuf.preview.buf.mp;
        }

        /*free camera_memory handles and return buffer back to surface*/
        putBufferToSurface();
    }

    ALOGE("%s: X", __func__);

  }
// ---------------------------------------------------------------------------
// QCameraStream_preview
// ---------------------------------------------------------------------------
  void QCameraStream_preview::release() {

    ALOGE("%s : BEGIN",__func__);
    int ret=MM_CAMERA_OK,i;

    if(!mInit)
    {
      ALOGE("%s : Stream not Initalized",__func__);
      return;
    }

    if(mActive) {
      this->stop();
    }

    ret= QCameraStream::deinitChannel(mCameraId, MM_CAMERA_CH_PREVIEW);
    ALOGE("Debug : %s : De init Channel",__func__);
    if(ret != MM_CAMERA_OK) {
      ALOGE("%s:Deinit preview channel failed=%d\n", __func__, ret);
      //ret = BAD_VALUE;
    }

    (void)cam_evt_register_buf_notify(mCameraId, MM_CAMERA_CH_PREVIEW,
                                      NULL,
                                      (mm_camera_register_buf_cb_type_t)NULL,
                                      0,
                                      NULL);
    mInit = false;
    ALOGE("%s: END", __func__);

  }

QCameraStream*
QCameraStream_preview::createInstance(int cameraId,
                                      camera_mode_t mode)
{
  QCameraStream* pme = new QCameraStream_preview(cameraId, mode);
  return pme;
}
// ---------------------------------------------------------------------------
// QCameraStream_preview
// ---------------------------------------------------------------------------

void QCameraStream_preview::deleteInstance(QCameraStream *p)
{
  if (p){
    ALOGV("%s: BEGIN", __func__);
    p->release();
    delete p;
    p = NULL;
    ALOGV("%s: END", __func__);
  }
}


/* Temp helper function */
void *QCameraStream_preview::getLastQueuedFrame(void)
{
    return mLastQueuedFrame;
}

status_t QCameraStream_preview::sendMappingBuf(int ext_mode, int idx, int fd, uint32_t size)
{
/*    cam_sock_packet_t packet;
    memset(&packet, 0, sizeof(cam_sock_packet_t));
    packet.msg_type = CAM_SOCK_MSG_TYPE_FD_MAPPING;
    packet.payload.frame_fd_map.ext_mode = ext_mode;
    packet.payload.frame_fd_map.frame_idx = idx;
    packet.payload.frame_fd_map.fd = fd;
    packet.payload.frame_fd_map.size = size;

    if ( cam_ops_sendmsg(mCameraId, &packet, sizeof(cam_sock_packet_t), packet.payload.frame_fd_map.fd) <= 0 ) {
        ALOGE("%s: sending frame mapping buf msg Failed", __func__);
        return FAILED_TRANSACTION;
    }
*/
    return NO_ERROR;
}

status_t QCameraStream_preview::sendUnMappingBuf(int ext_mode, int idx)
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

/* Set preview pause flag */
void QCameraStream_preview::setPreviewPauseFlag(bool bPaused)
{
    mbPausedBySnapshot = bPaused;
}

// ---------------------------------------------------------------------------
// No code beyone this line
// ---------------------------------------------------------------------------
}; // namespace android
