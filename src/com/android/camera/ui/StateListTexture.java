package com.android.camera.ui;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL11;

public class StateListTexture extends FrameTexture {

    private int mIndex;
    private final ArrayList<Entry> mEntries = new ArrayList<Entry>();

    private static class Entry {
        public int mMustHave;
        public int mMustNotHave;
        public FrameTexture mTexture;

        public Entry(int mustHave, int mustNotHave, FrameTexture texture) {
            mMustHave = mustHave;
            mMustNotHave = mustNotHave;
            mTexture = texture;
        }
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        mEntries.get(mIndex).mTexture.freeBitmap(bitmap);
    }

    @Override
    protected Bitmap getBitmap() {
        return mEntries.get(mIndex).mTexture.getBitmap();
    }

    private int getStateIndex(int state) {
        for (int i = 0, n = mEntries.size(); i < n; ++i) {
            Entry entry = mEntries.get(i);
            if ((entry.mMustHave & state) == entry.mMustHave
                    && (state & entry.mMustNotHave) == 0) return i;
        }
        return -1;
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        for (Entry entry : mEntries) {
            entry.mTexture.setSize(width, height);
        }
    }

    public boolean isDifferent(int stateA, int stateB) {
        return getStateIndex(stateA) != getStateIndex(stateB);
    }

    public boolean setState(int state) {
        int oldIndex = mIndex;
        mIndex = getStateIndex(state);
        return mIndex != oldIndex;
    }

    @Override
    public boolean bind(GLRootView root, GL11 gl) {
        if (mIndex < 0) return false;
        return mEntries.get(mIndex).mTexture.bind(root, gl);
    }

    public void addState(
            int mustHave, int mustNotHave, FrameTexture texture) {
        mEntries.add(new Entry(mustHave, mustNotHave, texture));
    }

    @Override
    public Rect getPaddings() {
        return mEntries.get(mIndex).mTexture.getPaddings();
    }
}
