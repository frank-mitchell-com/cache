package com.frank_mitchell.cache.spi;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

class ConcurrentHashSet<K> extends AbstractSet<K> {

    ConcurrentHashMap<K, Boolean> _map = new ConcurrentHashMap<>();

    @Override
    public boolean add(K e) {
        return (_map.put(e, Boolean.TRUE) == null);
    }

    @Override
    public Iterator<K> iterator() {
        return _map.keySet().iterator();
    }

    @Override
    @SuppressWarnings("element-type-mismatch")
    public boolean remove(Object o) {
        return (_map.remove(o) != null);
    }

    @Override
    public int size() {
        return _map.size();
    }
}