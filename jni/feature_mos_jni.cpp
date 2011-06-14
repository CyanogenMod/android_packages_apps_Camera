/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
*
 */
#include <string.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <db_utilities_camera.h>

#include <android/log.h>
#define ANDROID_LOG_VERBOSE ANDROID_LOG_DEBUG
#define LOG_TAG "CVJNI"
#define LOGV(...) __android_log_print(ANDROID_LOG_SILENT, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


#include "mosaic/ImageUtils.h"
#include "mosaic/AlignFeatures.h"
#include "mosaic/Blend.h"
#include "mosaic/Mosaic.h"

#ifdef __cplusplus
extern "C" {
#endif

char buffer[1024];

const int MAX_FRAMES_HR = 100;
const int MAX_FRAMES_LR = 200;

static double mTx;

enum { LR=0, HR, NR };
int tWidth[NR];
int tHeight[NR];
int H2L_FACTOR = 4; // Can be 2

ImageType tImage[NR][MAX_FRAMES_LR];// = {{ImageUtils::IMAGE_TYPE_NOIMAGE}}; // YVU24 format image
Mosaic *mosaic[NR] = {NULL,NULL};
ImageType resultYVU = ImageUtils::IMAGE_TYPE_NOIMAGE;
ImageType resultBGR = ImageUtils::IMAGE_TYPE_NOIMAGE;
float gTRS[10];

int c;
int ret;
int width=0, height=0;
int mosaicWidth=0, mosaicHeight=0;

//int blendingType = Blend::BLEND_TYPE_FULL;
//int blendingType = Blend::BLEND_TYPE_CYLPAN;
int blendingType = Blend::BLEND_TYPE_HORZ;
bool high_res = false;
bool quarter_res[NR] = {false,false};
float thresh_still[NR] = {5.0f,0.0f};

JNIEXPORT jstring JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_StringFromJNI( JNIEnv* env, jobject thiz )
{
    return (env)->NewStringUTF(buffer);
}

/* return current time in milliseconds*/

#ifndef now_ms
static double
now_ms(void)
{
    //struct timespec res;
    struct timeval res;
    //clock_gettime(CLOCK_REALTIME, &res);
    gettimeofday(&res, NULL);
    return 1000.0*res.tv_sec + (double)res.tv_usec/1e3;
}
#endif


static int frame_number_HR = 0;
static int frame_number_LR = 0;

int Init(int mID, int nmax)
{
        double  t0, t1, time_c;

        if(mosaic[mID]!=NULL)
        {
                delete mosaic[mID];
                mosaic[mID] = NULL;
        }

        mosaic[mID] = new Mosaic();

        t0 = now_ms();

        // When processing higher than 720x480 video, process low-res at quarter resolution
        if(tWidth[LR]>180)
            quarter_res[LR] = true;


        // Check for initialization and if not, initialize
        if (!mosaic[mID]->isInitialized())
        {
                mosaic[mID]->initialize(blendingType, tWidth[mID], tHeight[mID], nmax, quarter_res[mID], thresh_still[mID]);
        }

        t1 = now_ms();
        time_c = t1 - t0;
        LOGI("Init[%d]: %g ms [%d frames]",mID,time_c,nmax);
                return 1;
}

void GenerateQuarterResImagePlanar(ImageType im, int input_w, int input_h, ImageType &out)
{
    ImageType imp;
    ImageType outp;

    int count = 0;
  for (int j = 0; j < input_h; j+=4)
  {
    imp = im + j*input_w;
    outp = out + (int)(j/4)*(int(input_w/4));

    for (int i = 0; i < input_w; i+=4)
    {
        *outp++ = *(imp+i);
        count++;
    }
  }
  for (int j = input_h; j < 2*input_h; j+=4)
  {
    imp = im + j*input_w;
    outp = out + (int)(j/4)*(int(input_w/4));

    for (int i = 0; i < input_w; i+=4)
    {
        *outp++ = *(imp+i);
        count++;
    }
  }
  for (int j = 2*input_h; j < 3*input_h; j+=4)
  {
    imp = im + j*input_w;
    outp = out + (int)(j/4)*(int(input_w/4));

    for (int i = 0; i < input_w; i+=4)
    {
        *outp++ = *(imp+i);
        count++;
    }
  }
}

int AddFrame(int mID, int k, float* trs1d)
{
    double  t0, t1, time_c;
    double trs[3][3];

    t0 = now_ms();
    int ret_code = mosaic[mID]->addFrame(tImage[mID][k]);

    mosaic[mID]->getAligner()->getLastTRS(trs);

    //    LOGI("REG: %s",mosaic[mID]->getAligner()->getRegProfileString());
    t1 = now_ms();
    time_c = t1 - t0;
    LOGI("Align: %g ms",time_c);

    if(trs1d!=NULL)
    {

        trs1d[0] = trs[0][0];
        trs1d[1] = trs[0][1];
        trs1d[2] = trs[0][2];
        trs1d[3] = trs[1][0];
        trs1d[4] = trs[1][1];
        trs1d[5] = trs[1][2];
        trs1d[6] = trs[2][0];
        trs1d[7] = trs[2][1];
        trs1d[8] = trs[2][2];
    }

    return ret_code;
}

void Finalize(int mID)
{
    double  t0, t1, time_c;

    t0 = now_ms();
    // Create the mosaic
    ret = mosaic[mID]->createMosaic();
    t1 = now_ms();
    time_c = t1 - t0;
    LOGI("CreateMosaic: %g ms",time_c);

    // Get back the result
    resultYVU = mosaic[mID]->getMosaic(mosaicWidth, mosaicHeight);
}

void YUV420toYVU24(ImageType yvu24, ImageType yuv420sp, int width, int height)
{
    int frameSize = width * height;

    ImageType oyp = yvu24;
    ImageType ovp = yvu24+frameSize;
    ImageType oup = yvu24+frameSize+frameSize;

    for (int j = 0, yp = 0; j < height; j++)
    {
        unsigned char u = 0, v = 0;
        int uvp = frameSize + (j >> 1) * width;
        for (int i = 0; i < width; i++, yp++)
        {
            *oyp++ = yuv420sp[yp];
            //int y = (0xff & (int)yuv420sp[yp]) -16;
            //yvu24p[yp] = (y<0)?0:y;

            if ((i & 1) == 0)
            {
                v = yuv420sp[uvp++];
                u = yuv420sp[uvp++];
            }

            *ovp++ = v;
            *oup++ = u;
        }
    }
}

JNIEXPORT jboolean JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_SetSourceImageDimensions(JNIEnv* env, jobject thiz, jint width, jint height)
{
    tWidth[HR] = width;
    tHeight[HR] = height;
    tWidth[LR] = int(width/H2L_FACTOR);
    tHeight[LR] = int(height/H2L_FACTOR);

    for(int i=0; i<MAX_FRAMES_LR; i++)
    {
            tImage[LR][i] = ImageUtils::allocateImage(tWidth[LR], tHeight[LR], ImageUtils::IMAGE_TYPE_NUM_CHANNELS);
    }
    for(int i=0; i<MAX_FRAMES_HR; i++)
    {
            tImage[HR][i] = ImageUtils::allocateImage(tWidth[HR], tHeight[HR], ImageUtils::IMAGE_TYPE_NUM_CHANNELS);
    }
    return 1;
}


JNIEXPORT jfloatArray JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_SetSourceImage(JNIEnv* env, jobject thiz, jbyteArray photo_data)
{
    double  t0, t1, time_c;
    t0 = now_ms();

    if(frame_number_HR<MAX_FRAMES_HR && frame_number_LR<MAX_FRAMES_LR)
    {
        jbyte *pixels = env->GetByteArrayElements(photo_data, 0);
        YUV420toYVU24(tImage[HR][frame_number_HR], (ImageType)pixels, tWidth[HR], tHeight[HR]);

        env->ReleaseByteArrayElements(photo_data, pixels, 0);

        t1 = now_ms();
        time_c = t1 - t0;
        LOGI("[%d] ReadImage: %g ms",frame_number_HR,time_c);

        double last_tx = mTx;

        t0 = now_ms();
        GenerateQuarterResImagePlanar(tImage[HR][frame_number_HR], tWidth[HR], tHeight[HR], tImage[LR][frame_number_LR]);
        t1 = now_ms();
        time_c = t1 - t0;
        LOGI("[%d] HR->LR [%d]: %g ms",frame_number_HR,frame_number_LR,time_c);

        int ret_code = AddFrame(LR,frame_number_LR,gTRS);

        if(ret_code == Mosaic::MOSAIC_RET_OK)
        {
            frame_number_LR++;
            frame_number_HR++;
        }

    }
    else
    {
        gTRS[1] = gTRS[2] = gTRS[3] = gTRS[5] = gTRS[6] = gTRS[7] = 0.0f;
        gTRS[0] = gTRS[4] = gTRS[8] = 1.0f;
    }

    gTRS[9] = frame_number_HR;

    jfloatArray bytes = env->NewFloatArray(10);
    if(bytes != 0)
    {
        env->SetFloatArrayRegion(bytes, 0, 10, (jfloat*) gTRS);
    }
    return bytes;
}

JNIEXPORT jboolean JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_SetBlendingType(JNIEnv* env, jobject thiz, jint type)
{
    blendingType = int(type);

    return 1;
}

JNIEXPORT jboolean JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_Reset(JNIEnv* env, jobject thiz, jint type)
{
    frame_number_HR = 0;
    frame_number_LR = 0;

    blendingType = int(type);
    Init(LR,MAX_FRAMES_LR);

    return 1;
}

JNIEXPORT jboolean JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_CreateMosaic(JNIEnv* env, jobject thiz, jboolean value)
{
    high_res = bool(value);

    if(high_res)
    {
        Init(HR,frame_number_HR);
        for(int k=0; k<frame_number_HR; k++)
        {
            AddFrame(HR,k,NULL);
            LOGI("Added Frame [%d]",k);
        }

        Finalize(HR);
        high_res = false;
    }
    else
    {
        Finalize(LR);
    }

    return 1;
}
JNIEXPORT jintArray JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_GetFinalMosaic(JNIEnv* env, jobject thiz)
{
    int y,x;
    int width = mosaicWidth;
    int height = mosaicHeight;
    int imageSize = width * height;

    // Convert back to RGB24
    resultBGR = ImageUtils::allocateImage(mosaicWidth, mosaicHeight, ImageUtils::IMAGE_TYPE_NUM_CHANNELS);
    ImageUtils::yvu2bgr(resultBGR, resultYVU, mosaicWidth, mosaicHeight);

    LOGI("MosBytes: %d, W = %d, H = %d", imageSize, width, height);

    int* image = new int[imageSize];
    int* dims = new int[2];

    for(y=0; y<height; y++)
    {
        for(x=0; x<width; x++)
        {
            image[y*width+x] = (0xFF<<24) | (resultBGR[y*width*3+x*3+2]<<16)| (resultBGR[y*width*3+x*3+1]<<8)| (resultBGR[y*width*3+x*3]);
        }
    }

    dims[0] = width;
    dims[1] = height;

    ImageUtils::freeImage(resultBGR);

    jintArray bytes = env->NewIntArray(imageSize+2);
    if (bytes == 0) {
        LOGE("Error in creating the image.");
        delete[] image;
        return 0;
    }
    env->SetIntArrayRegion(bytes, 0, imageSize, (jint*) image);
    env->SetIntArrayRegion(bytes, imageSize, 2, (jint*) dims);
    delete[] image;
    delete[] dims;
    return bytes;
}

JNIEXPORT jbyteArray JNICALL Java_sri_ics_vt_SmartPhone_Mosaic_GetFinalMosaicNV21(JNIEnv* env, jobject thiz)
{
    int y,x;
    int width;
    int height;

    width = mosaicWidth;
    height = mosaicHeight;

    int imageSize = 1.5*width * height;

    // Convert YVU to NV21 format in-place
    ImageType V = resultYVU+mosaicWidth*mosaicHeight;
    ImageType U = V+mosaicWidth*mosaicHeight;
    for(int j=0; j<mosaicHeight; j++)
    {
        for(int i=0; i<mosaicWidth; i+=2)
        {
            V[j*mosaicWidth+i] = V[(2*j)*mosaicWidth+i];        // V
            V[j*mosaicWidth+i+1] = U[(2*j)*mosaicWidth+i+1];        // U
        }
    }

    LOGI("MosBytes: %d, W = %d, H = %d", imageSize, width, height);

    unsigned char* dims = new unsigned char[8];

    dims[0] = (unsigned char)(width >> 24);
    dims[1] = (unsigned char)(width >> 16);
    dims[2] = (unsigned char)(width >> 8);
    dims[3] = (unsigned char)width;

    dims[4] = (unsigned char)(height >> 24);
    dims[5] = (unsigned char)(height >> 16);
    dims[6] = (unsigned char)(height >> 8);
    dims[7] = (unsigned char)height;

    jbyteArray bytes = env->NewByteArray(imageSize+8);
    if (bytes == 0) {
        LOGE("Error in creating the image.");
        ImageUtils::freeImage(resultYVU);
        return 0;
    }
    env->SetByteArrayRegion(bytes, 0, imageSize, (jbyte*) resultYVU);
    env->SetByteArrayRegion(bytes, imageSize, 8, (jbyte*) dims);
    delete[] dims;
    ImageUtils::freeImage(resultYVU);
    return bytes;
}

#ifdef __cplusplus
}
#endif
