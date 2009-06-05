package com.android.camera.gallery;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {

    private final HashMap<K, V> mLruMap;
    private final HashMap<K, WeakReference<V>> mWeakMap =
            new HashMap<K, WeakReference<V>>();

    //TODO: use ReferenceQueue to clean the cache
    private final static int MAXIMUM_ADD_BEFORE_SWEEP = 128;
    private int mAddBeforeSweep = 0;

    public LruCache(final int capacity) {
        mLruMap = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V put(K key, V value) {
        if (++mAddBeforeSweep > MAXIMUM_ADD_BEFORE_SWEEP) {
            mAddBeforeSweep = 0;
            Iterator<Map.Entry<K, WeakReference<V>>> iter =
                    mWeakMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<K, WeakReference<V>> entry = iter.next();
                if (entry.getValue().get() == null) iter.remove();
            }
        }
        mLruMap.put(key, value);
        WeakReference<V> ref = mWeakMap.put(key, new WeakReference<V>(value));
        return ref == null ? null : ref.get();
    }

    public V get(K key) {
        V value = mLruMap.get(key);
        if (value != null) return value;
        WeakReference<V> ref = mWeakMap.get(key);
        value = ref == null ? null : ref.get();
        if (value == null && ref != null) {
            mWeakMap.remove(key);
        }
        return value;
    }

    public void clear() {
        mLruMap.clear();
        mWeakMap.clear();
    }
}
