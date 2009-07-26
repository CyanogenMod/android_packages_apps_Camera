package com.android.camera.gallery;

import android.test.AndroidTestCase;
import android.util.Log;

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

    private static final int TEST_COUNT = 10000;

    static class Accessor extends Thread {
        private final LruCache<Integer,Integer> mMap;

        public Accessor(LruCache<Integer, Integer> map) {
            mMap = map;
        }

        @Override
        public void run() {
            Log.v("TAG", "start get " + this);
            for (int i = 0; i < TEST_COUNT; ++i) {
                mMap.get(i % 2);
            }
            Log.v("TAG", "finish get " + this);
        }
    }

    @SuppressWarnings("unchecked")
    public void testConcurrentAccess() throws Exception {
        LruCache<Integer, Integer> cache = new LruCache<Integer, Integer>(4);
        cache.put(0, 0);
        cache.put(1, 1);
        Accessor accessor[] = new Accessor[4];
        for (int i = 0; i < accessor.length; ++i) {
            accessor[i] = new Accessor(cache);
        }
        for (int i = 0; i < accessor.length; ++i) {
            accessor[i].start();
        }
        for (int i = 0; i < accessor.length; ++i) {
            accessor[i].join();
        }
    }
}
