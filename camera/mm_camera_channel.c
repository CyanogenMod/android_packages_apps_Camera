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
#include "mm_camera_interface2.h"
#include "mm_camera.h"

#if 0
#undef CDBG
#define CDBG ALOGE
#endif
/* static functions prototype declarations */
static int mm_camera_channel_skip_frames(mm_camera_obj_t *my_obj,
                                          mm_camera_frame_queue_t *mq,
                                          mm_camera_frame_queue_t *sq,
                                          mm_camera_stream_t *mstream,
                                          mm_camera_stream_t *sstream,
                                          mm_camera_channel_attr_buffering_frame_t *frame_attr);
static int mm_camera_channel_get_starting_frame(mm_camera_obj_t *my_obj,
                                                mm_camera_ch_t *ch,
                                                mm_camera_stream_t *mstream,
                                                mm_camera_stream_t *sstream,
                                                mm_camera_frame_queue_t *mq,
                                                mm_camera_frame_queue_t *sq,
                                                mm_camera_frame_t **mframe,
                                                mm_camera_frame_t **sframe);
static int mm_camera_ch_search_frame_based_on_time(mm_camera_obj_t *my_obj,
                                                   mm_camera_ch_t *ch,
                                                   mm_camera_stream_t *mstream,
                                                   mm_camera_stream_t *sstream,
                                                   mm_camera_frame_queue_t *mq,
                                                   mm_camera_frame_queue_t *sq,
                                                   mm_camera_frame_t **mframe,
                                                   mm_camera_frame_t **sframe);



int mm_camera_ch_util_get_num_stream(mm_camera_obj_t * my_obj,mm_camera_channel_type_t ch_type)
{
    int num = 0;
    switch(ch_type) {
    case MM_CAMERA_CH_RAW:
        num =  1;
        break;
    case MM_CAMERA_CH_PREVIEW:
        num =  1;
        break;
    case MM_CAMERA_CH_VIDEO:
        num =  1;
        if(my_obj->ch[ch_type].video.has_main) {
            num +=  1;
        }
        break;
    case MM_CAMERA_CH_SNAPSHOT:
        num =  2;
        break;
    default:
        break;
    }
    return num;
}

void mm_camera_ch_util_get_stream_objs(mm_camera_obj_t * my_obj,
                                       mm_camera_channel_type_t ch_type,
                                       mm_camera_stream_t **stream1,
                                       mm_camera_stream_t **stream2)
{
    *stream1 = NULL;
    *stream2 = NULL;

    switch(ch_type) {
    case MM_CAMERA_CH_RAW:
        *stream1 = &my_obj->ch[ch_type].raw.stream;
        break;
    case MM_CAMERA_CH_PREVIEW:
        *stream1 = &my_obj->ch[ch_type].preview.stream;
        break;
    case MM_CAMERA_CH_VIDEO:
        *stream1 = &my_obj->ch[ch_type].video.video;
        if(my_obj->ch[ch_type].video.has_main) {
            *stream2 = &my_obj->ch[ch_type].video.main;
        }
        break;
    case MM_CAMERA_CH_SNAPSHOT:
        *stream1 = &my_obj->ch[ch_type].snapshot.main;
        if (!my_obj->full_liveshot)
            *stream2 = &my_obj->ch[ch_type].snapshot.thumbnail;
        break;
    default:
        break;
    }
}

static int32_t mm_camera_ch_util_set_fmt(mm_camera_obj_t * my_obj,
                                         mm_camera_channel_type_t ch_type,
                                         mm_camera_ch_image_fmt_parm_t *fmt)
{
    int32_t rc = MM_CAMERA_OK;
    mm_camera_stream_t *stream1 = NULL;
    mm_camera_stream_t *stream2 = NULL;
    mm_camera_image_fmt_t *fmt1 = NULL;
    mm_camera_image_fmt_t *fmt2 = NULL;

    switch(ch_type) {
    case MM_CAMERA_CH_RAW:
        stream1 = &my_obj->ch[ch_type].raw.stream;
        fmt1 = &fmt->def;
        break;
    case MM_CAMERA_CH_PREVIEW:
        stream1 = &my_obj->ch[ch_type].preview.stream;
        fmt1 = &fmt->def;
        break;
    case MM_CAMERA_CH_VIDEO:
        stream1 = &my_obj->ch[ch_type].video.video;
        fmt1 = &fmt->video.video;
        if(my_obj->ch[ch_type].video.has_main) {
            CDBG("%s:video channel has main image stream\n", __func__);
            stream2 = &my_obj->ch[ch_type].video.main;
            fmt2 = &fmt->video.main;
        }
        break;
    case MM_CAMERA_CH_SNAPSHOT:
        stream1 = &my_obj->ch[ch_type].snapshot.main;
        fmt1 = &fmt->snapshot.main;
        if (!my_obj->full_liveshot) {
            stream2 = &my_obj->ch[ch_type].snapshot.thumbnail;
            fmt2 = &fmt->snapshot.thumbnail;
        }
        break;
    default:
        rc = -1;
        break;
    }
    CDBG("%s:ch=%d, streams[0x%x,0x%x]\n", __func__, ch_type,
             (uint32_t)stream1, (uint32_t)stream2);
    if(stream1)
        rc = mm_camera_stream_fsm_fn_vtbl(my_obj, stream1,
                         MM_CAMERA_STATE_EVT_SET_FMT, fmt1);
    if(stream2 && !rc)
        rc = mm_camera_stream_fsm_fn_vtbl(my_obj, stream2,
                         MM_CAMERA_STATE_EVT_SET_FMT, fmt2);
    return rc;
}

static int32_t mm_camera_ch_util_acquire(mm_camera_obj_t * my_obj,
                                         mm_camera_channel_type_t ch_type)
{
    int32_t rc = MM_CAMERA_OK;
    mm_camera_stream_t *stream1 = NULL;
    mm_camera_stream_t *stream2 = NULL;
    mm_camera_stream_type_t type1;
    mm_camera_stream_type_t type2;

    if(my_obj->ch[ch_type].acquired) {
        rc = MM_CAMERA_OK;
        goto end;
    }
    pthread_mutex_init(&my_obj->ch[ch_type].mutex, NULL);
    switch(ch_type) {
    case MM_CAMERA_CH_RAW:
        stream1 = &my_obj->ch[ch_type].raw.stream;
        type1 = MM_CAMERA_STREAM_RAW;
        break;
    case MM_CAMERA_CH_PREVIEW:
        stream1 = &my_obj->ch[ch_type].preview.stream;
        type1 = MM_CAMERA_STREAM_PREVIEW;
        break;
    case MM_CAMERA_CH_VIDEO:
        stream1 = &my_obj->ch[ch_type].video.video;
        type1 = MM_CAMERA_STREAM_VIDEO;
        /* no full image live shot by default */
        my_obj->ch[ch_type].video.has_main = FALSE;
        break;
    case MM_CAMERA_CH_SNAPSHOT:
        stream1 = &my_obj->ch[ch_type].snapshot.main;
        type1 = MM_CAMERA_STREAM_SNAPSHOT;
        if (!my_obj->full_liveshot) {
            stream2 = &my_obj->ch[ch_type].snapshot.thumbnail;
            type2 = MM_CAMERA_STREAM_THUMBNAIL;
        }
        break;
    default:
        return -1;
        break;
    }
    if(stream1) rc = mm_camera_stream_fsm_fn_vtbl(my_obj, stream1,
                                            MM_CAMERA_STATE_EVT_ACQUIRE, &type1);
    if(stream2 && !rc) rc = mm_camera_stream_fsm_fn_vtbl(my_obj, stream2,
                                            MM_CAMERA_STATE_EVT_ACQUIRE, &type2);
    if(rc == MM_CAMERA_OK) {
        if(!my_obj->ch[ch_type].acquired)    my_obj->ch[ch_type].acquired = TRUE;
    }

end:
    return rc;
}

static int32_t mm_camera_ch_util_release(mm_camera_obj_t * my_obj,
                                         mm_camera_channel_type_t ch_type,
                                         mm_camera_state_evt_type_t evt)
{
    mm_camera_stream_t *stream1, *stream2;

    if(!my_obj->ch[ch_type].acquired) return MM_CAMERA_OK;

    mm_camera_ch_util_get_stream_objs(my_obj,ch_type, &stream1, &stream2);
    if(stream1)
        mm_camera_stream_fsm_fn_vtbl(my_obj, stream1, evt, NULL);
    if(stream2)
        mm_camera_stream_fsm_fn_vtbl(my_obj, stream2, evt, NULL);
    pthread_mutex_destroy(&my_obj->ch[ch_type].mutex);
    memset(&my_obj->ch[ch_type],0,sizeof(my_obj->ch[ch_type]));
    return 0;
}

static int32_t mm_camera_ch_util_stream_null_val(mm_camera_obj_t * my_obj,
                                                 mm_camera_channel_type_t ch_type,
                                                            mm_camera_state_evt_type_t evt, void *val)
{
        int32_t rc = 0;
        switch(ch_type) {
        case MM_CAMERA_CH_RAW:
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj, &my_obj->ch[ch_type].raw.stream,
                                              evt, NULL);
            break;
        case MM_CAMERA_CH_PREVIEW:
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj, &my_obj->ch[ch_type].preview.stream,
                                              evt, NULL);
            break;
        case MM_CAMERA_CH_VIDEO:
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                            &my_obj->ch[ch_type].video.video, evt,
                            NULL);
            if(!rc && my_obj->ch[ch_type].video.main.fd)
                rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                &my_obj->ch[ch_type].video.main, evt,
                                NULL);
            break;
        case MM_CAMERA_CH_SNAPSHOT:
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                            &my_obj->ch[ch_type].snapshot.main, evt,
                            NULL);
            if(!rc && !my_obj->full_liveshot)
                rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                &my_obj->ch[ch_type].snapshot.thumbnail, evt,
                                NULL);
            break;
        default:
            CDBG_ERROR("%s: Invalid ch_type=%d", __func__, ch_type);
            return -1;
            break;
        }
        return rc;
}

static int32_t mm_camera_ch_util_reg_buf(mm_camera_obj_t * my_obj,
                                         mm_camera_channel_type_t ch_type,
                                         mm_camera_state_evt_type_t evt, void *val)
{
        int32_t rc = 0;
        switch(ch_type) {
        case MM_CAMERA_CH_RAW:
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                             &my_obj->ch[ch_type].raw.stream, evt,
                                             (mm_camera_buf_def_t *)val);
            break;
        case MM_CAMERA_CH_PREVIEW:
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                             &my_obj->ch[ch_type].preview.stream, evt,
                                             (mm_camera_buf_def_t *)val);
            break;
        case MM_CAMERA_CH_VIDEO:
            {
                mm_camera_buf_video_t * buf = (mm_camera_buf_video_t *)val;
                rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                &my_obj->ch[ch_type].video.video, evt,
                                &buf->video);
                if(!rc && my_obj->ch[ch_type].video.has_main) {
                    rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                    &my_obj->ch[ch_type].video.main, evt,
                                    &buf->main);
                }
            }
            break;
        case MM_CAMERA_CH_SNAPSHOT:
            {
                mm_camera_buf_snapshot_t * buf = (mm_camera_buf_snapshot_t *)val;
                rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                &my_obj->ch[ch_type].snapshot.main, evt,
                                &buf->main);
                if(!rc && !my_obj->full_liveshot) {
                    rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                    &my_obj->ch[ch_type].snapshot.thumbnail, evt,
                                    & buf->thumbnail);
                }
            }
            break;
        default:
            return -1;
            break;
        }
        return rc;
}

static int32_t mm_camera_ch_util_attr(mm_camera_obj_t *my_obj,
                                      mm_camera_channel_type_t ch_type,
                                      mm_camera_channel_attr_t *val)
{
    int rc = -MM_CAMERA_E_NOT_SUPPORTED;
    /*if(ch_type != MM_CAMERA_CH_RAW) {
        CDBG("%s: attr type %d not support for ch %d\n", __func__, val->type, ch_type);
        return rc;
    }*/
    if(my_obj->ch[ch_type].acquired== 0) {
      CDBG_ERROR("%s Channel %d not yet acquired ", __func__, ch_type);
      return -MM_CAMERA_E_INVALID_OPERATION;
    }
    switch(val->type) {
    case MM_CAMERA_CH_ATTR_RAW_STREAMING_TYPE:
        if(val->raw_streaming_mode == MM_CAMERA_RAW_STREAMING_CAPTURE_SINGLE) {
            my_obj->ch[ch_type].raw.mode = val->raw_streaming_mode;
            rc = MM_CAMERA_OK;
        }
        break;
    case MM_CAMERA_CH_ATTR_BUFFERING_FRAME:
      /* it's good to check the stream state. TBD later  */
      memcpy(&my_obj->ch[ch_type].buffering_frame, &val->buffering_frame, sizeof(val->buffering_frame));
      break;
    default:
        break;
    }
    return MM_CAMERA_OK;
}

static int32_t mm_camera_ch_util_reg_buf_cb(mm_camera_obj_t *my_obj,
                                            mm_camera_channel_type_t ch_type,
                                            mm_camera_buf_cb_t *val)
{
    /* TODOhere: Need to return failure in case of MAX Cb registered
     * but in order to return fail case need to set up rc.
     * but the rc value needs to be thread safe
     */
    int i;
    ALOGE("%s: Trying to register",__func__);
//    pthread_mutex_lock(&my_obj->ch[ch_type].mutex);
    for( i=0 ;i < MM_CAMERA_BUF_CB_MAX; i++ ) {
        if(my_obj->ch[ch_type].buf_cb[i].cb==NULL) {
            memcpy(&my_obj->ch[ch_type].buf_cb[i],val,sizeof(mm_camera_buf_cb_t));
            break;
        }
    }
//    pthread_mutex_unlock(&my_obj->ch[ch_type].mutex);
    ALOGE("%s: Done register",__func__);
    return MM_CAMERA_OK;
}

static int32_t mm_camera_ch_util_qbuf(mm_camera_obj_t *my_obj,
                                    mm_camera_channel_type_t ch_type,
                                    mm_camera_state_evt_type_t evt,
                                    mm_camera_ch_data_buf_t *val)
{
    int32_t rc = -1;
    mm_camera_stream_t *stream;

    ALOGV("<DEBUG>: %s:ch_type:%d",__func__,ch_type);
    switch(ch_type) {
    case MM_CAMERA_CH_RAW:
        rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                          &my_obj->ch[ch_type].raw.stream, evt,
                                                                     &val->def);
        break;
    case MM_CAMERA_CH_PREVIEW:
        rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                         &my_obj->ch[ch_type].preview.stream, evt,
                                         &val->def);
        break;
    case MM_CAMERA_CH_VIDEO:
        {
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                            &my_obj->ch[ch_type].video.video, evt,
                            &val->video.video);
            if(!rc && val->video.main.frame)
                rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                &my_obj->ch[ch_type].video.main, evt,
                                &val->video.main);
        }
        break;
    case MM_CAMERA_CH_SNAPSHOT:
        {
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                            &my_obj->ch[ch_type].snapshot.main, evt,
                            &val->snapshot.main);
            if(!rc) {
                if (my_obj->op_mode == MM_CAMERA_OP_MODE_ZSL)
                  stream = &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream;
                else
                  stream = &my_obj->ch[ch_type].snapshot.thumbnail;
                rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                stream, evt,
                                &val->snapshot.thumbnail);
            }
        }
        break;
    default:
        return -1;
        break;
    }
    return rc;
}

static int mm_camera_ch_util_get_crop(mm_camera_obj_t *my_obj,
                                mm_camera_channel_type_t ch_type,
                                mm_camera_state_evt_type_t evt,
                                mm_camera_ch_crop_t *crop)
{
    int rc = MM_CAMERA_OK;
    switch(ch_type) {
    case MM_CAMERA_CH_RAW:
        rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                       &my_obj->ch[ch_type].raw.stream, evt,
                                       &crop->crop);
        break;
    case MM_CAMERA_CH_PREVIEW:
        rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                                    &my_obj->ch[ch_type].preview.stream, evt,
                                    &crop->crop);
        break;
    case MM_CAMERA_CH_VIDEO:
        rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                          &my_obj->ch[ch_type].video.video, evt,
                          &crop->crop);
        break;
    case MM_CAMERA_CH_SNAPSHOT:
        {
            rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                          &my_obj->ch[ch_type].snapshot.main, evt,
                          &crop->snapshot.main_crop);
            if(!rc && !my_obj->full_liveshot) {
              ALOGE("%s: should not come here for Live Shot", __func__);
              rc = mm_camera_stream_fsm_fn_vtbl(my_obj,
                              &my_obj->ch[ch_type].snapshot.thumbnail, evt,
                              &crop->snapshot.thumbnail_crop);
            }
        }
        break;
    default:
        return -1;
        break;
    }
    return rc;
}

static int mm_camera_ch_util_dispatch_buffered_frame(mm_camera_obj_t *my_obj,
                mm_camera_channel_type_t ch_type)
{
    return mm_camera_poll_dispatch_buffered_frames(my_obj, ch_type);
}

int mm_camera_channel_get_time_diff(struct timespec *cur_ts, int usec_target, struct timespec *frame_ts)
{
    int dtusec = (cur_ts->tv_nsec - frame_ts->tv_nsec)/1000;
    dtusec += (cur_ts->tv_sec - frame_ts->tv_sec)*1000000 - usec_target;
    return dtusec;
}

static int mm_camera_channel_skip_frames(mm_camera_obj_t *my_obj,
                                          mm_camera_frame_queue_t *mq,
                                          mm_camera_frame_queue_t *sq,
                                          mm_camera_stream_t *mstream,
                                          mm_camera_stream_t *sstream,
                                          mm_camera_channel_attr_buffering_frame_t *frame_attr)
{
    int count = 0;
    int i = 0;
    mm_camera_frame_t *mframe = NULL, *sframe = NULL;
    mm_camera_notify_frame_t notify_frame;

    count = mm_camera_stream_frame_get_q_cnt(mq);
    if(count < mm_camera_stream_frame_get_q_cnt(sq))
        count = mm_camera_stream_frame_get_q_cnt(sq);
    CDBG("count =%d, look_back=%d,mq->match_cnt=%d, sq->match_cnt=%d",
               count ,frame_attr->look_back, mq->match_cnt,sq->match_cnt);
    count -= frame_attr->look_back;
    for(i=0; i < count; i++) {
        mframe = mm_camera_stream_frame_deq(mq);
        sframe = mm_camera_stream_frame_deq(sq);
        if(mframe && sframe && mframe->frame.frame_id ==
           sframe->frame.frame_id) {
          mq->match_cnt--;
          sq->match_cnt--;
        }
        if(mframe) {
            notify_frame.frame = &mframe->frame;
            notify_frame.idx = mframe->idx;
            mm_camera_stream_util_buf_done(my_obj, mstream, &notify_frame);
        }
        if(sframe) {
            notify_frame.frame = &sframe->frame;
            notify_frame.idx = sframe->idx;
            mm_camera_stream_util_buf_done(my_obj, sstream, &notify_frame);
        }
    }
    return MM_CAMERA_OK;
}

/*for ZSL mode to send the image pair to client*/
void mm_camera_dispatch_buffered_frames(mm_camera_obj_t *my_obj,
                                        mm_camera_channel_type_t ch_type)
{
    int mcnt, i, rc = MM_CAMERA_E_GENERAL, scnt;
    int num_of_req_frame = 0;
    int j;
    mm_camera_ch_data_buf_t data;
    mm_camera_frame_t *mframe = NULL, *sframe = NULL;
    mm_camera_frame_t *qmframe = NULL, *qsframe = NULL;
    mm_camera_ch_t *ch = &my_obj->ch[ch_type];
    mm_camera_frame_queue_t *mq = NULL;
    mm_camera_frame_queue_t *sq = NULL;
    mm_camera_stream_t *stream1 = NULL;
    mm_camera_stream_t *stream2 = NULL;
ALOGE("%s: mzhu, E", __func__);
    mm_camera_ch_util_get_stream_objs(my_obj, ch_type, &stream1, &stream2);
    stream2 = &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream;
    if(stream1) {
      mq = &stream1->frame.readyq;
    }
    if(stream2) {
      sq = &stream2->frame.readyq;
    }
    pthread_mutex_lock(&ch->mutex);
    if (mq && sq && stream1 && stream2) {
        rc = mm_camera_channel_skip_frames(my_obj, mq, sq, stream1, stream2, &ch->buffering_frame);
        if(rc != MM_CAMERA_OK) {
            CDBG_ERROR("%s: Error getting right frame!", __func__);
            goto end;
        }
        num_of_req_frame = my_obj->snap_burst_num_by_user;
        ch->snapshot.pending_cnt = num_of_req_frame;
        for(i = 0; i < num_of_req_frame; i++) {
            mframe = mm_camera_stream_frame_deq(mq);
            sframe = mm_camera_stream_frame_deq(sq);
            if(mframe && sframe) {
                CDBG("%s: frame_id = 0x%x|0x%x, main idx = %d, thumbnail idx = %d", __func__,
                     mframe->frame.frame_id, sframe->frame.frame_id, mframe->idx, sframe->idx);
                if(mframe->frame.frame_id != sframe->frame.frame_id) {
                    CDBG_ERROR("%s: ZSL algorithm error, main and thumbnail "
                        "frame_ids not same. Need bug fix", __func__);
                }
                memset(&data, 0, sizeof(data));
                data.type = ch_type;
                data.snapshot.main.frame = &mframe->frame;
                data.snapshot.main.idx = mframe->idx;
                data.snapshot.thumbnail.frame = &sframe->frame;
                data.snapshot.thumbnail.idx = sframe->idx;
                ch->snapshot.pending_cnt--;
                mq->match_cnt--;
                sq->match_cnt--;
                for(j=0;j<MM_CAMERA_BUF_CB_MAX;j++) {
                    if( ch->buf_cb[j].cb!=NULL )
                        ch->buf_cb[j].cb(&data, ch->buf_cb[j].user_data);
                }
            } else {
               CDBG_ERROR("%s: mframe %p, sframe = %p", __func__, mframe, sframe);
                qmframe = mframe;
                qsframe = sframe;
                rc = -1;
                break;
            }
        }
        if(qmframe) {
            mm_camera_stream_frame_enq(mq, &stream1->frame.frame[qmframe->idx]);
            qmframe = NULL;
        }
        if(qsframe) {
            mm_camera_stream_frame_enq(sq, &stream2->frame.frame[qsframe->idx]);
            qsframe = NULL;
        }
    } else {
      CDBG_ERROR(" mq =%p sq =%p stream1 =%p stream2 =%p", mq, sq , stream1 , stream2);

    }
    CDBG("%s: burst number: %d, pending_count: %d", __func__,
        my_obj->snap_burst_num_by_user, ch->snapshot.pending_cnt);
end:
    pthread_mutex_unlock(&ch->mutex);
    /* If we are done sending callbacks for all the requested number of snapshots
       send data delivery done event*/
    if((rc == MM_CAMERA_OK) && (!ch->snapshot.pending_cnt)) {
        mm_camera_event_t data;
        data.event_type = MM_CAMERA_EVT_TYPE_CH;
        data.e.ch.evt = MM_CAMERA_CH_EVT_DATA_DELIVERY_DONE;
        data.e.ch.ch = ch_type;
        mm_camera_poll_send_ch_event(my_obj, &data);
    }
}

int32_t mm_camera_ch_fn(mm_camera_obj_t * my_obj,
        mm_camera_channel_type_t ch_type,
        mm_camera_state_evt_type_t evt, void *val)
{
    int32_t rc = MM_CAMERA_OK;

    CDBG("%s:ch = %d, evt=%d\n", __func__, ch_type, evt);
    switch(evt) {
    case MM_CAMERA_STATE_EVT_ACQUIRE:
        rc = mm_camera_ch_util_acquire(my_obj, ch_type);
        break;
    case MM_CAMERA_STATE_EVT_RELEASE:
      /* safe code in case no stream off before release. */
        //mm_camera_poll_thread_release(my_obj, ch_type);
        rc = mm_camera_ch_util_release(my_obj, ch_type, evt);
        break;
    case MM_CAMERA_STATE_EVT_ATTR:
        rc = mm_camera_ch_util_attr(my_obj, ch_type,
                                    (mm_camera_channel_attr_t *)val);
        break;
    case MM_CAMERA_STATE_EVT_REG_BUF_CB:
        rc = mm_camera_ch_util_reg_buf_cb(my_obj, ch_type,
                                          (mm_camera_buf_cb_t *)val);
        break;
    case MM_CAMERA_STATE_EVT_SET_FMT:
        rc = mm_camera_ch_util_set_fmt(my_obj, ch_type,
                                       (mm_camera_ch_image_fmt_parm_t *)val);
        break;
    case MM_CAMERA_STATE_EVT_REG_BUF:
    case MM_CAMERA_STATE_EVT_REQUEST_BUF:
    case MM_CAMERA_STATE_EVT_ENQUEUE_BUF:
        rc = mm_camera_ch_util_reg_buf(my_obj, ch_type, evt, val);
        break;
    case MM_CAMERA_STATE_EVT_UNREG_BUF:
        rc = mm_camera_ch_util_stream_null_val(my_obj, ch_type, evt, NULL);
        break;
    case MM_CAMERA_STATE_EVT_STREAM_ON: {
        if(ch_type == MM_CAMERA_CH_RAW &&
             my_obj->ch[ch_type].raw.mode == MM_CAMERA_RAW_STREAMING_CAPTURE_SINGLE) {
            if( MM_CAMERA_OK != (rc = mm_camera_util_s_ctrl(my_obj->ctrl_fd,
                MSM_V4L2_PID_CAM_MODE, MSM_V4L2_CAM_OP_RAW))) {
                CDBG("%s:set MM_CAMERA_RAW_STREAMING_CAPTURE_SINGLE err=%d\n", __func__, rc);
                break;
            }
        }
        mm_camera_poll_thread_add_ch(my_obj, ch_type);
        rc = mm_camera_ch_util_stream_null_val(my_obj, ch_type, evt, NULL);
        if(rc < 0) {
          CDBG_ERROR("%s: Failed in STREAM ON", __func__);
          mm_camera_poll_thread_release(my_obj, ch_type);
        }
        break;
    }
    case MM_CAMERA_STATE_EVT_STREAM_OFF: {
        mm_camera_poll_thread_del_ch(my_obj, ch_type);
        rc = mm_camera_ch_util_stream_null_val(my_obj, ch_type, evt, NULL);
        break;
    }
    case MM_CAMERA_STATE_EVT_QBUF:
        rc = mm_camera_ch_util_qbuf(my_obj, ch_type, evt,
                                    (mm_camera_ch_data_buf_t *)val);
        break;
    case MM_CAMERA_STATE_EVT_GET_CROP:
      rc = mm_camera_ch_util_get_crop(my_obj, ch_type, evt,
                                  (mm_camera_ch_crop_t *)val);
      break;
    case MM_CAMERA_STATE_EVT_DISPATCH_BUFFERED_FRAME:
      rc = mm_camera_ch_util_dispatch_buffered_frame(my_obj, ch_type);
      break;
    default:
        break;
    }
    return rc;
}
