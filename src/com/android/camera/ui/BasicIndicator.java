package com.android.camera.ui;

import android.content.Context;

import com.android.camera.IconListPreference;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.ui.GLListView.OnItemSelectedListener;

public class BasicIndicator extends AbstractIndicator {

    private final ResourceTexture mIcon[];
    private final IconListPreference mPreference;
    protected int mIndex;
    private GLListView mPopupContent;
    private PreferenceAdapter mModel;
    private String mOverride;

    public BasicIndicator(Context context, IconListPreference preference) {
        super(context);
        mPreference = preference;
        mIcon = new ResourceTexture[preference.getIconIds().length];
        mIndex = preference.findIndexOfValue(preference.getValue());
    }

    @Override
    public void overrideSettings(String key, String settings) {
        IconListPreference pref = mPreference;
        if (!pref.getKey().equals(key)) return;
        if (Util.equals(mOverride, settings)) return;

        mOverride = settings;
        int index = pref.findIndexOfValue(
                settings == null ? pref.getValue() : settings);
        if (mIndex != index) {
            mIndex = index;
            invalidate();
        }
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
            mModel = new PreferenceAdapter(context, mPreference);
            mPopupContent.setOnItemSelectedListener(new MyListener(mModel));
            mPopupContent.setDataModel(mModel);
        }
        mModel.overrideSettings(mOverride);
        return mPopupContent;
    }

    protected void onPreferenceChanged(int newIndex) {
        mIndex = newIndex;
        invalidate();
    }

    private class MyListener implements OnItemSelectedListener {

        private final PreferenceAdapter mAdapter;

        public MyListener(PreferenceAdapter adapter) {
            mAdapter = adapter;
        }

        public void onItemSelected(GLView view, int position) {
            mAdapter.onItemSelected(view, position);
            onPreferenceChanged(position - 1);
        }
    }

    @Override
    protected ResourceTexture getIcon() {
        int index = mIndex;
        if (mIcon[index] == null) {
            IconListPreference pref = mPreference;
            Context context = getGLRootView().getContext();
            mIcon[index] = new ResourceTexture(
                    context, mPreference.getLargeIconIds()[index]);
        }
        return mIcon[index];
    }
}
