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

///////////////////////////////////////////////////
// Blend.h
// $Id: Blend.h,v 1.23 2011/06/24 04:22:14 mbansal Exp $

#ifndef BLEND_H
#define BLEND_H

#include "MosaicTypes.h"
#include "Pyramid.h"

#include "Delaunay.h"

#define BLEND_RANGE_DEFAULT 6
#define BORDER 8

//#define LINEAR_INTERP

//#define LOGII(...) //
//#define LOGIE(...) //
#if 1
#ifdef ANDROID
#include <android/log.h>
#define ANDROID_LOG_VERBOSE ANDROID_LOG_DEBUG
#define LOG_TAG "CVJNI"
#define LOGII(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGIE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGII printf
#define LOGIE printf
#endif
#endif


/**
 *  Class for pyramid blending a mosaic.
 */
class Blend {

public:

  static const int BLEND_TYPE_NONE    = -1;
  static const int BLEND_TYPE_FULL    = 0;
  static const int BLEND_TYPE_PAN     = 1;
  static const int BLEND_TYPE_CYLPAN  = 2;
  static const int BLEND_TYPE_HORZ   = 3;

  static const int BLEND_RET_ERROR        = -1;
  static const int BLEND_RET_OK           = 0;
  static const int BLEND_RET_ERROR_MEMORY = 1;

  Blend();
  ~Blend();

  int initialize(int blendingType, int frame_width, int frame_height);

  int runBlend(MosaicFrame **frames, int frames_size, ImageType &imageMosaicYVU, int &mosaicWidth, int &mosaicHeight);

protected:

  PyramidShort *m_pFrameYPyr;
  PyramidShort *m_pFrameUPyr;
  PyramidShort *m_pFrameVPyr;

  PyramidShort *m_pMosaicYPyr;
  PyramidShort *m_pMosaicUPyr;
  PyramidShort *m_pMosaicVPyr;

  CDelaunay m_Triangulator;
  CSite *m_AllSites;

  BlendParams m_wb;

  // Height and width of individual frames
  int width, height;

   // Height and width of mosaic
  unsigned short Mwidth, Mheight;

  // Helper functions
  void FrameToMosaic(double trs[3][3], double x, double y, double &wx, double &wy);
  void MosaicToFrame(double trs[3][3], double x, double y, double &wx, double &wy);
  void FrameToMosaicRect(int width, int height, double trs[3][3], BlendRect &brect);
  void ClipBlendRect(CSite *csite, BlendRect &brect);
  void AlignToMiddleFrame(MosaicFrame **frames, int frames_size);

  int  DoMergeAndBlend(MosaicFrame **frames, int nsite,  int width, int height, YUVinfo &imgMos, MosaicRect &rect, MosaicRect &cropping_rect);
  void ComputeMask(CSite *csite, BlendRect &vcrect, BlendRect &brect, MosaicRect &rect, YUVinfo &imgMos, int site_idx);
  void ProcessPyramidForThisFrame(CSite *csite, BlendRect &vcrect, BlendRect &brect, MosaicRect &rect, YUVinfo &imgMos, double trs[3][3], int site_idx);

  int  FillFramePyramid(MosaicFrame *mb);
  void ComputeBlendParameters(MosaicFrame **frames, int frames_size, int is360);

  int  PerformFinalBlending(YUVinfo &imgMos, MosaicRect &cropping_rect);
  void CropFinalMosaic(YUVinfo &imgMos, MosaicRect &cropping_rect);
};

#endif
