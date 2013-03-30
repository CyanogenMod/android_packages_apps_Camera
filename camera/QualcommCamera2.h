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

#ifndef ANDROID_HARDWARE_QUALCOMM_CAMERA_H
#define ANDROID_HARDWARE_QUALCOMM_CAMERA_H


#include "QCameraHWI.h"

extern "C" {
/*#include <hardware/camera.h>*/

  int get_number_of_cameras();
  int get_camera_info(int camera_id, struct camera_info *info);

  int camera_device_open(const struct hw_module_t* module, const char* id,
          struct hw_device_t** device);

  hw_device_t * open_camera_device(int cameraId);

  int close_camera_device( hw_device_t *);

namespace android {
  int set_preview_window(struct camera_device *,
          struct preview_stream_ops *window);
  void set_CallBacks(struct camera_device *,
          camera_notify_callback notify_cb,
          camera_data_callback data_cb,
          camera_data_timestamp_callback data_cb_timestamp,
          camera_request_memory get_memory,
          void *user);

  void enable_msg_type(struct camera_device *, int32_t msg_type);

  void disable_msg_type(struct camera_device *, int32_t msg_type);
  int msg_type_enabled(struct camera_device *, int32_t msg_type);

  int start_preview(struct camera_device *);

  void stop_preview(struct camera_device *);

  int preview_enabled(struct camera_device *);
  int store_meta_data_in_buffers(struct camera_device *, int enable);

  int start_recording(struct camera_device *);

  void stop_recording(struct camera_device *);

  int recording_enabled(struct camera_device *);

  void release_recording_frame(struct camera_device *,
                  const void *opaque);

  int auto_focus(struct camera_device *);

  int cancel_auto_focus(struct camera_device *);

  int take_picture(struct camera_device *);

  int cancel_picture(struct camera_device *);

  int set_parameters(struct camera_device *, const char *parms);

  char* get_parameters(struct camera_device *);

  void put_parameters(struct camera_device *, char *);

  int send_command(struct camera_device *,
              int32_t cmd, int32_t arg1, int32_t arg2);

  void release(struct camera_device *);

  int dump(struct camera_device *, int fd);



}; // namespace android

} //extern "C"

#endif

