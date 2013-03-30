/* Copyright (c) 2012, Code Aurora Forum. All rights reserved.
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

#include <sys/types.h>
#include <fcntl.h>
#include "OMX_Types.h"
#include "OMX_Index.h"
#include "OMX_Core.h"
#include "OMX_Component.h"
#include "omx_debug.h"
#include "omx_jpeg_ext.h"
#include "mm_omx_jpeg_encoder.h"

#ifdef HW_ENCODE
static uint8_t hw_encode = true;
#else
static uint8_t hw_encode = false;
#endif

static int jpegRotation = 0;
static int isZSLMode = 0;
static int jpegThumbnailQuality = 75;
static int jpegMainimageQuality = 85;
static uint32_t phy_offset;
void *user_data;

static int encoding = 0;
pthread_mutex_t jpege_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t jpegcb_mutex = PTHREAD_MUTEX_INITIALIZER;

jpegfragment_callback_t mmcamera_jpegfragment_callback;
jpeg_callback_t mmcamera_jpeg_callback;


#define INPUT_PORT 0
#define OUTPUT_PORT 1
#define INPUT_PORT1 2
#define DEFAULT_COLOR_FORMAT YCRCBLP_H2V2

typedef struct {
  cam_format_t         isp_format;
  jpeg_color_format_t  jpg_format;
} color_format_map_t;


static const color_format_map_t color_format_map[] = {
  {CAMERA_YUV_420_NV21, YCRCBLP_H2V2}, /*default*/
  {CAMERA_YUV_420_NV21_ADRENO, YCRCBLP_H2V2},
  {CAMERA_YUV_420_NV12, YCBCRLP_H2V2},
  {CAMERA_YUV_420_YV12, YCBCRLP_H2V2},
  {CAMERA_YUV_422_NV61, YCRCBLP_H2V1},
  {CAMERA_YUV_422_NV16, YCBCRLP_H2V1},
};

static OMX_HANDLETYPE pHandle;
static OMX_CALLBACKTYPE callbacks;
static OMX_INDEXTYPE type;
static OMX_CONFIG_ROTATIONTYPE rotType;
static omx_jpeg_thumbnail thumbnail;
static OMX_CONFIG_RECTTYPE recttype;
static OMX_PARAM_PORTDEFINITIONTYPE * inputPort;
static OMX_PARAM_PORTDEFINITIONTYPE * outputPort;
static OMX_PARAM_PORTDEFINITIONTYPE * inputPort1;
static OMX_BUFFERHEADERTYPE* pInBuffers;
static OMX_BUFFERHEADERTYPE* pOutBuffers;
static OMX_BUFFERHEADERTYPE* pInBuffers1;
OMX_INDEXTYPE user_preferences;
omx_jpeg_user_preferences userpreferences;
OMX_INDEXTYPE exif;
static omx_jpeg_exif_info_tag tag;


static pthread_mutex_t lock;
static pthread_cond_t cond;
static int expectedEvent = 0;
static int expectedValue1 = 0;
static int expectedValue2 = 0;
static omx_jpeg_pmem_info pmem_info;
static omx_jpeg_pmem_info pmem_info1;
static OMX_IMAGE_PARAM_QFACTORTYPE qFactor;
static omx_jpeg_thumbnail_quality thumbnailQuality;
static OMX_INDEXTYPE thumbnailQualityType;
static void *out_buffer;
static int * out_buffer_size;
static OMX_INDEXTYPE buffer_offset;
static omx_jpeg_buffer_offset bufferoffset;

static jpeg_color_format_t get_jpeg_format_from_cam_format(
  cam_format_t cam_format )
{
  jpeg_color_format_t jpg_format = DEFAULT_COLOR_FORMAT;
  int i, j;
  j = sizeof (color_format_map) / sizeof(color_format_map_t);
  ALOGV("%s: j =%d, cam_format =%d", __func__, j, cam_format);
  for(i =0; i< j; i++) {
    if (color_format_map[i].isp_format == cam_format){
      jpg_format = color_format_map[i].jpg_format;
      break;
    }
  }
  ALOGV("%s x: i =%d, jpg_format=%d", __func__, i, jpg_format);

  return jpg_format;
}
static omx_jpeg_buffer_offset bufferoffset1;
void set_callbacks(
    jpegfragment_callback_t fragcallback,
    jpeg_callback_t eventcallback, void* userdata,
    void* output_buffer,
    int * outBufferSize) {
    pthread_mutex_lock(&jpegcb_mutex);
    mmcamera_jpegfragment_callback = fragcallback;
    mmcamera_jpeg_callback = eventcallback;
    user_data = userdata;
    out_buffer = output_buffer;
    out_buffer_size = outBufferSize;
    pthread_mutex_unlock(&jpegcb_mutex);
}


OMX_ERRORTYPE etbdone(OMX_OUT OMX_HANDLETYPE hComponent,
                      OMX_OUT OMX_PTR pAppData,
                      OMX_OUT OMX_BUFFERHEADERTYPE* pBuffer)
{
    pthread_mutex_lock(&lock);
    expectedEvent = OMX_EVENT_ETB_DONE;
    expectedValue1 = 0;
    expectedValue2 = 0;
    pthread_cond_signal(&cond);
    pthread_mutex_unlock(&lock);
    return 0;
}

OMX_ERRORTYPE ftbdone(OMX_OUT OMX_HANDLETYPE hComponent,
                      OMX_OUT OMX_PTR pAppData,
                      OMX_OUT OMX_BUFFERHEADERTYPE* pBuffer)
{
    ALOGE("%s", __func__);
    *out_buffer_size = pBuffer->nFilledLen;
    pthread_mutex_lock(&lock);
    expectedEvent = OMX_EVENT_FTB_DONE;
    expectedValue1 = 0;
    expectedValue2 = 0;
    pthread_cond_signal(&cond);
    pthread_mutex_unlock(&lock);
    OMX_DBG_INFO("%s:filled len = %u", __func__, (uint32_t)pBuffer->nFilledLen);
    if (mmcamera_jpeg_callback && encoding)
        mmcamera_jpeg_callback(0, user_data);
    return 0;
}

OMX_ERRORTYPE handleError(OMX_IN OMX_EVENTTYPE eEvent, OMX_IN OMX_U32 error)
{
    ALOGE("%s", __func__);
    if (error == OMX_EVENT_JPEG_ERROR) {
        if (mmcamera_jpeg_callback && encoding) {
            OMX_DBG_INFO("%s:OMX_EVENT_JPEG_ERROR\n", __func__);
            mmcamera_jpeg_callback(JPEG_EVENT_ERROR, user_data);
        }
    } else if (error == OMX_EVENT_THUMBNAIL_DROPPED) {
        if (mmcamera_jpeg_callback && encoding) {
            OMX_DBG_INFO("%s:(OMX_EVENT_THUMBNAIL_DROPPED\n", __func__);
            mmcamera_jpeg_callback(JPEG_EVENT_THUMBNAIL_DROPPED, user_data);
        }
    }
    return 0;
}

OMX_ERRORTYPE eventHandler( OMX_IN OMX_HANDLETYPE hComponent,
                            OMX_IN OMX_PTR pAppData, OMX_IN OMX_EVENTTYPE eEvent,
                            OMX_IN OMX_U32 nData1, OMX_IN OMX_U32 nData2,
                            OMX_IN OMX_PTR pEventData)
{
    OMX_DBG_INFO("%s", __func__);
    OMX_DBG_INFO("%s:got event %d ndata1 %u ndata2 %u", __func__,
      eEvent, nData1, nData2);
    pthread_mutex_lock(&lock);
    expectedEvent = eEvent;
    expectedValue1 = nData1;
    expectedValue2 = nData2;
    pthread_cond_signal(&cond);
    pthread_mutex_unlock(&lock);
    if ((nData1== OMX_EVENT_JPEG_ERROR)||(nData1== OMX_EVENT_THUMBNAIL_DROPPED))
        handleError(eEvent,nData1);
    return 0;
}

void waitForEvent(int event, int value1, int value2 ){
    pthread_mutex_lock(&lock);
    OMX_DBG_INFO("%s:Waiting for:event=%d, value1=%d, value2=%d",
      __func__, event, value1, value2);
    while (! (expectedEvent == event &&
    expectedValue1 == value1 && expectedValue2 == value2)) {
        pthread_cond_wait(&cond, &lock);
        OMX_DBG_INFO("%s:After cond_wait:expectedEvent=%d, expectedValue1=%d, expectedValue2=%d",
          __func__, expectedEvent, expectedValue1, expectedValue2);
        OMX_DBG_INFO("%s:After cond_wait:event=%d, value1=%d, value2=%d",
          __func__, event, value1, value2);
    }
    OMX_DBG_INFO("%s:done:expectedEvent=%d, expectedValue1=%d, expectedValue2=%d",
      __func__, expectedEvent, expectedValue1, expectedValue2);
    pthread_mutex_unlock(&lock);
}

int8_t mm_jpeg_encoder_get_buffer_offset(uint32_t width, uint32_t height,
    uint32_t* p_y_offset, uint32_t* p_cbcr_offset, uint32_t* p_buf_size,
    uint8_t *num_planes, uint32_t planes[])
{
    OMX_DBG_INFO("%s:", __func__);
    if ((NULL == p_y_offset) || (NULL == p_cbcr_offset)) {
        return FALSE;
    }
    *num_planes = 2;
    if (hw_encode ) {
        int cbcr_offset = 0;
        uint32_t actual_size = width*height;
        uint32_t padded_size = width * CEILING16(height);
        *p_y_offset = 0;
        *p_cbcr_offset = 0;
        if(!isZSLMode){
        if ((jpegRotation == 90) || (jpegRotation == 180)) {
            *p_y_offset = padded_size - actual_size;
            *p_cbcr_offset = ((padded_size - actual_size) >> 1);
          }
        }
        *p_buf_size = padded_size * 3/2;
        planes[0] = width * CEILING16(height);
        planes[1] = width * CEILING16(height)/2;
    } else {
        *p_y_offset = 0;
        *p_cbcr_offset = PAD_TO_WORD(width*height);
        *p_buf_size = *p_cbcr_offset * 3/2;
        planes[0] = PAD_TO_WORD(width*CEILING16(height));
        planes[1] = PAD_TO_WORD(width*CEILING16(height)/2);
    }
    return TRUE;
}

int8_t omxJpegOpen()
{
    OMX_DBG_INFO("%s", __func__);
    pthread_mutex_lock(&jpege_mutex);
    OMX_ERRORTYPE ret = OMX_GetHandle(&pHandle, "OMX.qcom.image.jpeg.encoder",
      NULL, &callbacks);
    pthread_mutex_unlock(&jpege_mutex);
    return TRUE;
}

int8_t omxJpegStart()
{
    ALOGE("%s", __func__);
    pthread_mutex_lock(&jpege_mutex);
    callbacks.EmptyBufferDone = etbdone;
    callbacks.FillBufferDone = ftbdone;
    callbacks.EventHandler = eventHandler;
    pthread_mutex_init(&lock, NULL);
    pthread_cond_init(&cond, NULL);
    OMX_Init();
    pthread_mutex_unlock(&jpege_mutex);
    return TRUE;
}

static omx_jpeg_color_format format_cam2jpeg(cam_format_t fmt)
{
    omx_jpeg_color_format jpeg_fmt = OMX_YCRCBLP_H2V2;
    switch (fmt) {
    case CAMERA_YUV_420_NV12:
        jpeg_fmt = OMX_YCBCRLP_H2V2;
        break;
    case CAMERA_YUV_420_NV21:
    case CAMERA_YUV_420_NV21_ADRENO:
        jpeg_fmt = OMX_YCRCBLP_H2V2;
        break;
    default:
        OMX_DBG_INFO("Camera format %d not supported", fmt);
        break;
    }
    return jpeg_fmt;
}

int8_t omxJpegEncodeNext(omx_jpeg_encode_params *encode_params)
{
    OMX_DBG_INFO("%s:E", __func__);
    pthread_mutex_lock(&jpege_mutex);
    encoding = 1;
    if(inputPort == NULL || inputPort1 == NULL || outputPort == NULL) {
      OMX_DBG_INFO("%s:pointer is null: X", __func__);
      return -1;
    }
    inputPort->nPortIndex = INPUT_PORT;
    outputPort->nPortIndex = OUTPUT_PORT;
    inputPort1->nPortIndex = INPUT_PORT1;
    OMX_GetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort);
    OMX_GetParameter(pHandle, OMX_IndexParamPortDefinition, outputPort);

    OMX_DBG_INFO("%s:nFrameWidth=%d nFrameHeight=%d nBufferSize=%d w=%d h=%d",
      __func__, inputPort->format.image.nFrameWidth,
      inputPort->format.image.nFrameHeight, inputPort->nBufferSize,
      bufferoffset.width, bufferoffset.height);
    OMX_GetExtensionIndex(pHandle,"omx.qcom.jpeg.exttype.buffer_offset",
      &buffer_offset);
    OMX_DBG_INFO("%s:Buffer w %d h %d yOffset %d cbcrOffset %d totalSize %d\n",
      __func__, bufferoffset.width, bufferoffset.height, bufferoffset.yOffset,
      bufferoffset.cbcrOffset,bufferoffset.totalSize);
    OMX_SetParameter(pHandle, buffer_offset, &bufferoffset);
    OMX_SetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort1);
    OMX_GetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort1);
    OMX_DBG_INFO("%s: thumbnail widht %d height %d", __func__,
      thumbnail.width, thumbnail.height);

    userpreferences.color_format =
        format_cam2jpeg(encode_params->dimension->main_img_format);
    userpreferences.thumbnail_color_format =
        format_cam2jpeg(encode_params->dimension->thumb_format);
    if (hw_encode)
        userpreferences.preference = OMX_JPEG_PREF_HW_ACCELERATED_PREFERRED;
    else
        userpreferences.preference = OMX_JPEG_PREF_SOFTWARE_ONLY;

    pmem_info.fd = encode_params->snapshot_fd;
    pmem_info.offset = 0;
    OMX_UseBuffer(pHandle, &pInBuffers, 0, &pmem_info, inputPort->nBufferSize,
    (void *) encode_params->snapshot_buf);

    pmem_info1.fd = encode_params->thumbnail_fd;
    pmem_info1.offset = 0;

    OMX_DBG_INFO("%s: input1 buff size %d", __func__, inputPort1->nBufferSize);
    OMX_UseBuffer(pHandle, &pInBuffers1, 2, &pmem_info1,
      inputPort1->nBufferSize, (void *) encode_params->thumbnail_buf);
    OMX_UseBuffer(pHandle, &pOutBuffers, 1, NULL, inputPort->nBufferSize,
      (void *) out_buffer);
    OMX_EmptyThisBuffer(pHandle, pInBuffers);
    OMX_EmptyThisBuffer(pHandle, pInBuffers1);
    OMX_FillThisBuffer(pHandle, pOutBuffers);
    pthread_mutex_unlock(&jpege_mutex);
    OMX_DBG_INFO("%s:X", __func__);
    return TRUE;
}

int8_t omxJpegEncode(omx_jpeg_encode_params *encode_params)
{
    int size = 0;
    uint8_t num_planes;
    uint32_t planes[10];
    int orientation;
    OMX_DBG_INFO("%s:E", __func__);

    inputPort = malloc(sizeof(OMX_PARAM_PORTDEFINITIONTYPE));
    outputPort = malloc(sizeof(OMX_PARAM_PORTDEFINITIONTYPE));
    inputPort1 = malloc(sizeof(OMX_PARAM_PORTDEFINITIONTYPE));

    pthread_mutex_lock(&jpege_mutex);
    encoding = 1;
    inputPort->nPortIndex = INPUT_PORT;
    outputPort->nPortIndex = OUTPUT_PORT;
    inputPort1->nPortIndex = INPUT_PORT1;
    OMX_GetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort);
    OMX_GetParameter(pHandle, OMX_IndexParamPortDefinition, outputPort);

    bufferoffset.width = encode_params->dimension->orig_picture_dx;
    bufferoffset.height = encode_params->dimension->orig_picture_dy;
    mm_jpeg_encoder_get_buffer_offset( bufferoffset.width, bufferoffset.height,&bufferoffset.yOffset,
                                       &bufferoffset.cbcrOffset,
                                       &bufferoffset.totalSize,&num_planes,planes);
    if (encode_params->a_cbcroffset > 0) {
        bufferoffset.totalSize = encode_params->a_cbcroffset * 1.5;
    }
    OMX_GetExtensionIndex(pHandle,"omx.qcom.jpeg.exttype.buffer_offset",&buffer_offset);
    OMX_DBG_INFO(" Buffer width = %d, Buffer  height = %d, yOffset =%d, cbcrOffset =%d, totalSize = %d\n",
                 bufferoffset.width, bufferoffset.height, bufferoffset.yOffset,
                 bufferoffset.cbcrOffset,bufferoffset.totalSize);
    OMX_SetParameter(pHandle, buffer_offset, &bufferoffset);


    if (encode_params->a_cbcroffset > 0) {
        OMX_DBG_ERROR("Using acbcroffset\n");
        bufferoffset1.cbcrOffset = encode_params->a_cbcroffset;
        OMX_GetExtensionIndex(pHandle,"omx.qcom.jpeg.exttype.acbcr_offset",&buffer_offset);
        OMX_SetParameter(pHandle, buffer_offset, &bufferoffset1);
    }

    inputPort->format.image.nFrameWidth = encode_params->dimension->orig_picture_dx;
    inputPort->format.image.nFrameHeight = encode_params->dimension->orig_picture_dy;
    inputPort->format.image.nStride = encode_params->dimension->orig_picture_dx;
    inputPort->format.image.nSliceHeight = encode_params->dimension->orig_picture_dy;
    inputPort->nBufferSize = bufferoffset.totalSize;

    inputPort1->format.image.nFrameWidth =
      encode_params->dimension->thumbnail_width;
    inputPort1->format.image.nFrameHeight =
      encode_params->dimension->thumbnail_height;
    inputPort1->format.image.nStride =
      encode_params->dimension->thumbnail_width;
    inputPort1->format.image.nSliceHeight =
      encode_params->dimension->thumbnail_height;

    OMX_SetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort);
    OMX_GetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort);
    size = inputPort->nBufferSize;
    thumbnail.width = encode_params->dimension->thumbnail_width;
    thumbnail.height = encode_params->dimension->thumbnail_height;

    OMX_SetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort1);
    OMX_GetParameter(pHandle, OMX_IndexParamPortDefinition, inputPort1);
    OMX_DBG_INFO("%s: thumbnail width %d height %d", __func__,
      encode_params->dimension->thumbnail_width,
      encode_params->dimension->thumbnail_height);

    if(encode_params->a_cbcroffset > 0)
        inputPort1->nBufferSize = inputPort->nBufferSize;

    userpreferences.color_format =
      get_jpeg_format_from_cam_format(encode_params->main_format);
    userpreferences.thumbnail_color_format =
      get_jpeg_format_from_cam_format(encode_params->thumbnail_format);

    if (hw_encode)
        userpreferences.preference = OMX_JPEG_PREF_HW_ACCELERATED_PREFERRED;
    else
        userpreferences.preference = OMX_JPEG_PREF_SOFTWARE_ONLY;


      OMX_DBG_ERROR("%s:Scaling params in1_w %d in1_h %d out1_w %d out1_h %d"
                  "main_img in2_w %d in2_h %d out2_w %d out2_h %d\n", __func__,
      encode_params->scaling_params->in1_w,
      encode_params->scaling_params->in1_h,
      encode_params->scaling_params->out1_w,
      encode_params->scaling_params->out1_h,
      encode_params->scaling_params->in2_w,
      encode_params->scaling_params->in2_h,
      encode_params->scaling_params->out2_w,
      encode_params->scaling_params->out2_h);
   /*Main image scaling*/
    OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);


    if (encode_params->scaling_params->in2_w &&
        encode_params->scaling_params->in2_h) {
        if (jpegRotation) {
            userpreferences.preference = OMX_JPEG_PREF_SOFTWARE_ONLY;
            OMX_DBG_INFO("%s:Scaling and roation true: setting pref to sw\n",
              __func__);
        }

      /* Scaler information  for main image */
        recttype.nWidth = CEILING2(encode_params->scaling_params->in2_w);
        recttype.nHeight = CEILING2(encode_params->scaling_params->in2_h);
        OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

        if (encode_params->main_crop_offset) {
            recttype.nLeft = encode_params->main_crop_offset->x;
            recttype.nTop = encode_params->main_crop_offset->y;
            OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

        } else {
            recttype.nLeft = 0;
            recttype.nTop = 0;
            OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

        }
        OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

        recttype.nPortIndex = 1;
        OMX_SetConfig(pHandle, OMX_IndexConfigCommonInputCrop, &recttype);
        OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

        if (encode_params->scaling_params->out2_w &&
            encode_params->scaling_params->out2_h) {
            recttype.nWidth = (encode_params->scaling_params->out2_w);
            recttype.nHeight = (encode_params->scaling_params->out2_h);
            OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);


            recttype.nPortIndex = 1;
            OMX_SetConfig(pHandle, OMX_IndexConfigCommonOutputCrop, &recttype);
            OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

        }

    } else {
        OMX_DBG_ERROR("%s: There is no main image scaling information",
          __func__);
    }
  /*case of thumbnail*/
    if (encode_params->scaling_params->in1_w &&
        encode_params->scaling_params->in1_h) {
        thumbnail.scaling = 0;
        thumbnail.cropWidth = CEILING2(encode_params->scaling_params->in1_w);
        thumbnail.cropHeight = CEILING2(encode_params->scaling_params->in1_h);
        thumbnail.width  = encode_params->scaling_params->out1_w;
        thumbnail.height = encode_params->scaling_params->out1_h;

        if (encode_params->thumb_crop_offset) {
            OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

            thumbnail.left = encode_params->thumb_crop_offset->x;
            thumbnail.top = encode_params->thumb_crop_offset->y;
            thumbnail.scaling = 1;
        } else {
            OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

            thumbnail.left = 0;
            thumbnail.top = 0;
        }
    } else {
        OMX_DBG_ERROR("%s:%d/n",__func__,__LINE__);

        thumbnail.scaling = 0;
        OMX_DBG_ERROR("%s: There is no thumbnail scaling information",
          __func__);
    }
    OMX_GetExtensionIndex(pHandle,"omx.qcom.jpeg.exttype.user_preferences",
      &user_preferences);
    OMX_DBG_INFO("%s:User Preferences: color_format %d"
      "thumbnail_color_format = %d encoder preference =%d\n", __func__,
      userpreferences.color_format,userpreferences.thumbnail_color_format,
      userpreferences.preference);
    OMX_SetParameter(pHandle,user_preferences,&userpreferences);

    OMX_DBG_INFO("%s Thumbnail present? : %d ", __func__,
                 encode_params->hasThumbnail);
    if (encode_params->hasThumbnail) {
	OMX_GetExtensionIndex(pHandle, "omx.qcom.jpeg.exttype.thumbnail", &type);
	OMX_SetParameter(pHandle, type, &thumbnail);
    }
    qFactor.nPortIndex = INPUT_PORT;
    OMX_GetParameter(pHandle, OMX_IndexParamQFactor, &qFactor);
    qFactor.nQFactor = jpegMainimageQuality;
    OMX_SetParameter(pHandle, OMX_IndexParamQFactor, &qFactor);

    OMX_GetExtensionIndex(pHandle, "omx.qcom.jpeg.exttype.thumbnail_quality",
    &thumbnailQualityType);

    OMX_DBG_INFO("%s: thumbnail quality %u %d",
      __func__, thumbnailQualityType, jpegThumbnailQuality);
    OMX_GetParameter(pHandle, thumbnailQualityType, &thumbnailQuality);
    thumbnailQuality.nQFactor = jpegThumbnailQuality;
    OMX_SetParameter(pHandle, thumbnailQualityType, &thumbnailQuality);

    ALOGE("isZSLMode is %d\n",isZSLMode);
    if(!isZSLMode){
    //Pass rotation if not ZSL mode
    rotType.nPortIndex = OUTPUT_PORT;
    rotType.nRotation = jpegRotation;
    OMX_SetConfig(pHandle, OMX_IndexConfigCommonRotate, &rotType);
    ALOGE("Set rotation to %d\n",jpegRotation);
    }

    OMX_GetExtensionIndex(pHandle, "omx.qcom.jpeg.exttype.exif", &exif);
    /*temporarily set rotation in EXIF data. This is done to avoid image corruption
      issues in ZSL mode since roation is known before hand. The orientation is set in the
      exif tag and decoder will decode it will the right orientation. need to add double
      padding to fix the issue */
    if(isZSLMode){

      //Get the orientation tag values depending on rotation
      switch(jpegRotation){
        case 0: orientation =1; //Normal
                break;
        case 90: orientation =6; //Rotated 90 CCW
                 break;
        case 180: orientation =3; //Rotated 180
                  break;
        case 270: orientation =8; //Rotated 90 CW
                  break;
        default: orientation =1;
                 break;
     }
      tag.tag_id = EXIFTAGID_ORIENTATION;
      tag.tag_entry.type = EXIFTAGTYPE_ORIENTATION;
      tag.tag_entry.count =1;
      tag.tag_entry.copy = 1;
      tag.tag_entry.data._short = orientation;
      ALOGE("%s jpegRotation = %d , orientation value =%d\n",__func__,jpegRotation,orientation);
      OMX_SetParameter(pHandle, exif, &tag);
      }

    //Set omx parameter for all exif tags
    int i;
    for(i=0; i<encode_params->exif_numEntries; i++) {
        memcpy(&tag, encode_params->exif_data + i, sizeof(omx_jpeg_exif_info_tag));
        OMX_SetParameter(pHandle, exif, &tag);
    }

    pmem_info.fd = encode_params->snapshot_fd;
    pmem_info.offset = 0;

    OMX_DBG_ERROR("input buffer size is %d",size);
    OMX_UseBuffer(pHandle, &pInBuffers, 0, &pmem_info, size,
    (void *) encode_params->snapshot_buf);

    pmem_info1.fd = encode_params->thumbnail_fd;
    pmem_info1.offset = 0;

    OMX_DBG_INFO("%s: input1 buff size %d", __func__, inputPort1->nBufferSize);
    OMX_UseBuffer(pHandle, &pInBuffers1, 2, &pmem_info1,
      inputPort1->nBufferSize, (void *) encode_params->thumbnail_buf);


    OMX_UseBuffer(pHandle, &pOutBuffers, 1, NULL, size, (void *) out_buffer);

    waitForEvent(OMX_EventCmdComplete, OMX_CommandStateSet, OMX_StateIdle);
    OMX_DBG_INFO("%s:State changed to OMX_StateIdle\n", __func__);
    OMX_SendCommand(pHandle, OMX_CommandStateSet, OMX_StateExecuting, NULL);
    waitForEvent(OMX_EventCmdComplete, OMX_CommandStateSet, OMX_StateExecuting);

    OMX_EmptyThisBuffer(pHandle, pInBuffers);
    OMX_EmptyThisBuffer(pHandle, pInBuffers1);
    OMX_FillThisBuffer(pHandle, pOutBuffers);
    pthread_mutex_unlock(&jpege_mutex);
    OMX_DBG_INFO("%s:X", __func__);
    return TRUE;
}

void omxJpegFinish()
{
    pthread_mutex_lock(&jpege_mutex);
    OMX_DBG_INFO("%s:encoding=%d", __func__, encoding);
    if (encoding) {
        encoding = 0;
        OMX_SendCommand(pHandle, OMX_CommandStateSet, OMX_StateIdle, NULL);
        OMX_SendCommand(pHandle, OMX_CommandStateSet, OMX_StateLoaded, NULL);
        OMX_FreeBuffer(pHandle, 0, pInBuffers);
        OMX_FreeBuffer(pHandle, 2, pInBuffers1);
        OMX_FreeBuffer(pHandle, 1, pOutBuffers);
        OMX_Deinit();
    }
    pthread_mutex_unlock(&jpege_mutex);
}

void omxJpegClose()
{
    OMX_DBG_INFO("%s:", __func__);
}

void omxJpegAbort()
{
    pthread_mutex_lock(&jpegcb_mutex);
    mmcamera_jpegfragment_callback = NULL;
    mmcamera_jpeg_callback = NULL;
    user_data = NULL;
    pthread_mutex_unlock(&jpegcb_mutex);
    pthread_mutex_lock(&jpege_mutex);
    OMX_DBG_INFO("%s: encoding=%d", __func__, encoding);
    if (encoding) {
      encoding = 0;
      OMX_SendCommand(pHandle, OMX_CommandFlush, NULL, NULL);
      OMX_DBG_INFO("%s:waitForEvent: OMX_CommandFlush", __func__);
      waitForEvent(OMX_EVENT_JPEG_ABORT, 0, 0);
      OMX_DBG_INFO("%s:waitForEvent: OMX_CommandFlush: DONE", __func__);
      OMX_SendCommand(pHandle, OMX_CommandStateSet, OMX_StateIdle, NULL);
      OMX_SendCommand(pHandle, OMX_CommandStateSet, OMX_StateLoaded, NULL);
      OMX_FreeBuffer(pHandle, 0, pInBuffers);
      OMX_FreeBuffer(pHandle, 2, pInBuffers1);
      OMX_FreeBuffer(pHandle, 1, pOutBuffers);
      OMX_Deinit();
    }
    pthread_mutex_unlock(&jpege_mutex);
}


int8_t mm_jpeg_encoder_setMainImageQuality(uint32_t quality)
{
    pthread_mutex_lock(&jpege_mutex);
    ALOGE("%s: current main inage quality %d ," \
    " new quality : %d\n", __func__, jpegMainimageQuality, quality);
    if (quality <= 100)
        jpegMainimageQuality = quality;
    pthread_mutex_unlock(&jpege_mutex);
    return TRUE;
}

int8_t mm_jpeg_encoder_setThumbnailQuality(uint32_t quality)
{
    pthread_mutex_lock(&jpege_mutex);
    ALOGE("%s: current thumbnail quality %d ," \
    " new quality : %d\n", __func__, jpegThumbnailQuality, quality);
    if (quality <= 100)
        jpegThumbnailQuality = quality;
    pthread_mutex_unlock(&jpege_mutex);
    return TRUE;
}

int8_t mm_jpeg_encoder_setRotation(int rotation, int isZSL)
{
    pthread_mutex_lock(&jpege_mutex);

    /*Set ZSL Mode*/
    isZSLMode = isZSL;
    ALOGE("%s: Setting ZSL Mode to %d Rotation = %d\n",__func__,isZSLMode,rotation);
    /* Set rotation configuration */
    switch (rotation) {
    case 0:
    case 90:
    case 180:
    case 270:
        jpegRotation = rotation;
        break;
    default:
        /* Invalid rotation mode, set to default */
        OMX_DBG_INFO("%s:Setting Default rotation mode", __func__);
        jpegRotation = 0;
        break;
    }
    pthread_mutex_unlock(&jpege_mutex);
    return TRUE;
}

/*===========================================================================
FUNCTION      mm_jpege_set_phy_offset

DESCRIPTION   Set physical offset for the buffer
===========================================================================*/
void mm_jpege_set_phy_offset(uint32_t a_phy_offset)
{
    phy_offset = a_phy_offset;
}
