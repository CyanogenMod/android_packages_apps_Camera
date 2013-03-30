/*
** Copyright (c) 2011 Code Aurora Forum. All rights reserved.
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

//#define LOG_NDEBUG 0
#define LOG_NIDEBUG 0
#define LOG_TAG "QCameraHAL"
#include <utils/Log.h>
#include <utils/threads.h>
#include <fcntl.h>
#include <sys/mman.h>


/* include QCamera Hardware Interface Header*/
#include "QCameraHAL.h"

int HAL_numOfCameras = 0;
camera_info_t HAL_cameraInfo[MSM_MAX_CAMERA_SENSORS];
mm_camera_t * HAL_camerahandle[MSM_MAX_CAMERA_SENSORS];
int HAL_currentCameraMode;

namespace android {
/* HAL function implementation goes here*/

/**
 * The functions need to be provided by the camera HAL.
 *
 * If getNumberOfCameras() returns N, the valid cameraId for getCameraInfo()
 * and openCameraHardware() is 0 to N-1.
 */
extern "C" int HAL_getNumberOfCameras()
{
    /* try to query every time we get the call!*/
    uint8_t num_camera = 0;
    mm_camera_t * handle_base = 0;
    ALOGV("%s: E", __func__);

    handle_base= mm_camera_query(&num_camera);

    if (!handle_base) {
        HAL_numOfCameras = 0;
    }
    else
    {
        camera_info_t* p_camera_info = 0;
        HAL_numOfCameras=num_camera;

        ALOGI("Handle base =0x%p",handle_base);
        ALOGI("getCameraInfo: numOfCameras = %d", HAL_numOfCameras);
        for(int i = 0; i < HAL_numOfCameras; i++) {
            ALOGI("Handle [%d]=0x%p",i,handle_base+i);
            HAL_camerahandle[i]=handle_base + i;
            p_camera_info = &(HAL_camerahandle[i]->camera_info);
            if (p_camera_info) {
                ALOGI("Camera sensor %d info:", i);
                ALOGI("camera_id: %d", p_camera_info->camera_id);
                ALOGI("modes_supported: %x", p_camera_info->modes_supported);
                ALOGI("position: %d", p_camera_info->position);
                ALOGI("sensor_mount_angle: %d", p_camera_info->sensor_mount_angle);
            }
        }
    }

    ALOGV("%s: X", __func__);

    return HAL_numOfCameras;
}

extern "C" int HAL_isIn3DMode()
{
    return HAL_currentCameraMode == CAMERA_MODE_3D;
}

extern "C" void HAL_getCameraInfo(int cameraId, struct CameraInfo* cameraInfo)
{
    mm_camera_t *mm_camer_obj = 0;
    ALOGV("%s: E", __func__);

    if (!HAL_numOfCameras || HAL_numOfCameras < cameraId || !cameraInfo)
        return;
    else
        mm_camer_obj = HAL_camerahandle[cameraId];

    if (!mm_camer_obj)
        return;
    else {
        cameraInfo->facing =
            (FRONT_CAMERA == mm_camer_obj->camera_info.position)?
            CAMERA_FACING_FRONT : CAMERA_FACING_BACK;

        cameraInfo->orientation = mm_camer_obj->camera_info.sensor_mount_angle;
#if 0
        // TODO: fix me
        /* We always supprot ZSL in our stack*/
        cameraInfo->mode = CAMERA_SUPPORT_MODE_ZSL;
        if (mm_camer_obj->camera_info.modes_supported & CAMERA_MODE_2D) {
            cameraInfo->mode |= CAMERA_SUPPORT_MODE_2D;
        }
        if (mm_camer_obj->camera_info.modes_supported & CAMERA_MODE_3D) {
            cameraInfo->mode |= CAMERA_SUPPORT_MODE_3D;
        }
#endif
    }
   ALOGV("%s: X", __func__);
   return;
}

/* HAL should return NULL if it fails to open camera hardware. */
extern "C" void * HAL_openCameraHardware(int cameraId, int mode)
{
    ALOGV("%s: E", __func__);
    if (!HAL_numOfCameras || HAL_numOfCameras < cameraId ||cameraId < 0) {
      return NULL;
    }
    return QCameraHAL_openCameraHardware(cameraId, mode);
}


}; // namespace android
