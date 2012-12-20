#pragma version(1)
#pragma rs java_package_name(com.android.camera)

#include "rs_graphics.rsh"

rs_allocation gInputLow;
rs_allocation gInputMid;
rs_allocation gInputHi;
rs_allocation gOutput;

rs_sampler gLinearClamp;
rs_program_fragment gSingleTextureProgramFragment;
rs_program_store gProgramStoreBlendNone;
rs_program_vertex gProgramVertex;

void init() {
	rsDebug("HDR init", rsUptimeMillis());
}

static void performHdrComputation() {
	if (gInputLow.p == 0 || gInputMid.p == 0
		|| gInputHi == 0 || gOutput.p == 0) {
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
				float3 pxOut;
				pxOut.r = (pxLow.r + pxMid.r + pxHi.r)/3.0f;
				pxOut.g = (pxLow.g + pxMid.g + pxHi.g)/3.0f;
				pxOut.b = (pxLow.b + pxMid.b + pxHi.b)/3.0f;

				// Copy the pixel to the output image
				*ptrOut = rsPackColorTo8888(pxOut);
			}
		}

		// Done computing the image in the output allocation
	}
}


int root() {
	rsgBindProgramVertex(gProgramVertex);
	rsgBindProgramFragment(gSingleTextureProgramFragment);
	rsgBindProgramStore(gProgramStoreBlendNone);

	performHdrComputation();
	return 0;
}
