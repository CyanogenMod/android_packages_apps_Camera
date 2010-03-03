package com.android.camera.ui;

import android.content.Context;

import com.android.camera.ListPreference;
import com.android.camera.R;

import java.util.HashMap;

public class OtherSettingsIndicator extends AbstractIndicator {

    private final ListPreference mPreference[];
    private final PreferenceAdapter mAdapters[];
    private ResourceTexture mIcon;
    private GLListView mPopupContent;
    private final HashMap<String, String> mOverrides = new HashMap<String, String>();

    public OtherSettingsIndicator(
            Context context, ListPreference preference[]) {
        super(context);
        mPreference = preference;
        mAdapters = new PreferenceAdapter[preference.length];
    }

    @Override
    protected ResourceTexture getIcon() {
        if (mIcon == null) {
            Context context = getGLRootView().getContext();
            mIcon = new ResourceTexture(
                    context, R.drawable.ic_viewfinder_settings);
        }
        return mIcon;
    }

    @Override
    public void overrideSettings(String key, String value) {
        if (value == null) {
            mOverrides.remove(key);
        } else {
            mOverrides.put(key, value);
        }
        if (mPopupContent != null) {
            ListPreference prefs[] = mPreference;
            for (int i = 0, n = prefs.length; i < n; ++i) {
                if (!prefs[i].getKey().equals(key)) continue;
                mAdapters[i].overrideSettings(value);
                break;
            }
        }
    }

    private UberAdapter buildUberAdapter() {
        ListPreference prefs[] = mPreference;
        PreferenceAdapter adapters[] = mAdapters;
        Context context = getGLRootView().getContext();
        for (int i = 0, n = prefs.length; i < n; ++i) {
            adapters[i] = new PreferenceAdapter(context, prefs[i]);
            String override = mOverrides.get(prefs[i].getKey());
            if (override != null) adapters[i].overrideSettings(override);
        }
        return new UberAdapter();
    }

    @Override
    public GLView getPopupContent() {
        if (mPopupContent == null) {
            Context context = getGLRootView().getContext();
            mPopupContent = new GLListView();
            mPopupContent.setHighLight(new NinePatchTexture(
                    context, R.drawable.optionitem_highlight));
            mPopupContent.setScroller(new NinePatchTexture(
                    context, R.drawable.scrollbar_handle_vertical));
            UberAdapter adapter = buildUberAdapter();
            mPopupContent.setOnItemSelectedListener(adapter);
            mPopupContent.setDataModel(adapter);
        }
        return mPopupContent;
    }

    private class UberAdapter implements
            GLListView.Model, GLListView.OnItemSelectedListener {

        public GLView getView(int index) {
            for (PreferenceAdapter adapter : mAdapters) {
                if (index < adapter.size()) {
                    return adapter.getView(index);
                }
                index -= adapter.size();
            }
            return null;
        }

        public boolean isSelectable(int index) {
            for (PreferenceAdapter adapter : mAdapters) {
                if (index < adapter.size()) {
                    return adapter.isSelectable(index);
                }
                index -= adapter.size();
            }
            return true;
        }

        public int size() {
            int size = 0;
            for (PreferenceAdapter adapter : mAdapters) {
                size += adapter.size();
            }
            return size;
        }

        public void onItemSelected(GLView view, int position) {
            for (PreferenceAdapter adapter : mAdapters) {
                if (position < adapter.size()) {
                    adapter.onItemSelected(view, position);
                    return;
                }
                position -= adapter.size();
            }
        }
    }
}
