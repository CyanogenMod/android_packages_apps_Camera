package com.android.camera.gallery;

import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;

public class MockImageList implements IImageList {

    private final ArrayList<IImage> mList = new ArrayList<IImage>();

    public void checkThumbnail(int index) {
    }

    public void deactivate() {
    }

    public HashMap<String, String> getBucketIds() {
        return null;
    }

    public int getCount() {
        return mList.size();
    }

    public IImage getImageAt(int i) {
        return mList.get(i);
    }

    public IImage getImageForUri(Uri uri) {
        return null;
    }

    public int getImageIndex(IImage image) {
        return mList.indexOf(image);
    }

    public boolean isEmpty() {
        return mList.isEmpty();
    }

    public boolean removeImage(IImage image) {
        return mList.remove(image);
    }

    public boolean removeImageAt(int i) {
        return mList.remove(i) != null;
    }

    public void addImage(MockImage image) {
        mList.add(image);
        image.setContainer(this);
    }
}
