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
#define LOG_TAG "QualcommCamera"
#include <utils/Log.h>
#include <utils/threads.h>
#include <fcntl.h>
#include <sys/mman.h>

/* include QCamera Hardware Interface Header*/
#include "QualcommCamera.h"
#include "QualcommCameraHardware.h"
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
  //reserved[0]:  0,
};

camera_module_t HAL_MODULE_INFO_SYM = {
  common: camera_common,
  get_number_of_cameras: get_number_of_cameras,
  get_camera_info: get_camera_info,
};

camera_device_ops_t camera_ops = {
  set_preview_window: android::set_preview_window,
  set_callbacks:      android::set_callbacks,
  enable_msg_type:    android::enable_msg_type,
  disable_msg_type:   android::disable_msg_type,
  msg_type_enabled:   android::msg_type_enabled,

  start_preview:      android::start_preview,
  stop_preview:       android::stop_preview,
  preview_enabled:    android::preview_enabled,
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

  release:                    android::release,
  dump:                       android::dump,
};

namespace android {

typedef struct {
  QualcommCameraHardware *hardware;
  int camera_released;
  CameraParameters parameters;
  #if 1
  camera_notify_callback notify_cb;
  camera_data_callback data_cb;
  camera_data_timestamp_callback data_cb_timestamp;
  camera_request_memory get_memory;
  void *user_data;
  #endif
} camera_hardware_t;

typedef struct {
  camera_memory_t mem;
  int32_t msgType;
  sp<IMemory> dataPtr;
  void* user;
  unsigned int index;
} q_cam_memory_t;


static void camera_release_memory(struct camera_memory *mem)
{
}

void cam_notify_callback(int32_t msgType,
                                int32_t ext1,
                                int32_t ext2,
                                void* user)
{
  ALOGE("Q%s: E", __func__);
  camera_device * device = (camera_device *)user;
  if(device) {
    camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
    if(camHal) {
      camera_notify_callback notify_cb = camHal->notify_cb;
      void *user_data = camHal->user_data;
      if(notify_cb) {
        notify_cb(msgType, ext1, ext2, user_data);
      }
    }
  }
}

camera_memory_t* get_mem(int fd,size_t buf_size,
                                unsigned int num_bufs,
                                void *user)
{
  ALOGE("Q%s: E", __func__);
  camera_device * device = (camera_device *)user;
  if(device) {
    camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
    if(camHal) {
      camera_request_memory getmem_cb = camHal->get_memory;
      void *user_data = camHal->user_data;
      if(getmem_cb) {
        return getmem_cb(fd, buf_size, num_bufs, user_data);
      }
    }
  }
  return NULL;
}
#if 0
void native_send_data_callback(int32_t msgType,
                              camera_memory_t * framebuffer,
                              void* user)
{
  ALOGE("Q%s: E", __func__);
  static unsigned int counter = 0;
#if 0
  camera_device * device = (camera_device *)user;
  if(device) {
    camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
    if(camHal) {
      camera_data_callback data_cb = camHal->data_cb;
      void *user_data = camHal->user_data;
      if(data_cb) {
        q_cam_memory_t *qmem = (q_cam_memory_t *)malloc(sizeof(q_cam_memory_t));
        if (qmem) {
          qmem->dataPtr = dataPtr;
          qmem->mem.data = (void *)((int)dataPtr->pointer() + dataPtr->offset());
          qmem->mem.handle = NULL; //(void *)dataPtr->getHeapID();
          qmem->mem.size = dataPtr->size( );
          qmem->mem.release = camera_release_memory;
          qmem->msgType = msgType;
          qmem->index = counter;
#endif
          data_cb(msgType, framebuffer, counter, NULL, user);
          counter++;
#if 0
        } else {
          ALOGE("%s: out of memory", __func__);
        }
#endif
//      }
//    }
//  }
}
#endif

static void cam_data_callback(int32_t msgType,
                              const sp<IMemory>& dataPtr,
                              void* user)
{
  ALOGE("Q%s: E", __func__);
  static unsigned int counter = 0;
  camera_device * device = (camera_device *)user;
  if(device) {
    camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
    if(camHal) {
      camera_data_callback data_cb = camHal->data_cb;
      void *user_data = camHal->user_data;
      if(data_cb) {
        q_cam_memory_t *qmem = (q_cam_memory_t *)malloc(sizeof(q_cam_memory_t));
        if (qmem) {
          qmem->dataPtr = dataPtr;
          qmem->mem.data = (void *)((int)dataPtr->pointer() + dataPtr->offset());
          qmem->mem.handle = NULL; //(void *)dataPtr->getHeapID();
          qmem->mem.size = dataPtr->size( );
          qmem->mem.release = camera_release_memory;
          qmem->msgType = msgType;
          qmem->index = counter;
          counter++;
          data_cb(msgType, (camera_memory_t *)qmem, counter, NULL, user_data);
        } else {
          ALOGE("%s: out of memory", __func__);
        }
      }
    }
  }
}

static void cam_data_callback_timestamp(nsecs_t timestamp,
                                        int32_t msgType,
                                        const sp<IMemory>& dataPtr,
                                        void* user)
{
  ALOGE("Q%s: E", __func__);

  static unsigned int counter = 0;
  camera_device * device = (camera_device *)user;
  if(device) {
    camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
    if(camHal) {
      camera_data_timestamp_callback data_cb_timestamp = camHal->data_cb_timestamp;
      void *user_data = camHal->user_data;
      if(data_cb_timestamp) {
        q_cam_memory_t *qmem = (q_cam_memory_t *)malloc(sizeof(q_cam_memory_t));
        if (qmem) {
          qmem->dataPtr = dataPtr;
          qmem->mem.data = (void *)((int)dataPtr->pointer() + dataPtr->offset());
          qmem->mem.handle = NULL; //(void *)dataPtr->getHeapID();
          qmem->mem.size = dataPtr->size( );
          qmem->mem.release = camera_release_memory;
          qmem->msgType = msgType;
          qmem->index = counter;
          counter++;
          data_cb_timestamp(timestamp, msgType, (camera_memory_t *)qmem, counter, user_data);
        } else {
          ALOGE("%s: out of memory", __func__);
        }
      }
    }
  }
}

QualcommCameraHardware * util_get_Hal_obj( struct camera_device * device)
{
  QualcommCameraHardware* hardware = NULL;
  if(device && device->priv){
      camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
      hardware = camHal->hardware;
  }
  return hardware;
}
void close_Hal_obj( struct camera_device * device)
{
  ALOGI("%s: E", __func__);
  QualcommCameraHardware* hardware = NULL;
  if(device && device->priv){
      camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
      ALOGI("%s: clear hw", __func__);
      hardware = camHal->hardware;
      delete hardware;
  }
  ALOGI("%s: X", __func__);
}


CameraParameters* util_get_HAL_parameter( struct camera_device * device)
{
  CameraParameters *param = NULL;
  if(device && device->priv){
      camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
      param = &(camHal->parameters);
  }
  return param;
}


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
    HAL_getCameraInfo(camera_id, &camInfo);
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
    ALOGE("Q%s: E", __func__);
    int rc = -1;
    camera_device *device = NULL;
    if(module && id && hw_device) {
      int cameraId = atoi(id);

      if (!strcmp(module->name, camera_common.name)) {
        device =
          (camera_device *)malloc(sizeof (struct camera_device));
        if(device) {
          camera_hardware_t *camHal =
            (camera_hardware_t *) malloc(sizeof (camera_hardware_t));
          if(camHal) {
            memset(camHal, 0, sizeof (camera_hardware_t));
            camHal->hardware = HAL_openCameraHardware(cameraId);
            if (camHal->hardware != NULL) {
              /*To Do: populate camHal*/
              device->common.close = close_camera_device;
              device->ops = &camera_ops;
              device->priv = (void *)camHal;
              rc =  0;
            } else {
              free(camHal);
              free (device);
             device = NULL;
            }
          } else {
            free (device);
            device = NULL;
          }
        }
      }
    }
    *hw_device = (hw_device_t*)device;
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
      //if(!camHal->camera_released) {
         QualcommCameraHardware* hardware = util_get_Hal_obj( device);
         if(hardware != NULL) {
           if(camHal->camera_released != true)
           hardware->release( );
           //hardware.clear( );

         }
      //}
      close_Hal_obj(device);
      free(device->priv);
      device->priv = NULL;
    }
    free(device);
    rc = 0;
  }
  return rc;
}


int set_preview_window(struct camera_device * device,
        struct preview_stream_ops *window)
{
  ALOGE("Q%s: E window = %p", __func__, window);
  int rc = -1;
  QualcommCameraHardware *hardware = util_get_Hal_obj(device);
  if(hardware != NULL) {
   rc = hardware->set_PreviewWindow((void *)window);
  }
  return rc;
}

void set_callbacks(struct camera_device * device,
        camera_notify_callback notify_cb,
        camera_data_callback data_cb,
        camera_data_timestamp_callback data_cb_timestamp,
        camera_request_memory get_memory,
        void *user)
{
  ALOGE("Q%s: E", __func__);
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    camera_hardware_t *camHal = (camera_hardware_t *)device->priv;
    if(camHal) {
      camera_notify_callback cam_nt_cb;
      camera_data_callback cam_dt_cb;
      camera_data_timestamp_callback cam_dt_timestamp_cb;

      camHal->notify_cb = notify_cb;
      camHal->data_cb = data_cb;
      camHal->data_cb_timestamp = data_cb_timestamp;
      camHal->user_data = user;
      camHal->get_memory = get_memory;
      #if 0
      if(notify_cb) {
        cam_nt_cb = cam_notify_callback;
      } else {
        cam_nt_cb = NULL;
      }

      if(data_cb) {
        cam_dt_cb = cam_data_callback;
      } else {
        cam_dt_cb = NULL;
      }

      if(data_cb_timestamp) {
        cam_dt_timestamp_cb = cam_data_callback_timestamp;
      } else {
        cam_dt_timestamp_cb = NULL;
      }
      #endif
      ALOGE("cam_nt_cb =%p,cam_dt_cb=%p,cam_dt_timestamp_cb=%p",  cam_nt_cb, cam_dt_cb, cam_dt_timestamp_cb);
      hardware->setCallbacks(notify_cb,data_cb,data_cb_timestamp,get_memory, user);
    }
  }
}

void enable_msg_type(struct camera_device * device, int32_t msg_type)
{
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    hardware->enableMsgType(msg_type);
  }
}

void disable_msg_type(struct camera_device * device, int32_t msg_type)
{
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  ALOGE("Q%s: E", __func__);
  if(hardware != NULL){
    hardware->disableMsgType(msg_type);
  }
}

int msg_type_enabled(struct camera_device * device, int32_t msg_type)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->msgTypeEnabled(msg_type);
  }
  return rc;
}

int start_preview(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->startPreview( );
  }
  ALOGE("Q%s: X", __func__);
  return rc;
}

void stop_preview(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    hardware->stopPreview( );
  }
}

int preview_enabled(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware* hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->previewEnabled( );
  }
  return rc;
}

int store_meta_data_in_buffers(struct camera_device * device, int enable)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->storeMetaDataInBuffers( enable);
  }
  return rc;
}

int start_recording(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->startRecording( );
  }
  return rc;
}

void stop_recording(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  QualcommCameraHardware* hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    hardware->stopRecording( );
  }
}

int recording_enabled(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->recordingEnabled( );
  }
  return rc;
}

void release_recording_frame(struct camera_device * device,
                const void *opaque)
{
  ALOGE("Q%s: E", __func__);
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    hardware->releaseRecordingFrame( opaque);
  }
}

int auto_focus(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->autoFocus( );
  }
  return rc;
}

int cancel_auto_focus(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->cancelAutoFocus( );
  }
  return rc;
}

int take_picture(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->takePicture( );
  }
  return rc;
}

int cancel_picture(struct camera_device * device)

{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->cancelPicture( );
  }
  return rc;
}

CameraParameters g_param;
String8 g_str;
int set_parameters(struct camera_device * device, const char *parms)

{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL && parms){
    // = util_get_HAL_parameter(device);
    g_str = String8(parms);

   g_param.unflatten(g_str);
   rc = hardware->setParameters( g_param );
  }
  return rc;
}

char* get_parameters(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  char* rc = NULL;

  CameraParameters param;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    g_param = hardware->getParameters( );
    g_str = g_param.flatten( );
    rc = (char *)g_str.string( );
    if (!rc) {
      ALOGE("get_parameters: NULL string");
    } else {
      //ALOGE("get_parameters: %s", rc);
    }
  }
  ALOGE("get_parameters X");
  return rc;
}

void put_parameters(struct camera_device * device, char *parm)

{
  ALOGE("Q%s: E", __func__);
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    if(hardware != NULL){
      //rc = hardware->putParameters(parm );
    }
  }
  ALOGE("put_parameters X");
}

int send_command(struct camera_device * device,
            int32_t cmd, int32_t arg1, int32_t arg2)
{
  ALOGE("Q%s: E", __func__);
  int rc = -1;
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    rc = hardware->sendCommand( cmd, arg1, arg2);
  }
  return rc;
}

void release(struct camera_device * device)
{
  ALOGE("Q%s: E", __func__);
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
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
  QualcommCameraHardware * hardware = util_get_Hal_obj(device);
  if(hardware != NULL){
    //rc = hardware->dump( fd );
    rc = 0;
  }
  return rc;
}

}; // namespace android
