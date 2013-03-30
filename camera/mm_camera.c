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
#include "mm_camera_sock.h"
#include "mm_camera_interface2.h"
#include "mm_camera.h"

static int32_t mm_camera_send_native_ctrl_cmd(mm_camera_obj_t * my_obj,
                    cam_ctrl_type type, uint32_t length, void *value);
static int32_t mm_camera_ctrl_set_specialEffect (mm_camera_obj_t *my_obj, int effect) {
    struct v4l2_control ctrl;
    if (effect == CAMERA_EFFECT_MAX)
        effect = CAMERA_EFFECT_OFF;
    int rc = 0;

    ctrl.id = MSM_V4L2_PID_EFFECT;
    ctrl.value = effect;
    rc = ioctl(my_obj->ctrl_fd, VIDIOC_S_CTRL, &ctrl);
    return rc;
}

static int32_t mm_camera_ctrl_set_antibanding (mm_camera_obj_t *my_obj, int antibanding) {
    int rc = 0;
    struct v4l2_control ctrl;

    ctrl.id = V4L2_CID_POWER_LINE_FREQUENCY;
    ctrl.value = antibanding;
    rc = ioctl(my_obj->ctrl_fd, VIDIOC_S_CTRL, &ctrl);
    return rc;
}

static int32_t mm_camera_ctrl_set_auto_focus (mm_camera_obj_t *my_obj, int value)
{
    int rc = 0;
    struct v4l2_queryctrl queryctrl;

    memset (&queryctrl, 0, sizeof (queryctrl));
    queryctrl.id = V4L2_CID_FOCUS_AUTO;

    ALOGE("mm_camera_ctrl_set_auto_focus(%d)",value);
    if(value != 0 && value != 1) {
        CDBG("%s:boolean required, invalid value = %d\n",__func__, value);
        return -MM_CAMERA_E_INVALID_INPUT;
    }
    if (-1 == ioctl (my_obj->ctrl_fd, VIDIOC_QUERYCTRL, &queryctrl)) {
        CDBG ("V4L2_CID_FOCUS_AUTO is not supported\n");
    } else if (queryctrl.flags & V4L2_CTRL_FLAG_DISABLED) {
        CDBG ("%s:V4L2_CID_FOCUS_AUTO is not supported\n", __func__);
    } else {
        if(0 != (rc =  mm_camera_util_s_ctrl(my_obj->ctrl_fd,
                V4L2_CID_FOCUS_AUTO, value))){
            CDBG("%s: error, id=0x%x, value=%d, rc = %d\n",
                     __func__, V4L2_CID_FOCUS_AUTO, value, rc);
            rc = -1;
        }
    }
    return rc;
}

static int32_t mm_camera_ctrl_set_whitebalance (mm_camera_obj_t *my_obj, int mode) {

    int rc = 0, value;
    uint32_t id;

    switch(mode) {
    case MM_CAMERA_WHITE_BALANCE_AUTO:
        id = V4L2_CID_AUTO_WHITE_BALANCE;
        value = 1; /* TRUE */
        break;
    case MM_CAMERA_WHITE_BALANCE_OFF:
        id = V4L2_CID_AUTO_WHITE_BALANCE;
        value = 0; /* FALSE */
        break;
    default:
        id = V4L2_CID_WHITE_BALANCE_TEMPERATURE;
        if(mode == WHITE_BALANCE_DAYLIGHT) value = 6500;
        else if(mode == WHITE_BALANCE_INCANDESCENT) value = 2800;
        else if(mode == WHITE_BALANCE_FLUORESCENT ) value = 4200;
        else if(mode == WHITE_BALANCE_CLOUDY) value = 7500;
        else
            value = 4200;
    }
    if(0 != (rc =  mm_camera_util_s_ctrl(my_obj->ctrl_fd,
            id, value))){
        CDBG("%s: error, exp_metering_action_param=%d, rc = %d\n", __func__, value, rc);
        goto end;
    }
end:
    return rc;
}

static int32_t mm_camera_ctrl_set_toggle_afr (mm_camera_obj_t *my_obj) {
    int rc = 0;
    int value = 0;
    if(0 != (rc =  mm_camera_util_g_ctrl(my_obj->ctrl_fd,
            V4L2_CID_EXPOSURE_AUTO, &value))){
        goto end;
    }
    /* V4L2_CID_EXPOSURE_AUTO needs to be AUTO or SHUTTER_PRIORITY */
    if (value != V4L2_EXPOSURE_AUTO && value != V4L2_EXPOSURE_SHUTTER_PRIORITY) {
    CDBG("%s: V4L2_CID_EXPOSURE_AUTO needs to be AUTO/SHUTTER_PRIORITY\n",
        __func__);
    return -1;
  }
    if(0 != (rc =  mm_camera_util_g_ctrl(my_obj->ctrl_fd,
            V4L2_CID_EXPOSURE_AUTO_PRIORITY, &value))){
        goto end;
    }
    value = !value;
    if(0 != (rc =  mm_camera_util_s_ctrl(my_obj->ctrl_fd,
            V4L2_CID_EXPOSURE_AUTO_PRIORITY, value))){
        goto end;
    }
end:
    return rc;
}

static mm_camera_channel_type_t mm_camera_util_opcode_2_ch_type(
                             mm_camera_obj_t *my_obj,
                             mm_camera_ops_type_t opcode)
{
    mm_camera_channel_type_t type = MM_CAMERA_CH_MAX;
    switch(opcode) {
    case MM_CAMERA_OPS_PREVIEW:
        return MM_CAMERA_CH_PREVIEW;
    case MM_CAMERA_OPS_VIDEO:
        return MM_CAMERA_CH_VIDEO;
    case MM_CAMERA_OPS_SNAPSHOT:
        return MM_CAMERA_CH_SNAPSHOT;
    case MM_CAMERA_OPS_PREPARE_SNAPSHOT:
        return MM_CAMERA_CH_SNAPSHOT;
    case MM_CAMERA_OPS_RAW:
        return MM_CAMERA_CH_RAW;
    case MM_CAMERA_OPS_ZSL:
        return MM_CAMERA_CH_SNAPSHOT;
    default:
        break;
    }
    return type;
}

static int32_t mm_camera_util_set_op_mode(mm_camera_obj_t * my_obj,
    mm_camera_op_mode_type_t *op_mode)
{
    int32_t rc = MM_CAMERA_OK;
    uint32_t v4l2_op_mode = MSM_V4L2_CAM_OP_DEFAULT;

    if (my_obj->op_mode == *op_mode)
        goto end;
    if(mm_camera_poll_busy(my_obj) == TRUE) {
        CDBG("%s: cannot change op_mode while stream on\n", __func__);
        rc = -MM_CAMERA_E_INVALID_OPERATION;
        goto end;
    }
    switch(*op_mode) {
    case MM_CAMERA_OP_MODE_ZSL:
        v4l2_op_mode = MSM_V4L2_CAM_OP_ZSL;
            break;
    case MM_CAMERA_OP_MODE_CAPTURE:
        v4l2_op_mode = MSM_V4L2_CAM_OP_CAPTURE;
            break;
    case MM_CAMERA_OP_MODE_VIDEO:
        v4l2_op_mode = MSM_V4L2_CAM_OP_VIDEO;
            break;
    default:
        rc = - MM_CAMERA_E_INVALID_INPUT;
        goto end;
        break;
    }
    if(0 != (rc =  mm_camera_util_s_ctrl(my_obj->ctrl_fd,
            MSM_V4L2_PID_CAM_MODE, v4l2_op_mode))){
        CDBG("%s: input op_mode=%d, s_ctrl rc=%d\n", __func__, *op_mode, rc);
        goto end;
    }
    /* if success update mode field */
    my_obj->op_mode = *op_mode;
end:
    CDBG("%s: op_mode=%d,rc=%d\n", __func__, *op_mode, rc);
    return rc;
}

int32_t mm_camera_set_general_parm(mm_camera_obj_t * my_obj, mm_camera_parm_t *parm)
{
    int rc = -MM_CAMERA_E_NOT_SUPPORTED;
    int isZSL =0;

    switch(parm->parm_type)  {
    case MM_CAMERA_PARM_EXPOSURE:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd,
                                                                        MSM_V4L2_PID_EXP_METERING,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_SHARPNESS:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, V4L2_CID_SHARPNESS,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_CONTRAST:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, V4L2_CID_CONTRAST,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_SATURATION:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, V4L2_CID_SATURATION,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_BRIGHTNESS:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, V4L2_CID_BRIGHTNESS,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_WHITE_BALANCE:
        return mm_camera_ctrl_set_whitebalance (my_obj, *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_ISO:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, MSM_V4L2_PID_ISO,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_ZOOM:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, V4L2_CID_ZOOM_ABSOLUTE,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_LUMA_ADAPTATION:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, MSM_V4L2_PID_LUMA_ADAPTATION,
                                                                            *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_ANTIBANDING:
        return mm_camera_ctrl_set_antibanding (my_obj, *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_CONTINUOUS_AF:
        return mm_camera_ctrl_set_auto_focus(my_obj, *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_HJR:
        return mm_camera_util_s_ctrl(my_obj->ctrl_fd, MSM_V4L2_PID_HJR, *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_EFFECT:
        return mm_camera_ctrl_set_specialEffect (my_obj, *((int *)(parm->p_value)));
    case MM_CAMERA_PARM_FPS:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_FPS, sizeof(uint16_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_FPS_MODE:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_FPS_MODE, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_EXPOSURE_COMPENSATION:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_EXPOSURE_COMPENSATION, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_LED_MODE:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_LED_MODE, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_ROLLOFF:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_ROLLOFF, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_MODE:
        my_obj->current_mode = *((camera_mode_t *)parm->p_value);
        break;
    case MM_CAMERA_PARM_FOCUS_RECT:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_FOCUS_RECT, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_AEC_ROI:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_AEC_ROI, sizeof(roi_info_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_AF_ROI:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_AF_ROI, sizeof(roi_info_t), (void *)parm->p_value);
#if 0 //to be enabled later: @punits
    case MM_CAMERA_PARM_AF_MTR_AREA:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_AF_MTR_AREA, sizeof(af_mtr_area_t), (void *)parm->p_value);*/
    case MM_CAMERA_PARM_AEC_MTR_AREA:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_AEC_MTR_AREA, sizeof(aec_mtr_area_t), (void *)parm->p_value);
#endif
    case MM_CAMERA_PARM_FOCUS_MODE:
        ALOGE("CAMERA_PARM_FOCUS_MODE: %d",*((int *)(parm->p_value)));
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_FOCUS_MODE, sizeof(uint32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_BESTSHOT_MODE:
        CDBG("%s : MM_CAMERA_PARM_BESTSHOT_MODE value : %d",__func__,*((int *)(parm->p_value)));
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_BESTSHOT_MODE, sizeof(int32_t), (void *)parm->p_value);
        break;
    case MM_CAMERA_PARM_VIDEO_DIS:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_VIDEO_DIS_PARAMS, sizeof(video_dis_param_ctrl_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_VIDEO_ROT:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_VIDEO_ROT_PARAMS, sizeof(video_rotation_param_ctrl_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_SCE_FACTOR:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_SCE_FACTOR, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_FD:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_FD, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_AEC_LOCK:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_AEC_LOCK, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_AWB_LOCK:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_SET_AWB_LOCK,
                                                     sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_MCE:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_SET_PARM_MCE,
                                                     sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_HORIZONTAL_VIEW_ANGLE:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_HORIZONTAL_VIEW_ANGLE,
                                                     sizeof(focus_distances_info_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_VERTICAL_VIEW_ANGLE:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_VERTICAL_VIEW_ANGLE,
                                                     sizeof(focus_distances_info_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_RESET_LENS_TO_INFINITY:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                            CAMERA_SET_PARM_RESET_LENS_TO_INFINITY,
                            0, NULL);
    case MM_CAMERA_PARM_SNAPSHOTDATA:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_SNAPSHOTDATA,
                                                     sizeof(snapshotData_info_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_HFR:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_HFR, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_REDEYE_REDUCTION:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_REDEYE_REDUCTION, sizeof(int32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_WAVELET_DENOISE:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_WAVELET_DENOISE, sizeof(denoise_param_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_3D_DISPLAY_DISTANCE:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_3D_DISPLAY_DISTANCE, sizeof(float), (void *)parm->p_value);
    case MM_CAMERA_PARM_3D_VIEW_ANGLE:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_3D_VIEW_ANGLE, sizeof(uint32_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_ZOOM_RATIO:
        break;
    case MM_CAMERA_PARM_HISTOGRAM:
        return mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_HISTOGRAM, sizeof(int8_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_JPEG_ROTATION:
        if(my_obj->op_mode == MM_CAMERA_OP_MODE_ZSL){
           isZSL =1;
        }
        mm_jpeg_encoder_setRotation(*((int *)parm->p_value));
        return MM_CAMERA_OK;

    case MM_CAMERA_PARM_ASD_ENABLE:
      return mm_camera_send_native_ctrl_cmd(my_obj,
                  CAMERA_SET_ASD_ENABLE, sizeof(uint32_t), (void *)parm->p_value);

/*    case MM_CAMERA_PARM_RECORDING_HINT:
      return mm_camera_send_native_ctrl_cmd(my_obj,
                  CAMERA_SET_RECORDING_HINT, sizeof(uint32_t), (void *)parm->p_value);
*/
    case MM_CAMERA_PARM_HDR_MODE:
      return mm_camera_send_native_ctrl_cmd(my_obj,
                  CAMERA_SET_HDR_MODE, sizeof(uint32_t), (void *)parm->p_value);


    case MM_CAMERA_PARM_PREVIEW_FORMAT:
      return mm_camera_send_native_ctrl_cmd(my_obj,
                  CAMERA_SET_PARM_PREVIEW_FORMAT, sizeof(uint32_t), (void *)parm->p_value);

    case MM_CAMERA_PARM_DIS_ENABLE:
      ALOGE("CAMERA_PARM_DIS_ENABLE not supported");
      return 0;//mm_camera_send_native_ctrl_cmd(my_obj,
               //   CAMERA_SET_DIS_ENABLE, sizeof(uint32_t), (void *)parm->p_value);

    case MM_CAMERA_PARM_FULL_LIVESHOT: {
      my_obj->full_liveshot = *((int *)(parm->p_value));
      ALOGE("full_liveshot = %d",my_obj->full_liveshot);
      return mm_camera_send_native_ctrl_cmd(my_obj,
                  CAMERA_SET_FULL_LIVESHOT, sizeof(uint32_t), (void *)parm->p_value);
    }
    case MM_CAMERA_PARM_HDR:
      return mm_camera_send_native_ctrl_cmd(my_obj,
                        CAMERA_SET_PARM_HDR, sizeof(exp_bracketing_t), (void *)parm->p_value);

    default:
        CDBG("%s: default: parm %d not supported\n", __func__, parm->parm_type);
        break;
    }
    return rc;
}
static int32_t mm_camera_send_native_ctrl_cmd(mm_camera_obj_t * my_obj,
                    cam_ctrl_type type, uint32_t length, void *value)
{
    int rc = -1;
    struct msm_ctrl_cmd ctrl_cmd;
    memset(&ctrl_cmd, 0, sizeof(ctrl_cmd));
    ctrl_cmd.type = type;
    ctrl_cmd.length = (uint16_t)length;
    ctrl_cmd.timeout_ms = 1000;
    ctrl_cmd.value = value;
    ctrl_cmd.status = CAM_CTRL_SUCCESS;
    rc = mm_camera_util_s_ctrl(my_obj->ctrl_fd, MSM_V4L2_PID_CTRL_CMD,
                                                                            (int)&ctrl_cmd);
    CDBG("%s: type=%d, rc = %d, status = %d\n",
                __func__, type, rc, ctrl_cmd.status);

    if(rc != MM_CAMERA_OK || ((ctrl_cmd.status != CAM_CTRL_ACCEPTED) &&
      (ctrl_cmd.status != CAM_CTRL_SUCCESS) &&
      (ctrl_cmd.status != CAM_CTRL_INVALID_PARM)))
        rc = -1;
    return rc;
}
int32_t mm_camera_set_parm(mm_camera_obj_t * my_obj,
    mm_camera_parm_t *parm)
{
    int32_t rc = -1;
    uint16_t len;
    CDBG("%s type =%d", __func__, parm->parm_type);
    switch(parm->parm_type) {
    case MM_CAMERA_PARM_OP_MODE:
        rc = mm_camera_util_set_op_mode(my_obj,
                        (mm_camera_op_mode_type_t *)parm->p_value);
        break;
    case MM_CAMERA_PARM_DIMENSION:
        rc = mm_camera_send_native_ctrl_cmd(my_obj,
                    CAMERA_SET_PARM_DIMENSION, sizeof(cam_ctrl_dimension_t), parm->p_value);
        if(rc != MM_CAMERA_OK) {
            CDBG("%s: mm_camera_send_native_ctrl_cmd err=%d\n", __func__, rc);
            break;
        }
        memcpy(&my_obj->dim, (cam_ctrl_dimension_t *)parm->p_value,
                     sizeof(cam_ctrl_dimension_t));
        CDBG("%s: dw=%d,dh=%d,vw=%d,vh=%d,pw=%d,ph=%d,tw=%d,th=%d,raw_w=%d,raw_h=%d, %d %d %d %d \n",
                 __func__,
                 my_obj->dim.display_width,my_obj->dim.display_height,
                 my_obj->dim.video_width, my_obj->dim.video_height,
                 my_obj->dim.picture_width,my_obj->dim.picture_height,
                 my_obj->dim.ui_thumbnail_width,my_obj->dim.ui_thumbnail_height,
                 my_obj->dim.raw_picture_width,my_obj->dim.raw_picture_height,
                 my_obj->dim.display_frame_offset.sp.len, my_obj->dim.video_frame_offset.sp.len,
                 my_obj->dim.picture_frame_offset.sp.len, my_obj->dim.thumb_frame_offset.sp.len);
/*
          unsigned *p=(char *)&my_obj->dim;
          int i;
          for(i=0;i<sizeof(my_obj->dim)/4;i++) {
              CDBG("%08x ",*p++);
          }
*/
          break;
    case MM_CAMERA_PARM_SNAPSHOT_BURST_NUM:
        CDBG("%s: Setting snapshot burst number: %d\n", __func__, *((int *)parm->p_value));
        my_obj->snap_burst_num_by_user = *((int *)parm->p_value);
        rc = MM_CAMERA_OK;
        break;
    case MM_CAMERA_PARM_CH_IMAGE_FMT:
        {
            mm_camera_ch_image_fmt_parm_t *fmt;
            fmt = (mm_camera_ch_image_fmt_parm_t *)parm->p_value;
            rc = mm_camera_ch_fn(my_obj,    fmt->ch_type,
                            MM_CAMERA_STATE_EVT_SET_FMT, fmt);
        }
        break;
    default:
        rc = mm_camera_set_general_parm(my_obj, parm);
        break;
    }
    return rc;
}

int32_t mm_camera_get_parm(mm_camera_obj_t * my_obj,
                            mm_camera_parm_t *parm)
{
    int32_t rc = MM_CAMERA_OK;

    switch(parm->parm_type) {
    case MM_CAMERA_PARM_CROP:
      return rc = mm_camera_ch_fn(my_obj,
                    ((mm_camera_ch_crop_t *)parm->p_value)->ch_type,
                    MM_CAMERA_STATE_EVT_GET_CROP, parm->p_value);
      break;
    case MM_CAMERA_PARM_DIMENSION:
        memcpy(parm->p_value, &my_obj->dim, sizeof(my_obj->dim));
        CDBG("%s: dw=%d,dh=%d,vw=%d,vh=%d,pw=%d,ph=%d,tw=%d,th=%d,ovx=%x,ovy=%d,opx=%d,opy=%d, m_fmt=%d, t_ftm=%d\n",
                 __func__,
                 my_obj->dim.display_width,my_obj->dim.display_height,
                 my_obj->dim.video_width,my_obj->dim.video_height,
                 my_obj->dim.picture_width,my_obj->dim.picture_height,
                 my_obj->dim.ui_thumbnail_width,my_obj->dim.ui_thumbnail_height,
                 my_obj->dim.orig_video_width,my_obj->dim.orig_video_height,
                 my_obj->dim.orig_picture_width,my_obj->dim.orig_picture_height,
                 my_obj->dim.main_img_format, my_obj->dim.thumb_format);
        break;
    case MM_CAMERA_PARM_MAX_PICTURE_SIZE: {
        mm_camera_dimension_t *dim =
            (mm_camera_dimension_t *)parm->p_value;
        dim->height = my_obj->properties.max_pict_height;
        dim->width = my_obj->properties.max_pict_width;
        CDBG("%s: Max Picture Size: %d X %d\n", __func__,
             dim->width, dim->height);
    }
        break;
    case MM_CAMERA_PARM_MAX_PREVIEW_SIZE: {
        mm_camera_dimension_t *dim =
            (mm_camera_dimension_t *)parm->p_value;
        dim->height = my_obj->properties.max_preview_height;
        dim->width = my_obj->properties.max_preview_width;
        CDBG("%s: Max Preview Size: %d X %d\n", __func__,
             dim->width, dim->height);
    }
        break;
    case MM_CAMERA_PARM_MAX_HFR_MODE:
        ALOGE("CAMERA_PARM_MAX_HFR_MODE not supported");
        return 0;//mm_camera_send_native_ctrl_cmd(my_obj, CAMERA_GET_PARM_MAX_HFR_MODE,
                //sizeof(camera_hfr_mode_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_FOCAL_LENGTH:
        return mm_camera_send_native_ctrl_cmd(my_obj, CAMERA_GET_PARM_FOCAL_LENGTH,
                     sizeof(focus_distances_info_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_HORIZONTAL_VIEW_ANGLE:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_HORIZONTAL_VIEW_ANGLE,
                                                     sizeof(focus_distances_info_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_VERTICAL_VIEW_ANGLE:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_VERTICAL_VIEW_ANGLE,
                                                     sizeof(focus_distances_info_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_FOCUS_DISTANCES:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_FOCUS_DISTANCES,
                     sizeof(focus_distances_info_t), (void *)parm->p_value);
  case MM_CAMERA_PARM_QUERY_FLASH4SNAP:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_QUERY_FLASH_FOR_SNAPSHOT,
                     sizeof(int), (void *)parm->p_value);
  case MM_CAMERA_PARM_3D_FRAME_FORMAT:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_3D_FRAME_FORMAT,
                     sizeof(camera_3d_frame_t), (void *)parm->p_value);
    case MM_CAMERA_PARM_MAXZOOM:
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_MAXZOOM,
                     sizeof(int), (void *)parm->p_value);
    case MM_CAMERA_PARM_ZOOM_RATIO: {
        mm_camera_zoom_tbl_t *tbl = (mm_camera_zoom_tbl_t *)parm->p_value;
        return mm_camera_send_native_ctrl_cmd(my_obj,   CAMERA_GET_PARM_ZOOMRATIOS,
                     sizeof(int16_t)*tbl->size, tbl->zoom_ratio_tbl);
    }
    case MM_CAMERA_PARM_OP_MODE:
        *((mm_camera_op_mode_type_t *)parm->p_value) = my_obj->op_mode;
        break;
    case MM_CAMERA_PARM_SNAPSHOT_BURST_NUM:
        *((int *)parm->p_value) = my_obj->snap_burst_num_by_user;
        break;
    default:
        /* needs to add more implementation */
        rc = -1;
        break;
    }
    return rc;
}

int32_t mm_camera_request_buf(mm_camera_obj_t * my_obj, mm_camera_reg_buf_t *buf)
{
    int32_t rc = -MM_CAMERA_E_GENERAL;
    rc = mm_camera_ch_fn(my_obj,    buf->ch_type,
                    MM_CAMERA_STATE_EVT_REQUEST_BUF, (void *)&buf->preview);
    return rc;
}

int32_t mm_camera_enqueue_buf(mm_camera_obj_t * my_obj, mm_camera_reg_buf_t *buf)
{
    int32_t rc = -MM_CAMERA_E_GENERAL;
    rc = mm_camera_ch_fn(my_obj,    buf->ch_type,
                    MM_CAMERA_STATE_EVT_ENQUEUE_BUF, (void *)&buf->preview);
    return rc;
}

int32_t mm_camera_prepare_buf(mm_camera_obj_t * my_obj, mm_camera_reg_buf_t *buf)
{
    int32_t rc = -MM_CAMERA_E_GENERAL;
    rc = mm_camera_ch_fn(my_obj,    buf->ch_type,
                    MM_CAMERA_STATE_EVT_REG_BUF, (void *)&buf->preview);
    return rc;
}
int32_t mm_camera_unprepare_buf(mm_camera_obj_t * my_obj, mm_camera_channel_type_t ch_type)
{
    int32_t rc = -MM_CAMERA_E_GENERAL;
    rc = mm_camera_ch_fn(my_obj, ch_type,
                    MM_CAMERA_STATE_EVT_UNREG_BUF, NULL);
    return rc;
}

static int mm_camera_evt_sub(mm_camera_obj_t * my_obj,
                             mm_camera_event_type_t evt_type, int reg_count)
{
    int rc = MM_CAMERA_OK;
    struct v4l2_event_subscription sub;

    memset(&sub, 0, sizeof(sub));
    sub.type = V4L2_EVENT_PRIVATE_START+MSM_CAM_APP_NOTIFY_EVENT;
    if(reg_count == 0) {
        /* unsubscribe */
        if(my_obj->evt_type_mask == (uint32_t)(1 << evt_type)) {
            rc = ioctl(my_obj->ctrl_fd, VIDIOC_UNSUBSCRIBE_EVENT, &sub);
            CDBG("%s: unsubscribe event 0x%x, rc = %d", __func__, sub.type, rc);
        }
        my_obj->evt_type_mask &= ~(1 << evt_type);
        if(my_obj->evt_type_mask == 0) {
            /* kill the polling thraed when unreg the last event */
            mm_camera_poll_thread_release(my_obj, MM_CAMERA_CH_MAX);
        }
    } else {
        if(!my_obj->evt_type_mask) {
            /* this is the first reg event */
            rc = ioctl(my_obj->ctrl_fd, VIDIOC_SUBSCRIBE_EVENT, &sub);
            CDBG("%s: subscribe event 0x%x, rc = %d", __func__, sub.type, rc);
            if (rc < 0)
                goto end;
        }
        my_obj->evt_type_mask |= (1 << evt_type);
        if(my_obj->evt_type_mask == (uint32_t)(1 << evt_type)) {
            /* launch event polling when subscribe the first event */
            rc = mm_camera_poll_thread_launch(my_obj, MM_CAMERA_CH_MAX);
        }
    }
end:
    return rc;
}

int mm_camera_reg_event(mm_camera_obj_t * my_obj, mm_camera_event_notify_t evt_cb,
                           void *user_data, mm_camera_event_type_t evt_type)
{
    int i;
    int rc = -1;
    mm_camera_evt_obj_t *evt_array = &my_obj->evt[evt_type];
    if(evt_cb) {
        /* this is reg case */
        for(i = 0; i < MM_CAMERA_EVT_ENTRY_MAX; i++) {
            if(evt_array->evt[i].user_data == NULL) {
                evt_array->evt[i].evt_cb = evt_cb;
                evt_array->evt[i].user_data = user_data;
                evt_array->reg_count++;
                rc = MM_CAMERA_OK;
                break;
            }
        }
    } else {
        /* this is unreg case */
        for(i = 0; i < MM_CAMERA_EVT_ENTRY_MAX; i++) {
            if(evt_array->evt[i].user_data == user_data) {
                evt_array->evt[i].evt_cb = NULL;
                evt_array->evt[i].user_data = NULL;
                evt_array->reg_count--;
                rc = MM_CAMERA_OK;
                break;
            }
        }
    }
    if(rc == MM_CAMERA_OK && evt_array->reg_count <= 1) {
        /* subscribe/unsubscribe event to kernel */
        rc = mm_camera_evt_sub(my_obj, evt_type, evt_array->reg_count);
    }
    return rc;
}


static int32_t mm_camera_send_af_failed_event(mm_camera_obj_t *my_obj)
{
    int rc = 0;
    mm_camera_event_t event;
    event.event_type = MM_CAMERA_EVT_TYPE_CTRL;
    event.e.ctrl.evt= MM_CAMERA_CTRL_EVT_AUTO_FOCUS_DONE;
    event.e.ctrl.status=CAM_CTRL_FAILED;
    CDBG_HIGH("%s: Issuing call",__func__);
    rc = mm_camera_poll_send_ch_event(my_obj, &event);
    return rc;
}

static int32_t mm_camera_send_ch_on_off_event(mm_camera_obj_t *my_obj,
                                       mm_camera_channel_type_t ch_type,
                                       mm_camera_ch_event_type_t evt)
{
    int rc = 0;
    mm_camera_event_t event;
    event.event_type = MM_CAMERA_EVT_TYPE_CH;
    event.e.ch.evt = evt;
    event.e.ch.ch = ch_type;
    CDBG("%s: stream on event, type=0x%x, ch=%d, evt=%d",
         __func__, event.event_type, event.e.ch.ch, event.e.ch.evt);
    rc = mm_camera_poll_send_ch_event(my_obj, &event);
    return rc;
}

int32_t mm_camera_action_start(mm_camera_obj_t *my_obj,
                            mm_camera_ops_type_t opcode, void *parm)
{
    int32_t rc = -MM_CAMERA_E_GENERAL;
    int send_on_off_evt = 1;
    mm_camera_channel_type_t ch_type;
    switch(opcode) {
    case MM_CAMERA_OPS_FOCUS: {
        if(!parm) return rc;
        if(0 > mm_camera_send_native_ctrl_cmd(my_obj,
          CAMERA_SET_PARM_AUTO_FOCUS,
          sizeof(isp3a_af_mode_t), parm))
          mm_camera_send_af_failed_event(my_obj);
        return MM_CAMERA_OK;
    }
    case MM_CAMERA_OPS_GET_BUFFERED_FRAME: {
        mm_camera_ops_parm_get_buffered_frame_t *tmp =
            (mm_camera_ops_parm_get_buffered_frame_t *)parm;
        rc = mm_camera_ch_fn(my_obj, tmp->ch_type,
                 MM_CAMERA_STATE_EVT_DISPATCH_BUFFERED_FRAME, NULL);
        return rc;
    }
    default:
        break;
    }
    ch_type = mm_camera_util_opcode_2_ch_type(my_obj, opcode);
    CDBG("%s:ch=%d,op_mode=%d,opcode=%d\n",
        __func__,ch_type,my_obj->op_mode,opcode);
    switch(my_obj->op_mode) {
    case MM_CAMERA_OP_MODE_ZSL:
    case MM_CAMERA_OP_MODE_CAPTURE:
        switch(opcode) {
        case MM_CAMERA_OPS_PREVIEW:
        case MM_CAMERA_OPS_SNAPSHOT:
        case MM_CAMERA_OPS_ZSL:
        case MM_CAMERA_OPS_RAW:
            rc = mm_camera_ch_fn(my_obj, ch_type,
                    MM_CAMERA_STATE_EVT_STREAM_ON, NULL);
            break;
        default:
            break;
        }
        break;
    case MM_CAMERA_OP_MODE_VIDEO:
        switch(opcode) {
        case MM_CAMERA_OPS_PREVIEW:
        case MM_CAMERA_OPS_VIDEO:
        case MM_CAMERA_OPS_SNAPSHOT:
            rc = mm_camera_ch_fn(my_obj,    ch_type,
                    MM_CAMERA_STATE_EVT_STREAM_ON, NULL);
            CDBG("%s: op_mode=%d, ch %d, rc=%d\n",
                __func__, MM_CAMERA_OP_MODE_VIDEO, ch_type ,rc);
            break;
        case MM_CAMERA_OPS_PREPARE_SNAPSHOT:
            send_on_off_evt = 0;
            rc = mm_camera_send_native_ctrl_cmd(my_obj,CAMERA_PREPARE_SNAPSHOT, 0, NULL);
            CDBG("%s: prepare snapshot done opcode = %d, rc= %d\n", __func__, opcode, rc);
            break;
        default:
            break;
        }
        break;
    default:
        break;
    }
    CDBG("%s: ch=%d,op_mode=%d,opcode=%d\n", __func__, ch_type,
      my_obj->op_mode, opcode);
    if(send_on_off_evt)
      rc = mm_camera_send_ch_on_off_event(my_obj,ch_type,MM_CAMERA_CH_EVT_STREAMING_ON);
    return rc;
}

int32_t mm_camera_action_stop(mm_camera_obj_t *my_obj,
    mm_camera_ops_type_t opcode, void *parm)
{
    int32_t rc = -MM_CAMERA_E_GENERAL;
    mm_camera_channel_type_t ch_type;

    if(opcode == MM_CAMERA_OPS_FOCUS) {
      return mm_camera_send_native_ctrl_cmd(my_obj,
                                            CAMERA_AUTO_FOCUS_CANCEL, 0, NULL);
    }

    ch_type = mm_camera_util_opcode_2_ch_type(my_obj, opcode);
    switch(my_obj->op_mode) {
    case MM_CAMERA_OP_MODE_ZSL:
    case MM_CAMERA_OP_MODE_CAPTURE:
        switch(opcode) {
        case MM_CAMERA_OPS_PREVIEW:
        case MM_CAMERA_OPS_SNAPSHOT:
        case MM_CAMERA_OPS_ZSL:
        case MM_CAMERA_OPS_RAW:
            rc = mm_camera_ch_fn(my_obj, ch_type,
                            MM_CAMERA_STATE_EVT_STREAM_OFF, NULL);
            CDBG("%s:CAPTURE mode STREAMOFF rc=%d\n",__func__, rc);
            break;
        default:
            break;
        }
        break;
    case MM_CAMERA_OP_MODE_VIDEO:
        switch(opcode) {
        case MM_CAMERA_OPS_PREVIEW:
        case MM_CAMERA_OPS_VIDEO:
        case MM_CAMERA_OPS_SNAPSHOT:
            rc = mm_camera_ch_fn(my_obj , ch_type,
                            MM_CAMERA_STATE_EVT_STREAM_OFF, NULL);
            CDBG("%s:VIDEO mode STREAMOFF rc=%d\n",__func__, rc);
            break;
        default:
            break;
        }
        break;
    default:
        break;
    }
    CDBG("%s:ch=%d\n",__func__, ch_type);
    rc = mm_camera_send_ch_on_off_event(my_obj,ch_type,MM_CAMERA_CH_EVT_STREAMING_OFF);
    return rc;
}

static void mm_camera_init_ch_stream_count(mm_camera_obj_t *my_obj)
{
    int i;

    for(i = 0; i < MM_CAMERA_CH_MAX; i++) {
        if(i == MM_CAMERA_CH_SNAPSHOT) {
            my_obj->ch_stream_count[i].stream_on_count_cfg = 2;
            my_obj->ch_stream_count[i].stream_off_count_cfg = 2;
        } else {
            my_obj->ch_stream_count[i].stream_on_count_cfg = 1;
            my_obj->ch_stream_count[i].stream_off_count_cfg = 1;
        }
    }
}

int32_t mm_camera_open(mm_camera_obj_t *my_obj,
                    mm_camera_op_mode_type_t op_mode)
{
    char dev_name[MM_CAMERA_DEV_NAME_LEN];
    int32_t rc = MM_CAMERA_OK;
    int8_t n_try=MM_CAMERA_DEV_OPEN_TRIES;
    uint8_t sleep_msec=MM_CAMERA_DEV_OPEN_RETRY_SLEEP;
    uint8_t i;

	CDBG("%s:  begin\n", __func__);

    if(my_obj->op_mode != MM_CAMERA_OP_MODE_NOTUSED) {
        CDBG("%s: not allowed in existing op mode %d\n",
                 __func__, my_obj->op_mode);
        return -MM_CAMERA_E_INVALID_OPERATION;
    }
    if(op_mode >= MM_CAMERA_OP_MODE_MAX) {
        CDBG("%s: invalid input %d\n",
                 __func__, op_mode);
        return -MM_CAMERA_E_INVALID_INPUT;
    }
    snprintf(dev_name, sizeof(dev_name), "/dev/%s", mm_camera_util_get_dev_name(my_obj));
    //rc = mm_camera_dev_open(&my_obj->ctrl_fd, dev_name);
	CDBG("%s: mm_camera_dev_open rc = %d\n", __func__, rc);

    do{
        n_try--;
        my_obj->ctrl_fd = open(dev_name,O_RDWR | O_NONBLOCK);
		ALOGE("%s:  ctrl_fd = %d", __func__, my_obj->ctrl_fd);
        ALOGE("Errno:%d",errno);
        if((my_obj->ctrl_fd > 0) || (errno != EIO) || (n_try <= 0 )) {
			ALOGE("%s:  opened, break out while loop", __func__);

            break;
		}
        CDBG("%s:failed with I/O error retrying after %d milli-seconds",
             __func__,sleep_msec);
        usleep(sleep_msec*1000);
    }while(n_try>0);

	ALOGE("%s:  after while loop", __func__);
    if (my_obj->ctrl_fd <= 0) {
        CDBG("%s: cannot open control fd of '%s' Errno = %d\n",
                 __func__, mm_camera_util_get_dev_name(my_obj),errno);
        return -MM_CAMERA_E_GENERAL;
    }
	ALOGE("%s:  2\n", __func__);

    /* open domain socket*/

/*
    n_try=MM_CAMERA_DEV_OPEN_TRIES;
    do{
        n_try--;
        my_obj->ds_fd = mm_camera_socket_create(my_obj->my_id, MM_CAMERA_SOCK_TYPE_UDP); // TODO: UDP for now, change to TCP
        ALOGE("%s:  ds_fd = %d", __func__, my_obj->ds_fd);
        ALOGE("Errno:%d",errno);
        if((my_obj->ds_fd > 0) || (n_try <= 0 )) {
            ALOGE("%s:  opened, break out while loop", __func__);
            break;
        }
        CDBG("%s:failed with I/O error retrying after %d milli-seconds",
             __func__,sleep_msec);
        usleep(sleep_msec*1000);
    }while(n_try>0);

    ALOGE("%s:  after while loop for domain socket open", __func__);
    if (my_obj->ds_fd <= 0) {
        CDBG("%s: cannot open domain socket fd of '%s' Errno = %d\n",
                 __func__, mm_camera_util_get_dev_name(my_obj),errno);
//        return -MM_CAMERA_E_GENERAL;
    }
*/
    /* set ctrl_fd to be the mem_mapping fd */
    rc =  mm_camera_util_s_ctrl(my_obj->ctrl_fd,
                        MSM_V4L2_PID_MMAP_INST, 0);
    if (rc < 0) {
        CDBG("error: ioctl VIDIOC_S_CTRL MSM_V4L2_PID_MMAP_INST failed: %s\n",
        strerror(errno));
        return -MM_CAMERA_E_GENERAL;
    }
    if(op_mode != MM_CAMERA_OP_MODE_NOTUSED)
        rc =  mm_camera_util_s_ctrl(my_obj->ctrl_fd,
                            MSM_V4L2_PID_CAM_MODE, op_mode);
    if(!rc) {
        my_obj->op_mode = op_mode;
        my_obj->current_mode = CAMERA_MODE_2D; /* set geo mode to 2D by default */
    }

    /* get camera capabilities */
    memset(&my_obj->properties, 0, sizeof(cam_prop_t));
    rc = mm_camera_send_native_ctrl_cmd(my_obj,
                                        CAMERA_GET_CAPABILITIES,
                                        sizeof(cam_prop_t),
                                        (void *)& my_obj->properties);
    if (rc != MM_CAMERA_OK) {
        CDBG("%s: cannot get camera capabilities\n", __func__);
        return -MM_CAMERA_E_GENERAL;
    }

    mm_camera_poll_threads_init(my_obj);
    mm_camera_init_ch_stream_count(my_obj);
    CDBG("%s : Launch Threads in Cam Open",__func__);
    for(i = 0; i < MM_CAMERA_CH_MAX; i++) {
        mm_camera_poll_thread_launch(my_obj,(mm_camera_channel_type_t)i);
    }
    CDBG("%s: '%s', ctrl_fd=%d,op_mode=%d,rc=%d\n",
             __func__, dev_name, my_obj->ctrl_fd, my_obj->op_mode, rc);
    return rc;
}

int32_t mm_camera_close(mm_camera_obj_t *my_obj)
{
    int i, rc = 0;

    for(i = 0; i < MM_CAMERA_CH_MAX; i++){
        mm_camera_ch_fn(my_obj, (mm_camera_channel_type_t)i,
                                MM_CAMERA_STATE_EVT_RELEASE, NULL);
    }

    CDBG("%s : Close Threads in Cam Close",__func__);
    for(i = 0; i < MM_CAMERA_CH_MAX; i++) {
        mm_camera_poll_thread_release(my_obj,(mm_camera_channel_type_t)i);
    }
    mm_camera_poll_threads_deinit(my_obj);
    my_obj->op_mode = MM_CAMERA_OP_MODE_NOTUSED;
    if(my_obj->ctrl_fd > 0) {
        mm_camera_histo_mmap(my_obj, NULL);
        rc = close(my_obj->ctrl_fd);
        if(rc < 0) {
            /* this is a dead end. */
            CDBG("%s: !!!!FATAL ERROR!!!! ctrl_fd = %d, rc = %d",
                 __func__, my_obj->ctrl_fd, rc);
        }
        my_obj->ctrl_fd = 0;
    }
    if(my_obj->ds_fd > 0) {
//        mm_camera_socket_close(my_obj->ds_fd);
        my_obj->ds_fd = 0;
    }
    return MM_CAMERA_OK;
}

int32_t mm_camera_action(mm_camera_obj_t *my_obj, uint8_t start,
                        mm_camera_ops_type_t opcode, void *parm)
{
    int32_t rc = - MM_CAMERA_E_INVALID_OPERATION;

    if(start)   rc = mm_camera_action_start(my_obj, opcode, parm);
    else rc = mm_camera_action_stop(my_obj, opcode, parm);
    CDBG("%s:start_flag=%d,opcode=%d,parm=%p,rc=%d\n",__func__,start,opcode,parm, rc);
    return rc;
}

int32_t mm_camera_ch_acquire(mm_camera_obj_t *my_obj, mm_camera_channel_type_t ch_type)
{
    return mm_camera_ch_fn(my_obj,ch_type, MM_CAMERA_STATE_EVT_ACQUIRE, 0);
}

void mm_camera_ch_release(mm_camera_obj_t *my_obj, mm_camera_channel_type_t ch_type)
{
    mm_camera_ch_fn(my_obj,ch_type, MM_CAMERA_STATE_EVT_RELEASE, 0);
}


int32_t mm_camera_sendmsg(mm_camera_obj_t *my_obj, void *msg, uint32_t buf_size, int sendfd)
{
	return 1;
//    return mm_camera_socket_sendmsg(my_obj->ds_fd, msg, buf_size, sendfd);
}

