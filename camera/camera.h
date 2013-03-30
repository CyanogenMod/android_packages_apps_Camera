#ifndef __CAMERA_H__
#define __CAMERA_H__

#include <stdint.h>
#include <media/msm_camera.h>
#include <pthread.h>
#include <linux/videodev2.h>
#include <sys/mman.h>

#ifdef _ANDROID_
  #define MSM_CAMERA_CONTROL "/dev/msm_camera/control%d"
  #define MSM_CAMERA_CONFIG  "/dev/msm_camera/config%d"
  #define MSM_CAMERA_FRAME   "/dev/msm_camera/frame%d"
  #define MSM_CAMERA_SERVER  "/dev/msm_camera/video_msm"
#else
  #define MSM_CAMERA_CONTROL "/dev/control%d"
  #define MSM_CAMERA_CONFIG  "/dev/config%d"
  #define MSM_CAMERA_FRAME   "/dev/frame%d"
#endif

#define TRUE (1)
#define FALSE (0)

#define JPEG_EVENT_DONE      0
#define JPEG_EVENT_THUMBNAIL_DROPPED 4

#define EXIFTAGID_GPS_LATITUDE_REF     0x10001
#define EXIFTAGID_GPS_LATITUDE         0x20002
#define EXIFTAGID_GPS_LONGITUDE_REF    0x30003
#define EXIFTAGID_GPS_LONGITUDE        0x40004
#define EXIFTAGID_GPS_ALTITUDE_REF     0x50005
#define EXIFTAGID_GPS_ALTITUDE         0x60006
#define EXIFTAGID_GPS_TIMESTAMP        0x70007
#define EXIFTAGID_GPS_PROCESSINGMETHOD 0x1b001b
#define EXIFTAGID_GPS_DATESTAMP        0x1d001d

#define EXIFTAGID_EXIF_DATE_TIME_ORIGINAL    0x939003
#define EXIFTAGID_EXIF_DATE_TIME_CREATED    0x949004
#define EXIFTAGID_FOCAL_LENGTH         0xa0920a
#define EXIFTAGID_ISO_SPEED_RATING     0x908827

enum {
    CAMERA_CMD_HISTOGRAM_ON = 8,
    CAMERA_CMD_HISTOGRAM_OFF = 9,
    CAMERA_CMD_HISTOGRAM_SEND_DATA = 10,
};

struct jpeg_buf_t;
typedef struct jpeg_buf_t * jpeg_buffer_t;

typedef uint32_t  jpeg_event_t;

struct jpeg_encoder_t;
typedef struct jpeg_encoder_t *jpege_obj_t;

struct exif_info_t;
typedef struct exif_info_t * exif_info_obj_t;

#define MAX_FRAGMENTS  8

#define JPEGERR_SUCCESS              0
#define JPEGERR_EFAILED              1

#define PAD_TO_2K(a)                 (((a)+2047)&~2047)

typedef enum
{
    YCRCBLP_H2V2 = 0,
    YCBCRLP_H2V2 = 1,

    YCRCBLP_H2V1 = 2,
    YCBCRLP_H2V1 = 3,

    YCRCBLP_H1V2 = 4,
    YCBCRLP_H1V2 = 5,

    YCRCBLP_H1V1 = 6,
    YCBCRLP_H1V1 = 7,

    RGB565 = 8,
    RGB888 = 9,
    RGBa   = 10,

    JPEG_BITSTREAM_H2V2 = 12,
    JPEG_BITSTREAM_H2V1 = 14,
    JPEG_BITSTREAM_H1V2 = 16,
    JPEG_BITSTREAM_H1V1 = 18,

    JPEG_COLOR_FORMAT_MAX,

} jpeg_color_format_t;

typedef struct {
    union {
        struct {
            jpeg_buffer_t luma_buf;
            jpeg_buffer_t chroma_buf;
        } yuv;
        struct {
            jpeg_buffer_t rgb_buf;
        } rgb;
        struct {
            jpeg_buffer_t bitstream_buf;
        } bitstream;
    } color;
    uint32_t   width;
    uint32_t   height;

} jpege_img_frag_t;
typedef struct
{
    jpeg_color_format_t color_format;
    uint32_t            width;
    uint32_t            height;
    uint32_t            fragment_cnt;
    jpege_img_frag_t    p_fragments[MAX_FRAGMENTS];

} jpege_img_data_t;
typedef struct
{
    jpege_img_data_t  *p_main;
    jpege_img_data_t  *p_thumbnail;
} jpege_src_t;

typedef  int(*jpege_output_handler_t)(void *p_user_data,void *p_arg,jpeg_buffer_t buf,uint8_t last_buf_flag);
typedef struct
{
    jpege_output_handler_t  p_output_handler;

    void                   *p_arg;

    uint32_t                buffer_cnt;
    jpeg_buffer_t           *p_buffer;
    jpeg_buffer_t           buffers[2];

} jpege_dst_t;

typedef struct
{
    uint32_t    input_width;
    uint32_t    input_height;
    uint32_t    h_offset;
    uint32_t    v_offset;
    uint32_t    output_width;
    uint32_t    output_height;
    uint8_t     enable;

} jpege_scale_cfg_t;

typedef struct
{
    uint8_t bits[17];
    uint8_t values[256];

} jpeg_huff_table_t;

typedef uint16_t* jpeg_quant_table_t;

typedef struct
{
    uint32_t              quality;
    uint32_t              rotation_degree_clk;
    jpeg_quant_table_t    luma_quant_tbl;
    jpeg_quant_table_t    chroma_quant_tbl;
    jpeg_huff_table_t     luma_dc_huff_tbl;
    jpeg_huff_table_t     chroma_dc_huff_tbl;
    jpeg_huff_table_t     luma_ac_huff_tbl;
    jpeg_huff_table_t     chroma_ac_huff_tbl;
    uint32_t              restart_interval;
    jpege_scale_cfg_t     scale_cfg;

} jpege_img_cfg_t;

typedef enum
{
    OUTPUT_EXIF = 0,
    OUTPUT_JFIF

} jpege_hdr_output_t;
typedef enum
{
    JPEG_ENCODER_PREF_HW_ACCELERATED_PREFERRED = 0,
    JPEG_ENCODER_PREF_HW_ACCELERATED_ONLY,
    JPEG_ENCODER_PREF_SOFTWARE_PREFERRED,
    JPEG_ENCODER_PREF_SOFTWARE_ONLY,
    JPEG_ENCODER_PREF_DONT_CARE,

    JPEG_ENCODER_PREF_MAX,

} jpege_preference_t;


typedef struct
{
    uint32_t             target_filesize;
    jpege_hdr_output_t   header_type;
    jpege_img_cfg_t      main_cfg;
    jpege_img_cfg_t      thumbnail_cfg;
    uint8_t              thumbnail_present;
    jpege_preference_t   preference;
    uint32_t            app2_header_length;

} jpege_cfg_t;


typedef uint32_t exif_tag_id_t;

typedef enum {
    EXIF_BYTE      = 1,
    EXIF_ASCII     = 2,
    EXIF_SHORT     = 3,
    EXIF_LONG      = 4,
    EXIF_RATIONAL  = 5,
    EXIF_UNDEFINED = 7,
    EXIF_SLONG     = 9,
    EXIF_SRATIONAL = 10
} exif_tag_type_t;

typedef struct {
    uint32_t  num;
    uint32_t  denom;
} rat_t;

typedef struct {
    int32_t  num;
    int32_t  denom;

} srat_t;

typedef struct {
    exif_tag_type_t type;
    uint8_t copy;
    uint32_t count;
    union {
        char      *_ascii;
        uint8_t   *_bytes;
        uint8_t    _byte;
        uint16_t  *_shorts;
        uint16_t   _short;
        uint32_t  *_longs;
        uint32_t   _long;
        rat_t     *_rats;
        rat_t      _rat;
        uint8_t   *_undefined;
        int32_t   *_slongs;
        int32_t    _slong;
        srat_t    *_srats;
        srat_t     _srat;
    } data;

} exif_tag_entry_t;


typedef struct {
    uint32_t      tag_id;
    exif_tag_entry_t  tag_entry;
} exif_tags_info_t;

typedef struct {
  int ext_mode;
  int frame_idx; 
  int fd;         
  uint32_t size;
} mm_camera_frame_map_type;


typedef struct {
  uint32_t  in1_w;
  uint32_t  out1_w;
  uint32_t  in1_h;
  uint32_t  out1_h;
  uint32_t  in2_w;
  uint32_t  out2_w;
  uint32_t  in2_h;
  uint32_t  out2_h;
  uint8_t update_flag;
} common_crop_t;

#define NATIVE_CAMERA_INTERFACE   1
#define V4L2_CAMERA_INTERFACE     2

#define PAD_TO_WORD(a)               (((a)+3)&~3)
#define PAD_TO_4K(a)                 (((a)+4095)&~4095)

#define CEILING32(X) (((X) + 0x0001F) & 0xFFFFFFE0)
#define CEILING16(X) (((X) + 0x000F) & 0xFFF0)
#define CEILING4(X)  (((X) + 0x0003) & 0xFFFC)
#define CEILING2(X)  (((X) + 0x0001) & 0xFFFE)

#define V4L2_PID_MOTION_ISO             V4L2_CID_PRIVATE_BASE
#define V4L2_PID_EFFECT                 (V4L2_CID_PRIVATE_BASE+1)
#define V4L2_PID_HJR                    (V4L2_CID_PRIVATE_BASE+2)
#define V4L2_PID_LED_MODE               (V4L2_CID_PRIVATE_BASE+3)
#define V4L2_PID_PREP_SNAPSHOT          (V4L2_CID_PRIVATE_BASE+4)

#define ANDROID_FB0 "/dev/graphics/fb0"
#define LE_FB0 "/dev/fb0"

#define MAX_ROI 2
#define MAX_NUM_PARM 5
#define MAX_NUM_OPS 2
#define MAX_DEV_NAME_LEN 50

#define MAX_SNAPSHOT_BUFFERS 3

typedef enum {
  CAM_CTRL_FAILED,
  CAM_CTRL_SUCCESS,
  CAM_CTRL_INVALID_PARM,
  CAM_CTRL_NOT_SUPPORTED,
  CAM_CTRL_ACCEPTED,     

  CAM_CTRL_MAX,
} cam_ctrl_status_t;

typedef struct {
  int denoise_enable;
  int process_plates;
} denoise_param_t;



typedef struct {
  uint8_t focus_position;
  uint32_t line_count;
  uint8_t luma_target;
  int32_t r_gain;
  int32_t b_gain;
  int32_t g_gain;
  uint8_t exposure_mode;
  uint8_t exposure_program;
  float exposure_time;
  uint32_t iso_speed;
} snapshotData_info_t;


typedef enum {
  CAMERA_PREVIEW_MODE_SNAPSHOT,
  CAMERA_PREVIEW_MODE_MOVIE,
  CAMERA_PREVIEW_MODE_MOVIE_120FPS,
  CAMERA_MAX_PREVIEW_MODE
} cam_preview_mode_t;

typedef enum {
  CAMERA_YUV_420_NV12,
  CAMERA_YUV_420_NV21,
  CAMERA_YUV_420_NV21_ADRENO,
  CAMERA_BAYER_SBGGR10,
  CAMERA_YUV_420_YV12,
  CAMERA_YUV_422_NV16,
  CAMERA_YUV_422_NV61
} cam_format_t;



typedef enum {
  CAMERA_PAD_NONE,
  CAMERA_PAD_TO_WORD,
  CAMERA_PAD_TO_LONG_WORD,
  CAMERA_PAD_TO_8,
  CAMERA_PAD_TO_16,
  CAMERA_PAD_TO_1K,
  CAMERA_PAD_TO_2K,
  CAMERA_PAD_TO_4K,
  CAMERA_PAD_TO_8K
} cam_pad_format_t;

typedef struct {
  uint16_t dispWidth;
  uint16_t dispHeight;
} cam_ctrl_disp_t;

typedef enum {
  CAMERA_MODE_2D = (1<<0),
  CAMERA_MODE_3D = (1<<1),
  CAMERA_NONZSL_MODE = (1<<2),
  CAMERA_ZSL_MODE = (1<<3),
  CAMERA_MODE_MAX = CAMERA_ZSL_MODE,
} camera_mode_t;


typedef enum {
  _SIDE_BY_SIDE_HALF,
  _SIDE_BY_SIDE_FULL,
  _TOP_DOWN_HALF,
  _TOP_DOWN_FULL,
}cam_3d_frame_format_t;


typedef enum {
  CAM_VIDEO_FRAME,
  CAM_SNAPSHOT_FRAME,
  CAM_PREVIEW_FRAME,
}cam_frame_type_t;

typedef enum {
  BACK_CAMERA,
  FRONT_CAMERA,
}cam_position_t;

typedef struct {
  cam_frame_type_t frame_type;
  cam_3d_frame_format_t format;
}camera_3d_frame_t;

typedef struct {
  camera_mode_t modes_supported;
  int8_t camera_id;
  cam_position_t position;
  uint32_t sensor_mount_angle;
}camera_info_t;

typedef struct {
  camera_mode_t mode;
  int8_t camera_id;
}config_params_t;

typedef struct {
  uint32_t parm[MAX_NUM_PARM];
  uint32_t ops[MAX_NUM_OPS];
  uint8_t yuv_output;
  uint8_t jpeg_capture;
  uint32_t max_pict_width;
  uint32_t max_pict_height;
  uint32_t max_preview_width;
  uint32_t max_preview_height;
  uint32_t effect;
  camera_mode_t modes;
}cam_prop_t;

typedef struct {
  uint32_t len;
  uint32_t y_offset;
  uint32_t cbcr_offset;
} cam_sp_len_offset_t;

typedef struct{
  uint32_t len;
  uint32_t offset;
} cam_mp_len_offset_t;

typedef struct {
  int num_planes;
  union {
    cam_sp_len_offset_t sp;
    cam_mp_len_offset_t mp[8];
  };
//  uint32_t frame_len;
} cam_frame_len_offset_t;

typedef struct {
  uint16_t video_width;
  uint16_t video_height;
  uint16_t picture_width;
  uint16_t picture_height;
  uint16_t display_width;
  uint16_t display_height;
  uint16_t orig_video_width;
  uint16_t orig_video_height;
  uint16_t orig_picture_dx;
  uint16_t orig_picture_dy;
  uint16_t ui_thumbnail_height;
  uint16_t ui_thumbnail_width;
  uint16_t thumbnail_height;
  uint16_t thumbnail_width;

  uint16_t orig_picture_width;
  uint16_t orig_picture_height;
  uint16_t orig_thumb_width;
  uint16_t orig_thumb_height;

  uint16_t raw_picture_height;
  uint16_t raw_picture_width;
  uint32_t hjr_xtra_buff_for_bayer_filtering;
  cam_format_t    prev_format;
  cam_format_t    enc_format;
  cam_format_t    thumb_format;
  cam_format_t    main_img_format;

  cam_pad_format_t prev_padding_format;
  cam_pad_format_t enc_padding_format;
  cam_pad_format_t thumb_padding_format;
  cam_pad_format_t main_padding_format;

  uint16_t display_luma_width;
  uint16_t display_luma_height;
  uint16_t display_chroma_width;
  uint16_t display_chroma_height;
  uint16_t video_luma_width;
  uint16_t video_luma_height;
  uint16_t video_chroma_width;
  uint16_t video_chroma_height;
  uint16_t thumbnail_luma_width;
  uint16_t thumbnail_luma_height;
  uint16_t thumbnail_chroma_width;
  uint16_t thumbnail_chroma_height;
  uint16_t main_img_luma_width;
  uint16_t main_img_luma_height;
  uint16_t main_img_chroma_width;
  uint16_t main_img_chroma_height;
  int rotation;
  cam_frame_len_offset_t display_frame_offset;
  cam_frame_len_offset_t video_frame_offset;
  cam_frame_len_offset_t picture_frame_offset;
  cam_frame_len_offset_t thumb_frame_offset;

} cam_ctrl_dimension_t;

typedef enum {
  CAMERA_SET_PARM_DISPLAY_INFO,
  CAMERA_SET_PARM_DIMENSION,

  CAMERA_SET_PARM_ZOOM,
  CAMERA_SET_PARM_SENSOR_POSITION,
  CAMERA_SET_PARM_FOCUS_RECT,
  CAMERA_SET_PARM_LUMA_ADAPTATION,
  CAMERA_SET_PARM_CONTRAST,
  CAMERA_SET_PARM_BRIGHTNESS,
  CAMERA_SET_PARM_EXPOSURE_COMPENSATION,
  CAMERA_SET_PARM_SHARPNESS,
  CAMERA_SET_PARM_HUE,  /* 10 */
  CAMERA_SET_PARM_SATURATION,
  CAMERA_SET_PARM_EXPOSURE,
  CAMERA_SET_PARM_AUTO_FOCUS,
  CAMERA_SET_PARM_WB,
  CAMERA_SET_PARM_EFFECT,
  CAMERA_SET_PARM_FPS,
  CAMERA_SET_PARM_FLASH,
  CAMERA_SET_PARM_NIGHTSHOT_MODE,
  CAMERA_SET_PARM_REFLECT,
  CAMERA_SET_PARM_PREVIEW_MODE,  /* 20 */
  CAMERA_SET_PARM_ANTIBANDING,
  CAMERA_SET_PARM_RED_EYE_REDUCTION,
  CAMERA_SET_PARM_FOCUS_STEP,
  CAMERA_SET_PARM_EXPOSURE_METERING,
  CAMERA_SET_PARM_AUTO_EXPOSURE_MODE,
  CAMERA_SET_PARM_ISO,
  CAMERA_SET_PARM_BESTSHOT_MODE,
  CAMERA_SET_PARM_ENCODE_ROTATION,
  CAMERA_SET_PARM_PREVIEW_FPS,
  CAMERA_SET_PARM_AF_MODE,  /* 30 */
  CAMERA_SET_PARM_HISTOGRAM,
  CAMERA_SET_PARM_FLASH_STATE,
  CAMERA_SET_PARM_FRAME_TIMESTAMP,
  CAMERA_SET_PARM_STROBE_FLASH,
  CAMERA_SET_PARM_FPS_LIST,
  CAMERA_SET_PARM_HJR,
  CAMERA_SET_PARM_ROLLOFF,

  CAMERA_STOP_PREVIEW,
  CAMERA_START_PREVIEW,
  CAMERA_START_SNAPSHOT, /* 40 */
  CAMERA_START_RAW_SNAPSHOT,
  CAMERA_STOP_SNAPSHOT,
  CAMERA_EXIT,
  CAMERA_ENABLE_BSM,
  CAMERA_DISABLE_BSM,
  CAMERA_GET_PARM_ZOOM,
  CAMERA_GET_PARM_MAXZOOM,
  CAMERA_GET_PARM_ZOOMRATIOS,
  CAMERA_GET_PARM_AF_SHARPNESS,
  CAMERA_SET_PARM_LED_MODE, /* 50 */
  CAMERA_SET_MOTION_ISO,
  CAMERA_AUTO_FOCUS_CANCEL,
  CAMERA_GET_PARM_FOCUS_STEP,
  CAMERA_ENABLE_AFD,
  CAMERA_PREPARE_SNAPSHOT,
  CAMERA_SET_FPS_MODE,
  CAMERA_START_VIDEO,
  CAMERA_STOP_VIDEO,
  CAMERA_START_RECORDING,
  CAMERA_STOP_RECORDING, /* 60 */
  CAMERA_SET_VIDEO_DIS_PARAMS,
  CAMERA_SET_VIDEO_ROT_PARAMS,
  CAMERA_SET_PARM_AEC_ROI,
  CAMERA_SET_CAF,
  CAMERA_SET_PARM_BL_DETECTION_ENABLE,
  CAMERA_SET_PARM_SNOW_DETECTION_ENABLE,
  CAMERA_SET_PARM_STROBE_FLASH_MODE,
  CAMERA_SET_PARM_AF_ROI,
  CAMERA_START_LIVESHOT,
  CAMERA_SET_SCE_FACTOR, /* 70 */
  CAMERA_GET_CAPABILITIES,
  CAMERA_GET_PARM_DIMENSION,
  CAMERA_GET_PARM_LED_MODE,
  CAMERA_SET_PARM_FD,
  CAMERA_GET_PARM_3D_FRAME_FORMAT,
  CAMERA_QUERY_FLASH_FOR_SNAPSHOT,
  CAMERA_GET_PARM_FOCUS_DISTANCES,
  CAMERA_START_ZSL,
  CAMERA_STOP_ZSL,
  CAMERA_ENABLE_ZSL, /* 80 */
  CAMERA_GET_PARM_FOCAL_LENGTH,
  CAMERA_GET_PARM_HORIZONTAL_VIEW_ANGLE,
  CAMERA_GET_PARM_VERTICAL_VIEW_ANGLE,
  CAMERA_SET_PARM_WAVELET_DENOISE,
  CAMERA_SET_PARM_MCE,
  CAMERA_ENABLE_STEREO_CAM,
  CAMERA_SET_PARM_RESET_LENS_TO_INFINITY,
  CAMERA_GET_PARM_SNAPSHOTDATA,
  CAMERA_SET_PARM_HFR,
  CAMERA_SET_REDEYE_REDUCTION, /* 90 */
  CAMERA_SET_PARM_3D_DISPLAY_DISTANCE,
  CAMERA_SET_PARM_3D_VIEW_ANGLE,
  CAMERA_SET_PARM_3D_EFFECT,
  CAMERA_SET_PARM_PREVIEW_FORMAT,
  CAMERA_GET_PARM_3D_DISPLAY_DISTANCE, /* 95 */
  CAMERA_GET_PARM_3D_VIEW_ANGLE,
  CAMERA_GET_PARM_3D_EFFECT,
  CAMERA_GET_PARM_3D_MANUAL_CONV_RANGE,
  CAMERA_SET_PARM_3D_MANUAL_CONV_VALUE,
  CAMERA_ENABLE_3D_MANUAL_CONVERGENCE, /* 100 */
  CAMERA_SET_PARM_HDR,
  CAMERA_SET_ASD_ENABLE,
  CAMERA_POSTPROC_ABORT,
  CAMERA_SET_AEC_MTR_AREA,
  CAMERA_UNKNOWN1,       /*105*/
  CAMERA_SET_FULL_LIVESHOT,
  CAMERA_GET_LIVESHOT_CROP,
  CAMERA_UNKNOWN_108,
  CAMERA_UNKNOWN_109,
  CAMERA_AWB_CALIBRATION,      /*110*/
  CAMERA_SET_AWB_CALIBRATION_STATUS,
  CAMERA_UNKNOWN_112,
  CAMERA_SET_AWB_LSC_PARAM,
  CAMERA_UNKNOWN_114,
  CAMERA_UNKNOWN_115,     /* 115 */
  CAMERA_UNKNOWN_116,
  CAMERA_UNKNOWN_117,
  CAMERA_SET_AEC_LOCK,
  CAMERA_SET_AWB_LOCK,
  CAMERA_UNKNOWN_120,  /* 120 */
  CAMERA_UNKNOWN_121,
  CAMERA_UNKNOWN_122,
  CAMERA_UNKNOWN_123,
  CAMERA_UNKNOWN_124,
  CAMERA_UNKNOWN_125, /* 125 */
  CAMERA_UNKNOWN_126, 
  CAMERA_SET_HDR_MODE,
  CAMERA_SET_BURST_MODE=0x86,
  CAMERA_SET_PARM_FOCUS_MODE=0x88,
  CAMERA_CTRL_PARM_MAX
} cam_ctrl_type;


typedef struct {
  int pic_width;
  int pic_hight;
  int prev_width;
  int prev_hight;
  int rotation;
} cam_ctrl_set_dimension_data_t;

#define CAMERA_FPS_DENOMINATOR 1000

typedef enum {
  CAMERA_NO_VIGNETTE_CORRECTION,
  CAMERA_LUMA_VIGNETTE_CORRECTION,
  CAMERA_BAYER_VIGNETTE_CORRECTION
} camera_vc_mode_type;

typedef enum {
  CAMERA_BVCM_DISABLE,
  CAMERA_BVCM_RAW_CAPTURE,
  CAMERA_BVCM_OFFLINE_CAPTURE
} camera_bvcm_capture_type;

#ifndef HAVE_CAMERA_SIZE_TYPE
  #define HAVE_CAMERA_SIZE_TYPE
struct camera_size_type {
  int width;
  int height;
};
#endif

typedef enum {
  CAMERA_SP_NORMAL = 0,
  CAMERA_SP_REVERSE,
} camera_sp_type;

typedef enum {
  CAMERA_EXPOSURE_MIN_MINUS_1,
  CAMERA_EXPOSURE_AUTO = 1,
  CAMERA_EXPOSURE_DAY,
  CAMERA_EXPOSURE_NIGHT,
  CAMERA_EXPOSURE_LANDSCAPE,
  CAMERA_EXPOSURE_STRONG_LIGHT,
  CAMERA_EXPOSURE_SPOTLIGHT,
  CAMERA_EXPOSURE_PORTRAIT,
  CAMERA_EXPOSURE_MOVING,
  CAMERA_EXPOSURE_MAX_PLUS_1
} camera_exposure_type;

typedef enum {
  CAMERA_NIGHTSHOT_MODE_OFF,
  CAMERA_NIGHTSHOT_MODE_ON,
  CAMERA_MAX_NIGHTSHOT_MODE
} camera_nightshot_mode_type;

typedef enum {
  CAMERA_EXIT_CB_ABORT = -2,
  CAMERA_EXIT_CB_FAILED = -1,
  CAMERA_EXIT_CB_DONE = 0,
  CAMERA_CB_MAX,
} camera_af_done_type;

typedef enum {
  CAMERA_ERROR_NO_MEMORY,
  CAMERA_ERROR_EFS_FAIL,
  CAMERA_ERROR_EFS_FILE_OPEN,
  CAMERA_ERROR_EFS_FILE_NOT_OPEN,
  CAMERA_ERROR_EFS_FILE_ALREADY_EXISTS,
  CAMERA_ERROR_EFS_NONEXISTENT_DIR,
  CAMERA_ERROR_EFS_NONEXISTENT_FILE,
  CAMERA_ERROR_EFS_BAD_FILE_NAME,
  CAMERA_ERROR_EFS_BAD_FILE_HANDLE,
  CAMERA_ERROR_EFS_SPACE_EXHAUSTED,
  CAMERA_ERROR_EFS_OPEN_TABLE_FULL,
  CAMERA_ERROR_EFS_OTHER_ERROR,
  CAMERA_ERROR_CONFIG,
  CAMERA_ERROR_EXIF_ENCODE,
  CAMERA_ERROR_VIDEO_ENGINE,
  CAMERA_ERROR_IPL,
  CAMERA_ERROR_INVALID_FORMAT,
  CAMERA_ERROR_TIMEOUT,
  CAMERA_ERROR_ESD,
  CAMERA_ERROR_MAX
} camera_error_type;

typedef enum {
  CAMERA_SUCCESS = 0,
  CAMERA_INVALID_STATE,
  CAMERA_INVALID_PARM,
  CAMERA_INVALID_FORMAT,
  CAMERA_NO_SENSOR,
  CAMERA_NO_MEMORY,
  CAMERA_NOT_SUPPORTED,
  CAMERA_FAILED,
  CAMERA_INVALID_STAND_ALONE_FORMAT,
  CAMERA_MALLOC_FAILED_STAND_ALONE,
  CAMERA_RET_CODE_MAX
} camera_ret_code_type;

typedef enum {
  CAMERA_RAW,
  CAMERA_JPEG,
  CAMERA_PNG,
  CAMERA_YCBCR_ENCODE,
  CAMERA_ENCODE_TYPE_MAX
} camera_encode_type;

#if !defined FEATURE_CAMERA_ENCODE_PROPERTIES && defined FEATURE_CAMERA_V7
typedef enum {
  CAMERA_SNAPSHOT,
  CAMERA_RAW_SNAPSHOT
} camera_snapshot_type;
#endif

typedef enum {
  CAMERA_YCBCR,
#ifdef FEATURE_CAMERA_V7
  CAMERA_YCBCR_4_2_0,
  CAMERA_YCBCR_4_2_2,
  CAMERA_H1V1,
  CAMERA_H2V1,
  CAMERA_H1V2,
  CAMERA_H2V2,
  CAMERA_BAYER_8BIT,
  CAMERA_BAYER_10BIT,
#endif
  CAMERA_RGB565,
  CAMERA_RGB666,
  CAMERA_RGB444,
  CAMERA_BAYER_BGGR,
  CAMERA_BAYER_GBRG,
  CAMERA_BAYER_GRBG,
  CAMERA_BAYER_RGGB,
  CAMERA_RGB888
} camera_format_type;

typedef enum {
  CAMERA_ORIENTATION_LANDSCAPE,
  CAMERA_ORIENTATION_PORTRAIT
} camera_orientation_type;

typedef enum {
  CAMERA_DESCRIPTION_STRING,
  CAMERA_USER_COMMENT_STRING,
  CAMERA_GPS_AREA_INFORMATION_STRING
} camera_string_type;

typedef struct {
  int32_t  buffer[256];
  int32_t  max_value;
} camera_preview_histogram_info;

typedef enum {
  CAM_STATS_TYPE_HIST,
  CAM_STATS_TYPE_MAX
}camstats_type;

typedef struct {
  uint32_t timestamp;
  double   latitude;   /* degrees, WGS ellipsoid */
  double   longitude;  /* degrees                */
  int16_t  altitude;   /* meters                          */
} camera_position_type;

typedef struct {
  /* Format of the frame */
  camera_format_type format;
  uint16_t dx;
  uint16_t dy;
  uint16_t captured_dx;
  uint16_t captured_dy;
  uint16_t rotation;

#ifdef FEATURE_CAMERA_V7
  uint8_t *thumbnail_image;
#endif /* FEATURE_CAMERA_V7 */
  uint8_t  *buffer;
} camera_frame_type;

typedef struct {
  uint16_t  uMode;        // Input / Output AAC mode
  uint32_t  dwFrequency;  // Sampling Frequency
  uint16_t  uQuality;     // Audio Quality
  uint32_t  dwReserved;   // Reserved for future use
} camera_aac_encoding_info_type;

typedef enum {
  TIFF_DATA_BYTE = 1,
  TIFF_DATA_ASCII = 2,
  TIFF_DATA_SHORT = 3,
  TIFF_DATA_LONG = 4,
  TIFF_DATA_RATIONAL = 5,
  TIFF_DATA_UNDEFINED = 7,
  TIFF_DATA_SLONG = 9,
  TIFF_DATA_SRATIONAL = 10
} tiff_data_type;

typedef enum {
  ZERO_IFD = 0, /* This must match AEECamera.h */
  FIRST_IFD,
  EXIF_IFD,
  GPS_IFD,
  INTEROP_IFD,
  DEFAULT_IFD
} camera_ifd_type;

typedef struct {
  uint16_t tag_id;
  camera_ifd_type ifd;
  tiff_data_type type;
  uint16_t count;
  void     *value;
} camera_exif_tag_type;

typedef struct {
  /* What is the ID for this sensor */
  uint16_t sensor_id;
  /* Sensor model number, null terminated, trancate to 31 characters */
  char sensor_model[32];
  /* Width and height of the sensor */
  uint16_t sensor_width;
  uint16_t sensor_height;
  /* Frames per second */
  uint16_t fps;
  /* Whether the device driver can sense when sensor is rotated */
  int8_t  sensor_rotation_sensing;
  /* How the sensor are installed */
  uint16_t default_rotation;
  camera_orientation_type default_orientation;
  /*To check antibanding support */
  int8_t  support_auto_antibanding;
} camera_info_type;

typedef struct {
  int32_t                quality;
  camera_encode_type     format;
  int32_t                file_size;
} camera_encode_properties_type;

typedef enum {
  CAMERA_DEVICE_MEM,
  CAMERA_DEVICE_EFS,
  CAMERA_DEVICE_MAX
} camera_device_type;

#define MAX_JPEG_ENCODE_BUF_NUM 4
#define MAX_JPEG_ENCODE_BUF_LEN (1024*8)

typedef struct {
  uint32_t buf_len;/* Length of each buffer */
  uint32_t used_len;
  int8_t   valid;
  uint8_t  *buffer;
} camera_encode_mem_type;

typedef struct {
  camera_device_type     device;
#ifndef FEATURE_CAMERA_ENCODE_PROPERTIES
  int32_t                quality;
  camera_encode_type     format;
#endif /* nFEATURE_CAMERA_ENCODE_PROPERTIES */
  int32_t                encBuf_num;
  camera_encode_mem_type encBuf[MAX_JPEG_ENCODE_BUF_NUM];
} camera_handle_mem_type;

#ifdef FEATURE_EFS
typedef struct {
  camera_device_type     device;
  #ifndef FEATURE_CAMERA_ENCODE_PROPERTIES
  int32_t                quality;
  camera_encode_type     format;
  #endif /* nFEATURE_CAMERA_ENCODE_PROPERTIES */
  char                   filename[FS_FILENAME_MAX_LENGTH_P];
} camera_handle_efs_type;
#endif /* FEATURE_EFS */

typedef enum {
  CAMERA_PARM_FADE_OFF,
  CAMERA_PARM_FADE_IN,
  CAMERA_PARM_FADE_OUT,
  CAMERA_PARM_FADE_IN_OUT,
  CAMERA_PARM_FADE_MAX
} camera_fading_type;

typedef union {
  camera_device_type      device;
  camera_handle_mem_type  mem;
} camera_handle_type;

typedef enum {
  CAMERA_AUTO_FOCUS,
  CAMERA_MANUAL_FOCUS
} camera_focus_e_type;

/* AEC: Frame average weights the whole preview window equally
   AEC: Center Weighted weights the middle X percent of the window
   X percent compared to the rest of the frame
   AEC: Spot metering weights the very center regions 100% and
   discounts other areas                                        */
typedef enum {
  CAMERA_AEC_FRAME_AVERAGE,
  CAMERA_AEC_CENTER_WEIGHTED,
  CAMERA_AEC_SPOT_METERING,
  CAMERA_AEC_MAX_MODES
} camera_auto_exposure_mode_type;

/* Auto focus mode, used for CAMERA_PARM_AF_MODE */
typedef enum {
  AF_MODE_UNCHANGED = -1,
  AF_MODE_NORMAL    = 1,
  AF_MODE_MACRO,
  AF_MODE_AUTO,
  AF_MODE_CAF,
  AF_MODE_CAF_VID,
  AF_MODE_MAX
} isp3a_af_mode_t;


typedef  struct {
  /* Focus Window dimensions, could be negative. */
  int16_t x;
  int16_t y;
  int16_t dx;
  int16_t dy;

  /* Focus Window Freedom granularity in x-direction */
  int16_t dx_step_size;

  /* Focus Window Freedom granularity in y-direction */
  int16_t dy_step_size;

  /*  Focus Window can only move within this Focus Region and
   *  the maximum Focus Window area must not exceed one quarter of the
   *  Focus Region.
   */
  int16_t min_x;
  int16_t min_y;
  int16_t max_x;
  int16_t max_y;
} camera_focus_window_type;

typedef unsigned int Offline_Input_PixelSizeType;
typedef uint16_t Offline_Snapshot_PixelSizeType;
typedef uint16_t Offline_Thumbnail_PixelSizeType;
typedef uint16_t Offline_NumFragments_For_Input;
typedef uint16_t Offline_NumFragments_For_Output;

typedef  struct {
  /* Focus Window dimensions */
  int16_t x_upper_left;
  int16_t y_upper_left;
  int16_t width;
  int16_t height;
} camera_focus_rectangle_dimensions_type;

typedef  struct {
  int16_t focus_window_count;
  camera_focus_rectangle_dimensions_type *windows_list;
} camera_focus_window_rectangles_type;

typedef enum Offline_InputFormatType {
  CAMERA_BAYER_G_B,
  CAMERA_BAYER_B_G,
  CAMERA_BAYER_G_R,
  CAMERA_BAYER_R_G,
  CAMERA_YCbCr_Y_Cb_Y_Cr,
  CAMERA_YCbCr_Y_Cr_Y_Cb,
  CAMERA_YCbCr_Cb_Y_Cr_Y,
  CAMERA_YCbCr_Cr_Y_Cb_Y,
  CAMERA_YCbCr_4_2_2_linepacked,
  CAMERA_YCbCr_4_2_0_linepacked,
  CAMERA_NumberOf_InputFormatType   /* Used for count purposes only */
} Offline_InputFormatType;

typedef enum Offline_YCbCr_InputCositingType {
  CAMERA_CHROMA_NOT_COSITED,
  CAMERA_CHROMA_COSITED,
  CAMERA_NumberOf_YCbCr_InputCositingType   /* Used for count purposes only */
} Offline_YCbCr_InputCositingType;

typedef enum Offline_Input_PixelDataWidthType {
  CAMERA_8Bit,
  CAMERA_10Bit,
  CAMERA_NumberOf_PixelDataWidthType /* Used for count purposes only */
} Offline_Input_PixelDataWidthType;

typedef struct OfflineInputConfigurationType {
  Offline_YCbCr_InputCositingType  YCbCrCositing    ;
  Offline_InputFormatType          format           ;
  Offline_Input_PixelDataWidthType dataWidth        ;
  Offline_Input_PixelSizeType      height           ;
  Offline_Input_PixelSizeType      width            ;
  Offline_Thumbnail_PixelSizeType  thumbnail_width  ;
  Offline_Thumbnail_PixelSizeType  thumbnail_height ;
  Offline_Snapshot_PixelSizeType   snapshot_width   ;
  Offline_Snapshot_PixelSizeType   snapshot_height  ;
  char*                            file_name        ;
  Offline_NumFragments_For_Input   input_fragments  ;
  Offline_NumFragments_For_Output  output_fragments ;
} OfflineInputConfigurationType;

/* Enum Type for bracketing support */
typedef enum {
  CAMERA_BRACKETING_OFF,
  CAMERA_BRACKETING_EXPOSURE,
  CAMERA_BRACKETING_MAX
} camera_bracketing_mode_type;

typedef enum {
  CAMERA_BESTSHOT_OFF = 0,
  CAMERA_BESTSHOT_AUTO = 0,
  CAMERA_BESTSHOT_LANDSCAPE = 1,
  CAMERA_BESTSHOT_SNOW,
  CAMERA_BESTSHOT_BEACH,
  CAMERA_BESTSHOT_SUNSET,
  CAMERA_BESTSHOT_NIGHT,
  CAMERA_BESTSHOT_PORTRAIT,
  CAMERA_BESTSHOT_BACKLIGHT,
  CAMERA_BESTSHOT_SPORTS,
  CAMERA_BESTSHOT_ANTISHAKE,
  CAMERA_BESTSHOT_FLOWERS,
  CAMERA_BESTSHOT_CANDLELIGHT,
  CAMERA_BESTSHOT_FIREWORKS,
  CAMERA_BESTSHOT_PARTY,
  CAMERA_BESTSHOT_NIGHT_PORTRAIT,
  CAMERA_BESTSHOT_THEATRE,
  CAMERA_BESTSHOT_ACTION,
  CAMERA_BESTSHOT_AR,
  CAMERA_BESTSHOT_MAX
} camera_bestshot_mode_type;


typedef struct la_config {
  /* Pointer in input image - input */
  uint8_t *data_in;
  /* Luma re-mapping curve - output */
  uint16_t *la_curve;
  /* Detect - if 1 image needs LA , if 0 does not - output */
  int16_t detect;
  /* input image size - input */
  uint32_t size;
  /* Old gamma correction LUT - input */
  uint8_t *gamma;
  /* New gamma correction LUT - output */
  uint8_t *gamma_new;
  /* Chroma scale value - output */
  uint32_t chroma_scale_Q20;

  /* Detection related parameters - inputs*/
  /****************************************/
  uint8_t low_range;
  uint16_t low_perc_Q11;
  uint8_t high_range;
  uint16_t high_perc_Q11;

  /* Capping of the histogram - input*/
  uint16_t cap_Q10;

  /* The range to test if diffusion shift is needed - input*/
  uint8_t diff_range;
  /* The percentile to test if diffusion shift is needed - input*/
  uint16_t diff_tail_threshold_Q8;

  /* Scale how much to include contrast tranform
     0 - no contrast transform, 32 - all is contrast transform   - input*/
  uint16_t scale;

  /* Cap high final re-mapping function - input */
  uint16_t cap_high_Q2;
  /* Cap low final re-mapping function - input */
  uint16_t cap_low_Q2;
  /* Number of itterations for the diffusion */
  uint16_t numIt;

} la_config;

typedef enum {
  CAMERA_ANTIBANDING_OFF,
  CAMERA_ANTIBANDING_60HZ,
  CAMERA_ANTIBANDING_50HZ,
  CAMERA_ANTIBANDING_AUTO,
  CAMERA_MAX_ANTIBANDING,
} camera_antibanding_type;

typedef enum {
  CAMERA_WB_MIN_MINUS_1,
  CAMERA_WB_AUTO = 1,  /* This list must match aeecamera.h */
  CAMERA_WB_CUSTOM,
  CAMERA_WB_INCANDESCENT,
  CAMERA_WB_FLUORESCENT,
  CAMERA_WB_DAYLIGHT,
  CAMERA_WB_CLOUDY_DAYLIGHT,
  CAMERA_WB_TWILIGHT,
  CAMERA_WB_SHADE,
  CAMERA_WB_MAX_PLUS_1
} config3a_wb_t;

typedef enum {
  LED_MODE_OFF,
  LED_MODE_AUTO,
  LED_MODE_ON,
  LED_MODE_TORCH,

  /*new mode above should be added above this line*/
  LED_MODE_MAX
} led_mode_t;

typedef enum {
  STROBE_FLASH_MODE_OFF,
  STROBE_FLASH_MODE_AUTO,
  STROBE_FLASH_MODE_ON,
  STROBE_FLASH_MODE_MAX,
} strobe_flash_mode_t;

/* Clockwise */
typedef enum {
  CAMERA_ENCODING_ROTATE_0,
  CAMERA_ENCODING_ROTATE_90,
  CAMERA_ENCODING_ROTATE_180,
  CAMERA_ENCODING_ROTATE_270
} camera_encoding_rotate_t;

typedef enum {
  MOTION_ISO_OFF,
  MOTION_ISO_ON
} motion_iso_t;

typedef enum {
  FPS_MODE_AUTO,
  FPS_MODE_FIXED,
} fps_mode_t;

typedef struct {
  int32_t minimum_value; /* Minimum allowed value */
  int32_t maximum_value; /* Maximum allowed value */
  int32_t step_value;    /* step value */
  int32_t default_value; /* Default value */
  int32_t current_value; /* Current value */
} cam_parm_info_t;

typedef struct {
  struct msm_ctrl_cmd ctrlCmd;
  int fd;
  void (*af_cb)(int8_t );
  int8_t is_camafctrl_thread_join;
  isp3a_af_mode_t af_mode;
} cam_af_ctrl_t;

typedef enum {
  AUTO = 1,
  SPOT,
  CENTER_WEIGHTED,
  AVERAGE
} cam_af_focusrect_t;

typedef enum {
  CAF_OFF,
  CAF_ON
} caf_ctrl_t;

typedef enum {
  AEC_ROI_OFF,
  AEC_ROI_ON
} aec_roi_ctrl_t;

typedef enum {
  AEC_ROI_BY_INDEX,
  AEC_ROI_BY_COORDINATE,
} aec_roi_type_t;

typedef struct {
  uint32_t x;
  uint32_t y;
} cam_coordinate_type_t;

typedef struct {
  aec_roi_ctrl_t aec_roi_enable;
  aec_roi_type_t aec_roi_type;
  union {
    cam_coordinate_type_t coordinate;
    uint32_t aec_roi_idx;
  } aec_roi_position;
} cam_set_aec_roi_t;

/*
 * Define DRAW_RECTANGLES to draw rectangles on screen. Just for test purpose.
 */
//#define DRAW_RECTANGLES

typedef struct {
  uint16_t x;
  uint16_t y;
  uint16_t dx;
  uint16_t dy;
} roi_t;


typedef struct {
  uint32_t frm_id;
  uint8_t num_roi;
  roi_t roi[MAX_ROI];
  uint8_t is_multiwindow;
} roi_info_t;

#define CAMERA_MIN_BRIGHTNESS  0
#define CAMERA_DEF_BRIGHTNESS  3
#define CAMERA_MAX_BRIGHTNESS  6
#define CAMERA_BRIGHTNESS_STEP 1

#define CAMERA_MIN_CONTRAST    0
#define CAMERA_DEF_CONTRAST    5
#define CAMERA_MAX_CONTRAST    10

#define CAMERA_MIN_SCE_FACTOR    -100
#define CAMERA_DEF_SCE_FACTOR    0
#define CAMERA_MAX_SCE_FACTOR    100

/* No saturation for default */
#define CAMERA_MIN_SATURATION  0
#define CAMERA_DEF_SATURATION  5
#define CAMERA_MAX_SATURATION  10

/* No hue for default. */
#define CAMERA_MIN_HUE         0
#define CAMERA_DEF_HUE         0
#define CAMERA_MAX_HUE         300
#define CAMERA_HUE_STEP        60

/* No sharpness for default */
#define CAMERA_MIN_SHARPNESS   0
#define CAMERA_DEF_SHARPNESS   10
#define CAMERA_MAX_SHARPNESS   30
#define CAMERA_SHARPNESS_STEP  2

#define CAMERA_MIN_ZOOM  0
#define CAMERA_DEF_ZOOM  0
#define CAMERA_MAX_ZOOM  0x31
#define CAMERA_ZOOM_STEP 0x3

typedef struct video_frame_info {

  uint32_t               y_buffer_width;     /* y plane */
  uint32_t               cbcr_buffer_width;  /* cbcr plane */
  uint32_t               image_width;        /**< original image width.   */
  uint32_t               image_height;       /**< original image height. */
  uint32_t               color_format;
} video_frame_info;

typedef enum VIDEO_ROT_ENUM {
  ROT_NONE               = 0,
  ROT_CLOCKWISE_90       = 1,
  ROT_CLOCKWISE_180      = 6,
  ROT_CLOCKWISE_270      = 7,
} VIDEO_ROT_ENUM;

typedef struct video_rotation_param_ctrl_t {
  VIDEO_ROT_ENUM rotation; /* 0 degree = rot disable. */
} video_rotation_param_ctrl_t;

typedef struct video_dis_param_ctrl_t {
  uint32_t dis_enable;       /* DIS feature: 1 = enable, 0 = disable.
                               when enable, caller makes sure w/h are 10% more. */
  uint32_t video_rec_width;  /* video frame width for recording */
  uint32_t video_rec_height; /* video frame height for recording */
  uint32_t output_cbcr_offset;
} video_dis_param_ctrl_t;

typedef struct {
  uint32_t               dis_enable;  /* DIS feature: 1 = enable, 0 = disable.
                                           when enable, caller makes sure w/h are 10% more. */
  VIDEO_ROT_ENUM         rotation;        /* when rotation = 0, that also means it is disabled.*/
  video_frame_info       input_frame;
  video_frame_info       output_frame;
} video_param_ctrl_t;

void *cam_conf (void *data);
int launch_cam_conf_thread(void);
int wait_cam_conf_ready(void);
int release_cam_conf_thread(void);
void set_config_start_params(config_params_t*);
int launch_camafctrl_thread(cam_af_ctrl_t *pAfctrl);
/* Stats */
typedef enum {
  CAM_STATS_MSG_HIST,
  CAM_STATS_MSG_EXIT
}camstats_msg_type;

/* Stats messages */
typedef struct {
  camstats_msg_type msg_type;
  union {
    camera_preview_histogram_info hist_data;
  } msg_data;
} camstats_msg;
/*cam stats thread*/
int launch_camstats_thread(void);
void release_camstats_thread(void);
int8_t send_camstats(camstats_type msg_type, void* data, int size);
int8_t send_camstats_msg(camstats_type stats_type, camstats_msg* p_msg);
int is_camstats_thread_running(void);

/* cam frame*/
typedef struct {
  cam_preview_mode_t m;
} cam_frame_start_parms;

int launch_camframe_thread(cam_frame_start_parms* parms);
void release_camframe_thread(void);
void camframe_terminate(void);
void *cam_frame(void *data);
/*sends the free frame of given type to mm-camera*/
int8_t camframe_add_frame(cam_frame_type_t, struct msm_frame*);
/*release all frames of given type*/
int8_t camframe_release_all_frames(cam_frame_type_t);

/* frame Q*/
struct fifo_node 
{
  struct fifo_node *next;
  void *f;
};
 
struct fifo_queue
{
  int num_of_frames;
  struct fifo_node *front;
  struct fifo_node *back;
  pthread_mutex_t mut;
  pthread_cond_t wait;
  char* name;
};

enum focus_distance_index{
  FOCUS_DISTANCE_NEAR_INDEX,  /* 0 */
  FOCUS_DISTANCE_OPTIMAL_INDEX,
  FOCUS_DISTANCE_FAR_INDEX,
  FOCUS_DISTANCE_MAX_INDEX
};

typedef struct {
  float focus_distance[FOCUS_DISTANCE_MAX_INDEX];
} focus_distances_info_t;

struct msm_frame* get_frame(struct fifo_queue* queue);
int8_t add_frame(struct fifo_queue* queue, struct msm_frame *p);
void flush_queue (struct fifo_queue* queue);
void wait_queue (struct fifo_queue* queue);
void signal_queue (struct fifo_queue* queue);

/* Display */
typedef struct {
    uint16_t user_input_display_width;
    uint16_t user_input_display_height;
} USER_INPUT_DISPLAY_T;
int launch_camframe_fb_thread(void);
void release_camframe_fb_thread(void);
void use_overlay_fb_display_driver(void);

/* v4L2 */
void *v4l2_cam_frame(void *data);
void release_v4l2frame_thread(pthread_t frame_thread);

typedef enum {
  LIVESHOT_SUCCESS,
  LIVESHOT_ENCODE_ERROR,
  LIVESHOT_UNKNOWN_ERROR,
}liveshot_status;

static struct fifo_node* dequeue(struct fifo_queue* q) {
  struct fifo_node *tmp = q->front;

  if (!tmp)
      return 0;
  if (q->front == q->back)
      q->front = q->back = 0;
  else if (q->front)
          q->front = q->front->next;
  tmp->next = 0;
  q->num_of_frames -=1;
  return tmp;
}

static void enqueue(struct fifo_queue* q, struct fifo_node*p) {
  if (q->back) {
      q->back->next = p;
      q->back = p;
  }
  else {
      q->front = p;
      q->back = p;   
  }
  q->num_of_frames +=1;
  return;
}

#define MAX_ROI 2
struct fd_rect_t {
  uint16_t x;
  uint16_t y;
  uint16_t dx;
  uint16_t dy;
};

struct fd_roi_t {
    uint32_t frame_id;
    int16_t rect_num;
    struct fd_rect_t faces[MAX_ROI];
};

typedef struct {
  int x;
  int y;
}cam_point_t;

typedef enum {
  MM_CAMERA_EVT_TYPE_CH,
  MM_CAMERA_EVT_TYPE_CTRL,
  MM_CAMERA_EVT_TYPE_STATS,
  MM_CAMERA_EVT_TYPE_INFO,
  MM_CAMERA_EVT_TYPE_MAX
} mm_camera_event_type_t;


typedef enum {
  MM_CAMERA_CTRL_EVT_ZOOM_DONE,
  MM_CAMERA_CTRL_EVT_AUTO_FOCUS_DONE,
  MM_CAMERA_CTRL_EVT_PREP_SNAPSHOT,
  MM_CAMERA_CTRL_EVT_SNAPSHOT_CONFIG_DONE,
  MM_CAMERA_CTRL_EVT_WDN_DONE, // wavelet denoise done
  MM_CAMERA_CTRL_EVT_ERROR,
  MM_CAMERA_CTRL_EVT_MAX
}mm_camera_ctrl_event_type_t;

typedef struct {
  mm_camera_ctrl_event_type_t evt;
  cam_ctrl_status_t status;
  unsigned long cookie;
} mm_camera_ctrl_event_t;

typedef enum {
  MM_CAMERA_CH_EVT_STREAMING_ON,
  MM_CAMERA_CH_EVT_STREAMING_OFF,
  MM_CAMERA_CH_EVT_STREAMING_ERR,
  MM_CAMERA_CH_EVT_DATA_DELIVERY_DONE,
  MM_CAMERA_CH_EVT_DATA_REQUEST_MORE,
  MM_CAMERA_CH_EVT_MAX
}mm_camera_ch_event_type_t;

typedef struct {
  uint32_t ch;
  mm_camera_ch_event_type_t evt;
} mm_camera_ch_event_t;

typedef enum {
  MM_CAMERA_STATS_EVT_HISTO,
  MM_CAMERA_STATS_EVT_MAX
} mm_camera_stats_event_type_t;

typedef struct {
  uint32_t buf_len;
  uint8_t num;
  uint8_t pmem_type;
  uint32_t vaddr[8];
} mm_camera_histo_mem_info_t;

typedef struct {
  uint32_t cookie;
  uint32_t histo_info;
  uint32_t histo_len;
} mm_camera_stats_histo_t;

typedef struct  {
  uint32_t event_id;
  union {
    mm_camera_stats_histo_t    stats_histo;
  } e;
} mm_camera_stats_event_t;

typedef enum {
  MM_CAMERA_INFO_EVT_HISTO_MEM_INFO,
  MM_CAMERA_INFO_EVT_ROI,
  MM_CAMERA_INFO_EVT_MAX
} mm_camera_info_event_type_t;

typedef struct  {
  uint32_t event_id;
  union {
    mm_camera_histo_mem_info_t histo_mem_info;
    struct fd_roi_t roi;
  } e;
} mm_camera_info_event_t;


typedef struct {
  mm_camera_event_type_t event_type;
  union {
    mm_camera_ch_event_t ch;
    mm_camera_ctrl_event_t ctrl;
    mm_camera_stats_event_t stats;
    mm_camera_info_event_t info;
  } e;
} mm_camera_event_t;


typedef enum {
  CAMERA_HFR_MODE_OFF = 1,
  CAMERA_HFR_MODE_60FPS,
  CAMERA_HFR_MODE_90FPS,
  CAMERA_HFR_MODE_120FPS,
  CAMERA_HFR_MODE_150FPS,
} camera_hfr_mode_t;


typedef enum {
  HDR_BRACKETING_OFF,
  HDR_MODE,
  EXP_BRACKETING_MODE
} hdr_mode;

#define MAX_EXP_BRACKETING_LENGTH 32

typedef struct {
  hdr_mode mode;
  uint32_t hdr_enable;
  uint32_t total_frames;
  uint32_t total_hal_frames;
  char values[32]; 
} exp_bracketing_t;



#endif /* __CAMERA_H__ */
