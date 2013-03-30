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

//#define LOG_NDEBUG 0
#define LOG_NIDEBUG 0
#define LOG_TAG "QCameraHWI_Parm"
#include <utils/Log.h>

#include <utils/Errors.h>
#include <utils/threads.h>
#include <binder/MemoryHeapPmem.h>
#include <utils/String16.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <cutils/properties.h>
#include <math.h>
#if HAVE_ANDROID_OS
#include <linux/android_pmem.h>
#endif
#include <linux/ioctl.h>
#include <camera/CameraParameters.h>
#include <media/mediarecorder.h>
#include <gralloc_priv.h>

#include "linux/msm_mdp.h"
#include <linux/fb.h>

extern "C" {
#include <fcntl.h>
#include <time.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <termios.h>
#include <assert.h>
#include <stdlib.h>
#include <ctype.h>
#include <signal.h>
#include <errno.h>
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <sys/time.h>
#include <stdlib.h>
#include <linux/ion.h>
#include <camera.h>
//#include <cam_fifo.h>
//#include <liveshot.h>
//#include <jpege.h>
//#include <jpeg_encoder.h>

} // extern "C"

#include "QCameraHWI.h"

// Disable exposure and white balance locking, it works but never gets disabled and breaks flash
#define ALLOW_AECAWBLOCK 0

/* QCameraHardwareInterface class implementation goes here*/
/* following code implements the parameter logic of this class*/
#define EXPOSURE_COMPENSATION_MAXIMUM_NUMERATOR 12
#define EXPOSURE_COMPENSATION_MINIMUM_NUMERATOR -12
#define EXPOSURE_COMPENSATION_DEFAULT_NUMERATOR 0
#define EXPOSURE_COMPENSATION_DENOMINATOR 6
#define EXPOSURE_COMPENSATION_STEP ((float (1))/EXPOSURE_COMPENSATION_DENOMINATOR)
//#define FOCUS_AREA_INIT "(-1000,-1000,1000,1000,1000)"

#define FOCUS_AREA_INIT "(0,0,0,0,0)"

#define HDR_HAL_FRAME 2

//Default FPS
#define MINIMUM_FPS 5
#define MAXIMUM_FPS 30
#define DEFAULT_FPS MAXIMUM_FPS

//Default Picture Width
#define DEFAULT_PICTURE_WIDTH  640
#define DEFAULT_PICTURE_HEIGHT 480

//Default Preview Width
#define DEFAULT_PREVIEW_WIDTH 640
#define DEFAULT_PREVIEW_HEIGHT 480

#define THUMBNAIL_SIZE_COUNT (sizeof(thumbnail_sizes)/sizeof(thumbnail_size_type))
#define DEFAULT_THUMBNAIL_SETTING 4
#define THUMBNAIL_WIDTH_STR "512"
#define THUMBNAIL_HEIGHT_STR "384"
#define THUMBNAIL_SMALL_HEIGHT 144

#define JPEG_THUMBNAIL_SIZE_COUNT (sizeof(jpeg_thumbnail_sizes)/sizeof(camera_size_type))
#define DONT_CARE_COORDINATE -1

//for histogram stats
#define HISTOGRAM_STATS_SIZE 257

//Supported preview fps ranges should be added to this array in the form (minFps,maxFps)
static  android::FPSRange FpsRangesSupported[] = {
            android::FPSRange(MINIMUM_FPS*1000,MAXIMUM_FPS*1000)
        };
#define FPS_RANGES_SUPPORTED_COUNT (sizeof(FpsRangesSupported)/sizeof(FpsRangesSupported[0]))


typedef struct {
    uint32_t aspect_ratio;
    uint32_t width;
    uint32_t height;
} thumbnail_size_type;

static thumbnail_size_type thumbnail_sizes[] = {
{ 7281, 512, 288 }, //1.777778
{ 6826, 480, 288 }, //1.666667
{ 6808, 256, 154 }, //1.66233
{ 6144, 432, 288 }, //1.5
{ 5461, 512, 384 }, //1.333333
{ 5006, 352, 288 }, //1.222222
{ 5461, 320, 240 }, //1.33333
{ 5006, 176, 144 }, //1.222222
};

static camera_size_type jpeg_thumbnail_sizes[]  = {
{ 512, 288 },
{ 480, 288 },
{ 432, 288 },
{ 512, 384 },
{ 352, 288 },
{ 640, 480},
{0,0}
};

static camera_size_type default_preview_sizes[] = {
  { 1920, 1088}, //1080p
  { 1280, 720}, // 720P, reserved
  { 960, 720}, // for panorama
//  { 960, 544},
  { 800, 480}, // WVGA
  { 768, 432},
  { 720, 480},
  { 640, 480}, // VGA
  { 576, 432},
  { 480, 320}, // HVGA
  { 384, 288},
  { 352, 288}, // CIF
  { 320, 240}, // QVGA
  { 240, 160}, // SQVGA
  { 176, 144}, // QCIF
};

static camera_size_type supported_video_sizes[] = {
  { 1920, 1088},// 1080p
  { 1280, 720}, // 720p
  { 960, 720},  // for panorama
  { 800, 480},  // WVGA
  { 768, 432},
  { 720, 480},  // 480p
  { 640, 480},  // VGA
  { 480, 320},  // HVGA
  { 352, 288},  // CIF
  { 320, 240},  // QVGA
  { 176, 144},  // QCIF
};
#define SUPPORTED_VIDEO_SIZES_COUNT (sizeof(supported_video_sizes)/sizeof(camera_size_type))

static struct camera_size_type zsl_picture_sizes[] = {
  { 1024, 768}, // 1MP XGA
  { 800, 600}, //SVGA
  { 800, 480}, // WVGA
  { 640, 480}, // VGA
  { 352, 288}, //CIF
  { 320, 240}, // QVGA
  { 176, 144} // QCIF
};

static camera_size_type default_picture_sizes[] = {
  { 4000, 3000}, // 12MP
  { 3264, 2448}, // 8MP
  { 3264, 1840}, // 6MP
  { 2592, 1944}, // 5MP
  { 2992, 1680},
  { 2592, 1456},
  { 2048, 1536}, // 3MP QXGA
  { 1920, 1088}, //HD1080
  { 1600, 1200}, // 2MP UXGA
  { 1280, 768}, //WXGA
  { 1280, 720}, //HD720
  { 1024, 768}, // 1MP XGA
  { 800, 600}, //SVGA
  { 800, 480}, // WVGA
  { 640, 480}, // VGA
  { 352, 288}, //CIF
  { 320, 240}, // QVGA
  { 176, 144} // QCIF
};

static camera_size_type hfr_sizes[] = {
  { 800, 480}, // WVGA
  { 640, 480} // VGA
};

static int iso_speed_values[] = {
    0, 1, 100, 200, 400, 800, 1600
};


extern int HAL_numOfCameras;
extern camera_info_t HAL_cameraInfo[MSM_MAX_CAMERA_SENSORS];
extern mm_camera_t * HAL_camerahandle[MSM_MAX_CAMERA_SENSORS];

namespace android {

static uint32_t  HFR_SIZE_COUNT=2;
static const int PICTURE_FORMAT_JPEG = 1;
static const int PICTURE_FORMAT_RAW = 2;

/********************************************************************/
static const str_map effects[] = {
    { CameraParameters::EFFECT_NONE,       CAMERA_EFFECT_OFF },
    { CameraParameters::EFFECT_MONO,       CAMERA_EFFECT_MONO },
    { CameraParameters::EFFECT_NEGATIVE,   CAMERA_EFFECT_NEGATIVE },
    { CameraParameters::EFFECT_SOLARIZE,   CAMERA_EFFECT_SOLARIZE },
    { CameraParameters::EFFECT_SEPIA,      CAMERA_EFFECT_SEPIA },
    { CameraParameters::EFFECT_POSTERIZE,  CAMERA_EFFECT_POSTERIZE },
    { CameraParameters::EFFECT_WHITEBOARD, CAMERA_EFFECT_WHITEBOARD },
    { CameraParameters::EFFECT_BLACKBOARD, CAMERA_EFFECT_BLACKBOARD },
    { CameraParameters::EFFECT_AQUA,       CAMERA_EFFECT_AQUA },
    { CameraParameters::EFFECT_EMBOSS,     CAMERA_EFFECT_EMBOSS },
    { CameraParameters::EFFECT_SKETCH,     CAMERA_EFFECT_SKETCH },
    { CameraParameters::EFFECT_NEON,       CAMERA_EFFECT_NEON }
};

static const str_map iso[] = {
    { CameraParameters::ISO_AUTO,  CAMERA_ISO_AUTO},
    { CameraParameters::ISO_HJR,   CAMERA_ISO_DEBLUR},
    { CameraParameters::ISO_100,   CAMERA_ISO_100},
    { CameraParameters::ISO_200,   CAMERA_ISO_200},
    { CameraParameters::ISO_400,   CAMERA_ISO_400},
    { CameraParameters::ISO_800,   CAMERA_ISO_800 },
    { CameraParameters::ISO_1600,  CAMERA_ISO_1600 }
};

static const str_map scenemode[] = {
    { CameraParameters::SCENE_MODE_AUTO,           CAMERA_BESTSHOT_OFF },
    { CameraParameters::SCENE_MODE_ASD,            CAMERA_BESTSHOT_AUTO },
    { CameraParameters::SCENE_MODE_ACTION,         CAMERA_BESTSHOT_ACTION },
    { CameraParameters::SCENE_MODE_PORTRAIT,       CAMERA_BESTSHOT_PORTRAIT },
    { CameraParameters::SCENE_MODE_LANDSCAPE,      CAMERA_BESTSHOT_LANDSCAPE },
    { CameraParameters::SCENE_MODE_NIGHT,          CAMERA_BESTSHOT_NIGHT },
    { CameraParameters::SCENE_MODE_NIGHT_PORTRAIT, CAMERA_BESTSHOT_NIGHT_PORTRAIT },
    { CameraParameters::SCENE_MODE_THEATRE,        CAMERA_BESTSHOT_THEATRE },
    { CameraParameters::SCENE_MODE_BEACH,          CAMERA_BESTSHOT_BEACH },
    { CameraParameters::SCENE_MODE_SNOW,           CAMERA_BESTSHOT_SNOW },
    { CameraParameters::SCENE_MODE_SUNSET,         CAMERA_BESTSHOT_SUNSET },
    { CameraParameters::SCENE_MODE_STEADYPHOTO,    CAMERA_BESTSHOT_ANTISHAKE },
    { CameraParameters::SCENE_MODE_FIREWORKS ,     CAMERA_BESTSHOT_FIREWORKS },
    { CameraParameters::SCENE_MODE_SPORTS ,        CAMERA_BESTSHOT_SPORTS },
    { CameraParameters::SCENE_MODE_PARTY,          CAMERA_BESTSHOT_PARTY },
    { CameraParameters::SCENE_MODE_CANDLELIGHT,    CAMERA_BESTSHOT_CANDLELIGHT },
    { CameraParameters::SCENE_MODE_BACKLIGHT,      CAMERA_BESTSHOT_BACKLIGHT },
    { CameraParameters::SCENE_MODE_FLOWERS,        CAMERA_BESTSHOT_FLOWERS },
    { CameraParameters::SCENE_MODE_AR,             CAMERA_BESTSHOT_AR },
};

static const str_map scenedetect[] = {
    { CameraParameters::SCENE_DETECT_OFF, FALSE  },
    { CameraParameters::SCENE_DETECT_ON, TRUE },
};

#define DONT_CARE AF_MODE_MAX
static const str_map focus_modes[] = {
    { CameraParameters::FOCUS_MODE_AUTO,     AF_MODE_AUTO},
    { CameraParameters::FOCUS_MODE_INFINITY, DONT_CARE },
    { CameraParameters::FOCUS_MODE_NORMAL,   AF_MODE_NORMAL },
    { CameraParameters::FOCUS_MODE_MACRO,    AF_MODE_MACRO },
    { CameraParameters::FOCUS_MODE_CONTINUOUS_PICTURE, AF_MODE_CAF},
    { CameraParameters::FOCUS_MODE_CONTINUOUS_VIDEO, /*AF_MODE_UNCHANGED*/ AF_MODE_CAF_VID }
};

static const str_map selectable_zone_af[] = {
    { CameraParameters::SELECTABLE_ZONE_AF_AUTO,  AUTO },
    { CameraParameters::SELECTABLE_ZONE_AF_SPOT_METERING, SPOT },
    { CameraParameters::SELECTABLE_ZONE_AF_CENTER_WEIGHTED, CENTER_WEIGHTED },
    { CameraParameters::SELECTABLE_ZONE_AF_FRAME_AVERAGE, AVERAGE }
};

// from qcamera/common/camera.h
static const str_map autoexposure[] = {
    { CameraParameters::AUTO_EXPOSURE_FRAME_AVG,  CAMERA_AEC_FRAME_AVERAGE },
    { CameraParameters::AUTO_EXPOSURE_CENTER_WEIGHTED, CAMERA_AEC_CENTER_WEIGHTED },
    { CameraParameters::AUTO_EXPOSURE_SPOT_METERING, CAMERA_AEC_SPOT_METERING }
};

// from aeecamera.h
static const str_map whitebalance[] = {
    { CameraParameters::WHITE_BALANCE_AUTO,            CAMERA_WB_AUTO },
    { CameraParameters::WHITE_BALANCE_INCANDESCENT,    CAMERA_WB_INCANDESCENT },
    { CameraParameters::WHITE_BALANCE_FLUORESCENT,     CAMERA_WB_FLUORESCENT },
    { CameraParameters::WHITE_BALANCE_DAYLIGHT,        CAMERA_WB_DAYLIGHT },
    { CameraParameters::WHITE_BALANCE_CLOUDY_DAYLIGHT, CAMERA_WB_CLOUDY_DAYLIGHT }
};

static const str_map antibanding[] = {
    { CameraParameters::ANTIBANDING_OFF,  CAMERA_ANTIBANDING_OFF },
    { CameraParameters::ANTIBANDING_50HZ, CAMERA_ANTIBANDING_50HZ },
    { CameraParameters::ANTIBANDING_60HZ, CAMERA_ANTIBANDING_60HZ },
    { CameraParameters::ANTIBANDING_AUTO, CAMERA_ANTIBANDING_AUTO }
};

static const str_map frame_rate_modes[] = {
        {CameraParameters::KEY_PREVIEW_FRAME_RATE_AUTO_MODE, FPS_MODE_AUTO},
        {CameraParameters::KEY_PREVIEW_FRAME_RATE_FIXED_MODE, FPS_MODE_FIXED}
};

static const str_map touchafaec[] = {
    { CameraParameters::TOUCH_AF_AEC_OFF, FALSE },
    { CameraParameters::TOUCH_AF_AEC_ON, TRUE }
};

static const str_map hfr[] = {
    { CameraParameters::VIDEO_HFR_OFF, CAMERA_HFR_MODE_OFF },
    { CameraParameters::VIDEO_HFR_2X, CAMERA_HFR_MODE_60FPS },
    { CameraParameters::VIDEO_HFR_3X, CAMERA_HFR_MODE_90FPS },
    { CameraParameters::VIDEO_HFR_4X, CAMERA_HFR_MODE_120FPS },
};
static const int HFR_VALUES_COUNT = (sizeof(hfr)/sizeof(str_map));

static const str_map flash[] = {
    { CameraParameters::FLASH_MODE_OFF,  LED_MODE_OFF },
    { CameraParameters::FLASH_MODE_AUTO, LED_MODE_AUTO },
    { CameraParameters::FLASH_MODE_ON, LED_MODE_ON },
    { CameraParameters::FLASH_MODE_TORCH, LED_MODE_TORCH}
};

static const str_map lensshade[] = {
    { CameraParameters::LENSSHADE_ENABLE, TRUE },
    { CameraParameters::LENSSHADE_DISABLE, FALSE }
};

static const str_map mce[] = {
    { CameraParameters::MCE_ENABLE, TRUE },
    { CameraParameters::MCE_DISABLE, FALSE }
};

static const str_map histogram[] = {
    { CameraParameters::HISTOGRAM_ENABLE, TRUE },
    { CameraParameters::HISTOGRAM_DISABLE, FALSE }
};

static const str_map skinToneEnhancement[] = {
    { CameraParameters::SKIN_TONE_ENHANCEMENT_ENABLE, TRUE },
    { CameraParameters::SKIN_TONE_ENHANCEMENT_DISABLE, FALSE }
};

static const str_map denoise[] = {
    { CameraParameters::DENOISE_OFF, FALSE },
    { CameraParameters::DENOISE_ON, TRUE }
};

static const str_map facedetection[] = {
    { CameraParameters::FACE_DETECTION_OFF, FALSE },
    { CameraParameters::FACE_DETECTION_ON, TRUE }
};

static const str_map redeye_reduction[] = {
    { CameraParameters::REDEYE_REDUCTION_ENABLE, TRUE },
    { CameraParameters::REDEYE_REDUCTION_DISABLE, FALSE }
};

static const str_map picture_formats[] = {
        {CameraParameters::PIXEL_FORMAT_JPEG, PICTURE_FORMAT_JPEG},
        {CameraParameters::PIXEL_FORMAT_RAW, PICTURE_FORMAT_RAW}
};

static const str_map recording_Hints[] = {
        {"false", FALSE},
        {"true",  TRUE}
};

static const str_map preview_formats[] = {
        {CameraParameters::PIXEL_FORMAT_YUV420SP,   HAL_PIXEL_FORMAT_YCrCb_420_SP},
        {CameraParameters::PIXEL_FORMAT_YUV420SP_ADRENO, HAL_PIXEL_FORMAT_YCrCb_420_SP_ADRENO},
        {CameraParameters::PIXEL_FORMAT_YV12, HAL_PIXEL_FORMAT_YV12},
        {CameraParameters::PIXEL_FORMAT_YUV420P,HAL_PIXEL_FORMAT_YV12},
        {CameraParameters::PIXEL_FORMAT_NV12, HAL_PIXEL_FORMAT_YCbCr_420_SP}
};

static const preview_format_info_t preview_format_info_list[] = {
  {HAL_PIXEL_FORMAT_YCrCb_420_SP, CAMERA_YUV_420_NV21, CAMERA_PAD_TO_WORD, 2},
  {HAL_PIXEL_FORMAT_YCrCb_420_SP_ADRENO, CAMERA_YUV_420_NV21, CAMERA_PAD_TO_4K, 2},
  {HAL_PIXEL_FORMAT_YCbCr_420_SP, CAMERA_YUV_420_NV12, CAMERA_PAD_TO_WORD, 2},
  {HAL_PIXEL_FORMAT_YV12,         CAMERA_YUV_420_YV12, CAMERA_PAD_TO_WORD, 3}
};

static const str_map zsl_modes[] = {
    { CameraParameters::ZSL_OFF, FALSE },
    { CameraParameters::ZSL_ON, TRUE },
};


static const str_map hdr_bracket[] = {
    { CameraParameters::AE_BRACKET_HDR_OFF,HDR_BRACKETING_OFF},
    { CameraParameters::AE_BRACKET_HDR,HDR_MODE },
    { CameraParameters::AE_BRACKET,EXP_BRACKETING_MODE }
};
/**************************************************************************/
static int attr_lookup(const str_map arr[], int len, const char *name)
{
    if (name) {
        for (int i = 0; i < len; i++) {
            if (!strcmp(arr[i].desc, name))
                return arr[i].val;
        }
    }
    return NOT_FOUND;
}

bool QCameraHardwareInterface::native_set_parms(
    mm_camera_parm_type_t type, uint16_t length, void *value)
{
    ALOGE("%s : type : %d Value : %d",__func__,type,*((int *)value));
    if(MM_CAMERA_OK != cam_config_set_parm(mCameraId, type,value )) {
        ALOGE("native_set_parms failed: type %d length %d error %s",
            type, length, strerror(errno));
        return false;
    }

    return true;

}

bool QCameraHardwareInterface::native_set_parms(
    mm_camera_parm_type_t type, uint16_t length, void *value, int *result)
{
    *result= cam_config_set_parm(mCameraId, type,value );
    if(MM_CAMERA_OK == *result) {
        ALOGE("native_set_parms: succeeded : %d", *result);
        return true;
    }

    ALOGE("native_set_parms failed: type %d length %d error str %s error# %d",
        type, length, strerror(errno), errno);
    return false;
}

//Filter Picture sizes based on max width and height
/* TBD: do we still need this - except for ZSL? */
void QCameraHardwareInterface::filterPictureSizes(){
    unsigned int i;
    if(mPictureSizeCount <= 0)
        return;
    maxSnapshotWidth = mPictureSizes[0].width;
    maxSnapshotHeight = mPictureSizes[0].height;
   // Iterate through all the width and height to find the max value
    for(i =0; i<mPictureSizeCount;i++){
        if(((maxSnapshotWidth < mPictureSizes[i].width) &&
            (maxSnapshotHeight <= mPictureSizes[i].height))){
            maxSnapshotWidth = mPictureSizes[i].width;
            maxSnapshotHeight = mPictureSizes[i].height;
        }
    }
    if(myMode & CAMERA_ZSL_MODE){
        // due to lack of PMEM we restrict to lower resolution
        mPictureSizesPtr = zsl_picture_sizes;
        mSupportedPictureSizesCount = 7;
    }else{
        mPictureSizesPtr = mPictureSizes;
        mSupportedPictureSizesCount = mPictureSizeCount;
    }
}

static String8 create_sizes_str(const camera_size_type *sizes, int len) {
    String8 str;
    char buffer[32];

    if (len > 0) {
        snprintf(buffer, sizeof(buffer), "%dx%d", sizes[0].width, sizes[0].height);
        str.append(buffer);
    }
    for (int i = 1; i < len; i++) {
        snprintf(buffer, sizeof(buffer), ",%dx%d", sizes[i].width, sizes[i].height);
        str.append(buffer);
    }
    return str;
}

String8 QCameraHardwareInterface::create_values_str(const str_map *values, int len) {
    String8 str;

    if (len > 0) {
        str.append(values[0].desc);
    }
    for (int i = 1; i < len; i++) {
        str.append(",");
        str.append(values[i].desc);
    }
    return str;
}

static String8 create_fps_str(const android:: FPSRange* fps, int len) {
    String8 str;
    char buffer[32];

    if (len > 0) {
        snprintf(buffer, sizeof(buffer), "(%d,%d)", fps[0].minFPS, fps[0].maxFPS);
        str.append(buffer);
    }
    for (int i = 1; i < len; i++) {
        snprintf(buffer, sizeof(buffer), ",(%d,%d)", fps[i].minFPS, fps[i].maxFPS);
        str.append(buffer);
    }
    return str;
}

static String8 create_values_range_str(int min, int max){
    String8 str;
    char buffer[32];

    if(min <= max){
        snprintf(buffer, sizeof(buffer), "%d", min);
        str.append(buffer);

        for (int i = min + 1; i <= max; i++) {
            snprintf(buffer, sizeof(buffer), ",%d", i);
            if(i%10 == 0)
                str.append(buffer);
        }
    }
    return str;
}

static int parse_size(const char *str, int &width, int &height)
{
    // Find the width.
    char *end;
    int w = (int)strtol(str, &end, 10);
    // If an 'x' or 'X' does not immediately follow, give up.
    if ( (*end != 'x') && (*end != 'X') )
        return -1;

    // Find the height, immediately after the 'x'.
    int h = (int)strtol(end+1, 0, 10);

    width = w;
    height = h;

    return 0;
}

bool QCameraHardwareInterface::isValidDimension(int width, int height) {
    bool retVal = FALSE;
    /* This function checks if a given resolution is valid or not.
     * A particular resolution is considered valid if it satisfies
     * the following conditions:
     * 1. width & height should be multiple of 16.
     * 2. width & height should be less than/equal to the dimensions
     *    supported by the camera sensor.
     * 3. the aspect ratio is a valid aspect ratio and is among the
     *    commonly used aspect ratio as determined by the thumbnail_sizes
     *    data structure.
     */

    if( (width == CEILING16(width)) && (height == CEILING16(height))
     && (width <= maxSnapshotWidth)
    && (height <= maxSnapshotHeight) )
    {
        uint32_t pictureAspectRatio = (uint32_t)((width * Q12)/height);
        for(uint32_t i = 0; i < THUMBNAIL_SIZE_COUNT; i++ ) {
            if(thumbnail_sizes[i].aspect_ratio == pictureAspectRatio) {
                retVal = TRUE;
                break;
            }
        }
    }
    return retVal;
}

void QCameraHardwareInterface::hasAutoFocusSupport(){

    ALOGV("%s",__func__);

    if(isZSLMode()){
        mHasAutoFocusSupport = false;
        return;
    }

    if(cam_ops_is_op_supported (mCameraId, MM_CAMERA_OPS_FOCUS )) {
        mHasAutoFocusSupport = true;
    }
    else {
        ALOGE("AutoFocus is not supported");
        mHasAutoFocusSupport = false;
    }

    ALOGV("%s:rc= %d",__func__, mHasAutoFocusSupport);

}

bool QCameraHardwareInterface::supportsSceneDetection() {
   bool rc = cam_config_is_parm_supported(mCameraId,MM_CAMERA_PARM_ASD_ENABLE);
   return rc;
}

bool QCameraHardwareInterface::supportsFaceDetection() {
    bool rc = cam_config_is_parm_supported(mCameraId,MM_CAMERA_PARM_FD);
    return rc;
}

bool QCameraHardwareInterface::supportsSelectableZoneAf() {
   bool rc = cam_config_is_parm_supported(mCameraId,MM_CAMERA_PARM_FOCUS_RECT);
   return rc;
}

bool QCameraHardwareInterface::supportsRedEyeReduction() {
   bool rc = cam_config_is_parm_supported(mCameraId,MM_CAMERA_PARM_REDEYE_REDUCTION);
   return rc;
}

static String8 create_str(int16_t *arr, int length){
    String8 str;
    char buffer[32];

    if(length > 0){
        snprintf(buffer, sizeof(buffer), "%d", arr[0]);
        str.append(buffer);
    }

    for (int i =1;i<length;i++){
        snprintf(buffer, sizeof(buffer), ",%d",arr[i]);
        str.append(buffer);
    }
    return str;
}

bool QCameraHardwareInterface::getMaxPictureDimension(mm_camera_dimension_t *maxDim)
{
    bool ret = NO_ERROR;
    mm_camera_dimension_t dim;

    ret = cam_config_get_parm(mCameraId,
                              MM_CAMERA_PARM_MAX_PICTURE_SIZE, &dim);
    if (ret != NO_ERROR)
        return ret;

    /* Find the first dimension in the mPictureSizes
     * array which is smaller than the max dimension.
     * This will be the valid max picture resolution */
    for (unsigned int i = 0; i < mPictureSizeCount; i++) {
        if ((mPictureSizes[i].width <= dim.width) &&
            (mPictureSizes[i].height <= dim.height)) {
            maxDim->height = mPictureSizes[i].height;
            maxDim->width  = mPictureSizes[i].width;
            break;
        }
    }
    ALOGD("%s: Found Max Picture dimension: %d x %d", __func__,
          maxDim->width, maxDim->height);
    return ret;
}
void QCameraHardwareInterface::initDefaultParameters()
{
    bool ret;
    char prop[PROPERTY_VALUE_MAX];
    int  snap_format;
    mm_camera_dimension_t maxDim;
    ALOGI("%s: E", __func__);

    memset(&maxDim, 0, sizeof(mm_camera_dimension_t));
    ret = getMaxPictureDimension(&maxDim);

    if (ret != NO_ERROR) {
        ALOGE("%s: Cannot get Max picture size supported", __func__);
        return;
    }
    if (!maxDim.width || !maxDim.height) {
        maxDim.width = DEFAULT_LIVESHOT_WIDTH;
        maxDim.height = DEFAULT_LIVESHOT_HEIGHT;
    }

    memset(prop, 0, sizeof(prop));
    property_get("persist.camera.snap.format", prop, "0");
    snap_format = atoi(prop);
    ALOGV("%s: prop =(%s), snap_format=%d", __func__, prop, snap_format);

    //cam_ctrl_dimension_t dim;
    mHFRLevel = 0;
    memset(&mDimension, 0, sizeof(cam_ctrl_dimension_t));
    memset(&mPreviewFormatInfo, 0, sizeof(preview_format_info_t));
    mDimension.video_width     = DEFAULT_STREAM_WIDTH;
    mDimension.video_height    = DEFAULT_STREAM_HEIGHT;
    // mzhu mDimension.picture_width   = DEFAULT_STREAM_WIDTH;
    // mzhu mDimension.picture_height  = DEFAULT_STREAM_HEIGHT;
    mDimension.picture_width   = maxDim.width;
    mDimension.picture_height  = maxDim.height;
    mDimension.display_width   = DEFAULT_STREAM_WIDTH;
    mDimension.display_height  = DEFAULT_STREAM_HEIGHT;
    mDimension.orig_picture_dx = mDimension.picture_width;
    mDimension.orig_picture_dy = mDimension.picture_height;
    mDimension.ui_thumbnail_width = DEFAULT_STREAM_WIDTH;
    mDimension.ui_thumbnail_height = DEFAULT_STREAM_HEIGHT;
    mDimension.orig_video_width = DEFAULT_STREAM_WIDTH;
    mDimension.orig_video_height = DEFAULT_STREAM_HEIGHT;

    mDimension.prev_format     = CAMERA_YUV_420_NV21;
    mDimension.enc_format      = CAMERA_YUV_420_NV21;
    if (snap_format == 1) {
      mDimension.main_img_format = CAMERA_YUV_422_NV61;
    } else {
      mDimension.main_img_format = CAMERA_YUV_420_NV21;
    }
    mDimension.thumb_format    = CAMERA_YUV_420_NV21;
    ALOGV("%s: main_img_format =%d, thumb_format=%d", __func__,
         mDimension.main_img_format, mDimension.thumb_format);
    mDimension.prev_padding_format = CAMERA_PAD_TO_WORD;

    ret = native_set_parms(MM_CAMERA_PARM_DIMENSION,
                              sizeof(cam_ctrl_dimension_t), (void *) &mDimension);
    if(!ret) {
      ALOGE("MM_CAMERA_PARM_DIMENSION Failed.");
      return;
    }

    hasAutoFocusSupport();

    // Initialize constant parameter strings. This will happen only once in the
    // lifetime of the mediaserver process.
    if (true/*!mParamStringInitialized*/) {
        //filter picture sizes
        filterPictureSizes();
        mPictureSizeValues = create_sizes_str(
                mPictureSizesPtr, mSupportedPictureSizesCount);
        mPreviewSizeValues = create_sizes_str(
                mPreviewSizes,  mPreviewSizeCount);

        //Query for max HFR value
        camera_hfr_mode_t maxHFR;
        cam_config_get_parm(mCameraId, MM_CAMERA_PARM_MAX_HFR_MODE, (void *)&maxHFR);
        //Filter HFR values and build parameter string
        String8 str;
        for(int i=0; i<HFR_VALUES_COUNT; i++){
            if(hfr[i].val <= maxHFR){
                if(i>0)	str.append(",");
                str.append(hfr[i].desc);
            }
        }
        mHfrValues = str;
        mHfrSizeValues = create_sizes_str(
                hfr_sizes, HFR_SIZE_COUNT);
        mFpsRangesSupportedValues = create_fps_str(
            FpsRangesSupported,FPS_RANGES_SUPPORTED_COUNT );
        mParameters.set(
            CameraParameters::KEY_SUPPORTED_PREVIEW_FPS_RANGE,
            mFpsRangesSupportedValues);
        mParameters.setPreviewFpsRange(MINIMUM_FPS*1000,MAXIMUM_FPS*1000);
        mFlashValues = create_values_str(
            flash, sizeof(flash) / sizeof(str_map));
        mLensShadeValues = create_values_str(
            lensshade,sizeof(lensshade)/sizeof(str_map));
        mMceValues = create_values_str(
            mce,sizeof(mce)/sizeof(str_map));
        mEffectValues = create_values_str(effects, sizeof(effects) / sizeof(str_map));
        mAntibandingValues = create_values_str(
            antibanding, sizeof(antibanding) / sizeof(str_map));
        mIsoValues = create_values_str(iso,sizeof(iso)/sizeof(str_map));
        mAutoExposureValues = create_values_str(
            autoexposure, sizeof(autoexposure) / sizeof(str_map));
        mWhitebalanceValues = create_values_str(
            whitebalance, sizeof(whitebalance) / sizeof(str_map));

        if(mHasAutoFocusSupport){
            mFocusModeValues = create_values_str(
                    focus_modes, sizeof(focus_modes) / sizeof(str_map));
        }

        mSceneModeValues = create_values_str(scenemode, sizeof(scenemode) / sizeof(str_map));

        if(mHasAutoFocusSupport){
            mTouchAfAecValues = create_values_str(
                touchafaec,sizeof(touchafaec)/sizeof(str_map));
        }
        //Currently Enabling Histogram for 8x60
        mHistogramValues = create_values_str(
            histogram,sizeof(histogram)/sizeof(str_map));

        mSkinToneEnhancementValues = create_values_str(
            skinToneEnhancement,sizeof(skinToneEnhancement)/sizeof(str_map));

        mPictureFormatValues = create_values_str(
            picture_formats, sizeof(picture_formats)/sizeof(str_map));

        mZoomSupported=false;
        mMaxZoom=0;
        mm_camera_zoom_tbl_t zmt;
        if(MM_CAMERA_OK != cam_config_get_parm(mCameraId,
                             MM_CAMERA_PARM_MAXZOOM, &mMaxZoom)){
            ALOGE("%s:Failed to get max zoom",__func__);
        }else{

            ALOGE("Max Zoom:%d",mMaxZoom);
            /* Kernel driver limits the max amount of data that can be retreived through a control
            command to 260 bytes hence we conservatively limit to 110 zoom ratios */
            if(mMaxZoom>MAX_ZOOM_RATIOS) {
                ALOGE("%s:max zoom is larger than sizeof zoomRatios table",__func__);
                mMaxZoom=MAX_ZOOM_RATIOS-1;
            }
            zmt.size=mMaxZoom;
            zmt.zoom_ratio_tbl=&zoomRatios[0];
            if(MM_CAMERA_OK != cam_config_get_parm(mCameraId,
                                 MM_CAMERA_PARM_ZOOM_RATIO, &zmt)){
                ALOGE("%s:Failed to get max zoom ratios",__func__);
            }else{
                mZoomSupported=true;
                mZoomRatioValues =  create_str(zoomRatios, mMaxZoom);
            }
        }

        ALOGE("Zoom supported:%d",mZoomSupported);

        denoise_value = create_values_str(
            denoise, sizeof(denoise) / sizeof(str_map));

       if(mHasAutoFocusSupport && supportsFaceDetection()) {
            mFaceDetectionValues = create_values_str(
                facedetection, sizeof(facedetection) / sizeof(str_map));
        }

        if(mHasAutoFocusSupport){
            mSelectableZoneAfValues = create_values_str(
                selectable_zone_af, sizeof(selectable_zone_af) / sizeof(str_map));
        }

        mSceneDetectValues = create_values_str(scenedetect, sizeof(scenedetect) / sizeof(str_map));

        mRedeyeReductionValues = create_values_str(
            redeye_reduction, sizeof(redeye_reduction) / sizeof(str_map));

        mZslValues = create_values_str(
            zsl_modes,sizeof(zsl_modes)/sizeof(str_map));

        mParamStringInitialized = true;
    }

    //set supported video sizes
    String8 videoSizes = create_sizes_str(supported_video_sizes, SUPPORTED_VIDEO_SIZES_COUNT);
    mParameters.set(CameraParameters::KEY_SUPPORTED_VIDEO_SIZES, videoSizes.string());

    //Set Preview size
    mParameters.setPreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
    mParameters.set(CameraParameters::KEY_SUPPORTED_PREVIEW_SIZES,
                    mPreviewSizeValues.string());
    mDimension.display_width = DEFAULT_PREVIEW_WIDTH;
    mDimension.display_height = DEFAULT_PREVIEW_HEIGHT;

    //Set Preview Frame Rate
    if(mFps >= MINIMUM_FPS && mFps <= MAXIMUM_FPS) {
        mPreviewFrameRateValues = create_values_range_str(
        MINIMUM_FPS, mFps);
    }else{
        mPreviewFrameRateValues = create_values_range_str(
        MINIMUM_FPS, MAXIMUM_FPS);
    }


    if (cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_FPS)) {
        mParameters.set(CameraParameters::KEY_SUPPORTED_PREVIEW_FRAME_RATES,
                        mPreviewFrameRateValues.string());
     }  else {
        mParameters.set(
            CameraParameters::KEY_SUPPORTED_PREVIEW_FRAME_RATES,
            DEFAULT_FPS);
    }

    //Set Preview Frame Rate Modes
    mParameters.setPreviewFrameRateMode("frame-rate-auto");
    mFrameRateModeValues = create_values_str(
            frame_rate_modes, sizeof(frame_rate_modes) / sizeof(str_map));
      if(cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_FPS_MODE)){
        mParameters.set(CameraParameters::KEY_SUPPORTED_PREVIEW_FRAME_RATE_MODES,
                    mFrameRateModeValues.string());
    }

    //Set Preview Format
    //mParameters.setPreviewFormat("yuv420sp"); // informative
    mParameters.setPreviewFormat(CameraParameters::PIXEL_FORMAT_YUV420SP);

    mPreviewFormatValues = create_values_str(
        preview_formats, sizeof(preview_formats) / sizeof(str_map));
    mParameters.set(CameraParameters::KEY_SUPPORTED_PREVIEW_FORMATS,
            mPreviewFormatValues.string());

    //Set Overlay Format
    mParameters.set("overlay-format", HAL_PIXEL_FORMAT_YCbCr_420_SP);
    mParameters.set("max-num-detected-faces-hw", "2");

    //Set Picture Size
    mParameters.setPictureSize(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
    mParameters.set(CameraParameters::KEY_SUPPORTED_PICTURE_SIZES,
                    mPictureSizeValues.string());

    //Set Preview Frame Rate
    if(mFps >= MINIMUM_FPS && mFps <= MAXIMUM_FPS) {
        mParameters.setPreviewFrameRate(mFps);
    }else{
        mParameters.setPreviewFrameRate(DEFAULT_FPS);
    }

    //Set Picture Format
    mParameters.setPictureFormat("jpeg"); // informative
    mParameters.set(CameraParameters::KEY_SUPPORTED_PICTURE_FORMATS,
                    mPictureFormatValues);

    mParameters.set(CameraParameters::KEY_JPEG_QUALITY, "85"); // max quality
    //Set Video Format
    mParameters.set(CameraParameters::KEY_VIDEO_FRAME_FORMAT, "yuv420sp");

    //Set Thumbnail parameters
    mParameters.set(CameraParameters::KEY_JPEG_THUMBNAIL_WIDTH,
                    THUMBNAIL_WIDTH_STR); // informative
    mParameters.set(CameraParameters::KEY_JPEG_THUMBNAIL_HEIGHT,
                    THUMBNAIL_HEIGHT_STR); // informative
    mDimension.ui_thumbnail_width =
            thumbnail_sizes[DEFAULT_THUMBNAIL_SETTING].width;
    mDimension.ui_thumbnail_height =
            thumbnail_sizes[DEFAULT_THUMBNAIL_SETTING].height;
    mParameters.set(CameraParameters::KEY_JPEG_THUMBNAIL_QUALITY, "50");
    String8 valuesStr = create_sizes_str(jpeg_thumbnail_sizes, JPEG_THUMBNAIL_SIZE_COUNT);
    mParameters.set(CameraParameters::KEY_SUPPORTED_JPEG_THUMBNAIL_SIZES,
                valuesStr.string());
    // Define CAMERA_SMOOTH_ZOOM in Android.mk file , to enable smoothzoom
#ifdef CAMERA_SMOOTH_ZOOM
    mParameters.set(CameraParameters::KEY_SMOOTH_ZOOM_SUPPORTED, "true");
#endif
    if(mZoomSupported){
        mParameters.set(CameraParameters::KEY_ZOOM_SUPPORTED, "true");
        ALOGE("max zoom is %d", mMaxZoom-1);
        /* mMaxZoom value that the query interface returns is the size
        ALOGV("max zoom is %d", mMaxZoom-1);
        * mMaxZoom value that the query interface returns is the size
         * of zoom table. So the actual max zoom value will be one
         * less than that value.          */

        mParameters.set("max-zoom",mMaxZoom-1);
        mParameters.set(CameraParameters::KEY_ZOOM_RATIOS,
                            mZoomRatioValues);
    } else
        {
        mParameters.set(CameraParameters::KEY_ZOOM_SUPPORTED, "false");
    }

    /* Enable zoom support for video application if VPE enabled */
    if(mZoomSupported) {
        mParameters.set("video-zoom-support", "true");
    } else {
        mParameters.set("video-zoom-support", "false");
    }

    if (mFullLiveshotEnabled) {
        //Set Live Snapshot support
        mParameters.set("full-video-snap-supported", "true");
        mParameters.set("video-snapshot-supported", "true");
    }else{
        mParameters.set("full-video-snap-supported", "false");
        mParameters.set("video-snapshot-supported", "false");
    }

    //Set Live shot support
    mParameters.set("video-snapshot-supported", "true");

    //Set Camera Mode
    mParameters.set(CameraParameters::KEY_CAMERA_MODE,0);
    mParameters.set(CameraParameters::KEY_AE_BRACKET_HDR,"Off");

    //Set Antibanding
    mParameters.set(CameraParameters::KEY_ANTIBANDING,
                    CameraParameters::ANTIBANDING_OFF);
    mParameters.set(CameraParameters::KEY_SUPPORTED_ANTIBANDING,
                    mAntibandingValues);

    //Set Effect
    mParameters.set(CameraParameters::KEY_EFFECT,
                    CameraParameters::EFFECT_NONE);
    mParameters.set(CameraParameters::KEY_SUPPORTED_EFFECTS, mEffectValues);

    //Set Auto Exposure
    mParameters.set(CameraParameters::KEY_AUTO_EXPOSURE,
                    CameraParameters::AUTO_EXPOSURE_FRAME_AVG);
    mParameters.set(CameraParameters::KEY_SUPPORTED_AUTO_EXPOSURE, mAutoExposureValues);

    //Set WhiteBalance
    mParameters.set(CameraParameters::KEY_WHITE_BALANCE,
                    CameraParameters::WHITE_BALANCE_AUTO);
    mParameters.set(CameraParameters::KEY_SUPPORTED_WHITE_BALANCE,mWhitebalanceValues);

    //Set AEC_LOCK
    mParameters.set(CameraParameters::KEY_AUTO_EXPOSURE_LOCK, "false");
    if(cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_AEC_LOCK) && ALLOW_AECAWBLOCK)
        mParameters.set(CameraParameters::KEY_AUTO_EXPOSURE_LOCK_SUPPORTED, "true");
    else
        mParameters.set(CameraParameters::KEY_AUTO_EXPOSURE_LOCK_SUPPORTED, "false");
    //Set AWB_LOCK
    mParameters.set(CameraParameters::KEY_AUTO_WHITEBALANCE_LOCK, "false");
    if(cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_AWB_LOCK) && ALLOW_AECAWBLOCK)
        mParameters.set(CameraParameters::KEY_AUTO_WHITEBALANCE_LOCK_SUPPORTED, "true");
    else
        mParameters.set(CameraParameters::KEY_AUTO_WHITEBALANCE_LOCK_SUPPORTED, "false");

    //Set Focus Mode
    if(mHasAutoFocusSupport){
       mParameters.set(CameraParameters::KEY_FOCUS_MODE,
                          CameraParameters::FOCUS_MODE_AUTO);
       mParameters.set(CameraParameters::KEY_SUPPORTED_FOCUS_MODES,
                          mFocusModeValues);
       mParameters.set(CameraParameters::KEY_MAX_NUM_FOCUS_AREAS, "1");
       mParameters.set(CameraParameters::KEY_MAX_NUM_METERING_AREAS, "1");
   } else {
       mParameters.set(CameraParameters::KEY_FOCUS_MODE,
       CameraParameters::FOCUS_MODE_INFINITY);
       mParameters.set(CameraParameters::KEY_SUPPORTED_FOCUS_MODES,
       CameraParameters::FOCUS_MODE_INFINITY);
       mParameters.set(CameraParameters::KEY_MAX_NUM_FOCUS_AREAS, "0");
       mParameters.set(CameraParameters::KEY_MAX_NUM_METERING_AREAS, "0");
   }

    mParameters.set(CameraParameters::KEY_FOCUS_AREAS, FOCUS_AREA_INIT);
    mParameters.set(CameraParameters::KEY_METERING_AREAS, FOCUS_AREA_INIT);

    //Set Flash
    if (cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_LED_MODE)) {
        mParameters.set(CameraParameters::KEY_FLASH_MODE,
                        CameraParameters::FLASH_MODE_OFF);
        mParameters.set(CameraParameters::KEY_SUPPORTED_FLASH_MODES,
                        mFlashValues);
    }

    //Set Sharpness
    mParameters.set("max-sharpness",CAMERA_MAX_SHARPNESS);
    mParameters.set(CameraParameters::KEY_MAX_SHARPNESS,
            CAMERA_MAX_SHARPNESS);
    mParameters.set(CameraParameters::KEY_SHARPNESS,
                    CAMERA_DEF_SHARPNESS);

    //Set Contrast
    mParameters.set("max-contrast",CAMERA_MAX_CONTRAST);
    mParameters.set(CameraParameters::KEY_MAX_CONTRAST,
            CAMERA_MAX_CONTRAST);
    mParameters.set(CameraParameters::KEY_CONTRAST,
                    CAMERA_DEF_CONTRAST);

    //Set Saturation
    mParameters.set("max-saturation",CAMERA_MAX_SATURATION);
    mParameters.set(CameraParameters::KEY_MAX_SATURATION,
            CAMERA_MAX_SATURATION);
    mParameters.set(CameraParameters::KEY_SATURATION,
                    CAMERA_DEF_SATURATION);

    //Set Brightness/luma-adaptaion
    mParameters.set("luma-adaptation", "3");

    mParameters.set(CameraParameters::KEY_PICTURE_FORMAT,
                    CameraParameters::PIXEL_FORMAT_JPEG);

    //Set Lensshading
    mParameters.set(CameraParameters::KEY_LENSSHADE,
                    CameraParameters::LENSSHADE_ENABLE);
    mParameters.set(CameraParameters::KEY_SUPPORTED_LENSSHADE_MODES,
                    mLensShadeValues);

    //Set ISO Mode
    mParameters.set(CameraParameters::KEY_ISO_MODE,
                    CameraParameters::ISO_AUTO);
    mParameters.set(CameraParameters::KEY_SUPPORTED_ISO_MODES,
                    mIsoValues);

    //Set MCE
    mParameters.set(CameraParameters::KEY_MEMORY_COLOR_ENHANCEMENT,
                    CameraParameters::MCE_ENABLE);
    mParameters.set(CameraParameters::KEY_SUPPORTED_MEM_COLOR_ENHANCE_MODES,
                    mMceValues);
    //Set HFR
    if (cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_HFR)) {
        mParameters.set(CameraParameters::KEY_VIDEO_HIGH_FRAME_RATE,
                    CameraParameters::VIDEO_HFR_OFF);
        mParameters.set(CameraParameters::KEY_SUPPORTED_HFR_SIZES,
                    mHfrSizeValues.string());
        mParameters.set(CameraParameters::KEY_SUPPORTED_VIDEO_HIGH_FRAME_RATE_MODES,
                    mHfrValues);
    } else{
        mParameters.set(CameraParameters::KEY_SUPPORTED_HFR_SIZES,"");
    }

    //Set Histogram
    mParameters.set(CameraParameters::KEY_HISTOGRAM,
                    CameraParameters::HISTOGRAM_DISABLE);
    mParameters.set(CameraParameters::KEY_SUPPORTED_HISTOGRAM_MODES,
                    mHistogramValues);

    //Set SkinTone Enhancement
    mParameters.set(CameraParameters::KEY_SKIN_TONE_ENHANCEMENT,
                    CameraParameters::SKIN_TONE_ENHANCEMENT_DISABLE);
    mParameters.set("skinToneEnhancement", "0");
    mParameters.set(CameraParameters::KEY_SUPPORTED_SKIN_TONE_ENHANCEMENT_MODES,
                    mSkinToneEnhancementValues);

    //Set Scene Mode
    mParameters.set(CameraParameters::KEY_SCENE_MODE,
                    CameraParameters::SCENE_MODE_AUTO);
    mParameters.set(CameraParameters::KEY_SUPPORTED_SCENE_MODES,
                    mSceneModeValues);

    //Set Streaming Textures
    mParameters.set("strtextures", "OFF");

    //Set Denoise
    mParameters.set(CameraParameters::KEY_DENOISE,
                    CameraParameters::DENOISE_OFF);
    mParameters.set(CameraParameters::KEY_SUPPORTED_DENOISE,
                        denoise_value);
    //Set Touch AF/AEC
    mParameters.set(CameraParameters::KEY_TOUCH_AF_AEC,
                    CameraParameters::TOUCH_AF_AEC_OFF);
    mParameters.set(CameraParameters::KEY_SUPPORTED_TOUCH_AF_AEC,
                    mTouchAfAecValues);
    mParameters.set("touchAfAec-dx","100");
    mParameters.set("touchAfAec-dy","100");

    //Set Scene Detection
    mParameters.set(CameraParameters::KEY_SCENE_DETECT,
                   CameraParameters::SCENE_DETECT_OFF);
    mParameters.set(CameraParameters::KEY_SUPPORTED_SCENE_DETECT,
                    mSceneDetectValues);

    //Set Selectable Zone AF
    mParameters.set(CameraParameters::KEY_SELECTABLE_ZONE_AF,
                    CameraParameters::SELECTABLE_ZONE_AF_AUTO);
    mParameters.set(CameraParameters::KEY_SUPPORTED_SELECTABLE_ZONE_AF,
                    mSelectableZoneAfValues);

    //Set Face Detection
    mParameters.set(CameraParameters::KEY_FACE_DETECTION,
                    CameraParameters::FACE_DETECTION_OFF);
    mParameters.set(CameraParameters::KEY_SUPPORTED_FACE_DETECTION,
                    mFaceDetectionValues);

    //Set Red Eye Reduction
    mParameters.set(CameraParameters::KEY_REDEYE_REDUCTION,
                    CameraParameters::REDEYE_REDUCTION_DISABLE);
    mParameters.set(CameraParameters::KEY_SUPPORTED_REDEYE_REDUCTION,
                    mRedeyeReductionValues);

    //Set ZSL
    mParameters.set(CameraParameters::KEY_ZSL,
                    CameraParameters::ZSL_OFF);
    mParameters.set(CameraParameters::KEY_SUPPORTED_ZSL_MODES,
                    mZslValues);

    //Set Focal length, horizontal and vertical view angles
    float focalLength = 0.0f;
    float horizontalViewAngle = 0.0f;
    float verticalViewAngle = 0.0f;
    cam_config_get_parm(mCameraId, MM_CAMERA_PARM_FOCAL_LENGTH,
            (void *)&focalLength);
    mParameters.setFloat(CameraParameters::KEY_FOCAL_LENGTH,
                    focalLength);
    cam_config_get_parm(mCameraId, MM_CAMERA_PARM_HORIZONTAL_VIEW_ANGLE,
            (void *)&horizontalViewAngle);
    mParameters.setFloat(CameraParameters::KEY_HORIZONTAL_VIEW_ANGLE,
                    horizontalViewAngle);
    cam_config_get_parm(mCameraId, MM_CAMERA_PARM_VERTICAL_VIEW_ANGLE,
            (void *)&verticalViewAngle);
    mParameters.setFloat(CameraParameters::KEY_VERTICAL_VIEW_ANGLE,
                    verticalViewAngle);

    //Set Exposure Compensation
    mParameters.set(
            CameraParameters::KEY_MAX_EXPOSURE_COMPENSATION,
            EXPOSURE_COMPENSATION_MAXIMUM_NUMERATOR);
    mParameters.set(
            CameraParameters::KEY_MIN_EXPOSURE_COMPENSATION,
            EXPOSURE_COMPENSATION_MINIMUM_NUMERATOR);
    mParameters.set(
            CameraParameters::KEY_EXPOSURE_COMPENSATION,
            EXPOSURE_COMPENSATION_DEFAULT_NUMERATOR);
    mParameters.setFloat(
            CameraParameters::KEY_EXPOSURE_COMPENSATION_STEP,
            EXPOSURE_COMPENSATION_STEP);

    mParameters.set("num-snaps-per-shutter", 1);

   // if(mIs3DModeOn)
   //     mParameters.set("3d-frame-format", "left-right");

    if (setParameters(mParameters) != NO_ERROR) {
        ALOGE("Failed to set default parameters?!");
    }
    //mUseOverlay = useOverlay();
    mParameters.set("zoom", 0);
    mInitialized = true;
    strTexturesOn = false;

    ALOGI("%s: X", __func__);
    return;
}

/**
 * Set the camera parameters. This returns BAD_VALUE if any parameter is
 * invalid or not supported.
 */

int QCameraHardwareInterface::setParameters(const char *parms)
{
    CameraParameters param;
    String8 str = String8(parms);
    param.unflatten(str);
    status_t ret = setParameters(param);
	if(ret == NO_ERROR)
		return 0;
	else
		return -1;
}

/**
 * Set the camera parameters. This returns BAD_VALUE if any parameter is
 * invalid or not supported. */
status_t QCameraHardwareInterface::setParameters(const CameraParameters& params)
{
    status_t ret = NO_ERROR;

    ALOGI("%s: E", __func__);
//    Mutex::Autolock l(&mLock);
    status_t rc, final_rc = NO_ERROR;

    if ((rc = setCameraMode(params)))                   final_rc = rc;
    if ((rc = setPreviewSize(params)))                  final_rc = rc;
    if ((rc = setVideoSize(params)))                    final_rc = rc;
    if ((rc = setPictureSize(params)))                  final_rc = rc;
    if ((rc = setJpegThumbnailSize(params)))            final_rc = rc;
    if ((rc = setJpegQuality(params)))                  final_rc = rc;
    if ((rc = setEffect(params)))                       final_rc = rc;
    if ((rc = setGpsLocation(params)))                  final_rc = rc;
    if ((rc = setRotation(params)))                     final_rc = rc;
    if ((rc = setZoom(params)))                         final_rc = rc;
    if ((rc = setOrientation(params)))                  final_rc = rc;
    if ((rc = setLensshadeValue(params)))               final_rc = rc;
    if ((rc = setMCEValue(params)))                     final_rc = rc;
    if ((rc = setPictureFormat(params)))                final_rc = rc;
    if ((rc = setSharpness(params)))                    final_rc = rc;
    if ((rc = setSaturation(params)))                   final_rc = rc;
    if ((rc = setSceneMode(params)))                    final_rc = rc;
    if ((rc = setContrast(params)))                     final_rc = rc;
    if ((rc = setSceneDetect(params)))                  final_rc = rc;
    if ((rc = setFaceDetect(params)))                   final_rc = rc;
    if ((rc = setStrTextures(params)))                  final_rc = rc;
    if ((rc = setPreviewFormat(params)))                final_rc = rc;
    if ((rc = setSkinToneEnhancement(params)))          final_rc = rc;
    if ((rc = setWaveletDenoise(params)))               final_rc = rc;
    if ((rc = setAntibanding(params)))                  final_rc = rc;
    //    if ((rc = setOverlayFormats(params)))         final_rc = rc;
    if ((rc = setRedeyeReduction(params)))              final_rc = rc;
    if ((rc = setCaptureBurstExp()))                    final_rc = rc;

    mParameters.set("num-snaps-per-shutter", params.get("num-snaps-per-shutter"));

    if ((rc = setAEBracket(params)))              final_rc = rc;
    //    if ((rc = setDenoise(params)))                final_rc = rc;
    if ((rc = setPreviewFpsRange(params)))              final_rc = rc;
    if((rc = setRecordingHint(params)))                 final_rc = rc;
    if ((rc = setNumOfSnapshot(params)))                final_rc = rc;
    if ((rc = setAecAwbLock(params)))                   final_rc = rc;

    const char *str = params.get(CameraParameters::KEY_SCENE_MODE);
    int32_t value = attr_lookup(scenemode, sizeof(scenemode) / sizeof(str_map), str);

    if((value != NOT_FOUND) && (value == CAMERA_BESTSHOT_OFF )) {
        if ((rc = setPreviewFrameRateMode(params)))     final_rc = rc;
        /* Fps mode has to be set before fps*/
        if ((rc = setPreviewFrameRate(params)))         final_rc = rc;
        if ((rc = setAutoExposure(params)))             final_rc = rc;
        if ((rc = setExposureCompensation(params)))     final_rc = rc;
        if ((rc = setWhiteBalance(params)))             final_rc = rc;
        if ((rc = setFlash(params)))                    final_rc = rc;
        if ((rc = setFocusMode(params)))                final_rc = rc;
        if ((rc = setBrightness(params)))               final_rc = rc;
        if ((rc = setISOValue(params)))                 final_rc = rc;
        if ((rc = setFocusAreas(params)))               final_rc = rc;
        if ((rc = setMeteringAreas(params)))            final_rc = rc;
    }
    //selectableZoneAF needs to be invoked after continuous AF
    if ((rc = setSelectableZoneAf(params)))             final_rc = rc;
    // setHighFrameRate needs to be done at end, as there can
    // be a preview restart, and need to use the updated parameters
    if ((rc = setHighFrameRate(params)))  final_rc = rc;
    setExifTags();

   ALOGI("%s: X", __func__);
   return rc;
}

/** Retrieve the camera parameters.  The buffer returned by the camera HAL
	must be returned back to it with put_parameters, if put_parameters
	is not NULL.
 */
int QCameraHardwareInterface::getParameters(char **parms)
{
    char* rc = NULL;
    String8 str;
    CameraParameters param = getParameters();
    str = param.flatten( );
    rc = (char *)malloc(sizeof(char)*(str.length()+1));
    strncpy(rc, str.string(), str.length());
	rc[str.length()] = 0;
	*parms = rc;
    return 0;
}

/** The camera HAL uses its own memory to pass us the parameters when we
	call get_parameters.  Use this function to return the memory back to
	the camera HAL, if put_parameters is not NULL.  If put_parameters
	is NULL, then you have to use free() to release the memory.
*/
void QCameraHardwareInterface::putParameters(char *rc)
{
    free(rc);
    rc = NULL;
}

CameraParameters& QCameraHardwareInterface::getParameters()
{
    Mutex::Autolock lock(mLock);
    return mParameters;
}

status_t QCameraHardwareInterface::runFaceDetection()
{
    bool ret = true;

    const char *str = mParameters.get(CameraParameters::KEY_FACE_DETECTION);
    if (str != NULL) {
        int value = attr_lookup(facedetection,
                sizeof(facedetection) / sizeof(str_map), str);
#if 0
        mMetaDataWaitLock.lock();
        if (value == true) {
            if(mMetaDataHeap != NULL)
                mMetaDataHeap.clear();

            mMetaDataHeap =
                new AshmemPool((sizeof(int)*(MAX_ROI*4+1)),
                        1,
                        (sizeof(int)*(MAX_ROI*4+1)),
                        "metadata");
            if (!mMetaDataHeap->initialized()) {
                ALOGE("Meta Data Heap allocation failed ");
                mMetaDataHeap.clear();
                ALOGE("runFaceDetection X: error initializing mMetaDataHeap");
                mMetaDataWaitLock.unlock();
                return UNKNOWN_ERROR;
            }
            mSendMetaData = true;
        } else {
            if(mMetaDataHeap != NULL)
                mMetaDataHeap.clear();
        }
        mMetaDataWaitLock.unlock();
#endif
        cam_ctrl_dimension_t dim;
        cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION,&dim);
        preview_parm_config (&dim, mParameters);
        ALOGE("%s: why set_dimension everytime?", __func__);
        ret = cam_config_set_parm(mCameraId, MM_CAMERA_PARM_DIMENSION,&dim);
        ret = native_set_parms(MM_CAMERA_PARM_FD, sizeof(int), (void *)&value);
        return ret ? NO_ERROR : UNKNOWN_ERROR;
    }
    ALOGE("Invalid Face Detection value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setSharpness(const CameraParameters& params)
{
    bool ret = false;
    int rc = MM_CAMERA_OK;
    ALOGE("%s",__func__);
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_SHARPNESS);
    if(!rc) {
        ALOGE("%s:CONTRAST not supported", __func__);
        return NO_ERROR;
    }
    int sharpness = params.getInt(CameraParameters::KEY_SHARPNESS);
    if((sharpness < CAMERA_MIN_SHARPNESS
            || sharpness > CAMERA_MAX_SHARPNESS))
        return UNKNOWN_ERROR;

    ALOGV("setting sharpness %d", sharpness);
    mParameters.set(CameraParameters::KEY_SHARPNESS, sharpness);
    ret = native_set_parms(MM_CAMERA_PARM_SHARPNESS, sizeof(sharpness),
                               (void *)&sharpness);
    return ret ? NO_ERROR : UNKNOWN_ERROR;
}

status_t QCameraHardwareInterface::setSaturation(const CameraParameters& params)
{
    bool ret = false;
    int rc = MM_CAMERA_OK;
    ALOGE("%s",__func__);
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_SATURATION);
    if(!rc) {
        ALOGE("%s:MM_CAMERA_PARM_SATURATION not supported", __func__);
        return NO_ERROR;
    }
    int result;
    int saturation = params.getInt(CameraParameters::KEY_SATURATION);

    if((saturation < CAMERA_MIN_SATURATION)
        || (saturation > CAMERA_MAX_SATURATION))
    return UNKNOWN_ERROR;

    ALOGV("Setting saturation %d", saturation);
    mParameters.set(CameraParameters::KEY_SATURATION, saturation);
    ret = native_set_parms(MM_CAMERA_PARM_SATURATION, sizeof(saturation),
        (void *)&saturation, (int *)&result);
    if(result != MM_CAMERA_OK)
        ALOGI("Saturation Value: %d is not set as the selected value is not supported", saturation);
    return ret ? NO_ERROR : UNKNOWN_ERROR;
}

status_t QCameraHardwareInterface::setContrast(const CameraParameters& params)
{
   ALOGE("%s E", __func__ );
   int rc = MM_CAMERA_OK;
   rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_CONTRAST);
   if(!rc) {
        ALOGE("%s:CONTRAST not supported", __func__);
        return NO_ERROR;
    }
   const char *str = params.get(CameraParameters::KEY_SCENE_MODE);
   ALOGE("Contrast : %s",str);
   int32_t value = attr_lookup(scenemode, sizeof(scenemode) / sizeof(str_map), str);
   if(value == CAMERA_BESTSHOT_OFF) {
        int contrast = params.getInt(CameraParameters::KEY_CONTRAST);
        if((contrast < CAMERA_MIN_CONTRAST)
                || (contrast > CAMERA_MAX_CONTRAST))
        {
            ALOGE("Contrast Value not matching");
            return UNKNOWN_ERROR;
        }
        ALOGV("setting contrast %d", contrast);
        mParameters.set(CameraParameters::KEY_CONTRAST, contrast);
        ALOGE("Calling Contrast set on Lower layer");
        bool ret = native_set_parms(MM_CAMERA_PARM_CONTRAST, sizeof(contrast),
                                   (void *)&contrast);
        ALOGE("Lower layer returned %d", ret);
        return ret ? NO_ERROR : UNKNOWN_ERROR;
    } else {
          ALOGI(" Contrast value will not be set " \
          "when the scenemode selected is %s", str);
          return NO_ERROR;
    }
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setSceneDetect(const CameraParameters& params)
{
    ALOGE("%s",__func__);
    bool retParm;
    int rc = MM_CAMERA_OK;

    rc = cam_config_is_parm_supported(mCameraId,MM_CAMERA_PARM_ASD_ENABLE);
    if(!rc) {
        ALOGE("%s:MM_CAMERA_PARM_ASD_ENABLE not supported", __func__);
        return NO_ERROR;
    }

    const char *str = params.get(CameraParameters::KEY_SCENE_MODE);
    ALOGE("Scene Detect string : %s",str);
    if (str != NULL) {
        int32_t value= (strcmp(str,CameraParameters::SCENE_MODE_ASD)==0 || strcmp(str,CameraParameters::SCENE_MODE_AUTO)==0);
        ALOGE("Scenedetect Value : %d",value);
        if (value != NOT_FOUND) {
            retParm = native_set_parms(MM_CAMERA_PARM_ASD_ENABLE, sizeof(value),
                                       (void *)&value);

            return retParm ? NO_ERROR : UNKNOWN_ERROR;
        }
    }
   return BAD_VALUE;
}

status_t QCameraHardwareInterface::setZoom(const CameraParameters& params)
{
    status_t rc = NO_ERROR;

    ALOGE("%s: E",__func__);


    if( !( cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_ZOOM))) {
        ALOGE("%s:MM_CAMERA_PARM_ZOOM not supported", __func__);
        return NO_ERROR;
    }
    // No matter how many different zoom values the driver can provide, HAL
    // provides applictations the same number of zoom levels. The maximum driver
    // zoom value depends on sensor output (VFE input) and preview size (VFE
    // output) because VFE can only crop and cannot upscale. If the preview size
    // is bigger, the maximum zoom ratio is smaller. However, we want the
    // zoom ratio of each zoom level is always the same whatever the preview
    // size is. Ex: zoom level 1 is always 1.2x, zoom level 2 is 1.44x, etc. So,
    // we need to have a fixed maximum zoom value and do read it from the
    // driver.
    static const int ZOOM_STEP = 1;
    int32_t zoom_level = params.getInt("zoom");
    if(zoom_level >= 0 && zoom_level <= mMaxZoom-1) {
        mParameters.set("zoom", zoom_level);
        int32_t zoom_value = ZOOM_STEP * zoom_level;
        bool ret = native_set_parms(MM_CAMERA_PARM_ZOOM,
            sizeof(zoom_value), (void *)&zoom_value);
        if(ret) {
            mCurrentZoom=zoom_level;
        }
        rc = ret ? NO_ERROR : UNKNOWN_ERROR;
    } else {
        rc = BAD_VALUE;
    }
    ALOGE("%s X",__func__);
    return rc;

}

status_t  QCameraHardwareInterface::setISOValue(const CameraParameters& params) {

    status_t rc = NO_ERROR;
    ALOGE("%s",__func__);

    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_ISO);
    if(!rc) {
        ALOGE("%s:MM_CAMERA_PARM_ISO not supported", __func__);
        return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_ISO_MODE);
    ALOGE("ISO string : %s",str);
    int temp_hjr;
    if (str != NULL) {
        int value = attr_lookup(
          iso, sizeof(iso) / sizeof(str_map), str);
        ALOGE("ISO Value : %d",value);
        if (value != NOT_FOUND) {
            int temp = value;
            if (value == CAMERA_ISO_DEBLUR) {
               temp_hjr = true;
               native_set_parms(MM_CAMERA_PARM_HJR, sizeof(int), (void*)&temp_hjr);
               mHJR = value;
            }
            else {
               if (mHJR == CAMERA_ISO_DEBLUR) {
                   temp_hjr = false;
                   native_set_parms(MM_CAMERA_PARM_HJR, sizeof(int), (void*)&temp_hjr);
                   mHJR = value;
               }
            }

            mParameters.set(CameraParameters::KEY_ISO_MODE, str);
            native_set_parms(MM_CAMERA_PARM_ISO, sizeof(int), (void *)&temp);
            return NO_ERROR;
        }
    }
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::updateFocusDistances(const char *focusmode)
{
    ALOGV("%s: IN", __FUNCTION__);
    focus_distances_info_t focusDistances;
    if(cam_config_get_parm(mCameraId, MM_CAMERA_PARM_FOCUS_DISTANCES,
      &focusDistances) == MM_CAMERA_OK) {
        String8 str;
        char buffer[32];
        snprintf(buffer, sizeof(buffer), "%f", focusDistances.focus_distance[0]);
        str.append(buffer);
        snprintf(buffer, sizeof(buffer), ",%f", focusDistances.focus_distance[1]);
        str.append(buffer);
        if(strcmp(focusmode, CameraParameters::FOCUS_MODE_INFINITY) == 0)
            snprintf(buffer, sizeof(buffer), ",%s", "Infinity");
        else
            snprintf(buffer, sizeof(buffer), ",%f", focusDistances.focus_distance[2]);
        str.append(buffer);
        ALOGE("%s: setting KEY_FOCUS_DISTANCES as %s", __FUNCTION__, str.string());
        mParameters.set(CameraParameters::KEY_FOCUS_DISTANCES, str.string());
        return NO_ERROR;
    }
    ALOGE("%s: get CAMERA_PARM_FOCUS_DISTANCES failed!!!", __FUNCTION__);
    return BAD_VALUE;
}

// Parse string like "(1, 2, 3, 4, ..., N)"
// num is pointer to an allocated array of size N
static int parseNDimVector(const char *str, int *num, int N, char delim = ',')
{
    char *start, *end;
    if(num == NULL) {
        ALOGE("Invalid output array (num == NULL)");
        return -1;
    }
    //check if string starts and ends with parantheses
    if(str[0] != '(' || str[strlen(str)-1] != ')') {
        ALOGE("Invalid format of string %s, valid format is (n1, n2, n3, n4 ...)", str);
        return -1;
    }
    start = (char*) str;
    start++;
    for(int i=0; i<N; i++) {
        *(num+i) = (int) strtol(start, &end, 10);
        if(*end != delim && i < N-1) {
            ALOGE("Cannot find delimeter '%c' in string \"%s\". end = %c", delim, str, *end);
            return -1;
        }
        start = end+1;
    }
    return 0;
}

// parse string like "(1, 2, 3, 4, 5),(1, 2, 3, 4, 5),..."
static int parseCameraAreaString(const char* str, int max_num_areas,
                                 camera_area_t *pAreas, int *num_areas_found)
{
    char area_str[32];
    const char *start, *end, *p;
    start = str; end = NULL;
    int values[5], index=0;
    *num_areas_found = 0;

    while(start != NULL) {
       if(*start != '(') {
            ALOGE("%s: error: Ill formatted area string: %s", __func__, str);
            return -1;
       }
       end = strchr(start, ')');
       if(end == NULL) {
            ALOGE("%s: error: Ill formatted area string: %s", __func__, str);
            return -1;
       }
       int i;
       for (i=0,p=start; p<=end; p++, i++) {
           area_str[i] = *p;
       }
       area_str[i] = '\0';
       if(parseNDimVector(area_str, values, 5) < 0){
            ALOGE("%s: error: Failed to parse the area string: %s", __func__, area_str);
            return -1;
       }
       // no more areas than max_num_areas are accepted.
       if(index >= max_num_areas) {
            ALOGE("%s: error: too many areas specified %s", __func__, str);
            return -1;
       }
       pAreas[index].x1 = values[0];
       pAreas[index].y1 = values[1];
       pAreas[index].x2 = values[2];
       pAreas[index].y2 = values[3];
       pAreas[index].weight = values[4];
       index++;
       start = strchr(end, '('); // serach for next '('
    }
    (*num_areas_found) = index;
    return 0;
}
static bool validateCameraAreas(camera_area_t *areas, int num_areas)
{
    for(int i=0; i<num_areas; i++) {

        // handle special case (0, 0, 0, 0, 0)
        if((areas[i].x1 == 0) && (areas[i].y1 == 0)
            && (areas[i].x2 == 0) && (areas[i].y2 == 0) && (areas[i].weight == 0)) {
            continue;
        }
        if(areas[i].x1 < -1000) return false;               // left should be >= -1000
        if(areas[i].y1 < -1000) return false;               // top  should be >= -1000
        if(areas[i].x2 > 1000) return false;                // right  should be <= 1000
        if(areas[i].y2 > 1000) return false;                // bottom should be <= 1000
        if(areas[i].weight <= 0 || areas[i].weight > 1000)  // weight should be in [1, 1000]
            return false;
        if(areas[i].x1 >= areas[i].x2) {                    // left should be < right
            return false;
        }
        if(areas[i].y1 >= areas[i].y2)                      // top should be < bottom
            return false;
    }
    return true;
}

status_t QCameraHardwareInterface::setFocusAreas(const CameraParameters& params)
{
    ALOGE("%s: E", __func__);
    status_t rc;
    int max_num_af_areas = mParameters.getInt(CameraParameters::KEY_MAX_NUM_FOCUS_AREAS);
    if(max_num_af_areas == 0) {
        return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_FOCUS_AREAS);
    if (str == NULL) {
        ALOGE("%s: Parameter string is null", __func__);
        rc = NO_ERROR;
    } else {
        camera_area_t *areas = new camera_area_t[max_num_af_areas];
        int num_areas_found=0;
        if(parseCameraAreaString(str, max_num_af_areas, areas, &num_areas_found) < 0) {
            ALOGE("%s: Failed to parse the string: %s", __func__, str);
            delete areas;
            return BAD_VALUE;
        }
        for(int i=0; i<num_areas_found; i++) {
            ALOGD("FocusArea[%d] = (%d, %d, %d, %d, %d)", i, (areas[i].x1), (areas[i].y1),
                        (areas[i].x2), (areas[i].y2), (areas[i].weight));
        }
        if(validateCameraAreas(areas, num_areas_found) == false) {
            ALOGE("%s: invalid areas specified : %s", __func__, str);
            delete areas;
            return BAD_VALUE;
        }
        mParameters.set(CameraParameters::KEY_FOCUS_AREAS, str);
        num_areas_found = 1; //temp; need to change after the multi-roi is enabled

        //if the native_set_parms is called when preview is not started, it
        //crashes in lower layer, so return of preview is not started
        if(mPreviewState == QCAMERA_HAL_PREVIEW_STOPPED) {
            delete areas;
            return NO_ERROR;
        }

        //for special area string (0, 0, 0, 0, 0), set the num_areas_found to 0,
        //so no action is takenby the lower layer
        if(num_areas_found == 1 && (areas[0].x1 == 0) && (areas[0].y1 == 0)
            && (areas[0].x2 == 0) && (areas[0].y2 == 0) && (areas[0].weight == 0)) {
            num_areas_found = 0;
        }
#if 1 //temp solution

        roi_info_t af_roi_value;
        memset(&af_roi_value, 0, sizeof(roi_info_t));
        uint16_t x1, x2, y1, y2, dx, dy;
        int previewWidth, previewHeight;
        this->getPreviewSize(&previewWidth, &previewHeight);
        //transform the coords from (-1000, 1000) to (0, previewWidth or previewHeight)
        x1 = (uint16_t)((areas[0].x1 + 1000.0f)*(previewWidth/2000.0f));
        y1 = (uint16_t)((areas[0].y1 + 1000.0f)*(previewHeight/2000.0f));
        x2 = (uint16_t)((areas[0].x2 + 1000.0f)*(previewWidth/2000.0f));
        y2 = (uint16_t)((areas[0].y2 + 1000.0f)*(previewHeight/2000.0f));
        dx = x2 - x1;
        dy = y2 - y1;

        af_roi_value.num_roi = num_areas_found;
        af_roi_value.roi[0].x = x1;
        af_roi_value.roi[0].y = y1;
        af_roi_value.roi[0].dx = dx;
        af_roi_value.roi[0].dy = dy;
        af_roi_value.is_multiwindow = 0;
        if (native_set_parms(MM_CAMERA_PARM_AF_ROI, sizeof(roi_info_t), (void*)&af_roi_value))
            rc = NO_ERROR;
        else
            rc = BAD_VALUE;
        delete areas;
#endif
#if 0   //better solution with multi-roi, to be enabled later
        af_mtr_area_t afArea;
        afArea.num_area = num_areas_found;

        uint16_t x1, x2, y1, y2, dx, dy;
        int previewWidth, previewHeight;
        this->getPreviewSize(&previewWidth, &previewHeight);

        for(int i=0; i<num_areas_found; i++) {
            //transform the coords from (-1000, 1000) to (0, previewWidth or previewHeight)
            x1 = (uint16_t)((areas[i].x1 + 1000.0f)*(previewWidth/2000.0f));
            y1 = (uint16_t)((areas[i].y1 + 1000.0f)*(previewHeight/2000.0f));
            x2 = (uint16_t)((areas[i].x2 + 1000.0f)*(previewWidth/2000.0f));
            y2 = (uint16_t)((areas[i].y2 + 1000.0f)*(previewHeight/2000.0f));
            dx = x2 - x1;
            dy = y2 - y1;
            afArea.mtr_area[i].x = x1;
            afArea.mtr_area[i].y = y1;
            afArea.mtr_area[i].dx = dx;
            afArea.mtr_area[i].dy = dy;
            afArea.weight[i] = areas[i].weight;
        }

        if(native_set_parms(MM_CAMERA_PARM_AF_MTR_AREA, sizeof(af_mtr_area_t), (void*)&afArea))
            rc = NO_ERROR;
        else
            rc = BAD_VALUE;*/
#endif
    }
    ALOGE("%s: X", __func__);
    return rc;
}

status_t QCameraHardwareInterface::setMeteringAreas(const CameraParameters& params)
{
    ALOGE("%s: E", __func__);
    status_t rc;
    int max_num_mtr_areas = mParameters.getInt(CameraParameters::KEY_MAX_NUM_METERING_AREAS);
    if(max_num_mtr_areas == 0) {
        return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_METERING_AREAS);
    if (str == NULL) {
        ALOGE("%s: Parameter string is null", __func__);
        rc = NO_ERROR;
    } else {
        camera_area_t *areas = new camera_area_t[max_num_mtr_areas];
        int num_areas_found=0;
        if(parseCameraAreaString(str, max_num_mtr_areas, areas, &num_areas_found) < 0) {
            ALOGE("%s: Failed to parse the string: %s", __func__, str);
            delete areas;
            return BAD_VALUE;
        }
        for(int i=0; i<num_areas_found; i++) {
            ALOGD("MeteringArea[%d] = (%d, %d, %d, %d, %d)", i, (areas[i].x1), (areas[i].y1),
                        (areas[i].x2), (areas[i].y2), (areas[i].weight));
        }
        if(validateCameraAreas(areas, num_areas_found) == false) {
            ALOGE("%s: invalid areas specified : %s", __func__, str);
            delete areas;
            return BAD_VALUE;
        }
        mParameters.set(CameraParameters::KEY_METERING_AREAS, str);

        //if the native_set_parms is called when preview is not started, it
        //crashes in lower layer, so return of preview is not started
        if(mPreviewState == QCAMERA_HAL_PREVIEW_STOPPED) {
            delete areas;
            return NO_ERROR;
        }

        num_areas_found = 1; //temp; need to change after the multi-roi is enabled

        //for special area string (0, 0, 0, 0, 0), set the num_areas_found to 0,
        //so no action is takenby the lower layer
        if(num_areas_found == 1 && (areas[0].x1 == 0) && (areas[0].y1 == 0)
             && (areas[0].x2 == 0) && (areas[0].y2 == 0) && (areas[0].weight == 0)) {
            num_areas_found = 0;
        }
#if 1
        roi_info_t aec_roi_value;
        memset(&aec_roi_value, 0, sizeof(roi_info_t));
        uint16_t x1, x2, y1, y2, dx, dy;

        int previewWidth, previewHeight;
        this->getPreviewSize(&previewWidth, &previewHeight);
        //transform the coords from (-1000, 1000) to (0, previewWidth or previewHeight)
        x1 = (uint16_t)((areas[0].x1 + 1000.0f)*(previewWidth/2000.0f));
        y1 = (uint16_t)((areas[0].y1 + 1000.0f)*(previewHeight/2000.0f));
        x2 = (uint16_t)((areas[0].x2 + 1000.0f)*(previewWidth/2000.0f));
        y2 = (uint16_t)((areas[0].y2 + 1000.0f)*(previewHeight/2000.0f));

        dx = x2 - x1;
        dy = y2 - y1;

        aec_roi_value.num_roi = num_areas_found;
        aec_roi_value.roi[0].x = x1;
        aec_roi_value.roi[0].y = y1;
        aec_roi_value.roi[0].dx = dx;
        aec_roi_value.roi[0].dy = dy;
        aec_roi_value.is_multiwindow = 0;

        if(native_set_parms(MM_CAMERA_PARM_AEC_ROI, sizeof(aec_roi_value), (void *)&aec_roi_value))
            rc = NO_ERROR;
        else
            rc = BAD_VALUE;
#endif
#if 0   //solution including multi-roi, to be enabled later
        aec_mtr_area_t aecArea;
        aecArea.num_area = num_areas_found;

        uint16_t x1, x2, y1, y2, dx, dy;
        int previewWidth, previewHeight;
        this->getPreviewSize(&previewWidth, &previewHeight);

        for(int i=0; i<num_areas_found; i++) {
            //transform the coords from (-1000, 1000) to (0, previewWidth or previewHeight)
            x1 = (uint16_t)((areas[i].x1 + 1000.0f)*(previewWidth/2000.0f));
            y1 = (uint16_t)((areas[i].y1 + 1000.0f)*(previewHeight/2000.0f));
            x2 = (uint16_t)((areas[i].x2 + 1000.0f)*(previewWidth/2000.0f));
            y2 = (uint16_t)((areas[i].y2 + 1000.0f)*(previewHeight/2000.0f));
            dx = x2 - x1;
            dy = y2 - y1;
            aecArea.mtr_area[i].x = x1;
            aecArea.mtr_area[i].y = y1;
            aecArea.mtr_area[i].dx = dx;
            aecArea.mtr_area[i].dy = dy;
            aecArea.weight[i] = areas[i].weight;
        }
        delete areas;

        if(native_set_parms(MM_CAMERA_PARM_AEC_MTR_AREA, sizeof(aec_mtr_area_t), (void*)&aecArea))
            rc = NO_ERROR;
        else
            rc = BAD_VALUE;
#endif
    }
    ALOGE("%s: X", __func__);
    return rc;
}

status_t QCameraHardwareInterface::setFocusMode(const CameraParameters& params)
{
    const char *str = params.get(CameraParameters::KEY_FOCUS_MODE);
    ALOGE("%s",__func__);
    if (str != NULL) {

      ALOGE("Focus mdoe %s",str);
        int32_t value = attr_lookup(focus_modes,
                                    sizeof(focus_modes) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            mParameters.set(CameraParameters::KEY_FOCUS_MODE, str);

            if(updateFocusDistances(str) != NO_ERROR) {
               ALOGE("%s: updateFocusDistances failed for %s", __FUNCTION__, str);
               return UNKNOWN_ERROR;
            }

            if(mHasAutoFocusSupport){
                int cafSupport = FALSE;
                if(!strcmp(str, CameraParameters::FOCUS_MODE_CONTINUOUS_VIDEO) ||
                   !strcmp(str, CameraParameters::FOCUS_MODE_CONTINUOUS_PICTURE)){
                    cafSupport = TRUE;
                }
                bool ret = native_set_parms(MM_CAMERA_PARM_CONTINUOUS_AF, sizeof(cafSupport),
                                       (void *)&cafSupport);
		ALOGE("MM_CAMERA_PARM_CONTINUOUS_AF(%d)=%d",cafSupport,ret);
                ret = native_set_parms(MM_CAMERA_PARM_FOCUS_MODE, sizeof(value),(void *)&value);
		ALOGE("MM_CAMERA_PARM_FOCUS_MODE(%d)=%d",value,ret);

//                ALOGE("Continuous Auto Focus %d", cafSupport);
            }

            return NO_ERROR;
        }
        ALOGE("%s:Could not look up str value",__func__);
    }
    ALOGE("Invalid focus mode value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setSceneMode(const CameraParameters& params)
{
    status_t rc = NO_ERROR;
    ALOGE("%s",__func__);

    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_BESTSHOT_MODE);
    if(!rc) {
        ALOGE("%s:Parameter Scenemode is not supported for this sensor", __func__);
        return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_SCENE_MODE);
    ALOGE("Scene Mode string : %s",str);

    if (str != NULL) {
        int32_t value = attr_lookup(scenemode, sizeof(scenemode) / sizeof(str_map), str);
        ALOGE("Setting Scenemode value = %d",value );
        if (value != NOT_FOUND) {
            mParameters.set(CameraParameters::KEY_SCENE_MODE, str);
            bool ret = native_set_parms(MM_CAMERA_PARM_BESTSHOT_MODE, sizeof(value),
                                       (void *)&value);
            return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
    }
    ALOGE("Invalid scenemode value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setSelectableZoneAf(const CameraParameters& params)
{
    ALOGE("%s",__func__);
    if(mHasAutoFocusSupport) {
        const char *str = params.get(CameraParameters::KEY_SELECTABLE_ZONE_AF);
        if (str != NULL) {
            int32_t value = attr_lookup(selectable_zone_af, sizeof(selectable_zone_af) / sizeof(str_map), str);
            if (value != NOT_FOUND) {
                mParameters.set(CameraParameters::KEY_SELECTABLE_ZONE_AF, str);
                bool ret = native_set_parms(MM_CAMERA_PARM_FOCUS_RECT, sizeof(value),
                        (void *)&value);
                ALOGE("zone_af(%d) = %d",value,ret);
                return ret ? NO_ERROR : UNKNOWN_ERROR;
            }
        }
        ALOGE("Invalid selectable zone af value: %s", (str == NULL) ? "NULL" : str);
        return BAD_VALUE;

    }
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setEffect(const CameraParameters& params)
{
    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    const char *str = params.get(CameraParameters::KEY_EFFECT);
    int result;
    if (str != NULL) {
        ALOGE("Setting effect %s",str);
        int32_t value = attr_lookup(effects, sizeof(effects) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
           rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_EFFECT);
           if(!rc) {
               ALOGE("Camera Effect - %s mode is not supported for this sensor",str);
               return NO_ERROR;
           }else {
               mParameters.set(CameraParameters::KEY_EFFECT, str);
               ALOGE("Setting effect to lower HAL : %d",value);
               bool ret = native_set_parms(MM_CAMERA_PARM_EFFECT, sizeof(value),
                                           (void *)&value,(int *)&result);
                if(result != MM_CAMERA_OK) {
                    ALOGI("Camera Effect: %s is not set as the selected value is not supported ", str);
                }
               return ret ? NO_ERROR : UNKNOWN_ERROR;
          }
        }
    }
    ALOGE("Invalid effect value: %s", (str == NULL) ? "NULL" : str);
    ALOGE("setEffect X");
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setBrightness(const CameraParameters& params) {

    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_BRIGHTNESS);
   if(!rc) {
       ALOGE("MM_CAMERA_PARM_BRIGHTNESS mode is not supported for this sensor");
       return NO_ERROR;
   }
   int brightness = params.getInt("luma-adaptation");
   if (mBrightness !=  brightness) {
       ALOGV(" new brightness value : %d ", brightness);
       mBrightness =  brightness;
       mParameters.set("luma-adaptation", brightness);
       bool ret = native_set_parms(MM_CAMERA_PARM_BRIGHTNESS, sizeof(mBrightness),
                                   (void *)&mBrightness);
        return ret ? NO_ERROR : UNKNOWN_ERROR;
   }

    return NO_ERROR;
}

status_t QCameraHardwareInterface::setAutoExposure(const CameraParameters& params)
{

    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_EXPOSURE);
   if(!rc) {
       ALOGE("MM_CAMERA_PARM_EXPOSURE mode is not supported for this sensor");
       return NO_ERROR;
   }
   const char *str = params.get(CameraParameters::KEY_AUTO_EXPOSURE);
    if (str != NULL) {
        int32_t value = attr_lookup(autoexposure, sizeof(autoexposure) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            mParameters.set(CameraParameters::KEY_AUTO_EXPOSURE, str);
            bool ret = native_set_parms(MM_CAMERA_PARM_EXPOSURE, sizeof(value),
                                       (void *)&value);
            return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
    }
    ALOGE("Invalid auto exposure value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setExposureCompensation(
        const CameraParameters & params){
    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_EXPOSURE_COMPENSATION);
    if(!rc) {
       ALOGE("MM_CAMERA_PARM_EXPOSURE_COMPENSATION mode is not supported for this sensor");
       return NO_ERROR;
    }
    int numerator = params.getInt(CameraParameters::KEY_EXPOSURE_COMPENSATION);
    if(EXPOSURE_COMPENSATION_MINIMUM_NUMERATOR <= numerator &&
            numerator <= EXPOSURE_COMPENSATION_MAXIMUM_NUMERATOR){
        int16_t  numerator16 = (int16_t)(numerator & 0x0000ffff);
        uint16_t denominator16 = EXPOSURE_COMPENSATION_DENOMINATOR;
        uint32_t  value = 0;
        value = numerator16 << 16 | denominator16;

        mParameters.set(CameraParameters::KEY_EXPOSURE_COMPENSATION,
                            numerator);
       bool ret = native_set_parms(MM_CAMERA_PARM_EXPOSURE_COMPENSATION,
                                    sizeof(value), (void *)&value);
        return ret ? NO_ERROR : UNKNOWN_ERROR;
    }
    ALOGE("Invalid Exposure Compensation");
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setWhiteBalance(const CameraParameters& params)
{

     ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_WHITE_BALANCE);
    if(!rc) {
       ALOGE("MM_CAMERA_PARM_WHITE_BALANCE mode is not supported for this sensor");
       return NO_ERROR;
    }
     int result;

    const char *str = params.get(CameraParameters::KEY_WHITE_BALANCE);
    if (str != NULL) {
        int32_t value = attr_lookup(whitebalance, sizeof(whitebalance) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            mParameters.set(CameraParameters::KEY_WHITE_BALANCE, str);
            bool ret = native_set_parms(MM_CAMERA_PARM_WHITE_BALANCE, sizeof(value),
                                       (void *)&value, (int *)&result);
            if(result != MM_CAMERA_OK) {
                ALOGI("WhiteBalance Value: %s is not set as the selected value is not supported ", str);
            }
            return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
    }
    ALOGE("Invalid whitebalance value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}
status_t QCameraHardwareInterface::setAntibanding(const CameraParameters& params)
{
    int result;

    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_ANTIBANDING);
    if(!rc) {
       ALOGE("ANTIBANDING mode is not supported for this sensor");
       return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_ANTIBANDING);
    if (str != NULL) {
        int value = (camera_antibanding_type)attr_lookup(
          antibanding, sizeof(antibanding) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            camera_antibanding_type temp = (camera_antibanding_type) value;
            ALOGE("Antibanding Value : %d",value);
            mParameters.set(CameraParameters::KEY_ANTIBANDING, str);
            bool ret = native_set_parms(MM_CAMERA_PARM_ANTIBANDING,
                       sizeof(camera_antibanding_type), (void *)&value ,(int *)&result);
            if(result != MM_CAMERA_OK) {
                ALOGI("AntiBanding Value: %s is not supported for the given BestShot Mode", str);
            }
            return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
    }
    ALOGE("Invalid antibanding value: %s", (str == NULL) ? "NULL" : str);

    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setPreviewFrameRate(const CameraParameters& params)
{
    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_FPS);
    if(!rc) {
       ALOGE("MM_CAMERA_PARM_FPS is not supported for this sensor");
       return NO_ERROR;
    }
    uint16_t previousFps = (uint16_t)mParameters.getPreviewFrameRate();
    uint16_t fps = (uint16_t)params.getPreviewFrameRate();
    ALOGV("requested preview frame rate  is %u", fps);

/*
    if(mInitialized && (fps == previousFps)){
        ALOGV("No change is FPS Value %d",fps );
        return NO_ERROR;
    }
*/


    if(MINIMUM_FPS <= fps && fps <=MAXIMUM_FPS){
        mParameters.setPreviewFrameRate(fps);
        bool ret = native_set_parms(MM_CAMERA_PARM_FPS,
                sizeof(fps), (void *)&fps);
        return ret ? NO_ERROR : UNKNOWN_ERROR;
    }

    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setPreviewFrameRateMode(const CameraParameters& params) {

    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    return rc;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_FPS);
    if(!rc) {
       ALOGE(" CAMERA FPS mode is not supported for this sensor");
       return NO_ERROR;
    }
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_FPS_MODE);
    if(!rc) {
       ALOGE("CAMERA FPS MODE mode is not supported for this sensor");
       return NO_ERROR;
    }

    const char *previousMode = mParameters.getPreviewFrameRateMode();
    const char *str = params.getPreviewFrameRateMode();
    if (NULL == previousMode) {
        ALOGE("Preview Frame Rate Mode is NULL\n");
        return NO_ERROR;
    }
    if (NULL == str) {
        ALOGE("Preview Frame Rate Mode is NULL\n");
        return NO_ERROR;
    }
    if( mInitialized && !strcmp(previousMode, str)) {
        ALOGE("frame rate mode same as previous mode %s", previousMode);
        return NO_ERROR;
    }
    int32_t frameRateMode = attr_lookup(frame_rate_modes, sizeof(frame_rate_modes) / sizeof(str_map),str);
    if(frameRateMode != NOT_FOUND) {
        ALOGV("setPreviewFrameRateMode: %s ", str);
        mParameters.setPreviewFrameRateMode(str);
        bool ret = native_set_parms(MM_CAMERA_PARM_FPS_MODE, sizeof(frameRateMode), (void *)&frameRateMode);
        if(!ret) return ret;
        //set the fps value when chaging modes
        int16_t fps = (uint16_t)params.getPreviewFrameRate();
        if(MINIMUM_FPS <= fps && fps <=MAXIMUM_FPS){
            mParameters.setPreviewFrameRate(fps);
            ret = native_set_parms(MM_CAMERA_PARM_FPS,
                                        sizeof(fps), (void *)&fps);
            return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
        ALOGE("Invalid preview frame rate value: %d", fps);
        return BAD_VALUE;
    }
    ALOGE("Invalid preview frame rate mode value: %s", (str == NULL) ? "NULL" : str);

    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setSkinToneEnhancement(const CameraParameters& params) {
    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_SCE_FACTOR);
    if(!rc) {
       ALOGE("SkinToneEnhancement is not supported for this sensor");
       return NO_ERROR;
    }
     int skinToneValue = params.getInt("skinToneEnhancement");
     if (mSkinToneEnhancement != skinToneValue) {
          ALOGV(" new skinTone correction value : %d ", skinToneValue);
          mSkinToneEnhancement = skinToneValue;
          mParameters.set("skinToneEnhancement", skinToneValue);
          bool ret = native_set_parms(MM_CAMERA_PARM_SCE_FACTOR, sizeof(mSkinToneEnhancement),
                        (void *)&mSkinToneEnhancement);
          return ret ? NO_ERROR : UNKNOWN_ERROR;
    }
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setWaveletDenoise(const CameraParameters& params) {
    ALOGE("%s",__func__);
/*
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_WAVELET_DENOISE);
    if(rc != MM_CAMERA_PARM_SUPPORT_SET) {
        ALOGE("Wavelet Denoise is not supported for this sensor");
//        return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_DENOISE);
    if (str != NULL) {
        int value = attr_lookup(denoise,
                sizeof(denoise) / sizeof(str_map), str);
        if ((value != NOT_FOUND) &&  (mDenoiseValue != value)) {
            mDenoiseValue =  value;
            mParameters.set(CameraParameters::KEY_DENOISE, str);

            char prop[PROPERTY_VALUE_MAX];
            memset(prop, 0, sizeof(prop));
            property_get("persist.denoise.process.plates", prop, "0");

            denoise_param_t temp;
            memset(&temp, 0, sizeof(denoise_param_t));
            temp.denoise_enable = value;
            temp.process_plates = atoi(prop);
            ALOGE("Denoise enable=%d, plates=%d", temp.denoise_enable, temp.process_plates);
            bool ret = native_set_parms(MM_CAMERA_PARM_WAVELET_DENOISE, sizeof(temp),
                    (void *)&temp);
            return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
        return NO_ERROR;
    }
  
    ALOGE("Invalid Denoise value: %s", (str == NULL) ? "NULL" : str);
*/
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setVideoSize(const CameraParameters& params)
{
    const char *str= NULL;
    str = params.get(CameraParameters::KEY_VIDEO_SIZE);
    if(!str) {
        mParameters.set(CameraParameters::KEY_VIDEO_SIZE, "");
        //If application didn't set this parameter string, use the values from
        //getPreviewSize() as video dimensions.
        ALOGV("No Record Size requested, use the preview dimensions");
        videoWidth = previewWidth;
        videoHeight = previewHeight;
    } else {
        //Extract the record witdh and height that application requested.
        ALOGI("%s: requested record size %s", __func__, str);
        if(!parse_size(str, videoWidth, videoHeight)) {
            mParameters.set(CameraParameters::KEY_VIDEO_SIZE, str);
            //VFE output1 shouldn't be greater than VFE output2.
            if( (previewWidth > videoWidth) || (previewHeight > videoHeight)) {
                //Set preview sizes as record sizes.
                ALOGI("Preview size %dx%d is greater than record size %dx%d,\
                   resetting preview size to record size",previewWidth,
                     previewHeight, videoWidth, videoHeight);
                previewWidth = videoWidth;
                previewHeight = videoHeight;
                mParameters.setPreviewSize(previewWidth, previewHeight);
            }

            if(mIs3DModeOn == true) {
                /* As preview and video frames are same in 3D mode,
                 * preview size should be same as video size. This
                 * cahnge is needed to take of video resolutions
                 * like 720P and 1080p where the application can
                 * request different preview sizes like 768x432
                 */
                previewWidth = videoWidth;
                previewHeight = videoHeight;
                mParameters.setPreviewSize(previewWidth, previewHeight);
            }
        } else {
            mParameters.set(CameraParameters::KEY_VIDEO_SIZE, "");
            ALOGE("initPreview X: failed to parse parameter record-size (%s)", str);
            return BAD_VALUE;
        }
    }
    ALOGE("%s: preview dimensions: %dx%d", __func__, previewWidth, previewHeight);
    ALOGE("%s: video dimensions: %dx%d", __func__, videoWidth, videoHeight);
    mDimension.display_width = previewWidth;
    mDimension.display_height= previewHeight;
    mDimension.orig_video_width = videoWidth;
    mDimension.orig_video_height = videoHeight;
    mDimension.video_width = videoWidth;
    mDimension.video_height = videoHeight;

    return NO_ERROR;
}

status_t QCameraHardwareInterface::setCameraMode(const CameraParameters& params) {
    int32_t value = params.getInt(CameraParameters::KEY_CAMERA_MODE);
    mParameters.set(CameraParameters::KEY_CAMERA_MODE,value);

    ALOGI("ZSL is enabled  %d", value);
    if (value == 1) {
        myMode = (camera_mode_t)(myMode | CAMERA_ZSL_MODE);
    } else {
        myMode = (camera_mode_t)(myMode & ~CAMERA_ZSL_MODE);
    }
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setPreviewSize(const CameraParameters& params)
{
    int width, height;
    params.getPreviewSize(&width, &height);
    ALOGE("################requested preview size %d x %d", width, height);

    // Validate the preview size
    for (size_t i = 0; i <  mPreviewSizeCount; ++i) {
        if (width ==  mPreviewSizes[i].width
           && height ==  mPreviewSizes[i].height) {
            mParameters.setPreviewSize(width, height);
            previewWidth = width;
            previewHeight = height;
            mDimension.display_width = width;
            mDimension.display_height= height;
            return NO_ERROR;
        }
    }
    ALOGE("Invalid preview size requested: %dx%d", width, height);
    return BAD_VALUE;
}
status_t QCameraHardwareInterface::setPreviewFpsRange(const CameraParameters& params)
{
    int minFps,maxFps;

    params.getPreviewFpsRange(&minFps,&maxFps);
    ALOGE("FPS: Range Values: %dx%d", minFps, maxFps);

    for(size_t i=0;i<FPS_RANGES_SUPPORTED_COUNT;i++)
    {
        if(minFps==FpsRangesSupported[i].minFPS && maxFps == FpsRangesSupported[i].maxFPS){
            ALOGE("FPS: i=%d : minFps = %d, maxFps = %d ",i,FpsRangesSupported[i].minFPS,FpsRangesSupported[i].maxFPS );
            mParameters.setPreviewFpsRange(minFps,maxFps);
            return NO_ERROR;
        }
    }

    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setJpegThumbnailSize(const CameraParameters& params){
    int width;
    int height;

    getThumbSizesFromAspectRatio((uint32_t)((mDimension.picture_width * Q12)/mDimension.picture_height),&width,&height);

    ALOGE("requested jpeg thumbnail size %d x %d", width, height);

    // Validate the picture size
    for (unsigned int i = 0; i < JPEG_THUMBNAIL_SIZE_COUNT; ++i) {
       if (width == jpeg_thumbnail_sizes[i].width
         && height == jpeg_thumbnail_sizes[i].height) {
           mParameters.set(CameraParameters::KEY_JPEG_THUMBNAIL_WIDTH, width);
           mParameters.set(CameraParameters::KEY_JPEG_THUMBNAIL_HEIGHT, height);
           return NO_ERROR;
       }
    }
    return BAD_VALUE;
}
status_t QCameraHardwareInterface::setPictureSize(const CameraParameters& params)
{
    int width, height;
    ALOGE("QualcommCameraHardware::setPictureSize E");
    params.getPictureSize(&width, &height);
    ALOGE("requested picture size %d x %d", width, height);

    // Validate the picture size
    for (int i = 0; i < mSupportedPictureSizesCount; ++i) {
        if (width == mPictureSizesPtr[i].width
          && height == mPictureSizesPtr[i].height) {
            mParameters.setPictureSize(width, height);
            mDimension.picture_width = width;
            mDimension.picture_height = height;
            return NO_ERROR;
        }
    }
    /* Dimension not among the ones in the list. Check if
     * its a valid dimension, if it is, then configure the
     * camera accordingly. else reject it.
     */
    if( isValidDimension(width, height) ) {
        mParameters.setPictureSize(width, height);
        mDimension.picture_width = width;
        mDimension.picture_height = height;
        return NO_ERROR;
    } else
        ALOGE("Invalid picture size requested: %dx%d", width, height);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setJpegRotation(int isZsl) {
    int rotation = mParameters.getInt("rotation");
    return mm_jpeg_encoder_setRotation(rotation);
}

int QCameraHardwareInterface::getJpegRotation(void) {
    int rotation = mParameters.getInt("rotation");
    return rotation;
}

int QCameraHardwareInterface::getISOSpeedValue()
{
    const char *iso_str = mParameters.get(CameraParameters::KEY_ISO_MODE);
    int iso_index = attr_lookup(iso, sizeof(iso) / sizeof(str_map), iso_str);
    int iso_value = iso_speed_values[iso_index];
    return iso_value;
}


status_t QCameraHardwareInterface::setJpegQuality(const CameraParameters& params) {
    status_t rc = NO_ERROR;
    int quality = params.getInt(CameraParameters::KEY_JPEG_QUALITY);
    ALOGE("setJpegQuality E");
    if (quality >= 0 && quality <= 100) {
        mParameters.set(CameraParameters::KEY_JPEG_QUALITY, quality);
    } else {
        ALOGE("Invalid jpeg quality=%d", quality);
        rc = BAD_VALUE;
    }

    quality = params.getInt(CameraParameters::KEY_JPEG_THUMBNAIL_QUALITY);
    if (quality >= 0 && quality <= 100) {
        mParameters.set(CameraParameters::KEY_JPEG_THUMBNAIL_QUALITY, quality);
    } else {
        ALOGE("Invalid jpeg thumbnail quality=%d", quality);
        rc = BAD_VALUE;
    }
    ALOGE("setJpegQuality X");
    return rc;
}

status_t QCameraHardwareInterface::
setNumOfSnapshot(const CameraParameters& params) {
    status_t rc = NO_ERROR;

    int num_of_snapshot = getNumOfSnapshots();

    if (num_of_snapshot <= 0) {
        num_of_snapshot = 1;
    }
    ALOGI("number of snapshots = %d", num_of_snapshot);
    mParameters.set("num-snaps-per-shutter", num_of_snapshot);

    bool result = native_set_parms(MM_CAMERA_PARM_SNAPSHOT_BURST_NUM,
                                   sizeof(int),
                                   (void *)&num_of_snapshot);
    if(!result)
        ALOGI("%s:Failure setting number of snapshots!!!", __func__);
    return rc;
}

status_t QCameraHardwareInterface::setPreviewFormat(const CameraParameters& params) {
    const char *str = params.getPreviewFormat();
    int32_t previewFormat = attr_lookup(preview_formats, sizeof(preview_formats) / sizeof(str_map), str);
    if(previewFormat != NOT_FOUND) {
        preview_format_info_t format_info;
        int num = sizeof(preview_format_info_list)/sizeof(preview_format_info_t);
        int i;
        for (i = 0; i < num; i++) {
          if (preview_format_info_list[i].Hal_format == previewFormat) {
            mPreviewFormatInfo = preview_format_info_list[i];
            break;
          }
        }
        if (i == num) {
          mPreviewFormatInfo.mm_cam_format = CAMERA_YUV_420_NV21;
          mPreviewFormatInfo.padding = CAMERA_PAD_TO_WORD;
          return BAD_VALUE;
        }
        bool ret = native_set_parms(MM_CAMERA_PARM_PREVIEW_FORMAT, sizeof(cam_format_t),
                                   (void *)&mPreviewFormatInfo.mm_cam_format);
        mParameters.set(CameraParameters::KEY_PREVIEW_FORMAT, str);
        mPreviewFormat = mPreviewFormatInfo.mm_cam_format;
        ALOGI("Setting preview format to %d, i =%d, num=%d, hal_format=%d",
             mPreviewFormat, i, num, mPreviewFormatInfo.Hal_format);
        return NO_ERROR;
    } else if ( strTexturesOn ) {
      mPreviewFormatInfo.mm_cam_format = CAMERA_YUV_420_NV21;
      mPreviewFormatInfo.padding = CAMERA_PAD_TO_4K;
    } else {
      mPreviewFormatInfo.mm_cam_format = CAMERA_YUV_420_NV21;
      mPreviewFormatInfo.padding = CAMERA_PAD_TO_WORD;
    }
    ALOGE("Invalid preview format value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setStrTextures(const CameraParameters& params) {
    const char *str = params.get("strtextures");
    if(str != NULL) {
        ALOGV("strtextures = %s", str);
        mParameters.set("strtextures", str);
        if(!strncmp(str, "on", 2) || !strncmp(str, "ON", 2)) {
            ALOGI("Resetting mUseOverlay to false");
            strTexturesOn = true;
            mUseOverlay = false;
        } else if (!strncmp(str, "off", 3) || !strncmp(str, "OFF", 3)) {
            strTexturesOn = false;
            mUseOverlay = true;
        }
    }
    return NO_ERROR;
}
status_t QCameraHardwareInterface::setFlash(const CameraParameters& params)
{
    ALOGI("%s: E",__func__);
    int rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_LED_MODE);
    if(!rc) {
        ALOGE("%s:LED FLASH not supported", __func__);
        return NO_ERROR;
    }

    const char *str = params.get(CameraParameters::KEY_FLASH_MODE);
    if (str != NULL) {
        int32_t value = attr_lookup(flash, sizeof(flash) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            mParameters.set(CameraParameters::KEY_FLASH_MODE, str);
            bool ret = native_set_parms(MM_CAMERA_PARM_LED_MODE,
                                       sizeof(value), (void *)&value);
            return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
    }
    ALOGE("Invalid flash mode value: %s", (str == NULL) ? "NULL" : str);

    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setAecAwbLock(const CameraParameters & params)
{
    ALOGD("%s : E", __func__);
    status_t rc = NO_ERROR;
    int32_t value;
    const char* str;

    //for AEC lock
    str = params.get(CameraParameters::KEY_AUTO_EXPOSURE_LOCK);
    value = (strcmp(str, "true") == 0)? 1 : 0;
    mParameters.set(CameraParameters::KEY_AUTO_EXPOSURE_LOCK, str);
    rc = (native_set_parms(MM_CAMERA_PARM_AEC_LOCK, sizeof(int32_t), (void *)(&value))) ?
                            NO_ERROR : UNKNOWN_ERROR;

    //for AWB lock
    str = params.get(CameraParameters::KEY_AUTO_WHITEBALANCE_LOCK);
    value = (strcmp(str, "true") == 0)? 1 : 0;
    mParameters.set(CameraParameters::KEY_AUTO_WHITEBALANCE_LOCK, str);
    rc = (native_set_parms(MM_CAMERA_PARM_AWB_LOCK, sizeof(int32_t), (void *)(&value))) ?
                        NO_ERROR : UNKNOWN_ERROR;
    ALOGD("%s : X", __func__);
    return rc;
}

status_t QCameraHardwareInterface::setOverlayFormats(const CameraParameters& params)
{
    mParameters.set("overlay-format", HAL_PIXEL_FORMAT_YCbCr_420_SP);
    if(mIs3DModeOn == true) {
       int ovFormat = HAL_PIXEL_FORMAT_YCrCb_420_SP|HAL_3D_IN_SIDE_BY_SIDE_L_R|HAL_3D_OUT_SIDE_BY_SIDE;
        mParameters.set("overlay-format", ovFormat);
    }
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setMCEValue(const CameraParameters& params)
{
    ALOGE("%s",__func__);
    status_t rc = NO_ERROR;
    rc = cam_config_is_parm_supported(mCameraId,MM_CAMERA_PARM_MCE);
   if(!rc) {
       ALOGE("MM_CAMERA_PARM_MCE mode is not supported for this sensor");
       return NO_ERROR;
   }
   const char *str = params.get(CameraParameters::KEY_MEMORY_COLOR_ENHANCEMENT);
    if (str != NULL) {
        int value = attr_lookup(mce, sizeof(mce) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            int temp = value;
            ALOGI("%s: setting MCE value of %s", __FUNCTION__, str);
            mParameters.set(CameraParameters::KEY_MEMORY_COLOR_ENHANCEMENT, str);

            native_set_parms(MM_CAMERA_PARM_MCE, sizeof(int), (void *)&temp);
            return NO_ERROR;
        }
    }
    ALOGE("Invalid MCE value: %s", (str == NULL) ? "NULL" : str);

    return NO_ERROR;
}

status_t QCameraHardwareInterface::setHighFrameRate(const CameraParameters& params)
{

    bool mCameraRunning;

    int rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_HFR);
    if(!rc) {
        ALOGE("%s: MM_CAMERA_PARM_HFR not supported", __func__);
        return NO_ERROR;
    }

    const char *str = params.get(CameraParameters::KEY_VIDEO_HIGH_FRAME_RATE);
    if (str != NULL) {
        int value = attr_lookup(hfr, sizeof(hfr) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            mHFRLevel = (int32_t)value;
            //Check for change in HFR value
            const char *oldHfr = mParameters.get(CameraParameters::KEY_VIDEO_HIGH_FRAME_RATE);
            if(strcmp(oldHfr, str)){
                mParameters.set(CameraParameters::KEY_VIDEO_HIGH_FRAME_RATE, str);
//              mHFRMode = true;
		mCameraRunning=isPreviewRunning();
                if(mCameraRunning == true) {
//                    mHFRThreadWaitLock.lock();
//                    pthread_attr_t pattr;
//                    pthread_attr_init(&pattr);
//                    pthread_attr_setdetachstate(&pattr, PTHREAD_CREATE_DETACHED);
//                    mHFRThreadRunning = !pthread_create(&mHFRThread,
//                                      &pattr,
//                                      hfr_thread,
//                                      (void*)NULL);
//                    mHFRThreadWaitLock.unlock();
 		    stopPreview();
                    native_set_parms(MM_CAMERA_PARM_HFR, sizeof(int32_t), (void *)&mHFRLevel);
                    startPreview();
                    return NO_ERROR;
                }
            }
            native_set_parms(MM_CAMERA_PARM_HFR, sizeof(int32_t), (void *)&mHFRLevel);
            return NO_ERROR;
        }
    }
    ALOGE("Invalid HFR value: %s", (str == NULL) ? "NULL" : str);
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setLensshadeValue(const CameraParameters& params)
{

    int rc = cam_config_is_parm_supported(mCameraId, MM_CAMERA_PARM_ROLLOFF);
    if(!rc) {
        ALOGE("%s:LENS SHADING not supported", __func__);
        return NO_ERROR;
    }

    const char *str = params.get(CameraParameters::KEY_LENSSHADE);
    if (str != NULL) {
        int value = attr_lookup(lensshade,
                                    sizeof(lensshade) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            int temp = value;
            mParameters.set(CameraParameters::KEY_LENSSHADE, str);
            native_set_parms(MM_CAMERA_PARM_ROLLOFF, sizeof(int), (void *)&temp);
            return NO_ERROR;
        }
    }
    ALOGE("Invalid lensShade value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setFaceDetect(const CameraParameters& params)
{
    const char *str = params.get(CameraParameters::KEY_FACE_DETECTION);
    ALOGE("setFaceDetect: %s", str);
    if (str != NULL) {
        int value = attr_lookup(facedetection,
                sizeof(facedetection) / sizeof(str_map), str);
        mFaceDetectOn = value;
        ALOGE("%s Face detection value = %d",__func__, value);
        cam_ctrl_dimension_t dim;
//        cam_config_get_parm(mCameraId, MM_CAMERA_PARM_DIMENSION,&dim);
//        preview_parm_config (&dim, mParameters);
//        cam_config_set_parm(mCameraId, MM_CAMERA_PARM_DIMENSION,&dim);
        native_set_parms(MM_CAMERA_PARM_FD, sizeof(int), (void *)&value);
        mParameters.set(CameraParameters::KEY_FACE_DETECTION, str);
        return NO_ERROR;
    }
    ALOGE("Invalid Face Detection value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}
status_t QCameraHardwareInterface::setFaceDetection(const char *str)
{
    if(supportsFaceDetection() == false){
        ALOGE("Face detection is not enabled");
        return NO_ERROR;
    }
    if (str != NULL) {
        int value = attr_lookup(facedetection,
                                    sizeof(facedetection) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            mMetaDataWaitLock.lock();
            mFaceDetectOn = value;
            mMetaDataWaitLock.unlock();
            mParameters.set(CameraParameters::KEY_FACE_DETECTION, str);
            native_set_parms(MM_CAMERA_PARM_FD, sizeof(int), (void *)&value);
            mParameters.set(CameraParameters::KEY_FACE_DETECTION, str);
            return NO_ERROR;
        }
    }
    ALOGE("Invalid Face Detection value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setAEBracket(const CameraParameters& params)
{
    if(!cam_config_is_parm_supported(mCameraId,MM_CAMERA_PARM_HDR) || (myMode & CAMERA_ZSL_MODE)) {
        ALOGI("Parameter HDR is not supported for this sensor/ ZSL mode");

        if (myMode & CAMERA_ZSL_MODE) {
            ALOGE("In ZSL mode, reset AEBBracket to HDR_OFF mode");
            exp_bracketing_t temp;
            memset(&temp, 0, sizeof(temp));
            mHdrMode = HDR_BRACKETING_OFF;
            temp.hdr_enable= FALSE;
            temp.mode = HDR_BRACKETING_OFF;
            native_set_parms(MM_CAMERA_PARM_HDR, sizeof(exp_bracketing_t), (void *)&temp);
        }
        return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_AE_BRACKET_HDR);

    if (str != NULL) {
        int value = attr_lookup(hdr_bracket,
                                    sizeof(hdr_bracket) / sizeof(str_map), str);
        exp_bracketing_t temp;
        memset(&temp, 0, sizeof(temp));
        switch (value) {
            case HDR_MODE:
                {
                    mHdrMode = HDR_MODE;
                    temp.hdr_enable= TRUE;
                    temp.mode = HDR_MODE;
                    temp.total_frames = 3;
                    temp.total_hal_frames = getNumOfSnapshots();
                    ALOGI("%s: setting HDR frames (%d)", __FUNCTION__, temp.total_hal_frames);
                    native_set_parms(MM_CAMERA_PARM_HDR, sizeof(exp_bracketing_t), (void *)&temp);
                }
                break;
            case EXP_BRACKETING_MODE:
                {
                    int numFrames = getNumOfSnapshots();
                    const char *str_val = params.get("capture-burst-exposures");
                    if ((str_val != NULL) && (strlen(str_val)>0)) {
                        ALOGI("%s: capture-burst-exposures %s", __FUNCTION__, str_val);

                        mHdrMode = EXP_BRACKETING_MODE;
                        temp.hdr_enable = FALSE;
                        temp.mode = EXP_BRACKETING_MODE;
                        temp.total_frames = (numFrames >  MAX_SNAPSHOT_BUFFERS -2) ? MAX_SNAPSHOT_BUFFERS -2 : numFrames;
                        temp.total_hal_frames = temp.total_frames;
                        strlcpy(temp.values, str_val, MAX_EXP_BRACKETING_LENGTH);
                        ALOGI("%s: setting Exposure Bracketing value of %s, frame (%d)", __FUNCTION__, temp.values, temp.total_hal_frames);
                        native_set_parms(MM_CAMERA_PARM_HDR, sizeof(exp_bracketing_t), (void *)&temp);
                    }
                    else {
                        /* Apps not set capture-burst-exposures, error case fall into bracketing off mode */
                        ALOGI("%s: capture-burst-exposures not set, back to HDR OFF mode", __FUNCTION__);
                        mHdrMode = HDR_BRACKETING_OFF;
                        temp.hdr_enable= FALSE;
                        temp.mode = HDR_BRACKETING_OFF;
                        native_set_parms(MM_CAMERA_PARM_HDR, sizeof(exp_bracketing_t), (void *)&temp);
                    }
                }
                break;
            case HDR_BRACKETING_OFF:
            default:
                {
                    mHdrMode = HDR_BRACKETING_OFF;
                    temp.hdr_enable= FALSE;
                    temp.mode = HDR_BRACKETING_OFF;
                    native_set_parms(MM_CAMERA_PARM_HDR, sizeof(exp_bracketing_t), (void *)&temp);
                }
                break;
        }

        /* save the value*/
        mParameters.set(CameraParameters::KEY_AE_BRACKET_HDR, str);
    }
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setCaptureBurstExp()
{
    char burst_exp[PROPERTY_VALUE_MAX];
    memset(burst_exp, 0, sizeof(burst_exp));
    property_get("persist.capture.burst.exposures", burst_exp, "");
    if (NULL != burst_exp)
      mParameters.set("capture-burst-exposures", burst_exp);
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setRedeyeReduction(const CameraParameters& params)
{
    if(supportsRedEyeReduction() == false) {
        ALOGE("Parameter Redeye Reduction is not supported for this sensor");
        return NO_ERROR;
    }

    const char *str = params.get(CameraParameters::KEY_REDEYE_REDUCTION);
    if (str != NULL) {
        int value = attr_lookup(redeye_reduction, sizeof(redeye_reduction) / sizeof(str_map), str);
        if (value != NOT_FOUND) {
            int temp = value;
            ALOGI("%s: setting Redeye Reduction value of %s", __FUNCTION__, str);
            mParameters.set(CameraParameters::KEY_REDEYE_REDUCTION, str);

            native_set_parms(MM_CAMERA_PARM_REDEYE_REDUCTION, sizeof(int), (void *)&temp);
            return NO_ERROR;
        }
    }
    ALOGE("Invalid Redeye Reduction value: %s", (str == NULL) ? "NULL" : str);
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setGpsLocation(const CameraParameters& params)
{
    const char *method = params.get(CameraParameters::KEY_GPS_PROCESSING_METHOD);
    if (method) {
        mParameters.set(CameraParameters::KEY_GPS_PROCESSING_METHOD, method);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_PROCESSING_METHOD);
    }

    const char *latitude = params.get(CameraParameters::KEY_GPS_LATITUDE);
    if (latitude) {
        ALOGE("latitude %s",latitude);
        mParameters.set(CameraParameters::KEY_GPS_LATITUDE, latitude);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_LATITUDE);
    }

    const char *latitudeRef = params.get(CameraParameters::KEY_GPS_LATITUDE_REF);
    if (latitudeRef) {
        mParameters.set(CameraParameters::KEY_GPS_LATITUDE_REF, latitudeRef);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_LATITUDE_REF);
    }

    const char *longitude = params.get(CameraParameters::KEY_GPS_LONGITUDE);
    if (longitude) {
        mParameters.set(CameraParameters::KEY_GPS_LONGITUDE, longitude);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_LONGITUDE);
    }

    const char *longitudeRef = params.get(CameraParameters::KEY_GPS_LONGITUDE_REF);
    if (longitudeRef) {
        mParameters.set(CameraParameters::KEY_GPS_LONGITUDE_REF, longitudeRef);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_LONGITUDE_REF);
    }

    const char *altitudeRef = params.get(CameraParameters::KEY_GPS_ALTITUDE_REF);
    if (altitudeRef) {
        mParameters.set(CameraParameters::KEY_GPS_ALTITUDE_REF, altitudeRef);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_ALTITUDE_REF);
    }

    const char *altitude = params.get(CameraParameters::KEY_GPS_ALTITUDE);
    if (altitude) {
        mParameters.set(CameraParameters::KEY_GPS_ALTITUDE, altitude);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_ALTITUDE);
    }

    const char *status = params.get(CameraParameters::KEY_GPS_STATUS);
    if (status) {
        mParameters.set(CameraParameters::KEY_GPS_STATUS, status);
    }

    const char *dateTime = params.get(CameraParameters::KEY_EXIF_DATETIME);
    if (dateTime) {
        mParameters.set(CameraParameters::KEY_EXIF_DATETIME, dateTime);
    }else {
         mParameters.remove(CameraParameters::KEY_EXIF_DATETIME);
    }

    const char *timestamp = params.get(CameraParameters::KEY_GPS_TIMESTAMP);
    if (timestamp) {
        mParameters.set(CameraParameters::KEY_GPS_TIMESTAMP, timestamp);
    }else {
         mParameters.remove(CameraParameters::KEY_GPS_TIMESTAMP);
    }
    ALOGE("setGpsLocation X");
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setRotation(const CameraParameters& params)
{
    status_t rc = NO_ERROR;
    int rotation = params.getInt(CameraParameters::KEY_ROTATION);
    if (rotation != NOT_FOUND) {
        if (rotation == 0 || rotation == 90 || rotation == 180
            || rotation == 270) {
          mParameters.set(CameraParameters::KEY_ROTATION, rotation);
          mRotation = rotation;
        } else {
            ALOGE("Invalid rotation value: %d", rotation);
            rc = BAD_VALUE;
        }
    }
    ALOGE("setRotation");
    return rc;
}

status_t QCameraHardwareInterface::setDenoise(const CameraParameters& params)
{
#if 0
    if(!mCfgControl.mm_camera_is_supported(MM_CAMERA_PARM_WAVELET_DENOISE)) {
        ALOGE("Wavelet Denoise is not supported for this sensor");
        return NO_ERROR;
    }
    const char *str = params.get(CameraParameters::KEY_DENOISE);
    if (str != NULL) {
        int value = attr_lookup(denoise,
        sizeof(denoise) / sizeof(str_map), str);
        if ((value != NOT_FOUND) &&  (mDenoiseValue != value)) {
        mDenoiseValue =  value;
        mParameters.set(CameraParameters::KEY_DENOISE, str);
        bool ret = native_set_parms(MM_CAMERA_PARM_WAVELET_DENOISE, sizeof(value),
                                               (void *)&value);
        return ret ? NO_ERROR : UNKNOWN_ERROR;
        }
        return NO_ERROR;
    }
    ALOGE("Invalid Denoise value: %s", (str == NULL) ? "NULL" : str);
#endif
    return BAD_VALUE;
}

status_t QCameraHardwareInterface::setOrientation(const CameraParameters& params)
{
    const char *str = params.get("orientation");

    if (str != NULL) {
        if (strcmp(str, "portrait") == 0 || strcmp(str, "landscape") == 0) {
            // Camera service needs this to decide if the preview frames and raw
            // pictures should be rotated.
            mParameters.set("orientation", str);
        } else {
            ALOGE("Invalid orientation value: %s", str);
            return BAD_VALUE;
        }
    }
    return NO_ERROR;
}

status_t QCameraHardwareInterface::setPictureFormat(const CameraParameters& params)
{
    const char * str = params.get(CameraParameters::KEY_PICTURE_FORMAT);

    if(str != NULL){
        int32_t value = attr_lookup(picture_formats,
                                    sizeof(picture_formats) / sizeof(str_map), str);
        if(value != NOT_FOUND){
            mParameters.set(CameraParameters::KEY_PICTURE_FORMAT, str);
        } else {
            ALOGE("Invalid Picture Format value: %s", str);
            return BAD_VALUE;
        }
    }
    return NO_ERROR;
}


status_t QCameraHardwareInterface::setRecordingHint(const CameraParameters& params)
{

  const char * str = params.get(CameraParameters::KEY_RECORDING_HINT);

  if(str != NULL){
      int32_t value = attr_lookup(recording_Hints,
                                  sizeof(recording_Hints) / sizeof(str_map), str);
      ALOGE("setRecordingHint %s",str);
      if(value != NOT_FOUND){
        mRecordingHint = value;
//        native_set_parms(MM_CAMERA_PARM_RECORDING_HINT, sizeof(value),
//                                               (void *)&value);
        if (value == TRUE) {
          native_set_parms(MM_CAMERA_PARM_CONTINUOUS_AF, sizeof(value),
                                               (void *)&value);
        }
        mParameters.set(CameraParameters::KEY_RECORDING_HINT, str);
      } else {
          ALOGE("Invalid Picture Format value: %s", str);
          setDISMode();
          setFullLiveshot();
          return BAD_VALUE;
      }
  }
  setDISMode();
  setFullLiveshot();
  return NO_ERROR;
}

status_t QCameraHardwareInterface::setDISMode() {

//  if(isLowPowerCamcorder())
//      mDisEnabled = 0; 

  uint32_t value = mRecordingHint && mDisEnabled;

  /* TODO Remove this workaround once the C2D limitation
   * (32 alignment on width) is fixed. */
  /* Start workaround */
  /*
  * in live effect case Dimension will be reversed.
  */
  if (mDimension.display_width == QCIF_WIDTH || mDimension.display_height == QCIF_WIDTH ||
      mDimension.display_width == D1_WIDTH || mDimension.display_height == D1_WIDTH) {
      value = 0;
  }
  /* End workaround */


  ALOGI("%s DIS is %s value = %d", __func__,
          value ? "Enabled" : "Disabled", value);
//  native_set_parms(MM_CAMERA_PARM_DIS_ENABLE, sizeof(value),
//                                               (void *)&value);

    video_dis_param_ctrl_t disCtrl;
    bool ret = true;
    ALOGV("mDisEnabled = %d", value);

    int video_frame_cbcroffset;
    video_frame_cbcroffset = PAD_TO_WORD(mDimension.video_width * mDimension.video_height);

    disCtrl.dis_enable = value;
    const char *str = mParameters.get(CameraParameters::KEY_VIDEO_HIGH_FRAME_RATE);
    if((str != NULL) && (strcmp(str, CameraParameters::VIDEO_HFR_OFF))) {
        ALOGI("%s: HFR is ON, setting DIS as OFF", __FUNCTION__);
        disCtrl.dis_enable = 0;
    }
    disCtrl.video_rec_width = mDimension.video_width;
    disCtrl.video_rec_height = mDimension.video_height;
    disCtrl.output_cbcr_offset = video_frame_cbcroffset;

    ret = native_set_parms( MM_CAMERA_PARM_VIDEO_DIS,
                       sizeof(disCtrl), &disCtrl);

  return NO_ERROR;
}

status_t QCameraHardwareInterface::setFullLiveshot()
{
  uint32_t value = mRecordingHint && mFullLiveshotEnabled;

  /* TODO Remove this workaround once the C2D limitation
   * (32 alignment on width) is fixed. */
  /* Start workaround */
  /*
  * in live effect case Dimension will be reversed.
  */
  if (mDimension.display_width == QCIF_WIDTH || mDimension.display_height == QCIF_WIDTH ||
      mDimension.display_width == D1_WIDTH || mDimension.display_height == D1_WIDTH) {
    value = 0;
  }
  /* End workaround */

  ALOGI("%s Full size liveshot %s value = %d", __func__,
          value ? "Enabled" : "Disabled", value);
  native_set_parms(MM_CAMERA_PARM_FULL_LIVESHOT, sizeof(value),
                                               (void *)&value);
  return NO_ERROR;
}


isp3a_af_mode_t QCameraHardwareInterface::getAutoFocusMode(
  const CameraParameters& params)
{

  isp3a_af_mode_t afMode = AF_MODE_MAX;
  const char * focusMode = params.get(CameraParameters::KEY_FOCUS_MODE);

  if (focusMode ) {
    afMode = (isp3a_af_mode_t)attr_lookup(focus_modes,
      sizeof(focus_modes) / sizeof(str_map),
      params.get(CameraParameters::KEY_FOCUS_MODE));
  }
  return afMode;
}

void QCameraHardwareInterface::getPictureSize(int *picture_width,
                                              int *picture_height) const
{
    mParameters.getPictureSize(picture_width, picture_height);
}

void QCameraHardwareInterface::getPreviewSize(int *preview_width,
                                              int *preview_height) const
{

    mParameters.getPreviewSize(preview_width, preview_height);
}

cam_format_t QCameraHardwareInterface::getPreviewFormat() const
{
  cam_format_t foramt = CAMERA_YUV_420_NV21;
    const char *str = mParameters.getPreviewFormat();
    int32_t value = attr_lookup(preview_formats,
                                sizeof(preview_formats)/sizeof(str_map),
                                str);

    if(value != NOT_FOUND) {
        int num = sizeof(preview_format_info_list)/sizeof(preview_format_info_t);
        int i;
        for (i = 0; i < num; i++) {
          if (preview_format_info_list[i].Hal_format == value) {
            foramt = preview_format_info_list[i].mm_cam_format;
            break;
          }
        }
    }

    return foramt;
}

cam_pad_format_t QCameraHardwareInterface::getPreviewPadding() const
{
  return mPreviewFormatInfo.padding;
}

int QCameraHardwareInterface::getJpegQuality() const
{
    return mParameters.getInt(CameraParameters::KEY_JPEG_QUALITY);
}

int QCameraHardwareInterface::getNumOfSnapshots(void) const
{
    char prop[PROPERTY_VALUE_MAX];
    memset(prop, 0, sizeof(prop));
    property_get("persist.camera.snapshot.number", prop, "0");
    ALOGI("%s: prop enable/disable = %d", __func__, atoi(prop));
    if (atoi(prop)) {
        ALOGE("%s: Reading maximum no of snapshots = %d"
             "from properties", __func__, atoi(prop));
        return atoi(prop);
    } else {
        return mParameters.getInt("num-snaps-per-shutter");
    }

}

int QCameraHardwareInterface::
getThumbSizesFromAspectRatio(uint32_t aspect_ratio,
                             int *picture_width,
                             int *picture_height)
{
    for(unsigned int i = 0; i < THUMBNAIL_SIZE_COUNT; i++ ){
        if(thumbnail_sizes[i].aspect_ratio == aspect_ratio)
        {
            *picture_width = thumbnail_sizes[i].width;
            *picture_height = thumbnail_sizes[i].height;
            return NO_ERROR;
        }
    }

    return BAD_VALUE;
}

bool QCameraHardwareInterface::isRawSnapshot()
{
  const char *format = mParameters.getPictureFormat();
    if( format!= NULL &&
       !strcmp(format, CameraParameters::PIXEL_FORMAT_RAW)){
        return true;
    }
    else{
        return false;
    }
}

status_t QCameraHardwareInterface::setPreviewSizeTable(void)
{
    status_t ret = NO_ERROR;
    mm_camera_dimension_t dim;
    struct camera_size_type* preview_size_table;
    int preview_table_size;
    int i = 0;
    char str[10] = {0};

    /* Initialize table with default values */
    preview_size_table = default_preview_sizes;
    preview_table_size = sizeof(default_preview_sizes)/
        sizeof(default_preview_sizes[0]);

    /* Get maximum preview size supported by sensor*/
    memset(&dim, 0, sizeof(mm_camera_dimension_t));
    ret = cam_config_get_parm(mCameraId,
                              MM_CAMERA_PARM_MAX_PREVIEW_SIZE, &dim);
    if (ret != NO_ERROR) {
        ALOGE("%s: Failure getting Max Preview Size supported by camera",
             __func__);
        goto end;
    }

    ALOGD("%s: Max Preview Sizes Supported: %d X %d", __func__,
         dim.width, dim.height);

    for (i = 0; i < preview_table_size; i++) {
        if ((preview_size_table->width <= dim.width) &&
            (preview_size_table->height <= dim.height)) {
            ALOGD("%s: Camera Preview Size Table "
                 "Max width: %d height %d table_size: %d",
                 __func__, preview_size_table->width,
                 preview_size_table->height, preview_table_size - i);
            break;
        }
        preview_size_table++;
    }
    //set preferred preview size to maximum preview size
    sprintf(str, "%dx%d", preview_size_table->width, preview_size_table->height);
    mParameters.set(CameraParameters::KEY_PREFERRED_PREVIEW_SIZE_FOR_VIDEO, str);
    ALOGD("KEY_PREFERRED_PREVIEW_SIZE_FOR_VIDEO = %s", str);

end:
    /* Save the table in global member*/
    mPreviewSizes = preview_size_table;
    mPreviewSizeCount = preview_table_size - i;

    return ret;
}

status_t QCameraHardwareInterface::setPictureSizeTable(void)
{
    status_t ret = NO_ERROR;
    mm_camera_dimension_t dim;
    struct camera_size_type* picture_size_table;
    int picture_table_size;
    int i = 0, count = 0;

    /* Initialize table with default values */
    picture_table_size = sizeof(default_picture_sizes)/
        sizeof(default_preview_sizes[0]);
    picture_size_table = default_picture_sizes;
    mPictureSizes =
        ( struct camera_size_type *)malloc(picture_table_size *
                                           sizeof(struct camera_size_type));
    if (mPictureSizes == NULL) {
        ALOGE("%s: Failre allocating memory to store picture size table",__func__);
        goto end;
    }

    /* Get maximum picture size supported by sensor*/
    memset(&dim, 0, sizeof(mm_camera_dimension_t));
    ret = cam_config_get_parm(mCameraId,
                              MM_CAMERA_PARM_MAX_PICTURE_SIZE, &dim);
    if (ret != NO_ERROR) {
        ALOGE("%s: Failure getting Max Picture Size supported by camera",
             __func__);
        ret = NO_MEMORY;
        free(mPictureSizes);
        mPictureSizes = NULL;
        goto end;
    }

    ALOGD("%s: Max Picture Sizes Supported: %d X %d", __func__,
         dim.width, dim.height);

    for (i = 0; i < picture_table_size; i++) {
        /* We'll store those dimensions whose width AND height
           are less than or equal to maximum supported */
        if ((picture_size_table->width <= dim.width) &&
            (picture_size_table->height <= dim.height)) {
            ALOGD("%s: Camera Picture Size Table "
                 "Max width: %d height %d table_size: %d",
                 __func__, picture_size_table->width,
                 picture_size_table->height, count+1);
            mPictureSizes[count].height = picture_size_table->height;
            mPictureSizes[count].width = picture_size_table->width;
            count++;
        }
        picture_size_table++;
    }
    mPictureSizeCount = count;

end:
     /* In case of error, we use default picture sizes */
     if (ret != NO_ERROR) {
        mPictureSizes = default_picture_sizes;
        mPictureSizeCount = picture_table_size;
    }
    return ret;
}

void QCameraHardwareInterface::freePictureTable(void)
{
    /* If we couldn't allocate memory to store picture table
       we use the picture table pointer to point to default
       picture table array. In that case we cannot free it.*/
    if ((mPictureSizes != default_picture_sizes) && mPictureSizes) {
        free(mPictureSizes);
    }
}

status_t QCameraHardwareInterface::setHistogram(int histogram_en)
{
    ALOGE("setHistogram: E");
    if(mStatsOn == histogram_en) {
        return NO_ERROR;
    }

    mSendData = histogram_en;
    mStatsOn = histogram_en;
    mCurrentHisto = -1;
    mStatSize = sizeof(uint32_t)* HISTOGRAM_STATS_SIZE;

    if (histogram_en == QCAMERA_PARM_ENABLE) {
        /*Currently the Ashmem is multiplying the buffer size with total number
        of buffers and page aligning. This causes a crash in JNI as each buffer
        individually expected to be page aligned  */
        int page_size_minus_1 = getpagesize() - 1;
        int statSize = sizeof (camera_preview_histogram_info );
        int32_t mAlignedStatSize = ((statSize + page_size_minus_1) & (~page_size_minus_1));
#if 0
        mStatHeap =
        new AshmemPool(mAlignedStatSize, 3, statSize, "stat");
        if (!mStatHeap->initialized()) {
            ALOGE("Stat Heap X failed ");
            mStatHeap.clear();
            mStatHeap = NULL;
            return UNKNOWN_ERROR;
        }
#endif
        for(int cnt = 0; cnt<3; cnt++) {
                mStatsMapped[cnt]=mGetMemory(-1, mStatSize, 1, mCallbackCookie);
                if(mStatsMapped[cnt] == NULL) {
                    ALOGE("Failed to get camera memory for stats heap index: %d", cnt);
                    return(-1);
                } else {
                   ALOGE("Received following info for stats mapped data:%p,handle:%p, size:%d,release:%p",
                   mStatsMapped[cnt]->data ,mStatsMapped[cnt]->handle, mStatsMapped[cnt]->size, mStatsMapped[cnt]->release);
                }
        }
    }
    ALOGV("Setting histogram = %d", histogram_en);
    native_set_parms(MM_CAMERA_PARM_HISTOGRAM, sizeof(int), &histogram_en);
    if(histogram_en == QCAMERA_PARM_DISABLE)
    {
        //release memory
        for(int i=0; i<3; i++){
            if(mStatsMapped[i] != NULL) {
                mStatsMapped[i]->release(mStatsMapped[i]);
            }
        }
    }
    return NO_ERROR;
}

/* mode: lookback mode -
   0: look back based on timestamp
   1: based on frame count.
   value: number of miliseconds or frame count*/
status_t QCameraHardwareInterface::setZSLLookBack(int mode, int value)
{
    if (value < 0) {
        ALOGE("%s: Undefined look back value!!!", __func__);
        return BAD_VALUE;
    }

    mZslLookBackMode = mode;
    mZslLookBackValue = value;

    return NO_ERROR;
}

void QCameraHardwareInterface::getZSLLookBack(int *mode, int *value)
{
    char prop[PROPERTY_VALUE_MAX];
    ALOGV("%s: BEGIN", __func__);

    memset(prop, 0, sizeof(prop));
    property_get("persist.camera.zsl.prop.enable", prop, "0");
    /* If we set this property, we'll read zsl look back values from set
       properties. Otherwise it should be the value passed by User. */
    if (atoi(prop)) {
        ALOGI("%s: Reading look-back mode from properties", __func__);
        memset(prop, 0, sizeof(prop));
        property_get("persist.camera.zsl.lb_mode", prop, "0");
        mZslLookBackMode = atoi(prop);

        memset(prop, 0, sizeof(prop));
        property_get("persist.camera.zsl.lb_value", prop, "0");
        mZslLookBackValue = atoi(prop);
    }
    *mode = mZslLookBackMode;
    *value = mZslLookBackValue;
    ALOGI("%s: ZSL Lookback mode: %d value: %d", __func__, *mode, *value);
}

void QCameraHardwareInterface::setZSLEmptyQueueFlag(bool value)
{
    ALOGI("%s: Setting ZSL Empty_Queue Flag to %d", __func__, value);
    mZslEmptyQueueFlag = value;
}

void QCameraHardwareInterface::getZSLEmptyQueueFlag(bool *flag)
{
    char value[PROPERTY_VALUE_MAX];
    ALOGV("%s: BEGIN", __func__);

    memset(value, 0, sizeof(value));
    property_get("persist.camera.zsl.prop.enable", value, "0");
    /* If we set this property, we'll read zsl look back values from set
       properties. Otherwise it should be the value passed by User */
    if (atoi(value)) {
        ALOGI("%s: Reading empty_queue flag from properties", __func__);
        memset(value, 0, sizeof(value));
        property_get("persist.camera.zsl.empty_queue", value, "0");
        mZslEmptyQueueFlag = (bool)atoi(value);
    }

    *flag = mZslEmptyQueueFlag;

    ALOGI("%s: ZSL Empty Queue Flag is set to %d", __func__, mZslEmptyQueueFlag);
}

int QCameraHardwareInterface::getZSLQueueDepth(void) const
{
    char prop[PROPERTY_VALUE_MAX];
    memset(prop, 0, sizeof(prop));
    property_get("persist.camera.zsl.queuedepth", prop, "2");
    ALOGI("%s: prop = %d", __func__, atoi(prop));
    return atoi(prop);
}

int QCameraHardwareInterface::getZSLBackLookCount(void) const
{
    char prop[PROPERTY_VALUE_MAX];
    memset(prop, 0, sizeof(prop));
    property_get("persist.camera.zsl.backlookcount", prop, "0");
    ALOGI("%s: prop = %d", __func__, atoi(prop));
    return atoi(prop);
}

//EXIF functions

void QCameraHardwareInterface::resetExifData()
{
    ALOGD("Clearing EXIF data");
    for(int i=0; i<MAX_EXIF_TABLE_ENTRIES; i++)
    {
        //clear all data
        memset(&mExifData[i], 0x00, sizeof(exif_tags_info_t));
    }
    mExifTableNumEntries = 0;
}

void QCameraHardwareInterface::addExifTag(exif_tag_id_t tagid, exif_tag_type_t type,
                        uint32_t count, uint8_t copy, void *data) {

    if(mExifTableNumEntries >= MAX_EXIF_TABLE_ENTRIES) {
        ALOGE("%s: Number of entries exceeded limit", __func__);
        return;
    }
    int index = mExifTableNumEntries;
    mExifData[index].tag_id = tagid;
    mExifData[index].tag_entry.type = type;
    mExifData[index].tag_entry.count = count;
    mExifData[index].tag_entry.copy = copy;
    if((type == EXIF_RATIONAL) && (count > 1))
        mExifData[index].tag_entry.data._rats = (rat_t *)data;
    if((type == EXIF_RATIONAL) && (count == 1))
        mExifData[index].tag_entry.data._rat = *(rat_t *)data;
    else if(type == EXIF_ASCII)
        mExifData[index].tag_entry.data._ascii = (char *)data;
    else if(type == EXIF_BYTE)
        mExifData[index].tag_entry.data._byte = *(uint8_t *)data;
    else if((type == EXIF_SHORT) && (count > 1))
        mExifData[index].tag_entry.data._shorts = (uint16_t *)data;
    else if((type == EXIF_SHORT) && (count == 1))
        mExifData[index].tag_entry.data._short = *(uint16_t *)data;

    if(type==EXIF_ASCII) ALOGE("addExifTag %x %s",tagid,(char *)data);
    // Increase number of entries
    mExifTableNumEntries++;
}

rat_t getRational(int num, int denom)
{
    rat_t temp = {num, denom};
    return temp;
}

void QCameraHardwareInterface::initExifData(){

    if (mExifValues.dateTime) {
        addExifTag(EXIFTAGID_EXIF_DATE_TIME_ORIGINAL, EXIF_ASCII,
                  20, 1, (void *)mExifValues.dateTime);
        addExifTag(EXIFTAGID_EXIF_DATE_TIME_CREATED, EXIF_ASCII,
                  20, 1, (void *)mExifValues.dateTime);
    }
    addExifTag(EXIFTAGID_FOCAL_LENGTH, EXIF_RATIONAL, 1, 1, (void *)&(mExifValues.focalLength));
    addExifTag(EXIFTAGID_ISO_SPEED_RATING,EXIF_SHORT,1,1,(void *)&(mExifValues.isoSpeed));

    if(mExifValues.mGpsProcess) {
        addExifTag(EXIFTAGID_GPS_PROCESSINGMETHOD, EXIF_ASCII,
           EXIF_ASCII_PREFIX_SIZE + strlen(mExifValues.gpsProcessingMethod + EXIF_ASCII_PREFIX_SIZE) + 1,
           1, (void *)mExifValues.gpsProcessingMethod);
    }

    if(mExifValues.mLatitude) {
        addExifTag(EXIFTAGID_GPS_LATITUDE, EXIF_RATIONAL, 3, 1, (void *)mExifValues.latitude);

        if(mExifValues.latRef) {
            addExifTag(EXIFTAGID_GPS_LATITUDE_REF, EXIF_ASCII, 2,
                                    1, (void *)mExifValues.latRef);
        }
    }

    if(mExifValues.mLongitude) {
        addExifTag(EXIFTAGID_GPS_LONGITUDE, EXIF_RATIONAL, 3, 1, (void *)mExifValues.longitude);

        if(mExifValues.lonRef) {
            addExifTag(EXIFTAGID_GPS_LONGITUDE_REF, EXIF_ASCII, 2,
                                1, (void *)mExifValues.lonRef);
        }
    }

    if(mExifValues.mAltitude) {
        addExifTag(EXIFTAGID_GPS_ALTITUDE, EXIF_RATIONAL, 1,
                    1, (void *)&(mExifValues.altitude));

        addExifTag(EXIFTAGID_GPS_ALTITUDE_REF, EXIF_BYTE, 1, 1, (void *)&mExifValues.mAltitude_ref);
    }

    if(mExifValues.mTimeStamp) {
        time_t unixTime;
        struct tm *UTCTimestamp;

        unixTime = (time_t)mExifValues.mGPSTimestamp;
        UTCTimestamp = gmtime(&unixTime);

        strftime(mExifValues.gpsDateStamp, sizeof(mExifValues.gpsDateStamp), "%Y:%m:%d", UTCTimestamp);
        addExifTag(EXIFTAGID_GPS_DATESTAMP, EXIF_ASCII,
                          strlen(mExifValues.gpsDateStamp)+1 , 1, (void *)mExifValues.gpsDateStamp);

        mExifValues.gpsTimeStamp[0] = getRational(UTCTimestamp->tm_hour, 1);
        mExifValues.gpsTimeStamp[1] = getRational(UTCTimestamp->tm_min, 1);
        mExifValues.gpsTimeStamp[2] = getRational(UTCTimestamp->tm_sec, 1);

        addExifTag(EXIFTAGID_GPS_TIMESTAMP, EXIF_RATIONAL,
                  3, 1, (void *)mExifValues.gpsTimeStamp);
        ALOGE("EXIFTAGID_GPS_TIMESTAMP set");
    }

}

//Add all exif tags in this function
void QCameraHardwareInterface::setExifTags()
{
    const char *str;

    //set TimeStamp
    time_t rawtime;
    struct tm * timeinfo;

    str = mParameters.get(CameraParameters::KEY_EXIF_DATETIME);
    if(str != NULL) {
      strncpy(mExifValues.dateTime, str, 19);
      mExifValues.dateTime[19] = '\0';
    } else {
      time ( &rawtime );
      timeinfo = localtime ( &rawtime );
      sprintf(mExifValues.dateTime,"%04d:%02d:%02d %02d:%02d:%02d",timeinfo->tm_year+1900, timeinfo->tm_mon+1,
            timeinfo->tm_mday, timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec);
    }
    ALOGE("setExifTags %s",mExifValues.dateTime);

    //Set focal length
    int focalLengthValue = (int) (mParameters.getFloat(
                CameraParameters::KEY_FOCAL_LENGTH) * FOCAL_LENGTH_DECIMAL_PRECISION);

    mExifValues.focalLength = getRational(focalLengthValue, FOCAL_LENGTH_DECIMAL_PRECISION);

    //Set ISO Speed
    mExifValues.isoSpeed = getISOSpeedValue();

    //set gps tags
    setExifTagsGPS();
}

void QCameraHardwareInterface::setExifTagsGPS()
{
    const char *str = NULL;

    //Set GPS processing method
    str = mParameters.get(CameraParameters::KEY_GPS_PROCESSING_METHOD);
    if(str != NULL) {
       memcpy(mExifValues.gpsProcessingMethod, ExifAsciiPrefix, EXIF_ASCII_PREFIX_SIZE);
       strncpy(mExifValues.gpsProcessingMethod + EXIF_ASCII_PREFIX_SIZE, str,
           GPS_PROCESSING_METHOD_SIZE - 1);
       mExifValues.gpsProcessingMethod[EXIF_ASCII_PREFIX_SIZE + GPS_PROCESSING_METHOD_SIZE-1] = '\0';
       ALOGE("EXIFTAGID_GPS_PROCESSINGMETHOD = %s %s", mExifValues.gpsProcessingMethod,
                                                    mExifValues.gpsProcessingMethod+8);
       mExifValues.mGpsProcess  = true;
    }else{
        mExifValues.mGpsProcess = false;
    }
    str = NULL;

    //Set Latitude
    str = mParameters.get(CameraParameters::KEY_GPS_LATITUDE);
    if(str != NULL) {
        parseGPSCoordinate(str, mExifValues.latitude);
        ALOGE("EXIFTAGID_GPS_LATITUDE = %s", str);

        //set Latitude Ref
        float latitudeValue = mParameters.getFloat(CameraParameters::KEY_GPS_LATITUDE);
        if(latitudeValue < 0.0f) {
            mExifValues.latRef[0] = 'S';
        } else {
            mExifValues.latRef[0] = 'N';
        }
        mExifValues.latRef[1] = '\0';
        mExifValues.mLatitude = true;
        mParameters.set(CameraParameters::KEY_GPS_LATITUDE_REF,mExifValues.latRef);
        ALOGE("EXIFTAGID_GPS_LATITUDE_REF = %s", mExifValues.latRef);
    }else{
        mExifValues.mLatitude = false;
    }

    //set Longitude
    str = NULL;
    str = mParameters.get(CameraParameters::KEY_GPS_LONGITUDE);
    if(str != NULL) {
        parseGPSCoordinate(str, mExifValues.longitude);
        ALOGE("EXIFTAGID_GPS_LONGITUDE = %s", str);

        //set Longitude Ref
        float longitudeValue = mParameters.getFloat(CameraParameters::KEY_GPS_LONGITUDE);
        if(longitudeValue < 0.0f) {
            mExifValues.lonRef[0] = 'W';
        } else {
            mExifValues.lonRef[0] = 'E';
        }
        mExifValues.lonRef[1] = '\0';
        mExifValues.mLongitude = true;
        ALOGE("EXIFTAGID_GPS_LONGITUDE_REF = %s", mExifValues.lonRef);
        mParameters.set(CameraParameters::KEY_GPS_LONGITUDE_REF, mExifValues.lonRef);
    }else{
        mExifValues.mLongitude = false;
    }

    //set Altitude
    str = mParameters.get(CameraParameters::KEY_GPS_ALTITUDE);
    if(str != NULL) {
        double value = atof(str);
        mExifValues.mAltitude_ref = 0;
        if(value < 0){
            mExifValues.mAltitude_ref = 1;
            value = -value;
        }
        mExifValues.altitude = getRational(value*1000, 1000);
        mExifValues.mAltitude = true;
        //set AltitudeRef
        mParameters.set(CameraParameters::KEY_GPS_ALTITUDE_REF, mExifValues.mAltitude_ref);
        ALOGE("EXIFTAGID_GPS_ALTITUDE = %f", value);
    }else{
        mExifValues.mAltitude = false;
    }

    //set Gps TimeStamp
    str = NULL;
    str = mParameters.get(CameraParameters::KEY_GPS_TIMESTAMP);
    if(str != NULL) {
      mExifValues.mTimeStamp = true;
      mExifValues.mGPSTimestamp = atol(str);
    }else{
         mExifValues.mTimeStamp = false;
    }
}

//latlonString is string formatted coordinate
//coord is rat_t[3]
void QCameraHardwareInterface::parseGPSCoordinate(const char *latlonString, rat_t* coord)
{
    if(coord == NULL) {
        ALOGE("%s: error, invalid argument coord == NULL", __func__);
        return;
    }
    float degF = fabs(atof(latlonString));
    float minF = (degF- (int) degF) * 60;
    float secF = (minF - (int) minF) * 60;

    coord[0] = getRational((int) degF, 1);
    coord[1] = getRational((int) minF, 1);
    coord[2] = getRational((int) (secF * 10000), 10000);
}
}; /*namespace android */
