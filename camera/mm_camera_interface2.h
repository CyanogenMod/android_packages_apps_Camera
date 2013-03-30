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

#ifndef __MM_CAMERA_INTERFACE2_H__
#define __MM_CAMERA_INTERFACE2_H__
#include <linux/ion.h>
#include <camera.h>
#include "mm_jpeg_encoder.h"

#define MM_CAMERA_MAX_NUM_FRAMES        16

typedef struct {
    int num;
    uint32_t frame_len;
    struct msm_frame frame[MM_CAMERA_MAX_NUM_FRAMES];
} mm_cameara_stream_buf_t;

typedef struct {
    int32_t width;
    int32_t height;
}mm_camera_dimension_t;

typedef enum {
    MM_CAMERA_OK,
    MM_CAMERA_E_GENERAL,
    MM_CAMERA_E_NO_MEMORY,
    MM_CAMERA_E_NOT_SUPPORTED,
    MM_CAMERA_E_INVALID_INPUT,
    MM_CAMERA_E_INVALID_OPERATION, /* 5 */
    MM_CAMERA_E_ENCODE,
    MM_CAMERA_E_BUFFER_REG,
    MM_CAMERA_E_PMEM_ALLOC,
    MM_CAMERA_E_CAPTURE_FAILED,
    MM_CAMERA_E_CAPTURE_TIMEOUT, /* 10 */
}mm_camera_status_type_t;

typedef enum {
    MM_CAMERA_OP_MODE_NOTUSED,
    MM_CAMERA_OP_MODE_CAPTURE,
    MM_CAMERA_OP_MODE_VIDEO,
    MM_CAMERA_OP_MODE_ZSL,
    MM_CAMERA_OP_MODE_MAX
}mm_camera_op_mode_type_t;

/* Add enumenrations at the bottom but before MM_CAMERA_PARM_MAX */
typedef enum {
    MM_CAMERA_PARM_PICT_SIZE,
    MM_CAMERA_PARM_ZOOM_RATIO,
    MM_CAMERA_PARM_HISTOGRAM,
    MM_CAMERA_PARM_DIMENSION,
    MM_CAMERA_PARM_FPS,
    MM_CAMERA_PARM_FPS_MODE, /*5*/
    MM_CAMERA_PARM_EFFECT,
    MM_CAMERA_PARM_EXPOSURE_COMPENSATION,
    MM_CAMERA_PARM_EXPOSURE,
    MM_CAMERA_PARM_SHARPNESS,
    MM_CAMERA_PARM_CONTRAST, /*10*/
    MM_CAMERA_PARM_SATURATION,
    MM_CAMERA_PARM_BRIGHTNESS,
    MM_CAMERA_PARM_WHITE_BALANCE,
    MM_CAMERA_PARM_LED_MODE,
    MM_CAMERA_PARM_ANTIBANDING, /*15*/
    MM_CAMERA_PARM_ROLLOFF,
    MM_CAMERA_PARM_CONTINUOUS_AF,
    MM_CAMERA_PARM_FOCUS_RECT,
    MM_CAMERA_PARM_AEC_ROI,
    MM_CAMERA_PARM_AF_ROI, /*20*/
    MM_CAMERA_PARM_HJR,
    MM_CAMERA_PARM_ISO,
    MM_CAMERA_PARM_BL_DETECTION,
    MM_CAMERA_PARM_SNOW_DETECTION,
    MM_CAMERA_PARM_BESTSHOT_MODE, /*25*/
    MM_CAMERA_PARM_ZOOM,
    MM_CAMERA_PARM_VIDEO_DIS,
    MM_CAMERA_PARM_VIDEO_ROT,
    MM_CAMERA_PARM_SCE_FACTOR,
    MM_CAMERA_PARM_FD, /*30*/
    MM_CAMERA_PARM_MODE,
    /* 2nd 32 bits */
    MM_CAMERA_PARM_3D_FRAME_FORMAT,
    MM_CAMERA_PARM_CAMERA_ID,
    MM_CAMERA_PARM_CAMERA_INFO,
    MM_CAMERA_PARM_PREVIEW_SIZE, /*35*/
    MM_CAMERA_PARM_QUERY_FLASH4SNAP,
    MM_CAMERA_PARM_FOCUS_DISTANCES,
    MM_CAMERA_PARM_BUFFER_INFO,
    MM_CAMERA_PARM_JPEG_ROTATION,
    MM_CAMERA_PARM_JPEG_MAINIMG_QUALITY, /* 40 */
    MM_CAMERA_PARM_JPEG_THUMB_QUALITY,
    MM_CAMERA_PARM_ZSL_ENABLE,
    MM_CAMERA_PARM_FOCAL_LENGTH,
    MM_CAMERA_PARM_HORIZONTAL_VIEW_ANGLE,
    MM_CAMERA_PARM_VERTICAL_VIEW_ANGLE, /* 45 */
    MM_CAMERA_PARM_MCE,
    MM_CAMERA_PARM_RESET_LENS_TO_INFINITY,
    MM_CAMERA_PARM_SNAPSHOTDATA,
    MM_CAMERA_PARM_HFR,
    MM_CAMERA_PARM_REDEYE_REDUCTION, /* 50 */
    MM_CAMERA_PARM_WAVELET_DENOISE,
    MM_CAMERA_PARM_3D_DISPLAY_DISTANCE,
    MM_CAMERA_PARM_3D_VIEW_ANGLE,
    MM_CAMERA_PARM_PREVIEW_FORMAT,
    MM_CAMERA_PARM_HFR_SIZE, /* 55 */
    MM_CAMERA_PARM_3D_EFFECT,
    MM_CAMERA_PARM_3D_MANUAL_CONV_RANGE,
    MM_CAMERA_PARM_3D_MANUAL_CONV_VALUE,
    MM_CAMERA_PARM_ENABLE_3D_MANUAL_CONVERGENCE,
    /* These are new parameters defined here */
    MM_CAMERA_PARM_CH_IMAGE_FMT, /* 60 */       // mm_camera_ch_image_fmt_parm_t
    MM_CAMERA_PARM_OP_MODE,             // camera state, sub state also
    MM_CAMERA_PARM_SHARPNESS_CAP,       //
    MM_CAMERA_PARM_SNAPSHOT_BURST_NUM,  // num shots per snapshot action
    MM_CAMERA_PARM_LIVESHOT_MAIN,       // enable/disable full size live shot
    MM_CAMERA_PARM_MAXZOOM, /* 65 */
    MM_CAMERA_PARM_LUMA_ADAPTATION,     // enable/disable
    MM_CAMERA_PARM_HDR,
    MM_CAMERA_PARM_CROP,
    MM_CAMERA_PARM_MAX_PICTURE_SIZE,
    MM_CAMERA_PARM_MAX_PREVIEW_SIZE, /* 70 */
    MM_CAMERA_PARM_ASD_ENABLE,
    MM_CAMERA_PARM_RECORDING_HINT,
    MM_CAMERA_PARM_CAF_ENABLE,
    MM_CAMERA_PARM_FULL_LIVESHOT,
    MM_CAMERA_PARM_DIS_ENABLE, /* 75 */
    MM_CAMERA_PARM_AEC_LOCK,
    MM_CAMERA_PARM_AWB_LOCK,
    MM_CAMERA_PARM_AF_MTR_AREA,
    MM_CAMERA_PARM_AEC_MTR_AREA,
    MM_CAMERA_PARM_MAX_HFR_MODE, /* 80 */
    MM_CAMERA_PARM_FOCUS_MODE,
    MM_CAMERA_PARM_HDR_MODE,
    MM_CAMERA_PARM_MAX
} mm_camera_parm_type_t;

#define MM_CAMERA_PARM_SUPPORT_SET      0x01
#define MM_CAMERA_PARM_SUPPORT_GET      0x02
#define MM_CAMERA_PARM_SUPPORT_BOTH     0x03

typedef struct  {
    int32_t left;
    int32_t top;
    int32_t width;
    int32_t height;
} mm_camera_rect_t;

typedef enum {
    WHITE_BALANCE_AUTO         = 1,
    WHITE_BALANCE_INCANDESCENT = 3,
    WHITE_BALANCE_FLUORESCENT  = 4,
    WHITE_BALANCE_DAYLIGHT     = 5,
    WHITE_BALANCE_CLOUDY       = 6,
    WHITE_BALANCE_OFF          = 9,
} White_Balance_modes;

typedef enum {
    MM_CAMERA_CH_PREVIEW,
    MM_CAMERA_CH_VIDEO,
    MM_CAMERA_CH_SNAPSHOT,
    MM_CAMERA_CH_RAW,
    MM_CAMERA_CH_MAX
} mm_camera_channel_type_t;

typedef enum {
    MM_CAMERA_WHITE_BALANCE_AUTO         = 1,
    MM_CAMERA_WHITE_BALANCE_OFF          = 2,
    MM_CAMERA_WHITE_BALANCE_DAYLIGHT     = 3,
    MM_CAMERA_WHITE_BALANCE_INCANDESCENT = 4,
    MM_CAMERA_WHITE_BALANCE_FLUORESCENT  = 5,
} mm_camera_white_balance_mode_type_t;
/* MM_CAMERA_PARM_RAW_IMAGE_FMT */
typedef struct {
    cam_format_t fmt;
    mm_camera_dimension_t dim;
} mm_camera_image_fmt_t;

typedef struct {
    mm_camera_image_fmt_t main;
    mm_camera_image_fmt_t thumbnail;
} mm_camera_ch_image_fmt_snapshot_t;

typedef enum {
    MM_CAMERA_RAW_STREAMING_CAPTURE_SINGLE,
    MM_CAMERA_RAW_STREAMING_MAX
} mm_camera_raw_streaming_type_t;

typedef struct {
    mm_camera_image_fmt_t main;
    mm_camera_image_fmt_t video;
} mm_camera_ch_image_fmt_video_t;

typedef struct {
    mm_camera_channel_type_t ch_type;
    union {
        mm_camera_image_fmt_t def;
        mm_camera_ch_image_fmt_snapshot_t snapshot;
        mm_camera_ch_image_fmt_video_t video;
    };
} mm_camera_ch_image_fmt_parm_t;
typedef struct {
    mm_camera_rect_t main_crop;
    mm_camera_rect_t thumbnail_crop;
} mm_camera_crop_snapshot_t;

typedef struct {
    mm_camera_channel_type_t ch_type;
    union {
    mm_camera_rect_t crop;
    mm_camera_crop_snapshot_t snapshot;
    };
} mm_camera_ch_crop_t;

typedef struct {
    uint8_t name[32];
    int32_t min_value;
    int32_t max_value;
    int32_t step;
    int32_t default_value;
} mm_camera_ctrl_cap_sharpness_t;

typedef struct {
    int16_t *zoom_ratio_tbl;
    int32_t size;
} mm_camera_zoom_tbl_t;

#define MM_CAMERA_MAX_FRAME_NUM 16

typedef struct {
    uint32_t *frame_offset;
    struct msm_frame *frame;
} mm_camera_sp_buf_t;

typedef struct {
    int8_t num_planes;
    struct v4l2_plane planes[VIDEO_MAX_PLANES];
    uint32_t frame_offset;
    struct msm_frame frame;
    int idx; /* index to stream frame */
} mm_camera_mp_buf_t;

typedef struct {
  int8_t num;
  union {
      mm_camera_sp_buf_t sp;
      mm_camera_mp_buf_t *mp;
  }buf;
} mm_camera_buf_def_t;

typedef struct {
    mm_camera_buf_def_t thumbnail;
    mm_camera_buf_def_t main;
} mm_camera_buf_snapshot_t;

typedef struct {
    mm_camera_buf_def_t video;
    mm_camera_buf_def_t main;
} mm_camera_buf_video_t;

typedef struct {
    mm_camera_channel_type_t ch_type;
    union {
        mm_camera_buf_def_t def;
        mm_camera_buf_def_t preview;
        mm_camera_buf_snapshot_t snapshot;
        mm_camera_buf_video_t video;
    };
} mm_camera_reg_buf_t;

typedef enum {
    MM_CAMERA_OPS_PREVIEW,                    // start/stop preview
    MM_CAMERA_OPS_VIDEO,                      // start/stop video
    MM_CAMERA_OPS_PREPARE_SNAPSHOT,           // prepare capture in capture mode
    MM_CAMERA_OPS_SNAPSHOT,                   // take snapshot (HDR,ZSL,live shot)
    MM_CAMERA_OPS_RAW,                        // take raw streaming (raw snapshot, etc)
    MM_CAMERA_OPS_ZSL,                        // start/stop zsl
    // mm_camera_ops_parm_get_buffered_frame_t is used for MM_CAMERA_OPS_GET_BUFFERED_FRAME
    MM_CAMERA_OPS_GET_BUFFERED_FRAME,         // channel to dispatch buffered frame to app through call back
    MM_CAMERA_OPS_FOCUS,                      // change focus,isp3a_af_mode_t* used in val
    MM_CAMERA_OPS_MAX                         // max ops
}mm_camera_ops_type_t;

/* Temp: We are declaring it here so that we can still use the
    legacy GET_CAPABILITIES call to config thread. A new design
    to query capabilities based on V4L2 interface is being
    discussed. */
typedef enum {
    CAMERA_OPS_LOCAL = -1,  /*no need to query mm-camera*/
    CAMERA_OPS_STREAMING_PREVIEW = 0,
    CAMERA_OPS_STREAMING_ZSL,
    CAMERA_OPS_STREAMING_VIDEO,
    CAMERA_OPS_CAPTURE, /*not supported*/
    CAMERA_OPS_FOCUS,
    CAMERA_OPS_GET_PICTURE, /*5*/
    CAMERA_OPS_PREPARE_SNAPSHOT,
    CAMERA_OPS_SNAPSHOT,
    CAMERA_OPS_LIVESHOT,
    CAMERA_OPS_RAW_SNAPSHOT,
    CAMERA_OPS_VIDEO_RECORDING, /*10*/
    CAMERA_OPS_REGISTER_BUFFER,
    CAMERA_OPS_UNREGISTER_BUFFER,
    CAMERA_OPS_CAPTURE_AND_ENCODE,
    CAMERA_OPS_RAW_CAPTURE,
    CAMERA_OPS_ENCODE, /*15*/
    CAMERA_OPS_ZSL_STREAMING_CB,
    /* add new above*/
    CAMERA_OPS_MAX
}mm_camera_legacy_ops_type_t;

typedef enum {
    MM_CAMERA_CH_ATTR_RAW_STREAMING_TYPE,
    MM_CAMERA_CH_ATTR_BUFFERING_FRAME,
    MM_CAMERA_CH_ATTR_MAX
} mm_camera_channel_attr_type_t;

typedef struct {
    /* how deep the circular frame queue */
    int water_mark;
    int look_back;
} mm_camera_channel_attr_buffering_frame_t;

typedef struct {
    mm_camera_channel_attr_type_t type;
    union {
        /* add more if needed */
        mm_camera_raw_streaming_type_t raw_streaming_mode;
        mm_camera_channel_attr_buffering_frame_t buffering_frame;
    };
} mm_camera_channel_attr_t;

typedef struct {
    mm_camera_channel_type_t ch_type;
} mm_camera_ops_parm_get_buffered_frame_t;

typedef struct mm_camera mm_camera_t;

typedef struct {
    /* if the parm is supported */
    uint8_t (*is_parm_supported)(mm_camera_t *camera, mm_camera_parm_type_t parm_type);
    /* if the channel is supported */
    uint8_t (*is_ch_supported)(mm_camera_t *camera, mm_camera_channel_type_t ch_type);
    /* set a parm’s current value */
    int32_t (*set_parm)(mm_camera_t *camera, mm_camera_parm_type_t parm_type,
          void* p_value);
    /* get a parm’s current value */
    int32_t (*get_parm)(mm_camera_t *camera, mm_camera_parm_type_t parm_type,
          void* p_value);
    int32_t (*request_buf) (mm_camera_t *camera, mm_camera_reg_buf_t *buf);
    int32_t (*enqueue_buf) (mm_camera_t *camera, mm_camera_reg_buf_t *buf);
    int32_t (*prepare_buf) (mm_camera_t *camera, mm_camera_reg_buf_t *buf);
    int32_t (*unprepare_buf) (mm_camera_t *camera, mm_camera_channel_type_t ch_type);
} mm_camera_config_t;

typedef struct {
    uint8_t (*is_op_supported)(mm_camera_t * camera, mm_camera_ops_type_t opcode);
    /* val is reserved for some action such as MM_CAMERA_OPS_FOCUS */
    int32_t (*action)(mm_camera_t * camera, uint8_t start,
                    mm_camera_ops_type_t opcode, void *val);
    int32_t (*open)(mm_camera_t * camera, mm_camera_op_mode_type_t op_mode);
    void (*close)(mm_camera_t * camera);
    int32_t (*ch_acquire)(mm_camera_t * camera, mm_camera_channel_type_t ch_type);
    void (*ch_release)(mm_camera_t * camera, mm_camera_channel_type_t ch_type);
    int32_t (*ch_set_attr)(mm_camera_t * camera, mm_camera_channel_type_t ch_type,
                                                 mm_camera_channel_attr_t *attr);
    int32_t (*sendmsg)(mm_camera_t * camera, void *msg, uint32_t buf_size, int sendfd);
} mm_camera_ops_t;

typedef struct {
    int type;
    uint32_t length;
    void *value;
} mm_camera_stats_t;

typedef struct {
    int idx;
    struct msm_frame *frame;
} mm_camera_notify_frame_t;

typedef struct {
    mm_camera_notify_frame_t video;
    mm_camera_notify_frame_t main;
} mm_camera_notify_video_buf_t;

typedef struct {
    mm_camera_notify_frame_t thumbnail;
    mm_camera_notify_frame_t main;
} mm_camera_notify_snapshot_buf_t;

typedef struct {
    mm_camera_channel_type_t type;
//    union {
        mm_camera_notify_snapshot_buf_t snapshot;
        mm_camera_notify_video_buf_t video;
        mm_camera_notify_frame_t def;
//    };
} mm_camera_ch_data_buf_t;


typedef enum {
    MM_CAMERA_REG_BUF_CB_ONCE,
    MM_CAMERA_REG_BUF_CB_COUNT,
    MM_CAMERA_REG_BUF_CB_INFINITE
} mm_camera_register_buf_cb_type_t;


typedef void (*mm_camera_event_notify_t)(mm_camera_event_t *evt,
    void *user_data);

typedef void (*mm_camera_buf_notify_t)(mm_camera_ch_data_buf_t *bufs,
    void *user_data);

typedef struct {
    uint8_t (*is_event_supported)(mm_camera_t * camera,
                                 mm_camera_event_type_t evt_type);
    int32_t (*register_event_notify)(mm_camera_t * camera,
                                    mm_camera_event_notify_t evt_cb,
                                     void * user_data,
                                    mm_camera_event_type_t evt_type);
    int32_t (*register_buf_notify)(mm_camera_t * camera,
                                mm_camera_channel_type_t ch_type,
                                mm_camera_buf_notify_t buf_cb,
                                mm_camera_register_buf_cb_type_t cb_type,
                                uint32_t cb_count,
                                void * user_data);
    int32_t (*buf_done)(mm_camera_t * camera, mm_camera_ch_data_buf_t *bufs);
} mm_camera_notify_t;

typedef enum {
    MM_CAMERA_JPEG_PARM_ROTATION,
    MM_CAMERA_JPEG_PARM_MAINIMG_QUALITY,
    MM_CAMERA_JPEG_PARM_THUMB_QUALITY,
    MM_CAMERA_JPEG_PARM_MAX
} mm_camera_jpeg_parm_type_t;

typedef struct {
    uint8_t* ptr;
    uint32_t filled_size;
    uint32_t size;
    int32_t fd;
    uint32_t offset;
}mm_camera_buffer_t;

typedef struct {
    exif_tags_info_t* exif_data;
    int exif_numEntries;
    mm_camera_buffer_t* p_output_buffer;
    uint8_t buffer_count;
    uint32_t rotation;
    uint32_t quality;
    int y_offset;
    int cbcr_offset;
    /* bitmask for the images to be encoded. if capture_and_encode
     * option is selected, all the images will be encoded irrespective
     * of bitmask.
     */
    uint8_t encodeBitMask;
    uint32_t output_picture_width;
    uint32_t output_picture_height;
    int format3d;
}encode_params_t;

typedef struct {
    void * src_img1_buf;            // input main image buffer
    uint32_t src_img1_size;     // input main image size
    void * src_img2_buf;            // input thumbnail image buffer
    uint32_t src_img2_size;     // input thumbnail image size
    void* out_jpeg1_buf;            // out jpeg buffer
    uint32_t out_jpeg1_size;    // IN/OUT-result buf size/jpeg image size
    void* out_jpeg2_buf;            // out jpeg buffer
    uint32_t out_jpeg2_size;    // IN/OUT-result buf size/jpeg image size
    mm_camera_status_type_t status; // result status place holder
} mm_camera_jpeg_encode_t;

typedef void (*mm_camera_jpeg_cb_t)(mm_camera_jpeg_encode_t *result,
    void *user_data);

typedef struct {
    uint8_t (*is_jpeg_supported)( mm_camera_t * camera);
    int32_t (*set_parm)(mm_camera_t * camera, mm_camera_jpeg_parm_type_t parm_type,
             void* p_value);
    int32_t (*get_parm)(mm_camera_t * camera, mm_camera_jpeg_parm_type_t parm_type,
                        void* p_value);
    int32_t (* register_event_cb)(mm_camera_t * camera, mm_camera_jpeg_cb_t * evt_cb,
             void * user_data);
    int32_t (*encode)(mm_camera_t * camera, uint8_t start,
        mm_camera_jpeg_encode_t *data);
} mm_camera_jpeg_t;

struct mm_camera {
    mm_camera_config_t *cfg;                // config interface
    mm_camera_ops_t *ops;                   // operation interface
    mm_camera_notify_t *evt;                // evt callback interface
    mm_camera_jpeg_t *jpeg_ops;         // jpeg config and encoding interface
    camera_info_t camera_info;      // postion, mount_angle, etc.
    enum sensor_type_t sensor_type; // BAYER, YUV, JPEG_SOC, etc.
    char *video_dev_name;           // device node name, e.g. /dev/video1
};

typedef enum {
    MM_CAMERA_PAD_WORD,
    MM_CAMERA_PAD_2K,
    MM_CAMERA_PAD_MAX
} mm_camera_pad_type_t;

/*configure methods*/
uint8_t cam_config_is_parm_supported(
  int cam_id,
  mm_camera_parm_type_t parm_type);
uint8_t cam_config_is_ch_supported(
  int cam_id,
  mm_camera_channel_type_t ch_type);
/* set a parm’s current value */
int32_t cam_config_set_parm(
  int cam_id,
  mm_camera_parm_type_t parm_type,
  void* p_value);
/* get a parm’s current value */
int32_t cam_config_get_parm(
  int cam_id,
  mm_camera_parm_type_t parm_type,
  void* p_value);
int32_t cam_config_request_buf(int cam_id, mm_camera_reg_buf_t *buf);
int32_t cam_config_enqueue_buf(int cam_id, mm_camera_reg_buf_t *buf);
int32_t cam_config_prepare_buf(int cam_id, mm_camera_reg_buf_t *buf);
int32_t cam_config_unprepare_buf(int cam_id, mm_camera_channel_type_t ch_type);

/*operation methods*/
uint8_t cam_ops_is_op_supported(int cam_id, mm_camera_ops_type_t opcode);
/* val is reserved for some action such as MM_CAMERA_OPS_FOCUS */
int32_t cam_ops_action(int cam_id, uint8_t start,
  mm_camera_ops_type_t opcode, void *val);
int32_t cam_ops_open(int cam_id, mm_camera_op_mode_type_t op_mode);
void cam_ops_close(int cam_id);
int32_t cam_ops_ch_acquire(int cam_id, mm_camera_channel_type_t ch_type);
void cam_ops_ch_release(int cam_id, mm_camera_channel_type_t ch_type);
int32_t cam_ops_ch_set_attr(int cam_id, mm_camera_channel_type_t ch_type,
  mm_camera_channel_attr_t *attr);
int32_t cam_ops_sendmsg(int cam_id, void *msg, uint32_t buf_size, int sendfd);

/*call-back notify methods*/
uint8_t cam_evt_is_event_supported(int cam_id, mm_camera_event_type_t evt_type);
int32_t cam_evt_register_event_notify(int cam_id,
  mm_camera_event_notify_t evt_cb,
  void * user_data,
  mm_camera_event_type_t evt_type);
int32_t cam_evt_register_buf_notify(int cam_id,
  mm_camera_channel_type_t ch_type,
  mm_camera_buf_notify_t buf_cb,
  mm_camera_register_buf_cb_type_t cb_type,
  uint32_t cb_count,
  void * user_data);

int32_t cam_evt_buf_done(int cam_id, mm_camera_ch_data_buf_t *bufs);

/*camera JPEG methods*/
uint8_t cam_jpeg_is_jpeg_supported(int cam_id);
int32_t cam_jpeg_set_parm(int cam_id, mm_camera_jpeg_parm_type_t parm_type,
  void* p_value);
int32_t cam_jpeg_get_parm(int cam_id, mm_camera_jpeg_parm_type_t parm_type,
  void* p_value);
int32_t cam_jpeg_register_event_cb(int cam_id, mm_camera_jpeg_cb_t * evt_cb,
  void * user_data);
int32_t cam_jpeg_encode(int cam_id, uint8_t start,
  mm_camera_jpeg_encode_t *data);

mm_camera_t * mm_camera_query(uint8_t *num_cameras);
extern uint8_t *mm_camera_do_mmap(uint32_t size, int *pmemFd);
extern int mm_camera_do_munmap(int pmem_fd, void *addr, size_t size);
extern int mm_camera_dump_image(void *addr, uint32_t size, char *filename);
extern uint32_t mm_camera_get_msm_frame_len(cam_format_t fmt_type,
                                            camera_mode_t mode,
                                            int width,
                                            int height,
                                            int image_type,
                                            uint8_t *num_planes,
                                            uint32_t planes[]);
#endif /*__MM_CAMERA_INTERFACE2_H__*/
