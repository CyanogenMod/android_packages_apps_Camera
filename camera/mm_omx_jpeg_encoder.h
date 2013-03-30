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
 */

#ifndef MM_OMX_JPEG_ENCODER_H_
#define MM_OMX_JPEG_ENCODER_H_
#include <linux/ion.h>
#include "camera.h"

typedef struct omx_jpeg_encode_params_t {
    const cam_ctrl_dimension_t * dimension;
    const uint8_t * thumbnail_buf;
    int thumbnail_fd;
    uint32_t thumbnail_offset;
    const uint8_t * snapshot_buf;
    int snapshot_fd;
    uint32_t snapshot_offset;
    common_crop_t *scaling_params;
    exif_tags_info_t *exif_data;
    int exif_numEntries;
    int32_t a_cbcroffset;
    cam_point_t* main_crop_offset;
    cam_point_t* thumb_crop_offset;
    int hasThumbnail;
    cam_format_t main_format;
    cam_format_t thumbnail_format;
}omx_jpeg_encode_params;

int8_t omxJpegOpen();
int8_t omxJpegStart();
int8_t omxJpegEncode(omx_jpeg_encode_params *encode_params);
int8_t omxJpegEncodeNext(omx_jpeg_encode_params *encode_params);
void omxJpegFinish();
void omxJpegClose();
void omxJpegAbort();

int8_t mm_jpeg_encoder_setMainImageQuality(uint32_t quality);
int8_t mm_jpeg_encoder_setThumbnailQuality(uint32_t quality);
int8_t mm_jpeg_encoder_setRotation(int rotation,int isZSL);
void jpege_set_phy_offset(uint32_t a_phy_offset);
int8_t mm_jpeg_encoder_get_buffer_offset(uint32_t width, uint32_t height,
    uint32_t* p_y_offset, uint32_t* p_cbcr_offset,
    uint32_t* p_buf_size,uint8_t *num_planes,
    uint32_t planes[]);
typedef void (*jpegfragment_callback_t)(uint8_t * buff_ptr,
    uint32_t buff_size, void* user_data);
typedef void (*jpeg_callback_t)(jpeg_event_t, void *);

void set_callbacks(
    jpegfragment_callback_t fragcallback,
    jpeg_callback_t eventcallback,
    void* userdata,
    void* output_buffer,
    int * outBufferSize
);


#endif /* MM_OMX_JPEG_ENCODER_H_ */
