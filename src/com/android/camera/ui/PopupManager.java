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
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A manager which notifies the event of a new popup in order to dismiss the
 * old popup if exists.
 */
public class PopupManager {
    private static HashMap<Context, PopupManager> sMap =
            new HashMap<Context, PopupManager>();

    public interface OnOtherPopupShowedListener {
        public void onOtherPopupShowed();
    }

    private PopupManager() {}

    private ArrayList<OnOtherPopupShowedListener> mListeners = new ArrayList<OnOtherPopupShowedListener>();

    public void notifyShowPopup(View view) {
        for (OnOtherPopupShowedListener listener : mListeners) {
            if ((View) listener != view) {
                listener.onOtherPopupShowed();
            }
        }
    }

    public void setOnOtherPopupShowedListener(OnOtherPopupShowedListener listener) {
        mListeners.add(listener);
    }

    public static PopupManager getInstance(Context context) {
        PopupManager instance = sMap.get(context);
        if (instance == null) {
            instance = new PopupManager();
            sMap.put(context, instance);
        }
        return instance;
    }

    public static void removeInstance(Context context) {
        PopupManager instance = sMap.get(context);
        sMap.remove(context);
    }
}
