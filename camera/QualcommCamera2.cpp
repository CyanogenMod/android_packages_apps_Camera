/* Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
/*#error uncomment this for compiler test!*/

//#define LOG_NDEBUG 0
#define LOG_NIDEBUG 0
#define LOG_TAG "QualcommCamera"
#include <utils/Log.h>
#include <utils/threads.h>
#include <fcntl.h>
#include <sys/mman.h>

#include "QCameraHAL.h"
/* include QCamera Hardware Interface Header*/
#include "QualcommCamera2.h"
//#include "QualcommCameraHardware.h"
//#include <camera/CameraHardwareInterface.h>

extern "C" {
#include <sys/time.h>
}

/* HAL function implementation goes here*/

/**
 * The functions need to be provided by the camera HAL.
 *
 * If getNumberOfCameras() returns N, the valid cameraId for getCameraInfo()
 * and openCameraHardware() is 0 to N-1.
 */

static hw_module_methods_t camera_module_methods = {
    open: camera_device_open,
};

static hw_module_t camera_common  = {
  tag: HARDWARE_MODULE_TAG,
  version_major: 0,
  version_minor: 01,
  id: CAMERA_HARDWARE_MODULE_ID,
  name: "Qcamera",
  author:"Qcom",
  methods: &camera_module_methods,
  dso: NULL,
  reserved:  {0},
};
camera_module_t HAL_MODULE_INFO_SYM = {
  common: camera_common,
  get_number_of_cameras: get_number_of_cameras,
  get_camera_info: get_camera_info,
};

camera_device_ops_t camera_ops = {
  set_preview_window:         android::set_preview_window,
  set_callbacks:              android::set_CallBacks,
  enable_msg_type:            android::enable_msg_type,
  disable_msg_type:           android::disable_msg_type,
  msg_type_enabled:           android::msg_type_enabled,

  start_preview:              android::start_preview,
  stop_preview:               android::stop_preview,
  preview_enabled:            android::preview_enabled,
  store_meta_data_in_buffers: android::store_meta_data_in_buffers,

  start_recording:            android::start_recording,
  stop_recording:             android::stop_recording,
  recording_enabled:          android::recording_enabled,
  release_recording_frame:    android::release_recording_frame,

  auto_focus:                 android::auto_focus,
  cancel_auto_focus:          android::cancel_auto_focus,

  take_picture:               android::take_picture,
  cancel_picture:             android::cancel_picture,

  set_parameters:             android::set_parameters,
  get_parameters:             android::get_parameters,
  put_parameters:             android::put_parameters,
  send_command:               android::send_command,

#ifdef USE_GET_VIDEO_BUFFER
  get_number_of_video_buffers: NULL,
  get_video_buffer:            NULL,
#endif

  release:                    android::release,
  dump:                       android::dump,
};

namespace android {

typedef struct {
  camera_device hw_dev;
  //sp<CameraHardwareInterface> hardware;
  QCameraHardwareInterface *hardware;
  int camera_released;
  int cameraId;
  //CameraParameters parameters;
} camera_hardware_t;

typedef struct {
  camera_memory_t mem;
  int32_t msgType;
  sp<IMemory> dataPtr;
  void* user;
  unsigned int index;
} q_cam_memory_t;

QCameraHardwareInterface *util_get_Hal_obj( struct camera_device * device)
{
    QCameraHardwareInterface *hardware = NULL;
    if(device && device->priv){
        camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
        hardware = camHal->hardware;
    }
    return hardware;
}

#if 0 //mzhu
CameraParameters* util_get_HAL_parameter( struct camera_device * device)
{
    CameraParameters *param = NULL;
    if(device && device->priv){
        camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
        param = &(camHal->parameters);
    }
    return param;
}
#endif //mzhu

extern "C" int get_number_of_cameras()
{
    /* try to query every time we get the call!*/

    ALOGE("Q%s: E", __func__);
    return android::HAL_getNumberOfCameras( );
}

extern "C" int get_camera_info(int camera_id, struct camera_info *info)
{
    int rc = -1;
    ALOGE("Q%s: E", __func__);
    if(info) {
        struct CameraInfo camInfo;
        memset(&camInfo, -1, sizeof (struct CameraInfo));
        android::HAL_getCameraInfo(camera_id, &camInfo);
        if (camInfo.facing >= 0) {
            rc = 0;
            info->facing = camInfo.facing;
            info->orientation = camInfo.orientation;
        }
    }
    ALOGV("Q%s: X", __func__);
    return rc;
}


/* HAL should return NULL if it fails to open camera hardware. */
extern "C" int  camera_device_open(
  const struct hw_module_t* module, const char* id,
          struct hw_device_t** hw_device)
{
    int rc = -1;
	int mode = 0; // TODO: need to add 3d/2d mode, etc
    camera_device *device = NULL;
    if(module && id && hw_device) {
        int cameraId = atoi(id);

        if (!strcmp(module->name, camera_common.name)) {
            camera_hardware_t *camHal =
                (camera_hardware_t *) malloc(sizeof (camera_hardware_t));
            if(!camHal) {
                *hw_device = NULL;
				    ALOGE("%s:  end in no mem", __func__);
				    return rc;
		    }
            /* we have the camera_hardware obj malloced */
            memset(camHal, 0, sizeof (camera_hardware_t));
            camHal->hardware = new QCameraHardwareInterface(cameraId, mode); //HAL_openCameraHardware(cameraId);
            if (camHal->hardware && camHal->hardware->isCameraReady()) {
				camHal->cameraId = cameraId;
		        device = &camHal->hw_dev;
                device->common.close = close_camera_device;
                device->ops = &camera_ops;
                device->priv = (void *)camHal;
                rc =  0;
            } else {
                free(camHal);
                device = NULL;
            }
        }
    }
	/* pass actual hw_device ptr to framework. This amkes that we actally be use memberof() macro */
    *hw_device = (hw_device_t*)&device->common;
    ALOGE("%s:  end rc %d", __func__, rc);
    return rc;
}

extern "C"  int close_camera_device( hw_device_t *hw_dev)
{
    ALOGE("Q%s: device =%p E", __func__, hw_dev);
    int rc =  -1;
    camera_device_t *device = (camera_device_t *)hw_dev;

    if(device) {
        camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
        if(camHal ) {
            QCameraHardwareInterface *hardware = util_get_Hal_obj( device);
            if(!camHal->camera_released) {
                if(hardware != NULL) {
                    hardware->release( );
                }
            } else {
                if(hardware != NULL)
                  delete hardware;
            }
            free(camHal);
        }
        rc = 0;
    }
    return rc;
}


int set_preview_window(struct camera_device * device,
        struct preview_stream_ops *window)
{
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);

    if(hardware != NULL) {
        rc = hardware->setPreviewWindow(window);
    }
    return rc;
}

void set_CallBacks(struct camera_device * device,
        camera_notify_callback notify_cb,
        camera_data_callback data_cb,
        camera_data_timestamp_callback data_cb_timestamp,
        camera_request_memory get_memory,
        void *user)
{
    ALOGE("Q%s: E", __func__);
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        hardware->setCallbacks(notify_cb,data_cb, data_cb_timestamp, get_memory, user);
    }
}

void enable_msg_type(struct camera_device * device, int32_t msg_type)
{
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        hardware->enableMsgType(msg_type);
    }
}

void disable_msg_type(struct camera_device * device, int32_t msg_type)
{
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    ALOGE("Q%s: E %d", __func__,msg_type);
    if(hardware != NULL){
        hardware->disableMsgType(msg_type);
    }
}

int msg_type_enabled(struct camera_device * device, int32_t msg_type)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->msgTypeEnabled(msg_type);
    }
    return rc;
}

int start_preview(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->startPreview( );
    }
    ALOGE("Q%s: X", __func__);
    return rc;
}

void stop_preview(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        hardware->stopPreview( );
    }
}

int preview_enabled(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->previewEnabled( );
    }
    return rc;
}

int store_meta_data_in_buffers(struct camera_device * device, int enable)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
      rc = hardware->storeMetaDataInBuffers(enable);
    }
    return rc;
}

int start_recording(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->startRecording( );
    }
    return rc;
}

void stop_recording(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        hardware->stopRecording( );
    }
}

int recording_enabled(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->recordingEnabled( );
    }
    return rc;
}

void release_recording_frame(struct camera_device * device,
                const void *opaque)
{
    ALOGV("Q%s: E", __func__);
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        hardware->releaseRecordingFrame(opaque);
    }
}

int auto_focus(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->autoFocus( );
    }
    return rc;
}

int cancel_auto_focus(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->cancelAutoFocus( );
    }
    return rc;
}

int take_picture(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->takePicture( );
    }
    return rc;
}

int cancel_picture(struct camera_device * device)

{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->cancelPicture( );
    }
    return rc;
}

int set_parameters(struct camera_device * device, const char *parms)

{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL && parms){
        //CameraParameters param;// = util_get_HAL_parameter(device);
        //String8 str = String8(parms);

        //param.unflatten(str);
        rc = hardware->setParameters(parms);
        //rc = 0;
    }
    ALOGE("Q%s: X", __func__);
    return rc;
}

char* get_parameters(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
		char *parms = NULL;
        hardware->getParameters(&parms);
		return parms;
    }
    return NULL;
}

void put_parameters(struct camera_device * device, char *parm)

{
    ALOGE("Q%s: E", __func__);
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        if(hardware != NULL){
            hardware->putParameters(parm );
        }
    }
}

int send_command(struct camera_device * device,
            int32_t cmd, int32_t arg1, int32_t arg2)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->sendCommand( cmd, arg1, arg2);
    }
    return rc;
}

void release(struct camera_device * device)
{
    ALOGE("Q%s: E", __func__);
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
        hardware->release( );
        camHal->camera_released = true;
    }
}

int dump(struct camera_device * device, int fd)
{
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    QCameraHardwareInterface *hardware = util_get_Hal_obj(device);
    if(hardware != NULL){
        rc = hardware->dump( fd );
      //rc = 0;
    }
    return rc;
}

}; // namespace android
