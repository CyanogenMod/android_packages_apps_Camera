package com.android.camera.gallery;

import com.android.camera.ImageManager;

import android.test.AndroidTestCase;

public class ImageListUberUnitTests extends AndroidTestCase {

    public void testTheOrderOfGetImageAt() {
        MockImageList listA = new MockImageList();
        MockImageList listB = new MockImageList();
        listA.addImage(new MockImage(2, 2));
        listA.addImage(new MockImage(0, 0));
        listB.addImage(new MockImage(1, 1));
        ImageListUber uber = new ImageListUber(
                new IImageList[] {listA, listB}, ImageManager.SORT_DESCENDING);

        assertEquals(2, uber.getImageAt(0).fullSizeImageId());
        assertEquals(1, uber.getImageAt(1).fullSizeImageId());
        assertEquals(0, uber.getImageAt(2).fullSizeImageId());
        uber.close();

        uber = new ImageListUber(
                new IImageList[] {listA, listB}, ImageManager.SORT_DESCENDING);
        assertEquals(2, uber.getImageAt(0).fullSizeImageId());
        assertEquals(1, uber.getImageAt(1).fullSizeImageId());
        assertEquals(0, uber.getImageAt(2).fullSizeImageId());
        uber.close();
    }

    public void testTheOrderOfGetImageAtCaseTwo() {
        MockImageList listA = new MockImageList();
        MockImageList listB = new MockImageList();
        listA.addImage(new MockImage(2, 2));
        listA.addImage(new MockImage(1, 1));
        listB.addImage(new MockImage(0, 0));
        ImageListUber uber = new ImageListUber(
                new IImageList[] {listB, listA}, ImageManager.SORT_DESCENDING);

        assertEquals(2, uber.getImageAt(0).fullSizeImageId());
        assertEquals(1, uber.getImageAt(1).fullSizeImageId());
        assertEquals(0, uber.getImageAt(2).fullSizeImageId());
        uber.close();

        uber = new ImageListUber(
                new IImageList[] {listA, listB}, ImageManager.SORT_DESCENDING);

        assertEquals(2, uber.getImageAt(0).fullSizeImageId());
        assertEquals(1, uber.getImageAt(1).fullSizeImageId());
        assertEquals(0, uber.getImageAt(2).fullSizeImageId());
        uber.close();
    }

    public void testRemoveImage() {
        MockImageList listA = new MockImageList();
        MockImageList listB = new MockImageList();
        MockImage target = new MockImage(1, 1);
        listA.addImage(new MockImage(2, 2));
        listA.addImage(new MockImage(0, 0));
        listB.addImage(target);
        ImageListUber uber = new ImageListUber(
                new IImageList[] {listB, listA}, ImageManager.SORT_DESCENDING);
        uber.removeImage(target);
        assertEquals(2, uber.getImageAt(0).fullSizeImageId());
        assertEquals(0, uber.getImageAt(1).fullSizeImageId());

        assertEquals(0, uber.getImageIndex(uber.getImageAt(0)));
        assertEquals(1, uber.getImageIndex(uber.getImageAt(1)));
        uber.close();
    }

    public void testRemoveImageAt() {
        MockImageList listA = new MockImageList();
        MockImageList listB = new MockImageList();
        MockImage target = new MockImage(1, 1);
        listA.addImage(new MockImage(2, 2));
        listA.addImage(new MockImage(0, 0));
        listB.addImage(target);
        ImageListUber uber = new ImageListUber(
                new IImageList[] {listB, listA}, ImageManager.SORT_DESCENDING);
        uber.removeImageAt(1);
        assertEquals(2, uber.getImageAt(0).fullSizeImageId());
        assertEquals(0, uber.getImageAt(1).fullSizeImageId());

        assertEquals(0, uber.getImageIndex(uber.getImageAt(0)));
        assertEquals(1, uber.getImageIndex(uber.getImageAt(1)));
        uber.close();
    }
}
