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

import android.view.View;

public interface LayoutChangeNotifier {
    public interface Listener {
        // Invoked only when the layout has changed or it is the first layout.
        public void onLayoutChange(View v, int l, int t, int r, int b);
    }

    public void setOnLayoutChangeListener(LayoutChangeNotifier.Listener listener);
}
