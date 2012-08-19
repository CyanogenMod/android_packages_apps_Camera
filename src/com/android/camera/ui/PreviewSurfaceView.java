/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.android.gallery3d.common.ApiHelper;

public class PreviewSurfaceView extends SurfaceView {
    public PreviewSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setZOrderMediaOverlay(true);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void shrink() {
        setLayoutSize(1);
    }

    public void expand() {
        setLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void setLayoutSize(int size) {
        ViewGroup.LayoutParams p = getLayoutParams();
        if (p.width != size || p.height != size) {
            p.width = size;
            p.height = size;
            setLayoutParams(p);
        }
    }
}
