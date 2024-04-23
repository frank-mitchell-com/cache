/*
 * The MIT License
 *
 * Copyright 2024 Frank Mitchell <me@frank-mitchell.com>.
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
import com.frank_mitchell.cache.CacheParameters;
import com.frank_mitchell.cache.CacheView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 *
 * @param <K>
 * @param <V>
 * 
 * @author Frank Mitchell
 */
public final class ThirdCache<K, V> implements Cache<K, V>, CacheParameters {
    
    private static final Duration DEFAULT_DURATION = Duration.ofMillis(Long.MAX_VALUE);

    private final Clock _clock;

    private final class Entry<K, V> {
        private final K _key;
        private final Lock _lock;
    
        private V _value;
        private Instant _access;
        private Instant _update;

        Entry(K key) {
            final Instant now = _clock.instant();
            _key = key;
            _lock = new ReentrantLock();
            _value = null;
            _access = now;
            _update = now;
        }

        public K getKey() {
            return _key;
        }

        public Lock getLock() {
            return _lock;
        }

        public V getValueWithAccess() {
            V result;
            _lock.lock();
            try {
                _access = _clock.instant();
                result = _value;
            } finally {
                _lock.unlock();
            }
            return result;
        }
    
        public V getValue() {
            V result;
            _lock.lock();
            try {
                result = _value;
            } finally {
                _lock.unlock();
            }
            return result;
        }

        public void setValue(V v) {
            _lock.lock();
            try {
                _value = v;
                _update = _clock.instant();
            } finally {
                _lock.unlock();
            }
        }
    
        public Instant getAccess() {
            Instant result;
            _lock.lock();
            try {
                result = _access;
            } finally {
                _lock.unlock();
            }
            return result;
        }

        public Instant getUpdate() {
            Instant result;
            _lock.lock();
            try {
                result = _update;
            } finally {
                _lock.unlock();
            }
            return result;
        }
    }

    private final ConcurrentMap<K, Entry<K, V>> _cache = new ConcurrentHashMap<>();
    
    protected volatile boolean _disabled = false;
    protected Duration _lastUpdateLimit = DEFAULT_DURATION;
    protected int _maxSize = Integer.MAX_VALUE;

    public ThirdCache() {
        _clock = Clock.systemUTC();
    }

    public ThirdCache(Clock clock) {
        _clock = clock;
    }
    
    @Override
    public Collection<CacheView<K,V>> cacheViews() {
        List<CacheView<K, V>> result = new ArrayList<>(size());
        for (Entry<K, V> e : _cache.values()) {
            e.getLock().lock();
            try {
                result.add(new SimpleCacheView<>(
                        e.getKey(),
                        e.getValue(),
                        e.getAccess(),
                        e.getUpdate()));
            } finally {
                e.getLock().unlock();
            }
        }
        return result;
    }

    @Override
    public void clear() {
        // TODO: Need global lock
        _cache.clear();
    }

    @Override
    public boolean containsKey(K k) {
        return _cache.containsKey(k);
    }

    private Entry<K, V> beginWith(K key, boolean create) {
        if (isDisabled()) {
            return null;
        }
        Entry<K, V> entry;
        synchronized (this) {
            entry = _cache.get(key);
            if (entry == null && create) {
                entry = new Entry<>(key);
                _cache.put(key, entry);
            }
            if (entry != null) {
                entry.getLock().lock();
            }
        }
        return entry;
    }
    
    private void removeInWith(Entry<K,V> e) {
        if (e == null) {
            return;
        }
        synchronized (this) {
            _cache.remove(e.getKey());
            e.getLock().unlock();
        }
    }
    
    private void endWith(K key) {
        synchronized (this) {
            Entry<K, V> entry = _cache.get(key);
            if (entry != null) {
                entry.getLock().unlock();
            }
        }
    }
    
    private void with(K key, boolean create, Consumer<Entry<K,V>> func) {
        try {
            Entry<K,V> entry = beginWith(key, create);
            if (entry == null) {
                return;
            }
            func.accept(entry);
        } finally {
            endWith(key);
        }
    }

    private boolean withBoolean(K key, boolean create, Predicate<Entry<K,V>> func) {
        try {
            Entry<K,V> entry = beginWith(key, create);
            if (entry == null) {
                return false;
            }
            return func.test(entry);
        } finally {
            endWith(key);
        }        
    }

    private V withValue(K key, boolean create, Function<Entry<K,V>, V> func) {
        try {
            Entry<K,V> entry = beginWith(key, create);
            if (entry == null) {
                return null;
            }
            return func.apply(entry);
        } finally {
            endWith(key);
        }                
    }

    @Override
    public V get(K key) {
        clearExpired();
        Objects.requireNonNull(key);
        return withValue(key, false, (Entry<K, V> e) -> e.getValueWithAccess());
        /*
        // TODO: Use faster procedure with a read lock, or maybe like below:
        if (isDisabled()) {
            return null;
        }
        V result = null;
        // Acquire read lock?
        Entry<K, V> entry = _cache.get(key);
        if (entry == null) {
            return null;
        }
        result = entry.getValueWithAccess();
        // Release read lock?
        return result;
        */
    }

    @Override
    public V getAndPut(K key, V value) {
        clearExpired();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withValue(key, true, (Entry<K, V> e) -> {
            final V oldvalue = e.getValue();
            e.setValue(value);
            return oldvalue;
        });
    }

    @Override
    public V getAndRemove(K key) {
        clearExpired();
        Objects.requireNonNull(key);
        return withValue(key, false, (Entry<K, V> e) -> {
            V oldvalue = e.getValue();
            removeInWith(e);
            return oldvalue;
        });
    }

    @Override
    public V getAndReplace(K key, V value) {
        clearExpired();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withValue(key, false, (Entry<K, V> e) -> {
            V oldvalue = e.getValue();
            e.setValue(value);
            return oldvalue;
        });
    }

    @Override
    public void put(K key, V value) {
        clearExpired();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        with(key, true, (Entry<K, V> e) -> { e.setValue(value); });
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withBoolean(key, true, (Entry<K, V> e) -> {
            if (e.getValue() == null) {
                e.setValue(value);
                return true;
            }
            return false;
        });
    }

    @Override
    public void remove(K key) {
        clearExpired();
        Objects.requireNonNull(key);
        with(key, false, (Entry<K, V> e) -> { removeInWith(e); });
    }

    @Override
    public boolean remove(K key, V value) {
        clearExpired();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withBoolean(key, false, (Entry<K, V> e) -> {
            final V oldvalue = e.getValue();
            if (value.equals(oldvalue)) {
                removeInWith(e);
                return true;
            }
            return false;
        });
    }

    @Override
    public void removeAll() {
        clear();
    }

    @Override
    public boolean replace(K key, V value, V newvalue) {
        clearExpired();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(newvalue);
        return withBoolean(key, false, (Entry<K, V> e) -> {
            if (value.equals(e.getValue())) {
                e.setValue(newvalue);
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean replace(K key, V value) {
        clearExpired();
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withBoolean(key, false, (Entry<K, V> e) -> {
            e.setValue(value);
            return true;
        });
    }

    @Override
    public int size() {
        clearExpired();
        return _cache.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public void clearExpired() {
        // TODO: Maintain indexes / linked lists as we insert/delete ...
        SortedMap<Instant, Set<K>> keyByAccess = new TreeMap<>();
        synchronized (this) {
            Instant now = _clock.instant();
            Iterator<Entry<K, V>> iter = _cache.values().iterator();
            while (iter.hasNext()) {
                final Entry<K, V> e = iter.next();
                final Instant expiry = e.getUpdate().plus(getLastUpdateLimit());
                if (expiry.isBefore(now)) {
                    iter.remove();
                } else {
                    addKey(keyByAccess, e.getAccess(), e.getKey());
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
    public CacheParameters getParameters() {
        return this;
    }
    
    @Override
    public boolean isDisabled() {
        return _disabled;
    }

    @Override
    public void setDisabled(boolean disabled) {
        _disabled = disabled;
        if (disabled) {
            clear();
        }
    }

    @Override
    public Duration getLastUpdateLimit() {
        return _lastUpdateLimit;
    }

    @Override
    public void setLastUpdateLimit(Duration value) {
        _lastUpdateLimit = (value != null) ? value : DEFAULT_DURATION;
    }

    @Override
    public int getMaximumSize() {
        return _maxSize;
    }

    @Override
    public void setMaximumSize(int value) {
        _maxSize = value;
    }
}
