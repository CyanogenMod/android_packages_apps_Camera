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
// AlignFeatures.cpp
// S.O. # :
// Author(s): zkira, mbansal, bsouthall, narodits
// $Id: AlignFeatures.cpp,v 1.20 2011/06/17 13:35:47 mbansal Exp $

#include <stdio.h>
#include <string.h>

#include "trsMatrix.h"
#include "MatrixUtils.h"
#include "AlignFeatures.h"

Align::Align()
{
  width = height = 0;
  frame_number = 0;
  db_Identity3x3(Hcurr);
}

Align::~Align()
{
  // Free gray-scale image
  if (imageGray != ImageUtils::IMAGE_TYPE_NOIMAGE)
    ImageUtils::freeImage(imageGray);
}

char* Align::getRegProfileString()
{
  return reg.profile_string;
}

int Align::initialize(int width, int height, bool _quarter_res, float _thresh_still)
{
  int    nr_corners = DEFAULT_NR_CORNERS;
  double max_disparity = DEFAULT_MAX_DISPARITY;
  int    motion_model_type = DEFAULT_MOTION_MODEL;
  int nrsamples = DB_DEFAULT_NR_SAMPLES;
  double scale = DB_POINT_STANDARDDEV;
  int chunk_size = DB_DEFAULT_CHUNK_SIZE;
  int nrhorz = 20; // 1280/32 = 40
  int nrvert = 12; // 720/30 = 24
  bool linear_polish = false;
  unsigned int reference_update_period = DEFAULT_REFERENCE_UPDATE_PERIOD;

  const bool DEFAULT_USE_SMALLER_MATCHING_WINDOW = false;
  bool   use_smaller_matching_window = DEFAULT_USE_SMALLER_MATCHING_WINDOW;

  quarter_res = _quarter_res;
  thresh_still = _thresh_still;

  frame_number = 0;
  db_Identity3x3(Hcurr);
  if (!reg.Initialized())
  {
    reg.Init(width,height,motion_model_type,20,linear_polish,quarter_res,scale,reference_update_period, false, 0, nrsamples,chunk_size,nr_corners,max_disparity,use_smaller_matching_window, nrhorz, nrvert);
  }
  this->width = width;
  this->height = height;

  imageGray = ImageUtils::allocateImage(width, height, 1);

  if (reg.Initialized())
    return ALIGN_RET_OK;
  else
    return ALIGN_RET_ERROR;
}

int Align::addFrameRGB(ImageType imageRGB)
{
  ImageUtils::rgb2gray(imageGray, imageRGB, width, height);
  return addFrame(imageGray);
}

int Align::addFrame(ImageType imageGray_)
{
  // compute the homography:
  double Hinv[9];
  double Hinv33[3][3];
  double Hprev33[3][3];
  double Hcurr33[3][3];

 // Obtain a vector of pointers to rows in image and pass in to dbreg
  ImageType *m_rows = ImageUtils::imageTypeToRowPointers(imageGray_, width, height);
  reg.AddFrame(m_rows, Hcurr);


  if (frame_number != 0)
  {

    if(fabs(Hcurr[2])<thresh_still && fabs(Hcurr[5])<thresh_still)  // Still camera
    {
        return ALIGN_RET_ERROR;
    }

    // Invert and multiple with previous transformation
    Matrix33::convert9to33(Hcurr33, Hcurr);
    Matrix33::convert9to33(Hprev33, Hprev);
    //NormalizeProjMat(Hcurr33);
    normProjMat33d(Hcurr33);

    inv33d(Hcurr33, Hinv33);

    mult33d(Hcurr33, Hprev33, Hinv33);
    normProjMat33d(Hcurr33);
    Matrix9::convert33to9(Hcurr, Hcurr33);

    reg.UpdateReference(m_rows,quarter_res,false);
  }

  frame_number++;

  // Copy curr to prev
  memcpy(Happly, Hcurr, sizeof(double)*9);
  memcpy(Hprev, Hcurr, sizeof(double)*9);

  return ALIGN_RET_OK;
}

// Get current transformation
int Align::getLastTRS(double trs[3][3])
{
  if (frame_number < 1)
  {
    fprintf(stderr, "Error: Align::getLastTRS called before a frame was processed\n");
    return ALIGN_RET_ERROR;
  }

  Matrix33::convert9to33(trs, Happly);
  return ALIGN_RET_OK;
}

