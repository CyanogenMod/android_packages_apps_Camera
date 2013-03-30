/*
** Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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

#ifndef ANDROID_HARDWARE_QCAMERAHWI_DISPLAY_H
#define ANDROID_HARDWARE_QCAMERAHWI_DISPLAY_H


#include <utils/threads.h>

#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <utils/threads.h>

extern "C" {

#include <camera.h>
//#include <camera_defs_i.h>
#include <mm_camera_interface2.h>
}

namespace android {

/*===============================
	Base Display Class
================================*/

class QCameraDisplay {

public:
	virtual int Display_prepare_buffers() = 0;
	virtual int Display_set_crop( ) = 0;
	virtual int Display_set_geometry( ) =0;
	virtual void Display_enqueue( ) = 0;
	virtual void Display_dequeue( ) = 0;
	virtual void Display_release_buffers( ) =0;
	virtual ~QCameraDisplay( );
};

/*================================
	Overlay Derivative
==================================*/
class QCameraDisplay_Overlay: public QCameraDisplay {

public:
	int Display_prepare_buffers();
	int Display_set_crop( );
	int Display_set_geometry( );
	void Display_enqueue( );
	void Display_dequeue( );
	void Display_release_buffers( );
	virtual ~QCameraDisplay_Overlay( );


};


}; // namespace android

#endif
