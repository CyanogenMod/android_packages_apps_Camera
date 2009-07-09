package com.android.camera.gallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.InputStream;

public class MockImage implements IImage {
    private final long mId;
    private final long mTakenDate;
    private IImageList mContainer;

    public MockImage(long id, long takenDate) {
        mId = id;
        mTakenDate = takenDate;
    }

    protected void setContainer(IImageList container) {
        this.mContainer = container;
    }

    public Bitmap fullSizeBitmap(int targetWidthOrHeight) {
        return null;
    }

    public Cancelable<Bitmap> fullSizeBitmapCancelable(int targetWidthOrHeight,
            BitmapFactory.Options options) {
        return null;
    }

    public InputStream fullSizeImageData() {
        return null;
    }

    public long fullSizeImageId() {
        return mId;
    }

    public Uri fullSizeImageUri() {
        return null;
    }

    public IImageList getContainer() {
        return mContainer;
    }

    public String getDataPath() {
        return null;
    }

    public long getDateTaken() {
        return mTakenDate;
    }

    public String getDisplayName() {
        return null;
    }

    public int getHeight() {
        return 0;
    }

    public String getMimeType() {
        return null;
    }

    public String getTitle() {
        return null;
    }

    public int getWidth() {
        return 0;
    }

    public boolean isDrm() {
        return false;
    }

    public boolean isReadonly() {
        return false;
    }

    public Bitmap miniThumbBitmap() {
        return null;
    }

    public boolean rotateImageBy(int degrees) {
        return false;
    }

    public void setTitle(String name) {
    }

    public Bitmap thumbBitmap() {
        return null;
    }

    public Uri thumbUri() {
        return null;
    }
}
