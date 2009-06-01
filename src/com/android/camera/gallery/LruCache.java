package com.android.camera.gallery;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> extends LinkedHashMap<K, V> {

    private final int mCapacity;

    public LruCache(int capacity) {
        super(16, 0.75f, true);
        this.mCapacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > mCapacity;
    }
}
