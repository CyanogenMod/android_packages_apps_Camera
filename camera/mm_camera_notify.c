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
#include <linux/ion.h>
#include <camera.h>
#include "mm_camera_interface2.h"
#include "mm_camera.h"

#if 0
#undef CDBG
#undef LOG_TAG
#define CDBG ALOGE
#define LOG_TAG "NotifyLogs"
#endif

static void mm_camera_read_raw_frame(mm_camera_obj_t * my_obj)
{
    int rc = 0;
    int idx;
    int i;
    mm_camera_stream_t *stream;

    stream = &my_obj->ch[MM_CAMERA_CH_RAW].raw.stream;
    idx =  mm_camera_read_msm_frame(my_obj, stream);
    if (idx < 0) {
        return;
    }
    pthread_mutex_lock(&my_obj->ch[MM_CAMERA_CH_RAW].mutex);
    for( i=0;i<MM_CAMERA_BUF_CB_MAX;i++) {
        if((my_obj->ch[MM_CAMERA_CH_RAW].buf_cb[i].cb) &&
                (my_obj->poll_threads[MM_CAMERA_CH_RAW].data.used == 1)){
            mm_camera_ch_data_buf_t data;
            data.type = MM_CAMERA_CH_RAW;
            data.def.idx = idx;
            data.def.frame = &my_obj->ch[MM_CAMERA_CH_RAW].raw.stream.frame.frame[idx].frame;
            my_obj->ch[MM_CAMERA_CH_RAW].raw.stream.frame.ref_count[idx]++;
            CDBG("%s:calling data notify cb 0x%x, 0x%x\n", __func__,
                     (uint32_t)my_obj->ch[MM_CAMERA_CH_RAW].buf_cb[i].cb,
                     (uint32_t)my_obj->ch[MM_CAMERA_CH_RAW].buf_cb[i].user_data);
            my_obj->ch[MM_CAMERA_CH_RAW].buf_cb[i].cb(&data,
                                    my_obj->ch[MM_CAMERA_CH_RAW].buf_cb[i].user_data);
        }
    }
    pthread_mutex_unlock(&my_obj->ch[MM_CAMERA_CH_RAW].mutex);
}

int mm_camera_zsl_check_burst_dispatching(mm_camera_obj_t * my_obj,
                               mm_camera_frame_queue_t *main_q,
                               mm_camera_frame_queue_t *thumb_q,
                               mm_camera_stream_t *main_stream,
                               mm_camera_stream_t *thumb_stream, int *deliver_done)
{
    mm_camera_ch_data_buf_t data;
    int i;
    mm_camera_frame_t *main_frame = NULL;
    mm_camera_frame_t *thumb_frame = NULL;
    mm_camera_ch_t *ch = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT];
    *deliver_done = 0;
    if(ch->snapshot.pending_cnt > 0) {
        if(!main_q->match_cnt || !thumb_q->match_cnt)
            return 0;
        /* dequeue one by one and then pass to HAL */
        main_frame = mm_camera_stream_frame_deq_no_lock(main_q);
        thumb_frame = mm_camera_stream_frame_deq_no_lock(thumb_q);
        main_q->match_cnt--;
        thumb_q->match_cnt--;
        CDBG("%s: Dequeued frame: main frame idx: %d thumbnail "
             "frame idx: %d", __func__, main_frame->idx, thumb_frame->idx);
        /* dispatch this pair of frames */
        memset(&data, 0, sizeof(data));
        data.type = MM_CAMERA_CH_SNAPSHOT;
        data.snapshot.main.frame = &main_frame->frame;
        data.snapshot.main.idx = main_frame->idx;
        data.snapshot.thumbnail.frame = &thumb_frame->frame;
        data.snapshot.thumbnail.idx = thumb_frame->idx;
        ch->snapshot.pending_cnt--;
        for( i=0;i<MM_CAMERA_BUF_CB_MAX;i++) {
            if (ch->buf_cb[i].cb)
              ch->buf_cb[i].cb(&data, ch->buf_cb[i].user_data);
        }
        if(ch->snapshot.pending_cnt == 0)
            *deliver_done = 1;
    }
    return 0;
}

int mm_camera_zsl_frame_cmp_and_enq(mm_camera_obj_t * my_obj,
                               mm_camera_frame_t *node,
                               mm_camera_stream_t *mystream)
{
    int watermark;
    mm_camera_frame_queue_t *myq;
    mm_camera_frame_queue_t *peerq;
    mm_camera_stream_t *peerstream;
    int rc = 0;
    int deliver_done = 0;
    mm_camera_frame_t *peer_frame;
    mm_camera_frame_t *peer_frame_prev;
    mm_camera_frame_t *peer_frame_tmp;
    mm_camera_notify_frame_t notify_frame;

    pthread_mutex_t *pSnapshotMutex = &(my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.readyq.mutex);
    pthread_mutex_t *pPreviewMutex = &(my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream.frame.readyq.mutex);

    if(mystream->stream_type == MM_CAMERA_STREAM_PREVIEW) {
        peerstream = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main;
    } else
        peerstream = &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream;
    myq = &mystream->frame.readyq;
    peerq = &peerstream->frame.readyq;
    watermark = my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buffering_frame.water_mark;
    /* lock both queues */
    pthread_mutex_lock(pSnapshotMutex);
    pthread_mutex_lock(pPreviewMutex);
    peer_frame = peerq->tail;
    /* for 30-120 fps streaming no need to consider the wrapping back of frame_id */
    if(!peer_frame || node->frame.frame_id > peer_frame->frame.frame_id) {
        /* new frame is newer than all stored peer frames. simply keep the node */
        /* in case the frame_id wraps back, the peer frame's frame_id will be
           larger than the new frame's frame id */
        CDBG("%s New frame. Just enqueue it into the queue ", __func__);
        mm_camera_stream_frame_enq_no_lock(myq, node);
        node->valid_entry = 1;
    }
    CDBG("%s Need to find match for the frame id %d ",
         __func__, node->frame.frame_id);
    /* the node is older than the peer, we will either find a match or drop it */
    peer_frame = peerq->head;
    peer_frame_prev = NULL;
    peer_frame_tmp = NULL;
    while(peer_frame) {
        CDBG("%s peer frame_id = %d node frame_id = %d", __func__,
             peer_frame->frame.frame_id, node->frame.frame_id);
        if(peer_frame->match) {
            CDBG("%s Peer frame already matched, keep looking in the list ",
                 __func__);
            /* matched frame., skip */
            peer_frame_prev = peer_frame;
            peer_frame = peer_frame->next;
            continue;
        }
        if(peer_frame->frame.frame_id == node->frame.frame_id) {
            /* find a match keep the frame */
            node->match = 1;
            peer_frame->match = 1;
            CDBG("%s Found match, add to myq ", __func__);
            mm_camera_stream_frame_enq_no_lock(myq, node);
            myq->match_cnt++;
            peerq->match_cnt++;
            goto water_mark;
        } else {
            /* no match */
            if(node->frame.frame_id > peer_frame->frame.frame_id) {
                /* the incoming frame is newer than the peer's unmatched frame.
                   drop the peer frame */
                CDBG("%s node frame is newer, release old peer frame ",
                     __func__);
                if(!peer_frame_prev) {
                    /* this is the head */
                    peer_frame_tmp = mm_camera_stream_frame_deq_no_lock(peerq);
                    notify_frame.frame = &peer_frame_tmp->frame;
                    notify_frame.idx = peer_frame_tmp->idx;
                    mm_camera_stream_util_buf_done(my_obj, peerstream,
                                                   &notify_frame);
                    peer_frame = peerq->head;
                    peer_frame_prev = NULL;
                    continue;
                } else {
                    /* this is not the head. */
                    peer_frame_tmp = peer_frame;
                    peer_frame_prev->next = peer_frame->next;
                    if(peer_frame == peerq->tail) {
                        /* peer_frame is the tail */
                        peerq->tail = peer_frame_prev;
                    }
                    notify_frame.frame = &peer_frame_tmp->frame;
                    notify_frame.idx = peer_frame_tmp->idx;
                    mm_camera_stream_util_buf_done(my_obj, peerstream,
                                                   &notify_frame);
                    peer_frame = peer_frame_prev->next;
                    peerq->cnt--;
                    continue;
                }
            } else {
                /* Current frame is older than peer's unmatched frame, dont add
                 * it into the queue. just drop it */
                CDBG("%s node frame is older than peer's unmatched frame. "
                     "Drop the current frame.", __func__);
                notify_frame.frame = &node->frame;
                notify_frame.idx = node->idx;
                mm_camera_stream_util_buf_done(my_obj, mystream, &notify_frame);
                goto end;
            }
        }
    }
    if(!node->match && !node->valid_entry) {
        /* if no match and not a valid entry.
         * the node is not added into the queue. it's dirty node */
        CDBG_ERROR("%s: stream type = %d and fd = %d, frame 0x%x is dirty"
                   " and queue back kernel", __func__, mystream->stream_type,
                    mystream->fd, node->frame.frame_id);
        notify_frame.frame = &node->frame;
        notify_frame.idx = node->idx;
        mm_camera_stream_util_buf_done(my_obj, mystream, &notify_frame);
    }
water_mark:
    while(myq->match_cnt > watermark) {
        peer_frame_tmp = mm_camera_stream_frame_deq_no_lock(peerq);
        notify_frame.frame = &peer_frame_tmp->frame;
        notify_frame.idx = peer_frame_tmp->idx;
        CDBG("%s match_cnt %d > watermark %d, buf_done on "
                   "peer frame idx %d id = %d", __func__,
                   myq->match_cnt, watermark, notify_frame.idx,
                   notify_frame.frame->frame_id);
        mm_camera_stream_util_buf_done(my_obj, peerstream, &notify_frame);
        peerq->match_cnt--;
        peer_frame_tmp = mm_camera_stream_frame_deq_no_lock(myq);
        notify_frame.frame = &peer_frame_tmp->frame;
        notify_frame.idx = peer_frame_tmp->idx;
        mm_camera_stream_util_buf_done(my_obj, mystream, &notify_frame);
        myq->match_cnt--;
    }
end:
    CDBG("%s myQ->cnt = %d myQ->match_cnt = %d ", __func__,
         myq->cnt, myq->match_cnt);
    if(myq->cnt > myq->match_cnt + 1) {
        /* drop the first unmatched frame */
        mm_camera_frame_t *peer_frame = myq->head;;
        mm_camera_frame_t *peer_frame_prev = NULL;
        while(peer_frame) {
            CDBG("%s myQ->cnt = %d myQ->match_cnt = %d ", __func__,
                   myq->cnt, myq->match_cnt);
            if(peer_frame->match == 0) {
                /* first unmatched frame */
                if(!peer_frame_prev) {
                    /* this is the head */
                    peer_frame_tmp = mm_camera_stream_frame_deq_no_lock(myq);
                    notify_frame.frame = &peer_frame_tmp->frame;
                    notify_frame.idx = peer_frame_tmp->idx;
                    CDBG("%s Head Issuing buf_done on my frame idx %d id %d",
                         __func__, notify_frame.idx,
                         notify_frame.frame->frame_id);
                    mm_camera_stream_util_buf_done(my_obj, mystream,
                                                   &notify_frame);
                } else {
                    /* this is not the head. */
                    peer_frame_tmp = peer_frame;
                    peer_frame_prev->next = peer_frame->next;
                    if(peer_frame == peerq->tail) {
                        /* peer_frame is the tail */
                        myq->tail = peer_frame_prev;
                    }
                    notify_frame.frame = &peer_frame_tmp->frame;
                    notify_frame.idx = peer_frame_tmp->idx;
                    CDBG("%s Issuing buf_done on my frame idx %d id = %d",
                         __func__, notify_frame.idx,
                         notify_frame.frame->frame_id);
                    mm_camera_stream_util_buf_done(my_obj, mystream,
                                                   &notify_frame);
                    myq->cnt--;
                }
                break;
            } else {
                peer_frame_prev= peer_frame;
                peer_frame = peer_frame_prev->next;
            }
        }
    }
    CDBG("%s peerQ->cnt = %d peerQ->match_cnt = %d ", __func__,
         peerq->cnt, peerq->match_cnt);
    if(peerq->cnt > peerq->match_cnt + 1) {
        /* drop the first unmatched frame */
        mm_camera_frame_t *peer_frame = peerq->head;
        mm_camera_frame_t *peer_frame_prev = NULL;
        while(peer_frame) {
            CDBG("%s Traverse peerq list frame idx %d frame_id = %d match %d ",
                  __func__, peer_frame->idx, peer_frame->frame.frame_id,
                  peer_frame->match);
            if(peer_frame->match == 0) {
                /* first unmatched frame */
                if(!peer_frame_prev) {
                    /* this is the head */
                    peer_frame_tmp = mm_camera_stream_frame_deq_no_lock(peerq);
                    notify_frame.frame = &peer_frame_tmp->frame;
                    notify_frame.idx = peer_frame_tmp->idx;
                    CDBG("%s Head Issuing buf_done on peer frame idx %d "
                         "id = %d", __func__, notify_frame.idx,
                         notify_frame.frame->frame_id);
                    mm_camera_stream_util_buf_done(my_obj, peerstream,
                                                   &notify_frame);
                } else {
                    /* this is not the head. */
                    peer_frame_tmp = peer_frame;
                    peer_frame_prev->next = peer_frame->next;
                    if(peer_frame == peerq->tail) {
                        /* peer_frame is the tail */
                        peerq->tail = peer_frame_prev;
                    }
                    notify_frame.frame = &peer_frame_tmp->frame;
                    notify_frame.idx = peer_frame_tmp->idx;
                    CDBG("%s Issuing buf_done on peer frame idx %d id = %d",
                         __func__, notify_frame.idx,
                         notify_frame.frame->frame_id);
                    mm_camera_stream_util_buf_done(my_obj, peerstream,
                                                   &notify_frame);
                    peerq->cnt--;
                }
                break;
            } else {
                peer_frame_prev= peer_frame;
                peer_frame = peer_frame_prev->next;
            }
        }
    }

    CDBG("%s Dispatching ZSL frame ", __func__);
    mm_camera_zsl_check_burst_dispatching(my_obj,
              &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.readyq,
              &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream.frame.readyq,
              &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main,
              &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream, &deliver_done);
    pthread_mutex_unlock(pPreviewMutex);
    pthread_mutex_unlock(pSnapshotMutex);
    if(deliver_done > 0) {
        mm_camera_event_t data;
        CDBG("%s: ZSL delivered", __func__);
        data.event_type = MM_CAMERA_EVT_TYPE_CH;
        data.e.ch.evt = MM_CAMERA_CH_EVT_DATA_DELIVERY_DONE;
        data.e.ch.ch = MM_CAMERA_CH_SNAPSHOT;
        mm_camera_poll_send_ch_event(my_obj, &data);
    }
    return rc;
}

static void mm_camera_read_preview_frame(mm_camera_obj_t * my_obj)
{
    int rc = 0;
    int idx;
    int i;
    mm_camera_stream_t *stream;

    stream = &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream;
    idx =  mm_camera_read_msm_frame(my_obj, stream);
    if (idx < 0) {
        return;
    }
    CDBG("%s Read Preview frame %d ", __func__, idx);
    pthread_mutex_lock(&my_obj->ch[MM_CAMERA_CH_PREVIEW].mutex);
    for( i=0;i<MM_CAMERA_BUF_CB_MAX;i++) {
        if((my_obj->ch[MM_CAMERA_CH_PREVIEW].buf_cb[i].cb) &&
                (my_obj->poll_threads[MM_CAMERA_CH_PREVIEW].data.used == 1)) {
            mm_camera_ch_data_buf_t data;
            data.type = MM_CAMERA_CH_PREVIEW;
            data.def.idx = idx;
            data.def.frame = &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream.frame.frame[idx].frame;
            /* Since the frame is originating here, reset the ref count to either
             * 2(ZSL case) or 1(non-ZSL case). */
            if(my_obj->op_mode == MM_CAMERA_OP_MODE_ZSL)
                my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream.frame.ref_count[idx] = 2;
            else
                my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream.frame.ref_count[idx] = 1;
            CDBG("%s:calling data notify cb 0x%x, 0x%x\n", __func__,
                     (uint32_t)my_obj->ch[MM_CAMERA_CH_PREVIEW].buf_cb[i].cb,
                     (uint32_t)my_obj->ch[MM_CAMERA_CH_PREVIEW].buf_cb[i].user_data);
            my_obj->ch[MM_CAMERA_CH_PREVIEW].buf_cb[i].cb(&data,
                                        my_obj->ch[MM_CAMERA_CH_PREVIEW].buf_cb[i].user_data);
        }
    }
    pthread_mutex_unlock(&my_obj->ch[MM_CAMERA_CH_PREVIEW].mutex);
    if(my_obj->op_mode == MM_CAMERA_OP_MODE_ZSL) {
        /* Reset match to 0. */
        stream->frame.frame[idx].match = 0;
        stream->frame.frame[idx].valid_entry = 0;
        mm_camera_zsl_frame_cmp_and_enq(my_obj,
          &my_obj->ch[MM_CAMERA_CH_PREVIEW].preview.stream.frame.frame[idx],
          stream);
    }
}

static void mm_camera_snapshot_send_liveshot_notify(mm_camera_obj_t * my_obj)
{
    int delivered = 0;
    mm_camera_frame_queue_t *s_q;
    int i;
//    mm_camera_frame_queue_t *s_q, *t_q;

    mm_camera_ch_data_buf_t data;
    mm_camera_frame_t *frame;
    s_q =   &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.readyq;
    pthread_mutex_lock(&my_obj->ch[MM_CAMERA_CH_SNAPSHOT].mutex);

    for( i=0;i<MM_CAMERA_BUF_CB_MAX;i++) {
        if(s_q->cnt && my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buf_cb[i].cb) {
            data.type = MM_CAMERA_CH_SNAPSHOT;
            frame = mm_camera_stream_frame_deq(s_q);
            data.snapshot.main.frame = &frame->frame;
            data.snapshot.main.idx = frame->idx;
            data.snapshot.thumbnail.frame = NULL;
            my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.ref_count[data.snapshot.main.idx]++;
            my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buf_cb[i].cb(&data,
                                    my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buf_cb[i].user_data);
            my_obj->snap_burst_num_by_user -= 1;
            CDBG("%s: burst number =%d", __func__, my_obj->snap_burst_num_by_user);
            delivered = 1;
        }
    }

    pthread_mutex_unlock(&my_obj->ch[MM_CAMERA_CH_SNAPSHOT].mutex);
    if(delivered) {
      mm_camera_event_t data;
      data.event_type = MM_CAMERA_EVT_TYPE_CH;
      data.e.ch.evt = MM_CAMERA_CH_EVT_DATA_DELIVERY_DONE;
      data.e.ch.ch = MM_CAMERA_CH_SNAPSHOT;
      mm_camera_poll_send_ch_event(my_obj, &data);
    }
}

static void mm_camera_snapshot_send_snapshot_notify(mm_camera_obj_t * my_obj)
{
    int delivered = 0;
    int i;
    mm_camera_frame_queue_t *s_q, *t_q;
    mm_camera_ch_data_buf_t data;
    mm_camera_frame_t *frame;
    mm_camera_buf_cb_t buf_cb;

    memset(&buf_cb, 0, sizeof(buf_cb));
    s_q =   &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.readyq;
    t_q =   &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.thumbnail.frame.readyq;
    pthread_mutex_lock(&my_obj->ch[MM_CAMERA_CH_SNAPSHOT].mutex);

    for( i=0;i<MM_CAMERA_BUF_CB_MAX;i++) {
        CDBG("%s Got notify: s_q->cnt = %d, t_q->cnt = %d, buf_cb = %x, "
             "data.used = %d ", __func__, s_q->cnt, t_q->cnt,
             (uint32_t)my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buf_cb[i].cb,
             my_obj->poll_threads[MM_CAMERA_CH_SNAPSHOT].data.used);
        if((s_q->cnt && t_q->cnt && my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buf_cb[i].cb) &&
                (my_obj->poll_threads[MM_CAMERA_CH_SNAPSHOT].data.used == 1)) {
            data.type = MM_CAMERA_CH_SNAPSHOT;
            frame = mm_camera_stream_frame_deq(s_q);
            data.snapshot.main.frame = &frame->frame;
            data.snapshot.main.idx = frame->idx;
            frame = mm_camera_stream_frame_deq(t_q);
            data.snapshot.thumbnail.frame = &frame->frame;
            data.snapshot.thumbnail.idx = frame->idx;
            my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.ref_count[data.snapshot.main.idx]++;
            my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.thumbnail.frame.ref_count[data.snapshot.thumbnail.idx]++;
            if(my_obj->poll_threads[MM_CAMERA_CH_SNAPSHOT].data.used == 1){
                buf_cb = my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buf_cb[i];
                buf_cb.cb(&data,buf_cb.user_data);
                my_obj->snap_burst_num_by_user -= 1;
                CDBG("%s: burst number =%d", __func__, my_obj->snap_burst_num_by_user);
                delivered = 1;
            }
        }
    }
    pthread_mutex_unlock(&my_obj->ch[MM_CAMERA_CH_SNAPSHOT].mutex);
    CDBG("%s Delivered = %d ", __func__, delivered );
    if(delivered) {
        mm_camera_event_t edata;
        /*for( i=0;i<MM_CAMERA_BUF_CB_MAX;i++){
            buf_cb = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buf_cb[i];
            if((buf_cb) && (my_obj->poll_threads[MM_CAMERA_CH_SNAPSHOT].data.used == 1)) {
                buf_cb->cb(&data,buf_cb->user_data);
            }
        }*/
        edata.event_type = MM_CAMERA_EVT_TYPE_CH;
        edata.e.ch.evt = MM_CAMERA_CH_EVT_DATA_DELIVERY_DONE;
        edata.e.ch.ch = MM_CAMERA_CH_SNAPSHOT;
        mm_camera_poll_send_ch_event(my_obj, &edata);
    }
}

static void mm_camera_read_snapshot_main_frame(mm_camera_obj_t * my_obj)
{
    int rc = 0;
    int idx;
    mm_camera_stream_t *stream;
    mm_camera_frame_queue_t *q;

    q = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.readyq;
    stream = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main;
    idx =  mm_camera_read_msm_frame(my_obj,stream);
    if (idx < 0)
        return;

    CDBG("%s Read Snapshot frame %d ", __func__, idx);
    if(my_obj->op_mode == MM_CAMERA_OP_MODE_ZSL) {
        my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.ref_count[idx]++;
        /* Reset match to 0. */
        stream->frame.frame[idx].match = 0;
        stream->frame.frame[idx].valid_entry = 0;
        mm_camera_zsl_frame_cmp_and_enq(my_obj,
          &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.frame[idx], stream);
    } else {
        /* send to HAL */
        mm_camera_stream_frame_enq(q, &stream->frame.frame[idx]);
        if (!my_obj->full_liveshot)
          mm_camera_snapshot_send_snapshot_notify(my_obj);
        else
          mm_camera_snapshot_send_liveshot_notify(my_obj);
    }
}
static void mm_camera_read_snapshot_thumbnail_frame(mm_camera_obj_t * my_obj)
{
    int idx, rc = 0;
    mm_camera_stream_t *stream;
    mm_camera_frame_queue_t *q;

    q = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.thumbnail.frame.readyq;
    stream = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.thumbnail;
    idx =  mm_camera_read_msm_frame(my_obj,stream);
    if (idx < 0)
        return;
    if(my_obj->op_mode != MM_CAMERA_OP_MODE_ZSL) {
        mm_camera_stream_frame_enq(q, &stream->frame.frame[idx]);
        mm_camera_snapshot_send_snapshot_notify(my_obj);
    } else {
//        CDBG("%s: ZSL does not use thumbnail stream",  __func__);
        rc = mm_camera_stream_qbuf(my_obj, stream, idx);
//        CDBG("%s Q back thumbnail buffer rc = %d ", __func__, rc);
    }
}

static void mm_camera_read_video_frame(mm_camera_obj_t * my_obj)
{
    int idx, rc = 0;
    mm_camera_stream_t *stream;
    mm_camera_frame_queue_t *q;
    int i;
    stream = &my_obj->ch[MM_CAMERA_CH_VIDEO].video.video;
    idx =  mm_camera_read_msm_frame(my_obj,stream);
    if (idx < 0)
        return;
    ALOGE("Video thread locked");
    pthread_mutex_lock(&my_obj->ch[MM_CAMERA_CH_VIDEO].mutex);
    for( i=0;i<MM_CAMERA_BUF_CB_MAX;i++) {
        if((my_obj->ch[MM_CAMERA_CH_VIDEO].buf_cb[i].cb) &&
                (my_obj->poll_threads[MM_CAMERA_CH_VIDEO].data.used == 1)){
            mm_camera_ch_data_buf_t data;
            data.type = MM_CAMERA_CH_VIDEO;
            data.video.main.frame = NULL;
            data.video.main.idx = -1;
            data.video.video.idx = idx;
            data.video.video.frame = &my_obj->ch[MM_CAMERA_CH_VIDEO].video.video.
                frame.frame[idx].frame;
            my_obj->ch[MM_CAMERA_CH_VIDEO].video.video.frame.ref_count[idx]++;
            ALOGE("Video thread callback issued");
            my_obj->ch[MM_CAMERA_CH_VIDEO].buf_cb[i].cb(&data,
                                    my_obj->ch[MM_CAMERA_CH_VIDEO].buf_cb[i].user_data);
            ALOGE("Video thread callback returned");
            if( my_obj->ch[MM_CAMERA_CH_VIDEO].buf_cb[i].cb_type==MM_CAMERA_BUF_CB_COUNT ) {
                ALOGE("<DEBUG>:%s: Additional cb called for buffer %p:%d",__func__,stream,idx);
                if(--(my_obj->ch[MM_CAMERA_CH_VIDEO].buf_cb[i].cb_count) == 0 )
                    my_obj->ch[MM_CAMERA_CH_VIDEO].buf_cb[i].cb=NULL;
            }
        }
    }
    pthread_mutex_unlock(&my_obj->ch[MM_CAMERA_CH_VIDEO].mutex);
    ALOGE("Video thread unlocked");
}

static void mm_camera_read_video_main_frame(mm_camera_obj_t * my_obj)
{
    int rc = 0;
    return;rc;
}

static void mm_camera_read_zsl_main_frame(mm_camera_obj_t * my_obj)
{
    int idx, rc = 0;
    mm_camera_stream_t *stream;
    mm_camera_frame_queue_t *q;
    mm_camera_frame_t *frame;
    int cnt, watermark;

    q =   &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main.frame.readyq;
    stream = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.main;
    idx =  mm_camera_read_msm_frame(my_obj,stream);
    if (idx < 0)
        return;

    CDBG("%s: Enqueuing frame id: %d", __func__, idx);
    mm_camera_stream_frame_enq(q, &stream->frame.frame[idx]);
    cnt = mm_camera_stream_frame_get_q_cnt(q);
    watermark = my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buffering_frame.water_mark;

    CDBG("%s: Watermark: %d Queue in a frame: %d", __func__, watermark, cnt);
    if(watermark < cnt) {
        /* water overflow, queue head back to kernel */
        frame = mm_camera_stream_frame_deq(q);
        if(frame) {
            rc = mm_camera_stream_qbuf(my_obj, stream, frame->idx);
            if(rc < 0) {
                CDBG("%s: mm_camera_stream_qbuf(idx=%d) err=%d\n",
                     __func__, frame->idx, rc);
                return;
            }
        }
    }
    mm_camera_check_pending_zsl_frames(my_obj, MM_CAMERA_CH_SNAPSHOT);
}

static void mm_camera_read_zsl_postview_frame(mm_camera_obj_t * my_obj)
{
    int idx, rc = 0;
    mm_camera_stream_t *stream;
    mm_camera_frame_queue_t *q;
    mm_camera_frame_t *frame;
    int cnt, watermark;
    q = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.thumbnail.frame.readyq;
    stream = &my_obj->ch[MM_CAMERA_CH_SNAPSHOT].snapshot.thumbnail;
    idx =  mm_camera_read_msm_frame(my_obj,stream);
    if (idx < 0)
        return;
    mm_camera_stream_frame_enq(q, &stream->frame.frame[idx]);
    watermark = my_obj->ch[MM_CAMERA_CH_SNAPSHOT].buffering_frame.water_mark;
    cnt = mm_camera_stream_frame_get_q_cnt(q);
    if(watermark < cnt) {
        /* water overflow, queue head back to kernel */
        frame = mm_camera_stream_frame_deq(q);
        if(frame) {
            rc = mm_camera_stream_qbuf(my_obj, stream, frame->idx);
            if(rc < 0) {
                CDBG("%s: mm_camera_stream_qbuf(idx=%d) err=%d\n",
                     __func__, frame->idx, rc);
                return;
            }
        }
    }
    mm_camera_check_pending_zsl_frames(my_obj, MM_CAMERA_CH_SNAPSHOT);
}

void mm_camera_msm_data_notify(mm_camera_obj_t * my_obj, int fd,
                               mm_camera_stream_type_t stream_type)
{
    switch(stream_type) {
    case MM_CAMERA_STREAM_RAW:
        mm_camera_read_raw_frame(my_obj);
        break;
    case MM_CAMERA_STREAM_PREVIEW:
        mm_camera_read_preview_frame(my_obj);
        break;
    case MM_CAMERA_STREAM_SNAPSHOT:
        mm_camera_read_snapshot_main_frame(my_obj);
        break;
    case MM_CAMERA_STREAM_THUMBNAIL:
        mm_camera_read_snapshot_thumbnail_frame(my_obj);
        break;
    case MM_CAMERA_STREAM_VIDEO:
        mm_camera_read_video_frame(my_obj);
        break;
    case MM_CAMERA_STREAM_VIDEO_MAIN:
        mm_camera_read_video_main_frame(my_obj);
        break;
    default:
        break;
    }
}

static mm_camera_channel_type_t mm_camera_image_mode_to_ch(int image_mode)
{
    switch(image_mode) {
    case MSM_V4L2_EXT_CAPTURE_MODE_PREVIEW:
        return MM_CAMERA_CH_PREVIEW;
    case MSM_V4L2_EXT_CAPTURE_MODE_MAIN:
    case MSM_V4L2_EXT_CAPTURE_MODE_THUMBNAIL:
        return MM_CAMERA_CH_SNAPSHOT;
    case MSM_V4L2_EXT_CAPTURE_MODE_VIDEO:
        return MM_CAMERA_CH_VIDEO;
    case MSM_V4L2_EXT_CAPTURE_MODE_RAW:
        return MM_CAMERA_CH_RAW;
    default:
        return MM_CAMERA_CH_MAX;
    }
}

void mm_camera_dispatch_app_event(mm_camera_obj_t *my_obj, mm_camera_event_t *event)
{
    int i;
    mm_camera_evt_obj_t evtcb;

    if(event->event_type <  MM_CAMERA_EVT_TYPE_MAX) {
      pthread_mutex_lock(&my_obj->mutex);
      memcpy(&evtcb,
       &my_obj->evt[event->event_type],
       sizeof(mm_camera_evt_obj_t));
      pthread_mutex_unlock(&my_obj->mutex);
      for(i = 0; i < MM_CAMERA_EVT_ENTRY_MAX; i++) {
        if(evtcb.evt[i].evt_cb) {
          evtcb.evt[i].evt_cb(event, evtcb.evt[i].user_data);
        }
      }
    }
}

void mm_camera_histo_mmap(mm_camera_obj_t * my_obj, mm_camera_event_t *evt)
{
    int i, *ret;
    int rc = MM_CAMERA_OK;
    off_t offset = 0;

    CDBG("%s", __func__);
    if(!evt || evt->e.info.e.histo_mem_info.num == 0 || my_obj->hist_mem_map.num) {
        for(i = 0; i < my_obj->hist_mem_map.num; i++) {
            munmap((void *)my_obj->hist_mem_map.entry[i].vaddr,
            my_obj->hist_mem_map.entry[i].cookie.length);
        }
        memset(&my_obj->hist_mem_map, 0,
            sizeof(my_obj->hist_mem_map));
    }
    if(!evt)
        return;
    if (evt->e.info.e.histo_mem_info.num > 0) {
        for(i = 0; i < evt->e.info.e.histo_mem_info.num; i++) {
            my_obj->hist_mem_map.entry[i].cookie.cookie =
            evt->e.info.e.histo_mem_info.vaddr[i];
            my_obj->hist_mem_map.entry[i].cookie.length =
            evt->e.info.e.histo_mem_info.buf_len;
            my_obj->hist_mem_map.entry[i].cookie.mem_type =
            evt->e.info.e.histo_mem_info.pmem_type;
            rc = mm_camera_util_s_ctrl(my_obj->ctrl_fd, MSM_V4L2_PID_MMAP_ENTRY,
                    (int)&my_obj->hist_mem_map.entry[i].cookie);
            if( rc < 0) {
                CDBG("%s: MSM_V4L2_PID_MMAP_ENTRY error", __func__);
                goto err;
            }
            ret = mmap(NULL, my_obj->hist_mem_map.entry[i].cookie.length,
                  (PROT_READ  | PROT_WRITE), MAP_SHARED, my_obj->ctrl_fd, offset);
            if(ret == MAP_FAILED) {
                CDBG("%s: mmap error at idx %d", __func__, i);
                goto err;
            }
            my_obj->hist_mem_map.entry[i].vaddr = (uint32_t)ret;
            my_obj->hist_mem_map.num++;
            CDBG("%s: mmap, idx=%d,cookie=0x%x,length=%d,pmem_type=%d,vaddr=0x%x",
                __func__, i,
                my_obj->hist_mem_map.entry[i].cookie.cookie,
                my_obj->hist_mem_map.entry[i].cookie.length,
                my_obj->hist_mem_map.entry[i].cookie.mem_type,
                my_obj->hist_mem_map.entry[i].vaddr);
        }
    }
    return;
err:
    for(i = 0; i < my_obj->hist_mem_map.num; i++) {
        munmap((void *)my_obj->hist_mem_map.entry[i].vaddr,
        my_obj->hist_mem_map.entry[i].cookie.length);
    }
    memset(&my_obj->hist_mem_map, 0,
    sizeof(my_obj->hist_mem_map));
}

static int mm_camera_histo_fill_vaddr(mm_camera_obj_t *my_obj, mm_camera_event_t *evt)
{
    int i, rc = -1;

    for(i = 0; i < my_obj->hist_mem_map.num; i++) {
        if(my_obj->hist_mem_map.entry[i].cookie.cookie ==
                evt->e.stats.e.stats_histo.cookie) {
            rc = MM_CAMERA_OK;
            evt->e.stats.e.stats_histo.histo_info =
              my_obj->hist_mem_map.entry[i].vaddr + 512;
            evt->e.stats.e.stats_histo.histo_len =
              my_obj->hist_mem_map.entry[i].cookie.length - 512;
            CDBG("%s: histo stats addr: cookie=0x%x, vaddr = 0x%x, len = %d",
                __func__, my_obj->hist_mem_map.entry[i].cookie.cookie,
                my_obj->hist_mem_map.entry[i].vaddr,
                my_obj->hist_mem_map.entry[i].cookie.length);
            CDBG("%s: histo stats addr: vaddr = 0x%x, len = %d",
                __func__, evt->e.stats.e.stats_histo.histo_info,
                evt->e.stats.e.stats_histo.histo_len);
            break;
        }
    }
    return rc;
}

void mm_camera_msm_evt_notify(mm_camera_obj_t * my_obj, int fd)
{
    struct v4l2_event ev;
    int rc;
    mm_camera_event_t *evt = NULL;

    memset(&ev, 0, sizeof(ev));
    rc = ioctl(fd, VIDIOC_DQEVENT, &ev);
    evt = (mm_camera_event_t *)ev.u.data;

    if (rc >= 0) {
//        CDBG("%s: VIDIOC_DQEVENT type = 0x%x, app event type = %d\n",
//            __func__, ev.type, evt->event_type);
        switch(evt->event_type) {
        case MM_CAMERA_EVT_TYPE_INFO:
            switch(evt->e.info.event_id) {
            case MM_CAMERA_INFO_EVT_HISTO_MEM_INFO:
                /* now mm_camear_interface2 hides the
                 * pmem mapping logic from HAL */
                mm_camera_histo_mmap(my_obj, evt);
                return;
            default:
                break;
            }
            break;
        case MM_CAMERA_EVT_TYPE_STATS:
            switch(evt->e.stats.event_id) {
            case MM_CAMERA_STATS_EVT_HISTO:
                rc = mm_camera_histo_fill_vaddr(my_obj, evt);
                if(rc != MM_CAMERA_OK) {
                    CDBG("%s:cannot find histo stat's vaddr (cookie=0x%x)",
                        __func__, evt->e.stats.e.stats_histo.cookie);
                    return;
                }
                break;
           default:
               break;
           }
           break;
        default:
            break;
        }
        mm_camera_dispatch_app_event(my_obj, evt);
    }
}
