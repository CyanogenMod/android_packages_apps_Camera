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
// Mosaic.pp
// S.O. # :
// Author(s): zkira
// $Id: Mosaic.cpp,v 1.20 2011/06/24 04:22:14 mbansal Exp $

#include <stdio.h>
#include <string.h>

#include "Mosaic.h"
#include "trsMatrix.h"

Mosaic::Mosaic()
{
    initialized = false;
    imageMosaicYVU = NULL;
    frames_size = 0;
    max_frames = 200;
}

Mosaic::~Mosaic()
{
    for (int i = 0; i < frames_size; i++)
    {
        if (frames[i])
            delete frames[i];
    }
    delete frames;

    if (aligner != NULL)
        delete aligner;
    if (blender != NULL)
        delete blender;
}

int Mosaic::initialize(int blendingType, int width, int height, int nframes, bool quarter_res, float thresh_still)
{
    this->blendingType = blendingType;
    this->width = width;
    this->height = height;

    mosaicWidth = mosaicHeight = 0;
    imageMosaicYVU = NULL;

    frames = new MosaicFrame *[max_frames];

    if(nframes>-1)
    {
        for(int i=0; i<nframes; i++)
        {
            frames[i] = new MosaicFrame(this->width,this->height,false); // Do no allocate memory for YUV data
        }
    }
    else
    {
        for(int i=0; i<max_frames; i++)
        {
            frames[i] = NULL;
        }


    }

    LOGIE("Initialize %d %d\n", width, height);
    LOGIE("Frame width %d,%d\n", width, height);
    LOGIE("Max num frames %d\n", max_frames);

        aligner = new Align();
        aligner->initialize(width, height,quarter_res,thresh_still);

    if (blendingType == Blend::BLEND_TYPE_FULL ||
            blendingType == Blend::BLEND_TYPE_PAN ||
            blendingType == Blend::BLEND_TYPE_CYLPAN ||
            blendingType == Blend::BLEND_TYPE_HORZ) {
        blender = new Blend();
        blender->initialize(blendingType, width, height);
    } else {
        blender = NULL;
        LOGIE("Error: Unknown blending type %d\n",blendingType);
        return MOSAIC_RET_ERROR;
    }

    initialized = true;

    return MOSAIC_RET_OK;
}

int Mosaic::addFrameRGB(ImageType imageRGB)
{
    ImageType imageYVU;
    // Convert to YVU24 which is used by blending
    imageYVU = ImageUtils::allocateImage(this->width, this->height, ImageUtils::IMAGE_TYPE_NUM_CHANNELS);
    ImageUtils::rgb2yvu(imageYVU, imageRGB, width, height);

    return addFrame(imageYVU);
}

int Mosaic::addFrame(ImageType imageYVU)
{
    if(frames[frames_size]==NULL)
        frames[frames_size] = new MosaicFrame(this->width,this->height,false);

    MosaicFrame *frame = frames[frames_size];

    frame->image = imageYVU;

    int align_flag = Align::ALIGN_RET_OK;

    // Add frame to aligner
    if (aligner != NULL)
    {
        // Note aligner takes in RGB images
        printf("Adding frame to aligner...\n");
        align_flag = aligner->addFrame(frame->image);
        aligner->getLastTRS(frame->trs);

        printf("Frame width %d,%d\n", frame->width, frame->height);
        if (frames_size >= max_frames)
        {
            fprintf(stderr, "WARNING: More frames than preallocated, ignoring.  Increase maximum number of frames (-f <max_frames>) to avoid this\n");
            return MOSAIC_RET_ERROR;
        }
        else if(align_flag == Align::ALIGN_RET_OK)
        {
            frames_size++;
            return MOSAIC_RET_OK;
        }
        else
        {
            return MOSAIC_RET_ERROR;
        }
    }
    else
    {
        return MOSAIC_RET_ERROR;
    }
}


int Mosaic::createMosaic(float &progress)
{
    printf("Creating mosaic\n");

    if (frames_size <= 0)
    {
        // Haven't accepted any frame in aligner. No need to do blending.
        progress = TIME_PERCENT_ALIGN + TIME_PERCENT_BLEND
                + TIME_PERCENT_FINAL;
        return MOSAIC_RET_OK;
    }

    if (blendingType == Blend::BLEND_TYPE_PAN)
    {

        balanceRotations();

    }

    // Blend the mosaic (alignment has already been done)
    if (blender != NULL)
    {
        blender->runBlend((MosaicFrame **) frames, frames_size, imageMosaicYVU,
                mosaicWidth, mosaicHeight, progress);
    }

    return MOSAIC_RET_OK;
}

ImageType Mosaic::getMosaic(int &width, int &height)
{
    width = mosaicWidth;
    height = mosaicHeight;

    return imageMosaicYVU;
}



int Mosaic::balanceRotations()
{
    // Normalize to the mean angle of rotation (Smiley face)
    double sineAngle = 0.0;

    for (int i = 0; i < frames_size; i++) sineAngle += frames[i]->trs[0][1];
    sineAngle /= frames_size;
    // Calculate the cosineAngle (1 - sineAngle*sineAngle) = cosineAngle*cosineAngle
    double cosineAngle = sqrt(1.0 - sineAngle*sineAngle);
    double m[3][3] = {
        { cosineAngle, -sineAngle, 0 },
        { sineAngle, cosineAngle, 0},
        { 0, 0, 1}};
    double tmp[3][3];

    for (int i = 0; i < frames_size; i++) {
        memcpy(tmp, frames[i]->trs, sizeof(tmp));
        mult33d(frames[i]->trs, m, tmp);
    }

    return MOSAIC_RET_OK;
}
