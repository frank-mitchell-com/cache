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
import com.frank_mitchell.cache.CacheView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
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
    private final Map<K, CacheEntry<K, V>> _cache = new HashMap<>();
    private final Clock _clock;

    /**
     * Default constructor
     */
    public SimpleCache() {
        this(Clock.systemUTC());
    }

    @Override
    protected Clock getClock() {
        return _clock;
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
    public V get(K key) {
        Objects.requireNonNull(key);
        
        V result = null;
        synchronized (this) {
            clearExpired();
            CacheEntry<K, V> e = rawget(key);
            if (e != null) {
                result = e.getValueWithAccess();
            }
        }
        return result;
    }

    @Override
    protected CacheEntry<K, V> rawget(K key) {
        synchronized (this) {
            return _cache.get(key);
        }
    }

    @Override
    protected boolean rawput(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        synchronized (this) {
            CacheEntry<K, V> e = _cache.get(key);
            if (e == null) {
                _cache.put(key, new CacheEntry<>(this, key, value));
                return true;
            } else {
                e.setValue(value);
                return false;
            }
        }
    }

    @Override
    public void remove(K key) {
        Objects.requireNonNull(key);
        synchronized (this) {
            rawremove(key);
        }
    }

    @Override
    protected boolean rawremove(K key) {
        synchronized (this) {
            return _cache.remove(key) != null;
        }
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
            Iterator<CacheEntry<K, V>> iter = _cache.values().iterator();
            while (iter.hasNext()) {
                final CacheEntry<K, V> e = iter.next();
                final Instant expiry = e.getUpdate().plus(getLastUpdateLimit());
                if (expiry.isBefore(now)) {
                    iter.remove();
                } else {
                    addKey(keyByAccess, e.getAccess(), e.getKey());
                    addKey(keyByUpdate, e.getUpdate(), e.getKey());
                }
            }
            while (_cache.size() > getMaximumSize()) {
                removeFirstKey(keyByAccess);
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

    @Override
    protected void entryAccessed(CacheEntry cache, Instant old) {
    }

    @Override
    protected void entryUpdated(CacheEntry cache, Instant old) {
    }
}
