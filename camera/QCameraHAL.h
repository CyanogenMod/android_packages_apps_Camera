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

#ifndef ANDROID_HARDWARE_QCAMERA_HAL_H
#define ANDROID_HARDWARE_QCAMERA_HAL_H


#include "QCameraHWI.h"

extern "C" {
#include <mm_camera_interface2.h>
}
namespace android {

/* HAL should return NULL if it fails to open camera hardware. */
extern "C" void *
       QCameraHAL_openCameraHardware(int  cameraId, int mode);
extern "C" int HAL_getNumberOfCameras();
extern "C" void HAL_getCameraInfo(int cameraId, struct CameraInfo* cameraInfo);

}; // namespace android

#endif

