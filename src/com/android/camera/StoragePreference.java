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

import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.AttributeSet;

public class StoragePreference extends ListPreference {

    private Context mContext;

    public StoragePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        buildStorage();
    }

    private void buildStorage() {
        StorageManager sm = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        StorageVolume[] volumes = sm.getVolumeList();
        String[] entries = new String[volumes.length];
        String[] entryValues = new String[volumes.length];
        int primary = 0;

        for (int i = 0; i < volumes.length; i++) {
            StorageVolume v = volumes[i];
            entries[i] = v.getDescription(mContext);
            entryValues[i] = v.getPath();
            if (v.isPrimary()) {
                primary = i;
            }
        }
        setEntries(entries);
        setEntryValues(entryValues);

        // Filter saved invalid value
        if (findIndexOfValue(getValue()) < 0) {
            // Default to the primary storage
            setValueIndex(primary);
        }
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        Storage.getStorage().setRoot(getValue());
    }

    @Override
    public void setValueIndex(int index) {
        super.setValueIndex(index);
        Storage.getStorage().setRoot(getValue());
    }

}
