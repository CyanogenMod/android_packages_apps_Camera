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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.SimpleAdapter;

import com.android.camera.ListPreference;
import com.android.camera.R;
import com.android.camera.Util;

import java.util.ArrayList;
import java.util.HashMap;

/* A knob setting control */
// Changed to popup on CM, but retaining the name for compatibility
public class InLineSettingKnob extends InLineSettingItem implements View.OnClickListener {
    private static final String TAG = "InLineSettingKnob";
    private Button mButton;
    private MiscSettingPopup mPopup;

    OnItemClickListener mItemClickedListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view,
                int index, long id) {
            changeIndex(index);
            dismiss();
        }

    };

    public InLineSettingKnob(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mButton = (Button) findViewById(R.id.setting_button);
        mButton.setOnClickListener(this);
    }

    @Override
    public void initialize(ListPreference preference,
            ViewGroup parent, OtherSettingsPopup parentPopup) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup root = (ViewGroup) parent.getRootView().findViewById(R.id.frame_layout);
        mPopup = (MiscSettingPopup) inflater.inflate(
                R.layout.misc_setting_popup, root, false);

        Context context = getContext();
        CharSequence[] entries = preference.getEntries();

        // Prepare the ListView.
        ArrayList<HashMap<String, Object>> listItem =
            new ArrayList<HashMap<String, Object>>();
        for(int i = 0; i < entries.length; ++i) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("text", entries[i].toString());
            listItem.add(map);
        }
        SimpleAdapter adapter = new SimpleAdapter(context, listItem,
                R.layout.setting_item,
                new String[] {"text"},
                new int[] {R.id.text});

        mPopup.setAdapter(adapter);
        mPopup.setOnItemClickListener(mItemClickedListener);
        root.addView(mPopup);

        // Initialize parent later because it relies on mPopup existing
        super.initialize(preference, parent, parentPopup);
    }

    protected void updateView() {
        if (mOverrideValue == null) {
            mButton.setText(mPreference.getEntry());
            mButton.setEnabled(true);
            Log.v(TAG, "updateView " + mPopup + " " + mIndex);
            mPopup.setSelection(mIndex);
        } else {
            int index = mPreference.findIndexOfValue(mOverrideValue);
            if (index != -1) {
                mButton.setText(mPreference.getEntries()[index]);
                mButton.setEnabled(false);
            } else {
                // Avoid the crash if camera driver has bugs.
                Log.e(TAG, "Fail to find override value=" + mOverrideValue);
                mPreference.print();
            }
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        event.getText().add(mPreference.getTitle());
    }

    @Override
    public void setOrientation(int orientation) {
        super.setOrientation(orientation);
        if (mPopup != null) {
            mPopup.setOrientation(orientation);
        }
    }

    @Override
    public void onClick(View v) {
        if (!dismiss()) {
            mParentPopup.dismissAll();
            mPopup.setVisibility(View.VISIBLE);
            mPopup.setOrientation(getOrientation());
        }
    }

    @Override
    public boolean dismiss() {
        if (mPopup.isShown()) {
            mPopup.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    @Override
    public boolean dismissOutside(MotionEvent m) {
      // Dismiss the popup window if is shown and is clicked outside
      if (mPopup.isShown() && !Util.pointInView(m.getX(), m.getY(), mPopup)) {
          dismiss();
          return true;
      }
      return false;
    }

}
