package com.android.camera.gallery;

import android.test.AndroidTestCase;

public class LruCacheUnitTests extends AndroidTestCase {

    public void testPut() {
        LruCache<Integer, Integer> cache = new LruCache<Integer, Integer>(2);
        Integer key = Integer.valueOf(1);
        Integer value = Integer.valueOf(3);
        cache.put(key, value);
        assertEquals(value, cache.get(key));
    }

    public void testTracingInUsedObject() {
        LruCache<Integer, Integer> cache = new LruCache<Integer, Integer>(2);
        Integer key = Integer.valueOf(1);
        Integer value = new Integer(3);
        cache.put(key, value);
        for (int i = 0; i < 3; ++i) {
            cache.put(i + 10, i * i);
        }
        System.gc();
        assertEquals(value, cache.get(key));
    }

    public void testLruAlgorithm() {
        LruCache<Integer, Integer> cache = new LruCache<Integer, Integer>(2);
        cache.put(0, new Integer(0));
        for (int i = 0; i < 3; ++i) {
            cache.put(i + 1, i * i);
            cache.get(0);
        }
        System.gc();
        assertEquals(Integer.valueOf(0), cache.get(0));
    }
}
