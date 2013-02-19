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
package com.android.camera;

import com.android.camera.ScriptC_HdrSoftware;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Matrix4f;
import android.renderscript.ProgramFragment;
import android.renderscript.ProgramFragmentFixedFunction;
import android.renderscript.ProgramStore;
import android.renderscript.ProgramVertex;
import android.renderscript.ProgramVertexFixedFunction;
import android.renderscript.RenderScript;
import android.renderscript.Sampler;
import android.util.Log;

/**
 * RenderScript host class for HdrSoftware RenderScript.
 */
public class HdrSoftwareRS {
    private RenderScript mRS;
    private ScriptC_HdrSoftware mScript;
    private Allocation[] mInBitmapAlloc;
    private Allocation mOutBitmapAlloc;
    private Bitmap mOutBitmap;

    public final static int BITMAP_LOW = 0;
    public final static int BITMAP_MID = 1;
    public final static int BITMAP_HI = 2;
    public final static String TAG = "HdrSoftwareRS";

    /**
     * Default constructor
     * @param rs The host RenderScriptGL pointer
     * @param res
     * @param resId
     */
    public HdrSoftwareRS(RenderScript rs, Resources res, int resId) {
        mRS = rs;
        mScript = new ScriptC_HdrSoftware(rs, res, resId);
        mInBitmapAlloc = new Allocation[3];
    }

    /**
     * Run the processing
     */
    public void process() {
        // We run the script...
        mScript.invoke_performHdrComputation();

        // And we copy the output to a bitmap
        mOutBitmapAlloc.copyTo(mOutBitmap);
    }

    /**
     * Returns a Bitmap containing a copy of the Output buffer allocation
     */
    public Bitmap getOutput() {
        return mOutBitmap;
    }

    /**
     * Set the input bitmaps for the processing.
     * @param bm_lo Bitmap of the picture with the lowest exposure
     * @param bm_mid Bitmap of the picture with normal exposure
     * @param bm_hi Bitmap of the picture with highest exposure
     */
    public void setBitmapInput(Bitmap bm_lo, Bitmap bm_mid, Bitmap bm_hi) {
        if (bm_lo == null || bm_mid == null || bm_hi == null) {
            Log.e(TAG, "Cannot set HdrSoftware input bitmaps: One of the input is null");
            return;
	}

        // We allocate and copy the bitmaps for the RS script
        for (int i = 0; i < 3; i++) {
            if (mInBitmapAlloc[i] != null) {
                mInBitmapAlloc[i].destroy();
            }
        }

        mInBitmapAlloc[BITMAP_LOW] = Allocation.createFromBitmap(mRS, bm_lo,  Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        mInBitmapAlloc[BITMAP_MID] = Allocation.createFromBitmap(mRS, bm_mid, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        mInBitmapAlloc[BITMAP_HI]  = Allocation.createFromBitmap(mRS, bm_hi,  Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

        // We make the output bitmap based on the inputs.
        // We don't really care about the content at this point, we just need the same size
        // and pixel depth.
        mOutBitmapAlloc = Allocation.createTyped(mRS, mInBitmapAlloc[BITMAP_LOW].getType());

        // Bind our allocations to our script
        mScript.set_gInputLow(mInBitmapAlloc[BITMAP_LOW]);
        mScript.set_gInputMid(mInBitmapAlloc[BITMAP_MID]);
        mScript.set_gInputHi(mInBitmapAlloc[BITMAP_HI]);
        mScript.set_gOutput(mOutBitmapAlloc);

        // We prepare our local output bitmap, we can copy the format and size from the
        // current inputs
        mOutBitmap = Bitmap.createBitmap(bm_lo.getWidth(), bm_lo.getHeight(), bm_lo.getConfig());
    }
}

