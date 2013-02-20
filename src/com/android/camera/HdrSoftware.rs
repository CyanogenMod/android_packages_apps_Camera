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

rs_script gScript;

rs_allocation gInIndex;
const uchar4* gInputLow;
const uchar4* gInputMid;
const uchar4* gInputHi;
uchar4* gOutput;

int gImageWidth;
int gImageHeight;

void init() {
	rsDebug("HDR init", rsUptimeMillis());
}

void root(const int32_t* v_in, int32_t* v_out) {
	// Get the row from the input
	int32_t y = *v_in;

	// Compute the average of each pixels from the 3 image samples
	float3 pxOut;
	const float lwhite = 2.4f;
	const float thres_bk = 0.05f;
	const float thres_wh = 0.95f;

	for (int x = 0; x < gImageWidth; x++) {
		const int32_t index = y+x;

		// Gather the pixels
		float3 pxLow = rsUnpackColor8888(gInputLow[index]).rgb;
		float3 pxMid = rsUnpackColor8888(gInputMid[index]).rgb;
		float3 pxHi = rsUnpackColor8888(gInputHi[index]).rgb;

		// Compute Reinhard global
		// We do a little appendix here, by using only relevant maps
		// (ie. don't take black or white pixels twice in mid+hi).
		float sumR = pxLow.r;
		if (!(pxMid.r < thres_bk && pxLow.r < thres_bk))
			sumR += pxMid.r;
		if (!(pxMid.r > thres_wh && pxHi.r > thres_wh))
			sumR += pxHi.r;

		float sumG = pxLow.g;
		if (!(pxMid.g < thres_bk && pxLow.g < thres_bk))
			sumG += pxMid.g;
		if (!(pxMid.g > thres_wh && pxHi.g > thres_wh))
			sumG += pxHi.g;

		float sumB = pxLow.b;
		if (!(pxMid.b < thres_bk && pxLow.b < thres_bk))
			sumB += pxMid.b;
		if (!(pxMid.b > thres_wh && pxHi.b > thres_wh))
			sumB += pxHi.b;


		// Compute the pixel's luminance
		float lum = 0.2125 * sumR + 0.7154 * sumG + 0.0721 * sumB;
		float key = (lum * (1 + lum/lwhite))/(1 + lum);
		float scale = key/lum;

		// Compute the final pixel
		float red = sumR * scale;
		float green = sumG * scale;
		float blue = sumB * scale;

		pxOut.r = (red > 1.0f   ? 1.0f : red);
		pxOut.g = (green > 1.0f ? 1.0f : green);
		pxOut.b = (blue > 1.0f  ? 1.0f : blue);

		// Copy the pixel to the output image
		gOutput[index] = rsPackColorTo8888(pxOut);
	}

	// Done computing this row in the output allocation
}



void performHdrComputation() {
	if (gInputLow == 0 || gInputMid == 0
		|| gInputHi == 0 || gOutput == 0) {
		// TODO: Compute if there are only 2 images
		rsDebug("There are pointers missing, skipping rendering.", rsUptimeMillis());
	}
	else {
		// v_out is not used, so we pass gInIndex again
		rsForEach(gScript, gInIndex, gInIndex);
	}
}
