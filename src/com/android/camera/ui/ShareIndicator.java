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

package com.android.camera.ui;

import android.content.Context;

import com.android.camera.R;

class ShareIndicator extends AbstractIndicator {
    private ResourceTexture mIcon;

    public ShareIndicator(Context context) {
        super(context);
        mIcon = new ResourceTexture(context, R.drawable.ic_viewfinder_share);
    }

    @Override
    protected BitmapTexture getIcon() {
        return mIcon;
    }

    @Override
    public GLView getPopupContent() {
        return null;
    }

    @Override
    public void overrideSettings(String key, String settings) {
    }

    @Override
    public void reloadPreferences() {
    }
}
