/*
Copyright (c) 2011, Code Aurora Forum. All rights reserved.

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

typedef enum {
    /* ask the channel to flash out the queued frames. */
    MM_CAMERA_PIPE_CMD_FLASH_QUEUED_FRAME,
    /* ask ctrl fd to generate ch event to HAL */
    MM_CAMERA_PIPE_CMD_CH_EVENT,
    /*start*/
    MM_CAMERA_PIPE_CMD_ADD_CH,

    /*stop*/
    MM_CAMERA_PIPE_CMD_DEL_CH,

    /* exit */
    MM_CAMERA_PIPE_CMD_EXIT,
    /* max count */
    MM_CAMERA_PIPE_CMD_MAX
} mm_camera_pipe_cmd_type_t;

typedef enum {
    MM_CAMERA_POLL_TASK_STATE_POLL,     /* polling pid in polling state. */
    MM_CAMERA_POLL_TASK_STATE_MAX
} mm_camera_poll_task_state_type_t;

typedef struct {
    uint8_t cmd;
    mm_camera_event_t event;
} mm_camera_sig_evt_t;

static int32_t mm_camera_poll_sig(mm_camera_poll_thread_t *poll_cb,
                                  uint32_t cmd)
{
    /* send through pipe */
    /* get the mutex */
    mm_camera_sig_evt_t cmd_evt;
    memset(&cmd_evt, 0, sizeof(cmd_evt));
    cmd_evt.cmd = cmd;
    int len;
    CDBG("%s: begin", __func__);
    pthread_mutex_lock(&poll_cb->mutex);
    /* reset the statue to false */
    poll_cb->status = FALSE;
    /* send cmd to worker */
    len = write(poll_cb->data.pfds[1], &cmd_evt, sizeof(cmd_evt));
    if(len < 1) {
      CDBG("%s: len = %d, errno = %d", __func__, len, errno);
      //pthread_mutex_unlock(&poll_cb->mutex);
      //return -1;
    }
    CDBG("%s: begin IN mutex write done, len = %d", __func__, len);
    /* wait till worker task gives positive signal */
    if(FALSE == poll_cb->status) {
      CDBG("%s: wait", __func__);
        pthread_cond_wait(&poll_cb->cond_v, &poll_cb->mutex);
    }
    /* done */
    pthread_mutex_unlock(&poll_cb->mutex);
    CDBG("%s: end, len = %d, size = %d", __func__, len, sizeof(cmd_evt));
    return MM_CAMERA_OK;
}

static void mm_camera_poll_sig_done(mm_camera_poll_thread_t *poll_cb)
{
    pthread_mutex_lock(&poll_cb->mutex);
    poll_cb->status = TRUE;
    pthread_cond_signal(&poll_cb->cond_v);
    CDBG("%s: done, in mutex", __func__);
    pthread_mutex_unlock(&poll_cb->mutex);
}

static int32_t mm_camera_poll_proc_msm(mm_camera_poll_thread_t *poll_cb, struct pollfd *fds)
{
   int i;

    for(i = 0; i < poll_cb->data.num_fds-1; i++) {
        /*Checking for data events*/
        if((poll_cb->data.poll_type == MM_CAMERA_POLL_TYPE_CH) &&
           (fds[i].revents & POLLIN) &&
           (fds[i].revents & POLLRDNORM)) {
            if(poll_cb->data.used) {
                mm_camera_msm_data_notify(poll_cb->data.my_obj,
                                        fds[i].fd,
                                        poll_cb->data.poll_streams[i]->stream_type);
            }

        }
        /*Checking for ctrl events*/
        if((poll_cb->data.poll_type == MM_CAMERA_POLL_TYPE_EVT) &&
           (fds[i].revents & POLLPRI)) {
          CDBG("%s: mm_camera_msm_evt_notify\n", __func__);
          mm_camera_msm_evt_notify(poll_cb->data.my_obj, fds[i].fd);
        }

    }
    return 0;
}

static void cm_camera_poll_set_state(mm_camera_poll_thread_t *poll_cb,
                                     mm_camera_poll_task_state_type_t state)
{
    poll_cb->data.state = state;
}

static void mm_camera_poll_proc_pipe(mm_camera_poll_thread_t *poll_cb)
{
    ssize_t read_len;
    int i;
    mm_camera_sig_evt_t cmd_evt;
    read_len = read(poll_cb->data.pfds[0], &cmd_evt, sizeof(cmd_evt));
    CDBG("%s: read_fd = %d, read_len = %d, expect_len = %d",
         __func__, poll_cb->data.pfds[0], (int)read_len, (int)sizeof(cmd_evt));
    switch(cmd_evt.cmd) {
    case MM_CAMERA_PIPE_CMD_FLASH_QUEUED_FRAME:
      mm_camera_dispatch_buffered_frames(poll_cb->data.my_obj,
                                         poll_cb->data.ch_type);
      break;
    case MM_CAMERA_PIPE_CMD_CH_EVENT: {
      mm_camera_event_t *event = &cmd_evt.event;
      CDBG("%s: ch event, type=0x%x, ch=%d, evt=%d",
           __func__, event->event_type, event->e.ch.ch, event->e.ch.evt);
      mm_camera_dispatch_app_event(poll_cb->data.my_obj, event);
      break;
    }
    case MM_CAMERA_PIPE_CMD_ADD_CH:
        if(poll_cb->data.poll_type == MM_CAMERA_POLL_TYPE_CH) {
            for(i = 0; i < MM_CAMERA_CH_STREAM_MAX; i++) {
                if(poll_cb->data.poll_streams[i]) {
                    poll_cb->data.poll_fd[poll_cb->data.num_fds + i] = poll_cb->data.poll_streams[i]->fd;
                }
            }
        }
        poll_cb->data.num_fds += mm_camera_ch_util_get_num_stream(poll_cb->data.my_obj,
                                                                      poll_cb->data.ch_type);
        poll_cb->data.used = 1;
        CDBG("Num fds after MM_CAMERA_PIPE_CMD_ADD_CH = %d",poll_cb->data.num_fds);
        break;

    case MM_CAMERA_PIPE_CMD_DEL_CH:
        poll_cb->data.num_fds -= mm_camera_ch_util_get_num_stream(poll_cb->data.my_obj,
                                                                  poll_cb->data.ch_type);
        poll_cb->data.used = 0;
        CDBG("Num fds after MM_CAMERA_PIPE_CMD_DEL_CH = %d",poll_cb->data.num_fds);
        break;

    case MM_CAMERA_PIPE_CMD_EXIT:
    default:
        cm_camera_poll_set_state(poll_cb, MM_CAMERA_POLL_TASK_STATE_MAX);
        mm_camera_poll_sig_done(poll_cb);
        break;
    }
}

static int mm_camera_poll_ch_busy(mm_camera_obj_t * my_obj, int ch_type)
{
    int i;
    int used = 0;
    mm_camera_poll_thread_t *poll_cb = &my_obj->poll_threads[ch_type];
    pthread_mutex_lock(&poll_cb->mutex);
    used = poll_cb->data.used;
    pthread_mutex_unlock(&poll_cb->mutex);
    if(used)
        return 1;
    else
        return 0;
}
int32_t mm_camera_poll_dispatch_buffered_frames(mm_camera_obj_t * my_obj, int ch_type)
{
    mm_camera_poll_thread_t *poll_cb = &my_obj->poll_threads[ch_type];
    mm_camera_sig_evt_t cmd;
    int len;

    cmd.cmd = MM_CAMERA_PIPE_CMD_FLASH_QUEUED_FRAME;
    memset(&cmd.event, 0, sizeof(cmd.event));
    pthread_mutex_lock(&poll_cb->mutex);
    len = write(poll_cb->data.pfds[1], &cmd, sizeof(cmd));
    pthread_mutex_unlock(&poll_cb->mutex);
    return MM_CAMERA_OK;
}

int mm_camera_poll_busy(mm_camera_obj_t * my_obj)
{
    int i;
    mm_camera_poll_thread_t *poll_cb;
    for(i = 0; i < (MM_CAMERA_POLL_THRAED_MAX - 1); i++) {
        if(mm_camera_poll_ch_busy(my_obj,  i) > 0)
          return 1;
    }
    return 0;
}

int mm_camera_poll_send_ch_event(mm_camera_obj_t * my_obj, mm_camera_event_t *event)
{
    mm_camera_poll_thread_t *poll_cb = &my_obj->poll_threads[MM_CAMERA_CH_MAX];
    mm_camera_sig_evt_t cmd;
    int len;

    cmd.cmd = MM_CAMERA_PIPE_CMD_CH_EVENT;
    memcpy(&cmd.event, event, sizeof(cmd.event));
    CDBG("%s: ch event, type=0x%x, ch=%d, evt=%d, poll_type = %d, read_fd=%d, write_fd=%d",
        __func__, event->event_type, event->e.ch.ch, event->e.ch.evt, poll_cb->data.poll_type,
        poll_cb->data.pfds[0], poll_cb->data.pfds[1]);
    pthread_mutex_lock(&poll_cb->mutex);
    len = write(poll_cb->data.pfds[1], &cmd, sizeof(cmd));
    pthread_mutex_unlock(&poll_cb->mutex);
    return MM_CAMERA_OK;
}

static void *mm_camera_poll_fn(mm_camera_poll_thread_t *poll_cb)
{
    int rc = 0, i;
    struct pollfd fds[MM_CAMERA_CH_STREAM_MAX+1];
    int timeoutms;
    CDBG("%s: poll type = %d, num_fd = %d\n",
         __func__, poll_cb->data.poll_type, poll_cb->data.num_fds);
    do {
        for(i = 0; i < poll_cb->data.num_fds; i++) {
            fds[i].fd = poll_cb->data.poll_fd[i];
            fds[i].events = POLLIN|POLLRDNORM|POLLPRI;
        }
        timeoutms = poll_cb->data.timeoutms;
        rc = poll(fds, poll_cb->data.num_fds, timeoutms);
        if(rc > 0) {
            if((fds[0].revents & POLLIN) && (fds[0].revents & POLLRDNORM))
                mm_camera_poll_proc_pipe(poll_cb);
            else
                mm_camera_poll_proc_msm(poll_cb, &fds[1]);
        } else {
            /* in error case sleep 10 us and then continue. hard coded here */
            usleep(10);
            continue;
        }
    } while (poll_cb->data.state == MM_CAMERA_POLL_TASK_STATE_POLL);
    return NULL;
}

static void *mm_camera_poll_thread(void *data)
{
    int rc = 0;
    int i;
    void *ret = NULL;
    mm_camera_poll_thread_t *poll_cb = data;

    poll_cb->data.poll_fd[poll_cb->data.num_fds++] = poll_cb->data.pfds[0];
    switch(poll_cb->data.poll_type) {
    case MM_CAMERA_POLL_TYPE_EVT:
        poll_cb->data.poll_fd[poll_cb->data.num_fds++] =
          ((mm_camera_obj_t *)(poll_cb->data.my_obj))->ctrl_fd;
        break;
    case MM_CAMERA_POLL_TYPE_CH:
    default:
        break;
    }
    mm_camera_poll_sig_done(poll_cb);
    ret = mm_camera_poll_fn(poll_cb);
    return ret;
}

int mm_camera_poll_start(mm_camera_obj_t * my_obj,  mm_camera_poll_thread_t *poll_cb)
{
    pthread_mutex_lock(&poll_cb->mutex);
    poll_cb->status = 0;
    pthread_create(&poll_cb->data.pid, NULL, mm_camera_poll_thread, (void *)poll_cb);
    if(!poll_cb->status) {
        pthread_cond_wait(&poll_cb->cond_v, &poll_cb->mutex);
    }
    pthread_mutex_unlock(&poll_cb->mutex);
    return MM_CAMERA_OK;
}

int mm_camera_poll_stop(mm_camera_obj_t * my_obj, mm_camera_poll_thread_t *poll_cb)
{
    CDBG("%s, my_obj=0x%x\n", __func__, (uint32_t)my_obj);
    mm_camera_poll_sig(poll_cb, MM_CAMERA_PIPE_CMD_EXIT);
    if (pthread_join(poll_cb->data.pid, NULL) != 0) {
        CDBG("%s: pthread dead already\n", __func__);
    }
    return MM_CAMERA_OK;
}


int mm_camera_poll_thread_add_ch(mm_camera_obj_t * my_obj, int ch_type)
{
    mm_camera_poll_thread_t *poll_cb = &my_obj->poll_threads[ch_type];
    mm_camera_sig_evt_t cmd;
    int len;

    CDBG("Run thread for ch_type = %d ",ch_type);
    cmd.cmd = MM_CAMERA_PIPE_CMD_ADD_CH;
    poll_cb->data.ch_type = ch_type;

    pthread_mutex_lock(&poll_cb->mutex);
    len = write(poll_cb->data.pfds[1], &cmd, sizeof(cmd));
    pthread_mutex_unlock(&poll_cb->mutex);
    poll_cb->data.used = 1;
    return MM_CAMERA_OK;
}

int mm_camera_poll_thread_del_ch(mm_camera_obj_t * my_obj, int ch_type)
{
    mm_camera_poll_thread_t *poll_cb = &my_obj->poll_threads[ch_type];
    mm_camera_sig_evt_t cmd;
    int len;

    CDBG("Stop thread for ch_type = %d ",ch_type);
    cmd.cmd = MM_CAMERA_PIPE_CMD_DEL_CH;
    poll_cb->data.ch_type = (mm_camera_channel_type_t)ch_type;

    pthread_mutex_lock(&poll_cb->mutex);
    len = write(poll_cb->data.pfds[1], &cmd, sizeof(cmd));
    pthread_mutex_unlock(&poll_cb->mutex);
    poll_cb->data.used = 0;
    return MM_CAMERA_OK;

}


int mm_camera_poll_thread_launch(mm_camera_obj_t * my_obj, int ch_type)
{
    int rc = MM_CAMERA_OK;
    mm_camera_poll_thread_t *poll_cb = &my_obj->poll_threads[ch_type];
    if(mm_camera_poll_ch_busy(my_obj, ch_type) > 0) {
        CDBG_ERROR("%s: err, poll thread of channel %d already running. cam_id=%d\n",
             __func__, ch_type, my_obj->my_id);
        return -MM_CAMERA_E_INVALID_OPERATION;
    }
    poll_cb->data.ch_type = ch_type;
    rc = pipe(poll_cb->data.pfds);
    if(rc < 0) {
        CDBG_ERROR("%s: camera_id = %d, pipe open rc=%d\n", __func__, my_obj->my_id, rc);
        rc = - MM_CAMERA_E_GENERAL;
    }
    CDBG("%s: ch = %d, poll_type = %d, read fd = %d, write fd = %d",
        __func__, ch_type, poll_cb->data.poll_type,
        poll_cb->data.pfds[0], poll_cb->data.pfds[1]);
    poll_cb->data.my_obj = my_obj;
    poll_cb->data.used = 0;
    poll_cb->data.timeoutms = -1;  /* Infinite seconds */

    if(ch_type < MM_CAMERA_CH_MAX) {
        poll_cb->data.poll_type = MM_CAMERA_POLL_TYPE_CH;
        mm_camera_ch_util_get_stream_objs(my_obj, ch_type,
                                      &poll_cb->data.poll_streams[0],
                                      &poll_cb->data.poll_streams[1]);
    } else{
        poll_cb->data.poll_type = MM_CAMERA_POLL_TYPE_EVT;
    }

    ALOGE("%s: ch_type = %d, poll_type = %d, read fd = %d, write fd = %d",
         __func__, ch_type, poll_cb->data.poll_type,
         poll_cb->data.pfds[0], poll_cb->data.pfds[1]);
    /* launch the thread */
    rc = mm_camera_poll_start(my_obj, poll_cb);
    return rc;
}

int mm_camera_poll_thread_release(mm_camera_obj_t * my_obj, int ch_type)
{
    int rc = MM_CAMERA_OK;
    mm_camera_poll_thread_t *poll_cb = &my_obj->poll_threads[ch_type];
    if(MM_CAMERA_POLL_TASK_STATE_MAX == poll_cb->data.state) {
        CDBG("%s: err, poll thread of channel % is not running. cam_id=%d\n",
             __func__, ch_type, my_obj->my_id);
        return -MM_CAMERA_E_INVALID_OPERATION;
    }
    rc = mm_camera_poll_stop(my_obj, poll_cb);

    if(poll_cb->data.pfds[0]) {
        close(poll_cb->data.pfds[0]);
    }
    if(poll_cb->data.pfds[1]) {
        close(poll_cb->data.pfds[1]);
    }
    memset(&poll_cb->data, 0, sizeof(poll_cb->data));
    return MM_CAMERA_OK;
}

void mm_camera_poll_threads_init(mm_camera_obj_t * my_obj)
{
    int i;
    mm_camera_poll_thread_t *poll_cb;

    for(i = 0; i < MM_CAMERA_POLL_THRAED_MAX; i++) {
        poll_cb = &my_obj->poll_threads[i];
        pthread_mutex_init(&poll_cb->mutex, NULL);
        pthread_cond_init(&poll_cb->cond_v, NULL);
    }
}

void mm_camera_poll_threads_deinit(mm_camera_obj_t * my_obj)
{
    int i;
    mm_camera_poll_thread_t *poll_cb;

    for(i = 0; i < MM_CAMERA_POLL_THRAED_MAX; i++) {
        poll_cb = &my_obj->poll_threads[i];
        if(poll_cb->data.used)
            mm_camera_poll_stop(my_obj, poll_cb);
        pthread_mutex_destroy(&poll_cb->mutex);
        pthread_cond_destroy(&poll_cb->cond_v);
        memset(poll_cb, 0, sizeof(mm_camera_poll_thread_t));
    }
}
