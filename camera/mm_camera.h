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

#ifndef __MM_CAMERA_H__
#define __MM_CAMERA_H__

typedef enum {
    MM_CAMERA_STREAM_STATE_NOTUSED,     /* not used */
    MM_CAMERA_STREAM_STATE_ACQUIRED,    /* acquired, fd opened  */
    MM_CAMERA_STREAM_STATE_CFG,             /* fmt & dim configured */
    MM_CAMERA_STREAM_STATE_REG,             /* buf regged, stream off */
    MM_CAMERA_STREAM_STATE_ACTIVE,      /* stream on */
    MM_CAMERA_STREAM_STATE_MAX
} mm_camera_stream_state_type_t;

typedef enum {
    MM_CAMERA_STATE_EVT_NOTUSED,
    MM_CAMERA_STATE_EVT_ACQUIRE,
    MM_CAMERA_STATE_EVT_ATTR,
    MM_CAMERA_STATE_EVT_RELEASE,
    MM_CAMERA_STATE_EVT_REG_BUF_CB,
    MM_CAMERA_STATE_EVT_SET_FMT,
    MM_CAMERA_STATE_EVT_SET_DIM,
    MM_CAMERA_STATE_EVT_REG_BUF, // request amount of buffers and enqueue all buffers to kernel
    MM_CAMERA_STATE_EVT_UNREG_BUF,
    MM_CAMERA_STATE_EVT_STREAM_ON,
    MM_CAMERA_STATE_EVT_STREAM_OFF,
    MM_CAMERA_STATE_EVT_QBUF,
    MM_CAMERA_STATE_EVT_GET_CROP,
    MM_CAMERA_STATE_EVT_DISPATCH_BUFFERED_FRAME,
    MM_CAMERA_STATE_EVT_REQUEST_BUF, // request amount of buffers to kernel only
    MM_CAMERA_STATE_EVT_ENQUEUE_BUF, // enqueue some of buffers to kernel only
    MM_CAMERA_STATE_EVT_MAX
} mm_camera_state_evt_type_t;

typedef struct {
    mm_camera_event_notify_t evt_cb;
    void * user_data;
} mm_camera_notify_cb_t;

typedef enum {
    MM_CAMERA_BUF_CB_ONCE,
    MM_CAMERA_BUF_CB_COUNT,
    MM_CAMERA_BUF_CB_INFINITE
} mm_camera_buf_cb_type_t;

typedef struct {
    mm_camera_buf_notify_t cb;
    mm_camera_buf_cb_type_t cb_type;
    uint32_t cb_count;
    void *user_data;
} mm_camera_buf_cb_t;

typedef enum {
    MM_CAMERA_STREAM_PIPE,
    MM_CAMERA_STREAM_PREVIEW,
    MM_CAMERA_STREAM_VIDEO,
    MM_CAMERA_STREAM_SNAPSHOT,
    MM_CAMERA_STREAM_THUMBNAIL,
    MM_CAMERA_STREAM_RAW,
    MM_CAMERA_STREAM_VIDEO_MAIN,
    MM_CAMERA_STREAM_MAX
} mm_camera_stream_type_t;

typedef struct mm_camera_frame_t mm_camera_frame_t;
struct mm_camera_frame_t{
    struct msm_frame frame;
    struct v4l2_plane planes[VIDEO_MAX_PLANES];
    uint8_t num_planes;
    int idx;
    int match;
    int valid_entry;
    mm_camera_frame_t *next;
};

typedef struct {
    pthread_mutex_t mutex;
    int cnt;
	int match_cnt;
    mm_camera_frame_t *head;
    mm_camera_frame_t *tail;
} mm_camera_frame_queue_t;

typedef struct {
    mm_camera_frame_queue_t readyq;
    int32_t num_frame;
    uint32_t frame_len;
    int8_t reg_flag;
    uint32_t frame_offset[MM_CAMERA_MAX_NUM_FRAMES];
    mm_camera_frame_t frame[MM_CAMERA_MAX_NUM_FRAMES];
    int8_t ref_count[MM_CAMERA_MAX_NUM_FRAMES];
    int32_t use_multi_fd;
    int qbuf;
    pthread_mutex_t mutex;
} mm_camera_stream_frame_t;

typedef struct {
    int32_t fd;
    mm_camera_stream_state_type_t state;
    mm_camera_stream_type_t stream_type;
    struct v4l2_format fmt;
    cam_format_t cam_fmt;
    mm_camera_stream_frame_t frame;
} mm_camera_stream_t;

typedef struct {
    mm_camera_stream_t stream;
    mm_camera_raw_streaming_type_t mode;
} mm_camera_ch_raw_t;

typedef struct {
    mm_camera_stream_t stream;
} mm_camera_ch_preview_t;

typedef struct {
    mm_camera_stream_t thumbnail;
    mm_camera_stream_t main;
    int    delivered_cnt;
    int8_t pending_cnt;
} mm_camera_ch_snapshot_t;

typedef struct {
    int8_t fifo[MM_CAMERA_MAX_FRAME_NUM];
    int8_t low;
    int8_t high;
    int8_t len;
    int8_t water_mark;
} mm_camera_circule_fifo_t;

typedef struct {
    mm_camera_stream_t video;
    mm_camera_stream_t main;
    uint8_t has_main;
} mm_camera_ch_video_t;

#define MM_CAMERA_BUF_CB_MAX 4
typedef struct {
    mm_camera_channel_type_t type;
    pthread_mutex_t mutex;
    uint8_t acquired;
    mm_camera_buf_cb_t buf_cb[MM_CAMERA_BUF_CB_MAX];
    mm_camera_channel_attr_buffering_frame_t buffering_frame;
    union {
        mm_camera_ch_raw_t raw;
        mm_camera_ch_preview_t preview;
        mm_camera_ch_snapshot_t snapshot;
        mm_camera_ch_video_t video;
    };
} mm_camera_ch_t;

#define MM_CAMERA_EVT_ENTRY_MAX 4
typedef struct {
    mm_camera_event_notify_t evt_cb;
    void *user_data;
} mm_camera_evt_entry_t;

typedef struct {
    mm_camera_evt_entry_t evt[MM_CAMERA_EVT_ENTRY_MAX];
    int reg_count;
} mm_camera_evt_obj_t;

#define MM_CAMERA_CH_STREAM_MAX 2
typedef enum {
    MM_CAMERA_POLL_TYPE_EVT,
    MM_CAMERA_POLL_TYPE_CH,
    MM_CAMERA_POLL_TYPE_MAX
} mm_camera_poll_thread_type_t;

typedef struct {
    mm_camera_poll_thread_type_t poll_type;
    int32_t pfds[2];
    int poll_fd[MM_CAMERA_CH_STREAM_MAX+1];
    int num_fds;
    int used;
    pthread_t pid;
    int32_t state;
    int timeoutms;
    void *my_obj;
    mm_camera_channel_type_t ch_type;
    mm_camera_stream_t *poll_streams[MM_CAMERA_CH_STREAM_MAX];
    uint32_t cmd;
} mm_camera_poll_thread_data_t;

typedef struct {
    pthread_mutex_t mutex;
    pthread_cond_t cond_v;
    int32_t status;
    mm_camera_poll_thread_data_t data;
} mm_camera_poll_thread_t;

typedef struct {
    int stream_on_count_cfg;
    int stream_off_count_cfg;
    int stream_on_count;
    int stream_off_count;
} mm_camera_ch_stream_count_t;
#define MM_CAMERA_POLL_THRAED_MAX (MM_CAMERA_CH_MAX+1)

typedef struct {
  struct msm_mem_map_info cookie;
  uint32_t vaddr;
} mm_camera_mem_map_entry_t;

#define MM_CAMERA_MEM_MAP_MAX 8
typedef struct {
  int num;
  mm_camera_mem_map_entry_t entry[MM_CAMERA_MEM_MAP_MAX];
} mm_camera_mem_map_t;

typedef struct {
    int8_t my_id;
    camera_mode_t current_mode;
    mm_camera_op_mode_type_t op_mode;
    mm_camera_notify_cb_t *notify;
    mm_camera_ch_t ch[MM_CAMERA_CH_MAX];
    int ref_count;
    uint32_t ch_streaming_mask;
    int32_t ctrl_fd;
    int32_t ds_fd; // domain socket fd
    cam_ctrl_dimension_t dim;
    cam_prop_t properties;
    pthread_mutex_t mutex;
    mm_camera_evt_obj_t evt[MM_CAMERA_EVT_TYPE_MAX];
    mm_camera_ch_stream_count_t ch_stream_count[MM_CAMERA_CH_MAX];
    uint32_t evt_type_mask;
    mm_camera_poll_thread_t poll_threads[MM_CAMERA_POLL_THRAED_MAX];
    mm_camera_mem_map_t hist_mem_map;
    int full_liveshot;
    int snap_burst_num_by_user;
} mm_camera_obj_t;

#define MM_CAMERA_DEV_NAME_LEN 32
#define MM_CAMERA_DEV_OPEN_TRIES 2
#define MM_CAMERA_DEV_OPEN_RETRY_SLEEP 20

typedef struct {
    mm_camera_t camera[MSM_MAX_CAMERA_SENSORS];
    int8_t num_cam;
    char video_dev_name[MSM_MAX_CAMERA_SENSORS][MM_CAMERA_DEV_NAME_LEN];
    mm_camera_obj_t *cam_obj[MSM_MAX_CAMERA_SENSORS];
} mm_camera_ctrl_t;

typedef struct {
    mm_camera_parm_type_t parm_type;
     void *p_value;
} mm_camera_parm_t;

extern int32_t mm_camera_stream_fsm_fn_vtbl (mm_camera_obj_t * my_obj,
                                            mm_camera_stream_t *stream,
                                            mm_camera_state_evt_type_t evt, void *val);
extern const char *mm_camera_util_get_dev_name(mm_camera_obj_t * my_obj);
extern int32_t mm_camera_util_s_ctrl( int32_t fd,
                                            uint32_t id, int32_t value);
extern int32_t mm_camera_util_g_ctrl( int32_t fd,
                                            uint32_t id, int32_t *value);
extern int32_t mm_camera_ch_fn(mm_camera_obj_t * my_obj,
                                            mm_camera_channel_type_t ch_type,
                                            mm_camera_state_evt_type_t evt, void *val);
extern int32_t mm_camera_action(mm_camera_obj_t *my_obj, uint8_t start,
                                            mm_camera_ops_type_t opcode, void *parm);
extern int32_t mm_camera_open(mm_camera_obj_t *my_obj,
                                            mm_camera_op_mode_type_t op_mode);
extern int32_t mm_camera_close(mm_camera_obj_t *my_obj);
extern int32_t mm_camera_start(mm_camera_obj_t *my_obj,
                                            mm_camera_ops_type_t opcode, void *parm);
extern int32_t mm_camera_stop(mm_camera_obj_t *my_obj,
                                            mm_camera_ops_type_t opcode, void *parm);
extern int32_t mm_camera_get_parm(mm_camera_obj_t * my_obj,
                                            mm_camera_parm_t *parm);
extern int32_t mm_camera_set_parm(mm_camera_obj_t * my_obj,
                                            mm_camera_parm_t *parm);
extern int32_t mm_camera_request_buf(mm_camera_obj_t * my_obj, mm_camera_reg_buf_t *buf);
extern int32_t mm_camera_enqueue_buf(mm_camera_obj_t * my_obj, mm_camera_reg_buf_t *buf);
extern int32_t mm_camera_prepare_buf(mm_camera_obj_t * my_obj, mm_camera_reg_buf_t *buf);
extern int32_t mm_camera_unprepare_buf(mm_camera_obj_t * my_obj, mm_camera_channel_type_t ch_type);
extern int mm_camera_poll_thread_launch(mm_camera_obj_t * my_obj, int ch_type);

int mm_camera_poll_thread_del_ch(mm_camera_obj_t * my_obj, int ch_type);
int mm_camera_poll_thread_add_ch(mm_camera_obj_t * my_obj, int ch_type);
extern int32_t mm_camera_poll_dispatch_buffered_frames(mm_camera_obj_t * my_obj, int ch_type);
extern int mm_camera_poll_thread_release(mm_camera_obj_t * my_obj, int ch_type);
extern void mm_camera_poll_threads_init(mm_camera_obj_t * my_obj);
extern void mm_camera_poll_threads_deinit(mm_camera_obj_t * my_obj);
extern int mm_camera_poll_busy(mm_camera_obj_t * my_obj);
extern void mm_camera_msm_data_notify(mm_camera_obj_t * my_obj, int fd,
                                            mm_camera_stream_type_t stream_type);
extern void mm_camera_msm_evt_notify(mm_camera_obj_t * my_obj, int fd);
extern int mm_camera_read_msm_frame(mm_camera_obj_t * my_obj,
                        mm_camera_stream_t *stream);
extern int32_t mm_camera_ch_acquire(mm_camera_obj_t *my_obj, mm_camera_channel_type_t ch_type);
extern void mm_camera_ch_release(mm_camera_obj_t *my_obj, mm_camera_channel_type_t ch_type);
extern int mm_camera_ch_is_active(mm_camera_obj_t * my_obj, mm_camera_channel_type_t ch_type);
extern void mm_camera_ch_util_get_stream_objs(mm_camera_obj_t * my_obj,
                                                            mm_camera_channel_type_t ch_type,
                                                            mm_camera_stream_t **stream1,
                                                            mm_camera_stream_t **stream2);
extern int mm_camera_stream_qbuf(mm_camera_obj_t * my_obj,
                                                            mm_camera_stream_t *stream,
                                                            int idx);
extern int mm_camera_stream_frame_get_q_cnt(mm_camera_frame_queue_t *q);
extern mm_camera_frame_t *mm_camera_stream_frame_deq(mm_camera_frame_queue_t *q);
extern mm_camera_frame_t *mm_camera_stream_frame_deq_no_lock(mm_camera_frame_queue_t *q);
extern void mm_camera_stream_frame_enq(mm_camera_frame_queue_t *q, mm_camera_frame_t *node);
extern void mm_camera_stream_frame_enq_no_lock(mm_camera_frame_queue_t *q, mm_camera_frame_t *node);
extern void mm_camera_stream_frame_refill_q(mm_camera_frame_queue_t *q, mm_camera_frame_t *node, int num);
extern int mm_camera_stream_is_active(mm_camera_stream_t *stream);
extern int32_t mm_camera_stream_util_buf_done(mm_camera_obj_t * my_obj,
                    mm_camera_stream_t *stream,
                    mm_camera_notify_frame_t *frame);
//extern int mm_camera_poll_add_stream(mm_camera_obj_t * my_obj, mm_camera_stream_t *stream);
//extern int mm_camera_poll_del_stream(mm_camera_obj_t * my_obj, mm_camera_stream_t *stream);
extern int mm_camera_dev_open(int *fd, char *dev_name);
extern int mm_camera_reg_event(mm_camera_obj_t * my_obj, mm_camera_event_notify_t evt_cb,
                           void *user_data, uint32_t evt_type);
extern int mm_camera_poll_send_ch_event(mm_camera_obj_t * my_obj, mm_camera_event_t *event);
extern void mm_camera_msm_proc_ch_event(mm_camera_obj_t *my_obj, mm_camera_event_t *event);
extern void mm_camera_dispatch_app_event(mm_camera_obj_t *my_obj, mm_camera_event_t *event);
extern void mm_camera_dispatch_buffered_frames(mm_camera_obj_t *my_obj, mm_camera_channel_type_t ch_type);
extern void mm_camera_histo_mmap(mm_camera_obj_t * my_obj, mm_camera_event_t *evt);
extern void mm_camera_check_pending_zsl_frames(mm_camera_obj_t *my_obj,
                                        mm_camera_channel_type_t ch_type);
extern int mm_camera_ch_util_get_num_stream(mm_camera_obj_t * my_obj,mm_camera_channel_type_t ch_type);
extern int32_t mm_camera_sendmsg(mm_camera_obj_t *my_obj, void *msg, uint32_t buf_size, int sendfd);
#endif /* __MM_CAMERA_H__ */
