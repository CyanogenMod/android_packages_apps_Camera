package com.android.camera.ui;

import static com.android.camera.ui.GLRootView.dpToPixel;
import android.content.Context;

import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.R;
import com.android.camera.Util;

import java.util.ArrayList;

public class PreferenceAdapter
        implements GLListView.Model, GLListView.OnItemSelectedListener {

    private static final int ICON_NONE = 0;
    private static final int HORIZONTAL_PADDINGS = 4;
    private static final int VERTICAL_PADDINGS = 2;

    private static int sHorizontalPaddings = -1;
    private static int sVerticalPaddings;

    private final ArrayList<GLView> mContent = new ArrayList<GLView>();
    private final ListPreference mPreference;
    private String mOverride;

    private static void initializeStaticVariable(Context context) {
        if (sHorizontalPaddings >= 0) return;
        sHorizontalPaddings = dpToPixel(context, HORIZONTAL_PADDINGS);
        sVerticalPaddings = dpToPixel(context, VERTICAL_PADDINGS);
    }

    public PreferenceAdapter(Context context, ListPreference preference) {
        initializeStaticVariable(context);
        mPreference = preference;
        generateContent(context, preference);
    }

    public void overrideSettings(String settings) {
        if (Util.equals(settings, mOverride)) return;
        mOverride = settings;

        CharSequence[] values = mPreference.getEntryValues();
        String value = mPreference.getValue();
        if (settings == null) {
            for (int i = 1, n = mContent.size(); i < n; ++i) {
                GLOptionItem item = (GLOptionItem) mContent.get(i);
                item.setChecked(values[i - 1].equals(value));
                item.setEnabled(true);
            }
        } else {
            for (int i = 1, n = mContent.size(); i < n; ++i) {
                GLOptionItem item = (GLOptionItem) mContent.get(i);
                boolean checked = values[i - 1].equals(settings);
                item.setChecked(checked);
                item.setEnabled(checked);
            }
        }
    }

    private void generateContent(Context context, ListPreference preference) {
        GLOptionHeader header = new GLOptionHeader(context, preference);
        header.setBackground(new NinePatchTexture(
                context, R.drawable.optionheader_background));
        header.setPaddings(sHorizontalPaddings,
                sVerticalPaddings, sHorizontalPaddings, sVerticalPaddings);
        mContent.add(header);
        CharSequence[] entries = preference.getEntries();
        CharSequence[] values = preference.getEntryValues();
        String value = preference.getValue();
        int [] icons = null;
        if (preference instanceof IconListPreference) {
            IconListPreference iPref = (IconListPreference) preference;
            icons = iPref.getIconIds();
        }
        for (int i = 0, n = entries.length; i < n; ++i) {
            GLOptionItem item = new GLOptionItem(
                    context, icons == null ? ICON_NONE : icons[i],
                    entries[i].toString());
            item.setPaddings(sHorizontalPaddings,
                    sVerticalPaddings, sHorizontalPaddings, sVerticalPaddings);
            item.setChecked(values[i].equals(value));
            mContent.add(item);
        }
    }

    public void onItemSelected(GLView view, int position) {
        if (mOverride != null) return;
        ListPreference pref = mPreference;
        CharSequence[] values = pref.getEntryValues();
        if (position < values.length + 1) {
            int index = position - 1;
            int oldIndex = pref.findIndexOfValue(pref.getValue());
            if (oldIndex != index) {
                pref.setValueIndex(index);
                ((GLOptionItem) mContent.get(1 + oldIndex)).setChecked(false);
                ((GLOptionItem) view).setChecked(true);
            }
            return;
        }
    }

    public GLView getView(int index) {
        return mContent.get(index);
    }

    public boolean isSelectable(int index) {
        return mContent.get(index) instanceof GLOptionItem;
    }

    public int size() {
        return mContent.size();
    }
}
