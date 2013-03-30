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

#define LOG_NDEBUG 0
#define LOG_NIDEBUG 0
#define LOG_TAG "QCameraHWI_Display"
#include <utils/Log.h>
#include <utils/threads.h>
#include <fcntl.h>
#include <sys/mman.h>

#include "QCameraHAL.h"
#include "QCameraHWI.h"
#include "QCameraHWI_Display.h"


namespace android {

int QCameraDisplay_Overlay::Display_prepare_buffers()
{
	return 0;
}


int QCameraDisplay_Overlay::Display_set_crop()
{
	return 0;
}


int QCameraDisplay_Overlay::Display_set_geometry()
{
	return 0;
}


void QCameraDisplay_Overlay::Display_enqueue()
{
	return ;
}



void QCameraDisplay_Overlay::Display_dequeue()
{
	return ;
}


void QCameraDisplay_Overlay::Display_release_buffers()
{
	return ;
}

QCameraDisplay::~QCameraDisplay(){}

QCameraDisplay_Overlay::~QCameraDisplay_Overlay()
{
	return ;
}
}; // namespace android
