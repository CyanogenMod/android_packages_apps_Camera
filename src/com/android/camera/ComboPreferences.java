/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ComboPreferences implements SharedPreferences, OnSharedPreferenceChangeListener {
    private SharedPreferences mPrefGlobal;  // global preferences
    private SharedPreferences mPrefLocal;  // per-camera preferences
    private CopyOnWriteArrayList<OnSharedPreferenceChangeListener> mListeners;
    private static WeakHashMap<Context, ComboPreferences> sMap =
            new WeakHashMap<Context, ComboPreferences>();

    public ComboPreferences(Context context) {
        mPrefGlobal = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefGlobal.registerOnSharedPreferenceChangeListener(this);
        synchronized (sMap) {
            sMap.put(context, this);
        }
        mListeners = new CopyOnWriteArrayList<OnSharedPreferenceChangeListener>();
    }

    public static ComboPreferences get(Context context) {
        synchronized (sMap) {
            return sMap.get(context);
        }
    }

    // Sets the camera id and reads its preferences. Each camera has its own
    // preferences.
    public void setLocalId(Context context, int cameraId) {
        String prefName = context.getPackageName() + "_preferences_" + cameraId;
        if (mPrefLocal != null) {
            mPrefLocal.unregisterOnSharedPreferenceChangeListener(this);
        }
        mPrefLocal = context.getSharedPreferences(
                prefName, Context.MODE_PRIVATE);
        mPrefLocal.registerOnSharedPreferenceChangeListener(this);
    }

    public SharedPreferences getGlobal() {
        return mPrefGlobal;
    }

    public SharedPreferences getLocal() {
        return mPrefLocal;
    }

    @Override
    public Map<String, ?> getAll() {
        throw new UnsupportedOperationException(); // Can be implemented if needed.
    }

    private static boolean isGlobal(String key) {
        return key.equals(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL)
                || key.equals(CameraSettings.KEY_CAMERA_ID)
                || key.equals(CameraSettings.KEY_RECORD_LOCATION)
                || key.equals(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN)
                || key.equals(CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN)
                || key.equals(CameraSettings.KEY_VIDEO_EFFECT);
    }

    @Override
    public String getString(String key, String defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getString(key, defValue);
        } else {
            return mPrefLocal.getString(key, defValue);
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getInt(key, defValue);
        } else {
            return mPrefLocal.getInt(key, defValue);
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getLong(key, defValue);
        } else {
            return mPrefLocal.getLong(key, defValue);
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getFloat(key, defValue);
        } else {
            return mPrefLocal.getFloat(key, defValue);
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getBoolean(key, defValue);
        } else {
            return mPrefLocal.getBoolean(key, defValue);
        }
    }

    // This method is not used.
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(String key) {
        if (mPrefLocal.contains(key)) return true;
        if (mPrefGlobal.contains(key)) return true;
        return false;
    }

    private class MyEditor implements Editor {
        private Editor mEditorGlobal;
        private Editor mEditorLocal;

        MyEditor() {
            mEditorGlobal = mPrefGlobal.edit();
            mEditorLocal = mPrefLocal.edit();
        }

        @Override
        public boolean commit() {
            boolean result1 = mEditorGlobal.commit();
            boolean result2 = mEditorLocal.commit();
            return result1 && result2;
        }

        @Override
        public void apply() {
            mEditorGlobal.apply();
            mEditorLocal.apply();
        }

        // Note: clear() and remove() affects both local and global preferences.
        @Override
        public Editor clear() {
            mEditorGlobal.clear();
            mEditorLocal.clear();
            return this;
        }

        @Override
        public Editor remove(String key) {
            mEditorGlobal.remove(key);
            mEditorLocal.remove(key);
            return this;
        }

        @Override
        public Editor putString(String key, String value) {
            if (isGlobal(key)) {
                mEditorGlobal.putString(key, value);
            } else {
                mEditorLocal.putString(key, value);
            }
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            if (isGlobal(key)) {
                mEditorGlobal.putInt(key, value);
            } else {
                mEditorLocal.putInt(key, value);
            }
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            if (isGlobal(key)) {
                mEditorGlobal.putLong(key, value);
            } else {
                mEditorLocal.putLong(key, value);
            }
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            if (isGlobal(key)) {
                mEditorGlobal.putFloat(key, value);
            } else {
                mEditorLocal.putFloat(key, value);
            }
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            if (isGlobal(key)) {
                mEditorGlobal.putBoolean(key, value);
            } else {
                mEditorLocal.putBoolean(key, value);
            }
            return this;
        }

        // This method is not used.
        @Override
        public Editor putStringSet(String key, Set<String> values) {
            throw new UnsupportedOperationException();
        }
    }

    // Note the remove() and clear() of the returned Editor may not work as
    // expected because it doesn't touch the global preferences at all.
    @Override
    public Editor edit() {
        return new MyEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        for (OnSharedPreferenceChangeListener listener : mListeners) {
            listener.onSharedPreferenceChanged(this, key);
        }
    }
}
