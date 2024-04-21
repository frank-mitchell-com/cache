/*
 * The MIT License
 *
 * Copyright 2023 Frank Mitchell.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.frank_mitchell.cache.spi;

import com.frank_mitchell.cache.Cache;
import com.frank_mitchell.cache.CacheEntry;
import com.frank_mitchell.cache.CacheView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A simple implementation of the {@link Cache} interface.
 * Built under the "simplest thing that can possibly work" philosophy,
 * it may not be the most efficient.
 *
 * @author Frank Mitchell
 *
 * @param <K> type of keys in the cache
 * @param <V> type of values in the cache
 */
public class SimpleCache<K, V> extends AbstractCache<K, V> {
    private final Map<K, SimpleCacheEntry<K, V>> _cache = new HashMap<>();
    private final Clock _clock;

    /**
     * Default constructor
     */
    public SimpleCache() {
        this(Clock.systemUTC());
    }

    /**
     * Debug constructor for a (mock) Clock.
     * 
     * @param clock an adjustable clock for fast unit tests.
     */
    public SimpleCache(Clock clock) {
        _clock = clock;
    }

    @Override
    public int size() {
        synchronized (this) {
            return _cache.size();
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (this) {
            return _cache.isEmpty();
        }
    }

    @Override
    @SuppressWarnings("element-type-mismatch")
    public boolean containsKey(Object o) {
        synchronized (this) {
            return _cache.containsKey(o);
        }
    }

    @Override
    @SuppressWarnings("element-type-mismatch")
    public CacheEntry<K, V> getEntry(Object o) {
        synchronized (this) {
            return _cache.get(o);
        }
    }

    @Override
    public V get(Object key) {
        V result = null;
        synchronized (this) {
            clearExpired();
            SimpleCacheEntry<K, V> e = rawget(key);
            if (e != null) {
                result = e.getValue();
            }
        }
        return result;
    }

    @SuppressWarnings("element-type-mismatch")
    private SimpleCacheEntry<K, V> rawget(Object key) {
        return _cache.get(key);
    }

    @Override
    protected V rawput(K key, V value) {
        V result = null;
        synchronized (this) {
            SimpleCacheEntry<K, V> e = _cache.get(key);
            if (e == null) {
                _cache.put(key, new SimpleCacheEntry<>(key, value));
            } else {
                result = e.setValue(value);
            }
        }
        return result;
    }

    @Override
    public V remove(Object o) {
        synchronized (this) {
            SimpleCacheEntry<K, V> e = rawremove(o);
            if (e == null) {
                return null;
            }
            return e.getValue();
        }
    }

    @SuppressWarnings("element-type-mismatch")
    private SimpleCacheEntry<K, V> rawremove(Object o) {
        return _cache.remove(o);
    }

    @Override
    public void clear() {
        synchronized (this) {
            _cache.clear();
        }
    }

    @Override
    public Collection<CacheView<K, V>> cacheViews() {
        List<CacheView<K, V>> result = new ArrayList<>(_cache.size());
        synchronized (this) {
            for (CacheEntry<K, V> e : _cache.values()) {
                result.add(new SimpleCacheView<>(e));
            }
        }
        return result;
    }

    @Override
    public void clearExpired() {
        SortedMap<Instant, Set<K>> keyByAccess = new TreeMap<>();
        SortedMap<Instant, Set<K>> keyByUpdate = new TreeMap<>();
        synchronized (this) {
            Instant now = _clock.instant();
            Iterator<SimpleCacheEntry<K, V>> iter = _cache.values().iterator();
            while (iter.hasNext()) {
                final SimpleCacheEntry<K, V> e = iter.next();
                final Instant accessExpiry = e.getAccess().plus(getLastAccessLimit());
                final Instant updateExpiry = e.getUpdate().plus(getLastUpdateLimit());
                if (accessExpiry.isBefore(now) || updateExpiry.isBefore(now)) {
                    iter.remove();
                } else {
                    addKey(keyByAccess, e.getAccess(), e.getKey());
                    addKey(keyByUpdate, e.getUpdate(), e.getKey());
                }
            }
            while (_cache.size() > getMaximumSize()) {
                if (isLastAccessedDroppedFirst()) {
                    removeFirstKey(keyByAccess);
                } else {
                    removeFirstKey(keyByUpdate);
                }
            }
        }
    }
    
    private void addKey(Map<Instant, Set<K>> index, Instant d, K k) {
        Set<K> keyset = index.get(d);
        if (keyset == null) {
            keyset = new HashSet<>();
            index.put(d, keyset);
        }
        keyset.add(k);
    }

    private void removeFirstKey(final SortedMap<Instant, Set<K>> index) {
        Instant t = index.firstKey();
        for (K k : index.getOrDefault(t, Collections.emptySet())) {
            _cache.remove(k);
        }
        index.remove(t);
    }
    
    @SuppressWarnings("hiding")
    private final class SimpleCacheEntry<K, V> implements CacheEntry<K, V> {
        
        private final K _key;
        private V _value;
        private Instant _access;
        private Instant _update;
        
        SimpleCacheEntry(K key, V value) {
            _key = key;
            _value = value;
            _access = _clock.instant();
            _update = _clock.instant();
        }

        @Override
        public K getKey() {
            return _key;
        }

        @Override
        public V getValue() {
            synchronized (this) {
                _access = _clock.instant();
                return _value;
            }
        }

        @Override
        public V getValueSnapshot() {
            synchronized (this) {
                return _value;
            }
        }

        @Override
        public V setValue(V v) {
            synchronized (this) {
                V oldvalue = _value;
                _value = v;
                _update = _clock.instant();
                return oldvalue;
            }
        }

        @Override
        public Instant getAccess() {
            synchronized (this) {
                return _access;
            }
        }

        @Override
        public Instant getUpdate() {
            synchronized (this) {
                return _update;
            }
        }
        
    }
}
