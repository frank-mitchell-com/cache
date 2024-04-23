/*
 * The MIT License
 *
 * Copyright 2023 Frank Mitchell.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.frank_mitchell.cache.spi;

import com.frank_mitchell.cache.Cache;
import com.frank_mitchell.cache.CacheView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A "default" implementation of the {@link Cache} interface.
 *
 * @author Frank Mitchell
 *
 * @param <K> type of entries in the cache
 * @param <V> type of values in the cache
 */
public class DefaultCache<K, V> extends AbstractCache<K, V> {

    private final Clock _clock;

    private final ConcurrentMap<K, CacheEntry<K, V>> _cache = new ConcurrentHashMap<>();
    private final NavigableMap<Instant, Set<CacheEntry<K, V>>> _accessIndex = new TreeMap<>();
    private final NavigableMap<Instant, Set<CacheEntry<K, V>>> _updateIndex = new TreeMap<>();
    private final Lock _indexLock = new ReentrantLock(true);

    /**
     * Default constructor
     */
    public DefaultCache() {
        this(Clock.systemUTC());
    }

    /**
     * Debug constructor for a (mock) Clock.
     *
     * @param clock an adjustable clock for fast unit tests.
     */
    public DefaultCache(Clock clock) {
        _clock = clock;
    }

    @Override
    protected Clock getClock() {
        return _clock;
    }
    
    @Override
    public int size() {
        return _cache.size();
    }

    @Override
    public boolean isEmpty() {
        return _cache.isEmpty();
    }

    @Override
    @SuppressWarnings("element-type-mismatch")
    public boolean containsKey(Object o) {
        clearExpired();
        return _cache.containsKey(o);
    }

    @Override
    public V get(K key) {
        V result = null;
        clearExpired();
        CacheEntry<K, V> entry = rawget(key);
        if (entry != null) {
            result = entry.getValueWithAccess();
        }
        return result;
    }

    @Override
    protected CacheEntry<K, V> rawget(K key) {
        Objects.requireNonNull(key);
        return _cache.get(key);
    }

    @Override
    protected boolean rawput(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        clearExpired();
        CacheEntry<K, V> entry = _cache.get((K) key);
        if (entry == null) {
            entry = new CacheEntry<>(this, key, value);
            entryCreated(entry);
            _cache.put(key, entry);
            return true;
        } else {
            entry.setValue(value);
            return false;
        }
    }

    @Override
    public void remove(K key) {
        Objects.requireNonNull(key);

        clearExpired();
        CacheEntry<K, V> entry = rawget(key);
        if (entry != null) {
            rawremove(key);
            entryDeleted(entry);
        }
    }

    @Override
    protected boolean rawremove(K key) {
        return _cache.remove(key) != null;
    }

    @Override
    public Collection<CacheView<K, V>> cacheViews() {
        ArrayList<CacheView<K, V>> result = new ArrayList<>(_cache.size());
        clearExpired();
        _cache.forEach((K key, CacheEntry<K, V> e) -> {
            result.add(new SimpleCacheView<>(e));
        });
        return result;
    }

    @Override
    public void clear() {
        _cache.clear();

        _indexLock.lock();
        try {
            _accessIndex.clear();
            _updateIndex.clear();
        } finally {
            _indexLock.unlock();
        }
    }

    @Override
    public void clearExpired() {
        ArrayList<Instant> accessCleanup = new ArrayList<>();
        ArrayList<Instant> updateCleanup = new ArrayList<>();

        _indexLock.lock();
        try {
            // crawl through updates, deleting entries until we hit the minimums
            removeIfPastExpiry(_updateIndex, getLastUpdateLimit(), accessCleanup, updateCleanup);

            // if we're still over the maximum size, start throwing out old stuff
            // based on whether we throw out old updates or least recently accessed.
            removeOldest(_accessIndex, accessCleanup, updateCleanup);

            // Clean up deleted entries from both indexes.
            removeOrphanKeys(_accessIndex, accessCleanup);
            removeOrphanKeys(_updateIndex, updateCleanup);
        } finally {
            _indexLock.unlock();
        }
    }

    private void removeOrphanKeys(Map<Instant, Set<CacheEntry<K, V>>> index,
            Collection<Instant> cleanup) {
        for (Instant inst : cleanup) {
            Set<CacheEntry<K, V>> entryset = index.get(inst);

            if (entryset == null) {
                continue;
            }

            Iterator<CacheEntry<K, V>> entryiter = entryset.iterator();
            while (entryiter.hasNext()) {
                CacheEntry<K, V> e = entryiter.next();
                if (!_cache.containsKey(e.getKey())) {
                    entryiter.remove();
                }
            }
            if (entryset.isEmpty()) {
                index.remove(inst);
            }
        }
    }

    private void removeOldest(NavigableMap<Instant, Set<CacheEntry<K, V>>> index,
            Collection<Instant> accesses,
            Collection<Instant> updates) {
        while (getMaximumSize() < size()) {
            Map.Entry<Instant, Set<CacheEntry<K, V>>> first = index.firstEntry();
            for (CacheEntry<K, V> e : first.getValue()) {
                CacheEntry<K, V> d = _cache.remove(e.getKey());
                accesses.add(d.getAccess());
                updates.add(d.getUpdate());
            }
            index.remove(first.getKey());
        }
    }

    private void removeIfPastExpiry(final NavigableMap<Instant, Set<CacheEntry<K, V>>> index,
            Duration limit,
            Collection<Instant> accesses,
            Collection<Instant> updates) {
        // crawl through each "list", deleting entries until we hit the minimums
        Instant now = _clock.instant();
        Iterator<Map.Entry<Instant, Set<CacheEntry<K, V>>>> iter = index.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Instant, Set<CacheEntry<K, V>>> entry = iter.next();
            Instant expiry = entry.getKey().plus(limit);
            if (expiry.isBefore(now)) {
                for (CacheEntry<K, V> e : entry.getValue()) {
                    CacheEntry<K, V> d = _cache.remove(e.getKey());
                    accesses.add(d.getAccess());
                    updates.add(d.getUpdate());
                }
                iter.remove();
            } else {
                // Past "now", so no more to expire
                break;
            }
        }
    }

    @Override
    protected void entryAccessed(CacheEntry<K, V> entry, Instant old) {
        _indexLock.lock();
        try {
            updateIndex(_accessIndex, entry, old, entry.getAccess());
        } finally {
            _indexLock.unlock();
        }
    }

    @Override
    protected void entryUpdated(CacheEntry<K, V> entry, Instant old) {
        _indexLock.lock();
        try {
            updateIndex(_updateIndex, entry, old, entry.getUpdate());
        } finally {
            _indexLock.unlock();
        }
    }

    void entryCreated(CacheEntry<K, V> entry) {
        _indexLock.lock();
        try {
            updateIndex(_accessIndex, entry, null, entry.getAccess());
            updateIndex(_updateIndex, entry, null, entry.getUpdate());
        } finally {
            _indexLock.unlock();
        }
    }

    void entryDeleted(CacheEntry<K, V> entry) {
        _indexLock.lock();
        try {
            updateIndex(_accessIndex, entry, entry.getAccess(), null);
            updateIndex(_updateIndex, entry, entry.getUpdate(), null);
        } finally {
            _indexLock.unlock();
        }
    }

    private void updateIndex(
            final NavigableMap<Instant, Set<CacheEntry<K, V>>> index,
            final CacheEntry<K, V> entry,
            final Instant oldtime,
            final Instant newtime) {
        if (entry == null || index == null) {
            return;
        }
        // 1. remove the exact entry at `oldtime` (if not null and present)
        if (oldtime != null) {
            Set<CacheEntry<K, V>> entries = index.get(oldtime);
            if (entries != null) {
                entries.remove(entry);
                if (entries.isEmpty()) {
                    index.remove(oldtime);
                }
            }
        }
        // 2. re-add it to `index` at `newtime` (if not null)
        if (newtime != null) {
            Set<CacheEntry<K, V>> entries = index.get(newtime);
            if (entries == null) {
                entries = new ConcurrentHashSet<>();
                index.put(newtime, entries);
            }
            entries.add(entry);
        }
    }

    private static class ConcurrentHashSet<K> extends AbstractSet<K> {

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
}
