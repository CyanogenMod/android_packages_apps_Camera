/*
 * Copyright (C) 2013 The CyanogenMod Project
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

#pragma version(1)
#pragma rs java_package_name(com.android.camera)

#include "rs_graphics.rsh"

rs_allocation gInputLow;
rs_allocation gInputMid;
rs_allocation gInputHi;
rs_allocation gOutput;

void init() {
	rsDebug("HDR init", rsUptimeMillis());
}

void performHdrComputation() {
	if (gInputLow.p == 0 || gInputMid.p == 0
		|| gInputHi.p == 0 || gOutput.p == 0) {
		// TODO: Compute if there are only 2 images
		rsDebug("There are pointers missing, skipping rendering.", rsUptimeMillis());
	}
	else {
		// We assume all three images has same size
		int dimX = rsAllocationGetDimX(gInputLow);
		int dimY = rsAllocationGetDimY(gInputLow);

		// Compute the average of each pixels from the 3 image samples
		for (int y = 0; y < dimY; y++) {
			for (int x = 0; x < dimX; x++) {
				// Gather the pixels
				uchar4* ptrOut = (uchar4*)rsGetElementAt(gOutput, x, y);
				uchar4* ptrLow = (uchar4*)rsGetElementAt(gInputLow, x, y);
				uchar4* ptrMid = (uchar4*)rsGetElementAt(gInputMid, x, y);
				uchar4* ptrHi = (uchar4*)rsGetElementAt(gInputHi, x, y);

				float3 pxLow = rsUnpackColor8888(*ptrLow).rgb;
				float3 pxMid = rsUnpackColor8888(*ptrMid).rgb;
				float3 pxHi = rsUnpackColor8888(*ptrHi).rgb;

				// Compute the average
				float4 pxOut;
				pxOut.r = (pxLow.r + pxMid.r + pxHi.r)/3.0f;
				pxOut.g = (pxLow.g + pxMid.g + pxHi.g)/3.0f;
				pxOut.b = (pxLow.b + pxMid.b + pxHi.b)/3.0f;
                                pxOut.a = 1.0;

				// Copy the pixel to the output image
				*ptrOut = rsPackColorTo8888(pxOut);
			}
		}

		// Done computing the image in the output allocation
	}
}

int root() {
	return 0;
}
