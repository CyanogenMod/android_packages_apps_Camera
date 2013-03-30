/*
Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of Code Aurora Forum, Inc. nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <pthread.h>
#include "mm_camera_dbg.h"
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <poll.h>
#include <time.h>

#include "mm_camera_interface2.h"
#include "mm_camera.h"

static void mm_camera_stream_util_set_state(mm_camera_stream_t *stream,
                         mm_camera_stream_state_type_t state);

int mm_camera_stream_init_q(mm_camera_frame_queue_t *q)
{
    pthread_mutex_init(&q->mutex, NULL);
    return MM_CAMERA_OK;
}
int mm_camera_stream_deinit_q(mm_camera_frame_queue_t *q)
{
    pthread_mutex_destroy(&q->mutex);
    return MM_CAMERA_OK;
}

int mm_camera_stream_frame_get_q_cnt(mm_camera_frame_queue_t *q)
{
    int cnt;
    pthread_mutex_lock(&q->mutex);
    cnt = q->cnt;
    pthread_mutex_unlock(&q->mutex);
    return cnt;
}

mm_camera_frame_t *mm_camera_stream_frame_deq_no_lock(mm_camera_frame_queue_t *q)
{
    mm_camera_frame_t *tmp;

    tmp = q->head;

    if(tmp == NULL) goto end;
    if(q->head == q->tail) {
        q->head = NULL;
        q->tail = NULL;
    } else {
        q->head = tmp->next;
    }
    tmp->next = NULL;
    q->cnt--;
end:
    return tmp;
}

void mm_camera_stream_frame_enq_no_lock(mm_camera_frame_queue_t *q, mm_camera_frame_t *node)
{
    node->next = NULL;
    if(q->head == NULL) {
        q->head = node;
        q->tail = node;
    } else {
        q->tail->next = node;
        q->tail = node;
    }
    q->cnt++;
}

mm_camera_frame_t *mm_camera_stream_frame_deq(mm_camera_frame_queue_t *q)
{
    mm_camera_frame_t *tmp;

    pthread_mutex_lock(&q->mutex);
    tmp = q->head;

    if(tmp == NULL) goto end;
    if(q->head == q->tail) {
        q->head = NULL;
        q->tail = NULL;
    } else {
        q->head = tmp->next;
    }
    tmp->next = NULL;
    q->cnt--;
end:
    pthread_mutex_unlock(&q->mutex);
    return tmp;
}

void mm_camera_stream_frame_enq(mm_camera_frame_queue_t *q, mm_camera_frame_t *node)
{
    pthread_mutex_lock(&q->mutex);
    node->next = NULL;
    if(q->head == NULL) {
        q->head = node;
        q->tail = node;
    } else {
        q->tail->next = node;
        q->tail = node;
    }
    q->cnt++;
    pthread_mutex_unlock(&q->mutex);
}

void mm_stream_frame_flash_q(mm_camera_frame_queue_t *q)
{
    pthread_mutex_lock(&q->mutex);
    q->cnt = 0;
    q->match_cnt = 0;
    q->head = NULL;
    q->tail = NULL;
    pthread_mutex_unlock(&q->mutex);
}

void mm_camera_stream_frame_refill_q(mm_camera_frame_queue_t *q, mm_camera_frame_t *node, int num)
{
    int i;

    mm_stream_frame_flash_q(q);
    for(i = 0; i < num; i++)
        mm_camera_stream_frame_enq(q, &node[i]);
    CDBG("%s: q=0x%x, num = %d, q->cnt=%d\n",
             __func__,(uint32_t)q,num, mm_camera_stream_frame_get_q_cnt(q));
}

void mm_camera_stream_deinit_frame(mm_camera_stream_frame_t *frame)
{
    pthread_mutex_destroy(&frame->mutex);
    mm_camera_stream_deinit_q(&frame->readyq);
    memset(frame, 0, sizeof(mm_camera_stream_frame_t));
}

void mm_camera_stream_init_frame(mm_camera_stream_frame_t *frame)
{
    memset(frame, 0, sizeof(mm_camera_stream_frame_t));
    pthread_mutex_init(&frame->mutex, NULL);
    mm_camera_stream_init_q(&frame->readyq);
}

void mm_camera_stream_release(mm_camera_stream_t *stream)
{
    mm_camera_stream_deinit_frame(&stream->frame);
    if(stream->fd > 0) close(stream->fd);
    memset(stream, 0, sizeof(*stream));
    //stream->fd = -1;
    mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_NOTUSED);
}

int mm_camera_stream_is_active(mm_camera_stream_t *stream)
{
    return (stream->state == MM_CAMERA_STREAM_STATE_ACTIVE)? TRUE : FALSE;
}

static void mm_camera_stream_util_set_state(mm_camera_stream_t *stream,
                                 mm_camera_stream_state_type_t state)
{
    CDBG("%s:stream fd=%d, stream type=%d, cur_state=%d,new_state=%d\n",
      __func__, stream->fd, stream->stream_type, stream->state, state);
    stream->state = state;
}

int mm_camera_read_msm_frame(mm_camera_obj_t * my_obj,
                        mm_camera_stream_t *stream)
{
    int idx = -1, rc = MM_CAMERA_OK;
    struct v4l2_buffer vb;
    struct v4l2_plane planes[VIDEO_MAX_PLANES];

    memset(&vb,  0,  sizeof(vb));
    vb.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    vb.memory = V4L2_MEMORY_USERPTR;
    vb.m.planes = &planes[0];

    CDBG("%s: VIDIOC_DQBUF ioctl call\n", __func__);
    rc = ioctl(stream->fd, VIDIOC_DQBUF, &vb);
    if (rc < 0)
        return idx;
    idx = vb.index;
    stream->frame.frame[idx].frame.frame_id = vb.sequence;
    stream->frame.frame[idx].frame.ts.tv_sec  = vb.timestamp.tv_sec;
    stream->frame.frame[idx].frame.ts.tv_nsec = vb.timestamp.tv_usec * 1000;
    return idx;
}

static int mm_camera_stream_util_proc_get_crop(mm_camera_obj_t *my_obj,
                            mm_camera_stream_t *stream,
                            mm_camera_rect_t *val)
{
  struct v4l2_crop crop;
  int rc = MM_CAMERA_OK;
  memset(&crop, 0, sizeof(crop));
  crop.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
  rc = ioctl(stream->fd, VIDIOC_G_CROP, &crop);
  if (rc < 0)
      return rc;
  val->left = crop.c.left;
  val->top = crop.c.top;
  val->width = crop.c.width;
  val->height = crop.c.height;
  return rc;
}

int32_t mm_camera_util_s_ctrl( int32_t fd,  uint32_t id, int32_t value)
{
    int rc = MM_CAMERA_OK;
    struct v4l2_control control;

    memset(&control, 0, sizeof(control));
    control.id = id;
    control.value = value;
    rc = ioctl (fd, VIDIOC_S_CTRL, &control);

    if(rc) {
//        CDBG("%s: fd=%d, S_CTRL, id=0x%x, value = 0x%x, rc = %ld\n",
//                 __func__, fd, id, (uint32_t)value, rc);
        rc = MM_CAMERA_E_GENERAL;
    }
    return rc;
}

int32_t mm_camera_util_g_ctrl( int32_t fd, uint32_t id, int32_t *value)
{
    int rc = MM_CAMERA_OK;
  struct v4l2_control control;

    memset(&control, 0, sizeof(control));
    control.id = id;
    control.value = (int32_t)value;
    rc = ioctl (fd, VIDIOC_G_CTRL, &control);
    if(rc) {
        CDBG("%s: fd=%d, G_CTRL, id=0x%x, rc = %d\n", __func__, fd, id, rc);
        rc = MM_CAMERA_E_GENERAL;
    }
    *value = control.value;
    return rc;
}

static uint32_t mm_camera_util_get_v4l2_fmt(cam_format_t fmt,
                                            uint8_t *num_planes)
{
    uint32_t val;
    switch(fmt) {
    case CAMERA_YUV_420_NV12:
        val = V4L2_PIX_FMT_NV12;
        *num_planes = 2;
        break;
    case CAMERA_YUV_420_NV21:
        val = V4L2_PIX_FMT_NV21;
        *num_planes = 2;
        break;
    case CAMERA_BAYER_SBGGR10:
        val= V4L2_PIX_FMT_SBGGR10;
        *num_planes = 1;
        break;
    case CAMERA_YUV_422_NV61:
        val= V4L2_PIX_FMT_NV61;
        *num_planes = 2;
        break;
    default:
        val = 0;
        *num_planes = 0;
        break;
    }
    return val;
}

static int mm_camera_stream_util_set_ext_mode(mm_camera_stream_t *stream)
{
    int rc = 0;
    struct v4l2_streamparm s_parm;
    s_parm.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        switch(stream->stream_type) {
        case MM_CAMERA_STREAM_PREVIEW:
            s_parm.parm.capture.extendedmode = MSM_V4L2_EXT_CAPTURE_MODE_PREVIEW;
            break;
        case MM_CAMERA_STREAM_SNAPSHOT:
            s_parm.parm.capture.extendedmode = MSM_V4L2_EXT_CAPTURE_MODE_MAIN;
            break;
        case MM_CAMERA_STREAM_THUMBNAIL:
            s_parm.parm.capture.extendedmode = MSM_V4L2_EXT_CAPTURE_MODE_THUMBNAIL;
            break;
        case MM_CAMERA_STREAM_VIDEO:
            s_parm.parm.capture.extendedmode = MSM_V4L2_EXT_CAPTURE_MODE_VIDEO;
            break;
        case MM_CAMERA_STREAM_RAW:
                s_parm.parm.capture.extendedmode = MSM_V4L2_EXT_CAPTURE_MODE_MAIN; //MSM_V4L2_EXT_CAPTURE_MODE_RAW;
                break;
        case MM_CAMERA_STREAM_VIDEO_MAIN:
        default:
            return 0;
        }

    rc = ioctl(stream->fd, VIDIOC_S_PARM, &s_parm);
        CDBG("%s:stream fd=%d,type=%d,rc=%d,extended_mode=%d\n",
                 __func__, stream->fd, stream->stream_type, rc,
                 s_parm.parm.capture.extendedmode);
    return rc;
}

static int mm_camera_util_set_op_mode(int fd, int opmode)
{
    int rc = 0;
    struct v4l2_control s_ctrl;
    s_ctrl.id = MSM_V4L2_PID_CAM_MODE;
    s_ctrl.value = opmode;

        rc = ioctl(fd, VIDIOC_S_CTRL, &s_ctrl);
    if (rc < 0)
        CDBG("%s: VIDIOC_S_CTRL failed, rc=%d\n",
                         __func__, rc);
    return rc;
}

int mm_camera_stream_qbuf(mm_camera_obj_t * my_obj, mm_camera_stream_t *stream,
  int idx)
{
  int32_t i, rc = MM_CAMERA_OK;
  int *ret;
  struct v4l2_buffer buffer;

  memset(&buffer, 0, sizeof(buffer));
  buffer.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
  buffer.memory = V4L2_MEMORY_USERPTR;
  buffer.index = idx;
  buffer.m.planes = &(stream->frame.frame[idx].planes[0]);
  buffer.length = stream->frame.frame[idx].num_planes;

  CDBG("%s Ref : PREVIEW=%d VIDEO=%d SNAPSHOT=%d THUMB=%d ", __func__,
    MM_CAMERA_STREAM_PREVIEW, MM_CAMERA_STREAM_VIDEO,
    MM_CAMERA_STREAM_SNAPSHOT, MM_CAMERA_STREAM_THUMBNAIL);
  CDBG("%s:fd=%d,type=%d,frame idx=%d,num planes %d\n", __func__,
    stream->fd, stream->stream_type, idx, buffer.length);

  rc = ioctl(stream->fd, VIDIOC_QBUF, &buffer);
  if (rc < 0) {
      CDBG_ERROR("%s: VIDIOC_QBUF error = %d, stream type=%d\n", __func__, rc, stream->stream_type);
      return rc;
  }
  CDBG("%s: X idx: %d, stream_type:%d", __func__, idx, stream->stream_type);
  return rc;
}

/* This function let kernel know amount of buffers will be registered */
static int mm_camera_stream_util_request_buf(mm_camera_obj_t * my_obj,
                      mm_camera_stream_t *stream,
                      int8_t buf_num)
{
    int32_t rc = MM_CAMERA_OK;
    struct v4l2_requestbuffers bufreq;

    if(buf_num > MM_CAMERA_MAX_NUM_FRAMES) {
        rc = -MM_CAMERA_E_GENERAL;
        CDBG("%s: buf num %d > max limit %d\n",
                 __func__, buf_num, MM_CAMERA_MAX_NUM_FRAMES);
        goto end;
    }

    memset(&bufreq, 0, sizeof(bufreq));
    bufreq.count = buf_num;
    bufreq.type  = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    bufreq.memory = V4L2_MEMORY_USERPTR;
    rc = ioctl(stream->fd, VIDIOC_REQBUFS, &bufreq);
    if (rc < 0) {
      CDBG("%s: fd=%d, ioctl VIDIOC_REQBUFS failed: rc=%d\n",
        __func__, stream->fd, rc);
      goto end;
    }
    ALOGE("%s: stream fd=%d, ioctl VIDIOC_REQBUFS: memtype = %d, num_frames = %d, rc=%d\n",
        __func__, stream->fd, bufreq.memory, bufreq.count, rc);

end:
    return rc;
}

/* This function enqueue existing buffers (not first time allocated buffers from Surface) to kernel */
static int mm_camera_stream_util_enqueue_buf(mm_camera_obj_t * my_obj,
                      mm_camera_stream_t *stream,
                      mm_camera_buf_def_t *vbuf)
{
    int32_t i, rc = MM_CAMERA_OK, j;

    if(vbuf->num > MM_CAMERA_MAX_NUM_FRAMES) {
        rc = -MM_CAMERA_E_GENERAL;
        CDBG("%s: buf num %d > max limit %d\n",
                 __func__, vbuf->num, MM_CAMERA_MAX_NUM_FRAMES);
        goto end;
    }

    for(i = 0; i < vbuf->num; i++){
        int idx = vbuf->buf.mp[i].idx;
        ALOGE("%s: enqueue buf index = %d\n",__func__, idx);
        if(idx < MM_CAMERA_MAX_NUM_FRAMES) {
            ALOGE("%s: stream_fd = %d, frame_fd = %d, frame ID = %d, offset = %d\n",
                     __func__, stream->fd, stream->frame.frame[i].frame.fd,
                     idx, stream->frame.frame_offset[idx]);
            rc = mm_camera_stream_qbuf(my_obj, stream, stream->frame.frame[idx].idx);
            if (rc < 0) {
                CDBG("%s: VIDIOC_QBUF rc = %d\n", __func__, rc);
                goto end;
            }
            stream->frame.ref_count[idx] = 0;
        }
    }
    stream->frame.qbuf = 1;
end:
    return rc;
}

static int mm_camera_stream_util_reg_buf(mm_camera_obj_t * my_obj,
                      mm_camera_stream_t *stream,
                      mm_camera_buf_def_t *vbuf)
{
    int32_t i, rc = MM_CAMERA_OK, j;
    int *ret;
    struct v4l2_requestbuffers bufreq;
    int image_type;
    uint8_t num_planes;
    uint32_t planes[VIDEO_MAX_PLANES];

    if(vbuf->num > MM_CAMERA_MAX_NUM_FRAMES) {
        rc = -MM_CAMERA_E_GENERAL;
        CDBG_ERROR("%s: buf num %d > max limit %d\n",
                 __func__, vbuf->num, MM_CAMERA_MAX_NUM_FRAMES);
        goto end;
    }
    switch(stream->stream_type) {
    case MM_CAMERA_STREAM_PREVIEW:
      image_type = OUTPUT_TYPE_P;
      break;
    case MM_CAMERA_STREAM_SNAPSHOT:
    case MM_CAMERA_STREAM_RAW:
      image_type = OUTPUT_TYPE_S;
      break;
    case MM_CAMERA_STREAM_THUMBNAIL:
      image_type = OUTPUT_TYPE_T;
      break;
    case MM_CAMERA_STREAM_VIDEO:
    default:
      image_type = OUTPUT_TYPE_V;
      break;
    }
    stream->frame.frame_len = mm_camera_get_msm_frame_len(stream->cam_fmt,
                              my_obj->current_mode,
                              stream->fmt.fmt.pix.width,
                              stream->fmt.fmt.pix.height,
                              image_type, &num_planes, planes);
    if(stream->frame.frame_len == 0) {
        CDBG_ERROR("%s:incorrect frame size = %d\n", __func__, stream->frame.frame_len);
        rc = -1;
        goto end;
    }
    stream->frame.num_frame = vbuf->num;
    bufreq.count = stream->frame.num_frame;
    bufreq.type  = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    bufreq.memory = V4L2_MEMORY_USERPTR;
    CDBG("%s: calling VIDIOC_REQBUFS - fd=%d, num_buf=%d, type=%d, memory=%d\n",
             __func__,stream->fd, bufreq.count, bufreq.type, bufreq.memory);
    rc = ioctl(stream->fd, VIDIOC_REQBUFS, &bufreq);
    if (rc < 0) {
      CDBG_ERROR("%s: fd=%d, ioctl VIDIOC_REQBUFS failed: rc=%d\n",
        __func__, stream->fd, rc);
      goto end;
    }
    CDBG("%s: stream fd=%d, ioctl VIDIOC_REQBUFS: memtype = %d,"
      "num_frames = %d, rc=%d\n", __func__, stream->fd, bufreq.memory,
      bufreq.count, rc);

    for(i = 0; i < vbuf->num; i++){
        vbuf->buf.mp[i].idx = i; /* remember the index to stream frame if first time qbuf */
        memcpy(&stream->frame.frame[i].frame, &(vbuf->buf.mp[i].frame),
                     sizeof(vbuf->buf.mp[i].frame));
        stream->frame.frame[i].idx = i;
        stream->frame.frame[i].num_planes = vbuf->buf.mp[i].num_planes;
        for(j = 0; j < vbuf->buf.mp[i].num_planes; j++) {
            stream->frame.frame[i].planes[j] = vbuf->buf.mp[i].planes[j];
        }

        if(vbuf->buf.mp[i].frame_offset) {
            stream->frame.frame_offset[i] = vbuf->buf.mp[i].frame_offset;
        } else {
            stream->frame.frame_offset[i] = 0;
        }

        rc = mm_camera_stream_qbuf(my_obj, stream, stream->frame.frame[i].idx);
        if (rc < 0) {
            CDBG_ERROR("%s: VIDIOC_QBUF rc = %d\n", __func__, rc);
            goto end;
        }
        stream->frame.ref_count[i] = 0;
        CDBG("%s: stream_fd = %d, frame_fd = %d, frame ID = %d, offset = %d\n",
          __func__, stream->fd, stream->frame.frame[i].frame.fd,
          i, stream->frame.frame_offset[i]);
    }
    stream->frame.qbuf = 1;
end:
    return rc;
}
static int mm_camera_stream_util_unreg_buf(mm_camera_obj_t * my_obj,
                          mm_camera_stream_t *stream)
{
    struct v4l2_requestbuffers bufreq;
    int32_t i, rc = MM_CAMERA_OK;

    bufreq.count = 0;
    bufreq.type  = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    bufreq.memory = V4L2_MEMORY_USERPTR;
    rc = ioctl(stream->fd, VIDIOC_REQBUFS, &bufreq);
    if (rc < 0) {
        CDBG_ERROR("%s: fd=%d, VIDIOC_REQBUFS failed, rc=%d\n",
              __func__, stream->fd, rc);
        return rc;
    }
    mm_stream_frame_flash_q(&stream->frame.readyq);
    memset(stream->frame.ref_count,0,(stream->frame.num_frame * sizeof(int8_t)));
    stream->frame.qbuf = 0;
    CDBG("%s:fd=%d,type=%d,rc=%d\n", __func__, stream->fd,
      stream->stream_type, rc);
    return rc;
}

static int32_t mm_camera_stream_fsm_notused(mm_camera_obj_t * my_obj,
                         mm_camera_stream_t *stream,
                         mm_camera_state_evt_type_t evt, void *val)
{
    int32_t rc = 0;
    char dev_name[MM_CAMERA_DEV_NAME_LEN];

    switch(evt) {
    case MM_CAMERA_STATE_EVT_ACQUIRE:
        snprintf(dev_name, sizeof(dev_name), "/dev/%s", mm_camera_util_get_dev_name(my_obj));
        CDBG("%s: open dev '%s', stream type = %d\n",
                 __func__, dev_name, *((mm_camera_stream_type_t *)val));
        stream->fd = open(dev_name, O_RDWR | O_NONBLOCK);
        if(stream->fd <= 0){
            CDBG("%s: open dev returned %d\n", __func__, stream->fd);
            return -1;
        }
        stream->stream_type = *((mm_camera_stream_type_t *)val);
        rc = mm_camera_stream_util_set_ext_mode(stream);
        CDBG("%s: fd=%d, stream type=%d, mm_camera_stream_util_set_ext_mode() err=%d\n",
                 __func__, stream->fd, stream->stream_type, rc);
        if(rc == MM_CAMERA_OK) {
            mm_camera_stream_init_frame(&stream->frame);
            mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_ACQUIRED);
        } else if(stream->fd > 0) {
            close(stream->fd);
            stream->fd = 0;
        }
        break;
    default:
        CDBG_ERROR("%s: Invalid evt=%d, stream_state=%d", __func__, evt,
          stream->state);
        return -1;
    }
    return rc;
}

static int32_t mm_camera_stream_util_proc_fmt(mm_camera_obj_t *my_obj,
    mm_camera_stream_t *stream,
    mm_camera_image_fmt_t *fmt)
{
    int32_t rc = MM_CAMERA_OK;

    if(fmt->dim.width == 0 || fmt->dim.height == 0) {
        rc = -MM_CAMERA_E_INVALID_INPUT;
        CDBG("%s:invalid input[w=%d,h=%d,fmt=%d]\n",
                 __func__, fmt->dim.width, fmt->dim.height, fmt->fmt);
        goto end;
    }
    CDBG("%s: dw=%d,dh=%d,vw=%d,vh=%d,pw=%d,ph=%d,tw=%d,th=%d,raw_w=%d,raw_h=%d,fmt=%d\n",
       __func__,
       my_obj->dim.display_width,my_obj->dim.display_height,
       my_obj->dim.video_width,my_obj->dim.video_height,
       my_obj->dim.picture_width,my_obj->dim.picture_height,
       my_obj->dim.ui_thumbnail_width,my_obj->dim.ui_thumbnail_height,
       my_obj->dim.raw_picture_width,my_obj->dim.raw_picture_height,fmt->fmt);
    stream->cam_fmt = fmt->fmt;
    stream->fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    stream->fmt.fmt.pix_mp.width = fmt->dim.width;
    stream->fmt.fmt.pix_mp.height= fmt->dim.height;
    stream->fmt.fmt.pix_mp.field = V4L2_FIELD_NONE;
    stream->fmt.fmt.pix_mp.pixelformat =
            mm_camera_util_get_v4l2_fmt(stream->cam_fmt,
                                      &(stream->fmt.fmt.pix_mp.num_planes));
    rc = ioctl(stream->fd, VIDIOC_S_FMT, &stream->fmt);
    if (rc < 0) {
        CDBG("%s: ioctl VIDIOC_S_FMT failed: rc=%d\n", __func__, rc);
        rc = -MM_CAMERA_E_GENERAL;
    }
end:
    CDBG("%s:fd=%d,type=%d,rc=%d\n",
             __func__, stream->fd, stream->stream_type, rc);
    return rc;
}
static int32_t mm_camera_stream_fsm_acquired(mm_camera_obj_t * my_obj,
                  mm_camera_stream_t *stream,
                  mm_camera_state_evt_type_t evt, void *val)
{
    int32_t rc = 0;

    switch(evt) {
    case MM_CAMERA_STATE_EVT_SET_FMT:
        rc = mm_camera_stream_util_proc_fmt(my_obj,stream,
                    (mm_camera_image_fmt_t *)val);
        if(!rc) mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_CFG);
        break;
    case MM_CAMERA_STATE_EVT_RELEASE:
        mm_camera_stream_release(stream);
        break;
    case MM_CAMERA_STATE_EVT_GET_CROP:
      rc = mm_camera_stream_util_proc_get_crop(my_obj,stream, val);
      break;
    default:
        CDBG_ERROR("%s: Invalid evt=%d, stream_state=%d", __func__, evt,
          stream->state);
        return -1;
    }
    return rc;
}
static int32_t mm_camera_stream_fsm_cfg(mm_camera_obj_t * my_obj,
                         mm_camera_stream_t *stream,
                         mm_camera_state_evt_type_t evt, void *val)
{
    int32_t rc = 0;
    switch(evt) {
    case MM_CAMERA_STATE_EVT_RELEASE:
        mm_camera_stream_release(stream);
        break;
    case MM_CAMERA_STATE_EVT_SET_FMT:
        rc = mm_camera_stream_util_proc_fmt(my_obj,stream,
                    (mm_camera_image_fmt_t *)val);
        break;
    case MM_CAMERA_STATE_EVT_REG_BUF:
        rc = mm_camera_stream_util_reg_buf(my_obj, stream, (mm_camera_buf_def_t *)val);
        if(!rc) mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_REG);
        break;
    case MM_CAMERA_STATE_EVT_GET_CROP:
      rc = mm_camera_stream_util_proc_get_crop(my_obj,stream, val);
      break;
    case MM_CAMERA_STATE_EVT_REQUEST_BUF:
        rc = mm_camera_stream_util_request_buf(my_obj, stream, ((mm_camera_buf_def_t *)val)->num);
        break;
    case MM_CAMERA_STATE_EVT_ENQUEUE_BUF:
        rc = mm_camera_stream_util_enqueue_buf(my_obj, stream, (mm_camera_buf_def_t *)val);
        if(!rc) mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_REG);
        break;
    default:
        CDBG_ERROR("%s: Invalid evt=%d, stream_state=%d", __func__, evt,
          stream->state);
        return -1;
    }
    return rc;
}

int32_t mm_camera_stream_util_buf_done(mm_camera_obj_t * my_obj,
                    mm_camera_stream_t *stream,
                    mm_camera_notify_frame_t *frame)
{
    int32_t rc = MM_CAMERA_OK;
    pthread_mutex_lock(&stream->frame.mutex);

    if(stream->frame.ref_count[frame->idx] == 0) {
        rc = mm_camera_stream_qbuf(my_obj, stream, frame->idx);
        CDBG_ERROR("%s: Error Trying to free second time?(idx=%d) count=%d, stream type=%d\n",
                   __func__, frame->idx, stream->frame.ref_count[frame->idx], stream->stream_type);
        rc = -1;
    }else{
        stream->frame.ref_count[frame->idx]--;
        if(0 == stream->frame.ref_count[frame->idx]) {
            CDBG("<DEBUG> : Buf done for buffer:%p:%d",stream,frame->idx);
            rc = mm_camera_stream_qbuf(my_obj, stream, frame->idx);
            if(rc < 0)
                CDBG_ERROR("%s: mm_camera_stream_qbuf(idx=%d) err=%d\n",
                     __func__, frame->idx, rc);
        }else{
            CDBG("<DEBUG> : Still ref count pending count :%d",stream->frame.ref_count[frame->idx]);
            CDBG("<DEBUG> : for buffer:%p:%d, stream type=%d",stream,frame->idx, stream->stream_type);
        }
    }

#if 0
    stream->frame.ref_count[frame->idx]--;
    if(stream->frame.ref_count[frame->idx] == 0) {
        CDBG("%s: Queue the buffer (idx=%d) count=%d frame id = %d\n",
                 __func__, frame->idx, stream->frame.ref_count[frame->idx],
                 frame->frame->frame_id);
        rc = mm_camera_stream_qbuf(my_obj, stream, frame->idx);
        if(rc < 0)
          CDBG_ERROR("%s: mm_camera_stream_qbuf(idx=%d) err=%d\n", __func__,
            frame->idx, rc);
    } else if(stream->frame.ref_count[frame->idx] == 1) {
        ALOGE("<DEBUG> : Buf done for buffer:%p:%d",stream,frame->idx);
        rc = mm_camera_stream_qbuf(my_obj, stream, frame->idx);
        if(rc < 0)
            CDBG("%s: mm_camera_stream_qbuf(idx=%d) err=%d\n",
                 __func__, frame->idx, rc);
    } else {
        CDBG_ERROR("%s: Error Trying to free second time?(idx=%d) count=%d\n",
          __func__, frame->idx, stream->frame.ref_count[frame->idx]);
        rc = -1;
    }
#endif
    pthread_mutex_unlock(&stream->frame.mutex);
    return rc;
}

static int32_t mm_camera_stream_fsm_reg(mm_camera_obj_t * my_obj,
                             mm_camera_stream_t *stream,
                             mm_camera_state_evt_type_t evt, void *val)
{
    int32_t rc = 0;
    switch(evt) {
    case MM_CAMERA_STATE_EVT_GET_CROP:
      rc = mm_camera_stream_util_proc_get_crop(my_obj,stream, val);
      break;
    case MM_CAMERA_STATE_EVT_QBUF:
        break;
    case MM_CAMERA_STATE_EVT_RELEASE:
        mm_camera_stream_release(stream);
        break;
    case MM_CAMERA_STATE_EVT_UNREG_BUF:
        rc = mm_camera_stream_util_unreg_buf(my_obj, stream);
        if(!rc)
            mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_CFG);
        break;
    case MM_CAMERA_STATE_EVT_STREAM_ON:
        {
            enum v4l2_buf_type buf_type;
            int i = 0;
            mm_camera_frame_t *frame;
            if(stream->frame.qbuf == 0) {
                for(i = 0; i < stream->frame.num_frame; i++) {
                    rc = mm_camera_stream_qbuf(my_obj, stream,
                             stream->frame.frame[i].idx);
                    if (rc < 0) {
                        CDBG_ERROR("%s: ioctl VIDIOC_QBUF error=%d, stream->type=%d\n",
                           __func__, rc, stream->stream_type);
                        return rc;
                    }
                    stream->frame.ref_count[i] = 0;
                }
                stream->frame.qbuf = 1;
            }
            buf_type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
            CDBG("%s: STREAMON,fd=%d,stream_type=%d\n",
                     __func__, stream->fd, stream->stream_type);
            rc = ioctl(stream->fd, VIDIOC_STREAMON, &buf_type);
            if (rc < 0) {
                    CDBG_ERROR("%s: ioctl VIDIOC_STREAMON failed: rc=%d\n",
                        __func__, rc);
            }
            else
                mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_ACTIVE);
        }
        break;
    case MM_CAMERA_STATE_EVT_ENQUEUE_BUF:
        rc = mm_camera_stream_util_enqueue_buf(my_obj, stream, (mm_camera_buf_def_t *)val);
        break;
    default:
        CDBG_ERROR("%s: Invalid evt=%d, stream_state=%d", __func__, evt,
          stream->state);
        return -1;
    }
    return rc;
}
static int32_t mm_camera_stream_fsm_active(mm_camera_obj_t * my_obj,
               mm_camera_stream_t *stream,
               mm_camera_state_evt_type_t evt, void *val)
{
    int32_t rc = 0;
    switch(evt) {
    case MM_CAMERA_STATE_EVT_GET_CROP:
      rc = mm_camera_stream_util_proc_get_crop(my_obj,stream, val);
      break;
    case MM_CAMERA_STATE_EVT_QBUF:
        rc = mm_camera_stream_util_buf_done(my_obj, stream,
          (mm_camera_notify_frame_t *)val);
        break;
    case MM_CAMERA_STATE_EVT_RELEASE:
        mm_camera_stream_release(stream);
        break;
    case MM_CAMERA_STATE_EVT_STREAM_OFF:
        {
            enum v4l2_buf_type buf_type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
            CDBG("%s: STREAMOFF,fd=%d,type=%d\n",
                __func__, stream->fd, stream->stream_type);
            rc = ioctl(stream->fd, VIDIOC_STREAMOFF, &buf_type);
            if (rc < 0) {
                    CDBG_ERROR("%s: STREAMOFF failed: %s\n",
                        __func__, strerror(errno));
            }
            else {
                stream->frame.qbuf = 0;
                mm_camera_stream_util_set_state(stream, MM_CAMERA_STREAM_STATE_REG);
            }
        }
        break;
    case MM_CAMERA_STATE_EVT_ENQUEUE_BUF:
        rc = mm_camera_stream_util_enqueue_buf(my_obj, stream, (mm_camera_buf_def_t *)val);
        break;
    default:
        CDBG_ERROR("%s: Invalid evt=%d, stream_state=%d", __func__, evt,
          stream->state);
        return -1;
    }
    return rc;
}

typedef int32_t (*mm_camera_stream_fsm_fn_t) (mm_camera_obj_t * my_obj,
                    mm_camera_stream_t *stream,
                    mm_camera_state_evt_type_t evt, void *val);

static mm_camera_stream_fsm_fn_t mm_camera_stream_fsm_fn[MM_CAMERA_STREAM_STATE_MAX] = {
    mm_camera_stream_fsm_notused,
    mm_camera_stream_fsm_acquired,
    mm_camera_stream_fsm_cfg,
    mm_camera_stream_fsm_reg,
    mm_camera_stream_fsm_active
};
int32_t mm_camera_stream_fsm_fn_vtbl (mm_camera_obj_t * my_obj,
                   mm_camera_stream_t *stream,
                   mm_camera_state_evt_type_t evt, void *val)
{
    CDBG("%s: stream fd=%d, type = %d, state=%d, evt=%d\n",
                 __func__, stream->fd, stream->stream_type, stream->state, evt);
    return mm_camera_stream_fsm_fn[stream->state] (my_obj, stream, evt, val);
}

