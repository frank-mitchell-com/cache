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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A cache implementation that makes all operations atomic (or as atomic as possible).
 *
 * @param <K> key type
 * @param <V> value type
 *
 * @author Frank Mitchell
 */
public final class DefaultCache<K, V> implements Cache<K, V>, CacheParameters {

    private static final Duration DEFAULT_DURATION = Duration.ofMillis(Long.MAX_VALUE);

    private final Clock _clock;

    private final class EntryRecord<K, V> {
        private final K _key;

        private V _value;
        private Instant _access;
        private Instant _update;

        EntryRecord(K key) {
            final Instant now = _clock.instant();
            _key = key;
            _value = null;
            _access = now;
            _update = now;
        }

        public K getKey() {
            return _key;
        }

        public V getValueWithAccess() {
            V result;
            synchronized (this) {
                _access = _clock.instant();
                result = _value;
            }
            return result;
        }

        public V getValue() {
            V result;
            synchronized (this) {
                result = _value;
            }
            return result;
        }

        public void setValue(V v) {
            Instant instant = _clock.instant();
            synchronized (this) {
                _value = v;
                _access = instant;
                _update = instant;
            }
        }

        public Instant getAccess() {
            Instant result;
            synchronized (this) {
                result = _access;
            }
            return result;
        }

        public Instant getUpdate() {
            Instant result;
            synchronized (this) {
                result = _update;
           }
            return result;
        }
        
        public void markForDeletion() {
            synchronized (this) {
                _value = null;
            }
        }
        
        public boolean isDeleted() {
            synchronized (this) {
                return (_value == null);
            }
        }
    }

    private final ReadWriteLock _indexLock = new ReentrantReadWriteLock(true);
    private final Map<K, EntryRecord<K, V>> _cache = new HashMap<>();
    private final Queue<EntryRecord<K,V>> _lruQueue = new LinkedList<>();
    private final NavigableMap<Instant, Set<EntryRecord<K,V>>> _expiryIndex = new TreeMap<>();

    private final Lock _keyLock = new ReentrantLock(true);
    private final Condition _keyNotInSet = _keyLock.newCondition();
    private final Set<K> _keySet = new ConcurrentHashSet<>();

    protected volatile boolean _disabled = false;
    protected Duration _lastUpdateLimit = DEFAULT_DURATION;
    protected int _maxSize = Integer.MAX_VALUE;

    public DefaultCache() {
        _clock = Clock.systemUTC();
    }

    public DefaultCache(Clock clock) {
        _clock = clock;
    }

    private void lockKey(K key) {
        _keyLock.lock();
        try {
            while (_keySet.contains(key)) {
                _keyNotInSet.awaitUninterruptibly();
            }
            _keySet.add(key);
        } finally {
            _keyLock.unlock();
        }
    }

    private void unlockKey(K key) {
        _keyLock.lock();
        try {
            _keySet.remove(key);
            _keyNotInSet.signalAll();
        } finally {
            _keyLock.unlock();
        }
    }
    
    private <R> R syncRead(Supplier<R> p) {
        _indexLock.readLock().lock();
        try {
            return p.get();
        } finally {
            _indexLock.readLock().unlock();
        }
    }

    private boolean syncReadBoolean(BooleanSupplier p) {
        _indexLock.readLock().lock();
        try {
            return p.getAsBoolean();
        } finally {
            _indexLock.readLock().unlock();
        }
    }

    private <T> boolean syncReadBoolean(T t, Predicate<T> p) {
        _indexLock.readLock().lock();
        try {
            return p.test(t);
        } finally {
            _indexLock.readLock().unlock();
        }
    }

    private int syncReadInt(IntSupplier p) {
        _indexLock.readLock().lock();
        try {
            return p.getAsInt();
        } finally {
            _indexLock.readLock().unlock();
        }
    }

    private <T> void syncWrite(Consumer<T> p) {
        _indexLock.writeLock().lock();
        try {
            p.accept(null);
        } finally {
            _indexLock.writeLock().unlock();
        }
    }

    private void syncWriteInt(int t, IntConsumer p) {
        _indexLock.writeLock().lock();
        try {
            p.accept(t);
        } finally {
            _indexLock.writeLock().unlock();
        }
    }

    private void syncWriteBoolean(boolean t, Consumer<Boolean> p) {
        _indexLock.writeLock().lock();
        try {
            p.accept(t);
        } finally {
            _indexLock.writeLock().unlock();
        }
    }

    private <T> void syncWrite(T t, Consumer<T> p) {
        _indexLock.writeLock().lock();
        try {
            p.accept(t);
        } finally {
            _indexLock.writeLock().unlock();
        }
    }

    private <T, U> void syncWrite(T t, U u, BiConsumer<T, U> p) {
        _indexLock.writeLock().lock();
        try {
            p.accept(t, u);
        } finally {
            _indexLock.writeLock().unlock();
        }
    }

    @Override
    public Iterable<CacheView<K,V>> cacheViews() {
        List<CacheView<K, V>> result = new ArrayList<>(size());
        _indexLock.readLock().lock();
        try {
            for (EntryRecord<K, V> e : _cache.values()) {
                lockKey(e.getKey());
                try {
                    result.add(new SimpleCacheView<>(
                                e.getKey(),
                                e.getValue(),
                                e.getAccess(),
                                e.getUpdate()));
                } finally {
                    unlockKey(e.getKey());
                }
            }
        } finally {
            _indexLock.readLock().unlock();
        }
        return result;
    }

    @Override
    public void clear() {
        syncWrite((Object o) -> {
            _cache.clear();
        });
    }

    @Override
    public boolean containsKey(K key) {
        return syncReadBoolean(key, (K k) -> _cache.containsKey(k));
    }

    private EntryRecord<K, V> beginWith(K key, boolean create) {
        if (isDisabled()) {
            return null;
        }
        clearExpired();

        _indexLock.readLock().lock();
        lockKey(key);

        try {
            EntryRecord<K, V> entry = _cache.get(key);
            if (entry == null && create) {
                _indexLock.readLock().unlock();
                _indexLock.writeLock().lock();
                try {
                    entry = new EntryRecord<>(key);
                    entryCreated(entry);
                    _cache.put(key, entry);
                } finally {
                    _indexLock.writeLock().unlock();
                    _indexLock.readLock().lock();
                }
            }
            return entry;
        } finally {
            _indexLock.readLock().unlock();
        }
    }

    private V accessValue(EntryRecord<K, V> e) {
        V value = e.getValueWithAccess();
        entryAccessed(e);
        return value;
    }

    private void putInWith(EntryRecord<K, V> e, V value) {
        Instant oldtime = e.getUpdate();
        e.setValue(value);
        entryUpdated(e, oldtime);
    }

    private void removeInWith(EntryRecord<K,V> e) {
        if (e == null) {
            return;
        }
        if (deleteEntryFromCache(e.getKey(), e)) {
            entryDeleted(e);
        }
    }

    private boolean deleteEntryFromCache(final K key, final EntryRecord<K, V> e) {
        return _cache.remove(key, e);
    }

    private void endWith(K key) {
        unlockKey(key);
    }

    private void with(K key, boolean create, Consumer<EntryRecord<K,V>> func) {
        try {
            EntryRecord<K,V> entry = beginWith(key, create);
            if (entry == null) {
                return;
            }
            func.accept(entry);
        } finally {
            endWith(key);
        }
    }

    private boolean withBoolean(K key, boolean create, Predicate<EntryRecord<K,V>> func) {
        try {
            EntryRecord<K,V> entry = beginWith(key, create);
            if (entry == null) {
                return false;
            }
            return func.test(entry);
        } finally {
            endWith(key);
        }
    }

    private V withValue(K key, boolean create, Function<EntryRecord<K,V>, V> func) {
        try {
            EntryRecord<K,V> entry = beginWith(key, create);
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
        Objects.requireNonNull(key);
        return withValue(key, false, (EntryRecord<K, V> e) -> accessValue(e));
    }

    @Override
    public V getAndPut(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withValue(key, true, (EntryRecord<K, V> e) -> {
            final V oldvalue = e.getValue();
            putInWith(e, value);
            return oldvalue;
        });
    }

    @Override
    public V getAndRemove(K key) {
        Objects.requireNonNull(key);
        return withValue(key, false, (EntryRecord<K, V> e) -> {
            V oldvalue = e.getValue();
            removeInWith(e);
            return oldvalue;
        });
    }

    @Override
    public V getAndReplace(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withValue(key, false, (EntryRecord<K, V> e) -> {
            V oldvalue = e.getValue();
            putInWith(e, value);
            return oldvalue;
        });
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        with(key, true, (EntryRecord<K, V> e) -> { putInWith(e, value); });
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withBoolean(key, true, (EntryRecord<K, V> e) -> {
            if (e.getValue() == null) {
                putInWith(e, value);
                return true;
            }
            return false;
        });
    }

    @Override
    public void remove(K key) {
        Objects.requireNonNull(key);
        with(key, false, (EntryRecord<K, V> e) -> { removeInWith(e); });
    }

    @Override
    public boolean remove(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withBoolean(key, false, (EntryRecord<K, V> e) -> {
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
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(newvalue);
        return withBoolean(key, false, (EntryRecord<K, V> e) -> {
            if (value.equals(e.getValue())) {
                putInWith(e, newvalue);
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean replace(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return withBoolean(key, false, (EntryRecord<K, V> e) -> {
            putInWith(e, value);
            return true;
        });
    }

    @Override
    public int size() {
        clearExpired();
        return syncReadInt(() -> _cache.size());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    private void insertIntoLruQueue(EntryRecord<K, V> entry) {
        _lruQueue.add(entry);
    }

    private void updateLruQueue(EntryRecord<K, V> entry) {
        deleteFromLruQueue(entry);
        insertIntoLruQueue(entry);
    }

    private void deleteFromLruQueue(EntryRecord<K, V> entry) {
        _lruQueue.remove(entry);
    }

    private void indexUpdate(EntryRecord<K, V> entry, Instant oldtime, Instant newtime) {
        if (oldtime != null) {
            Set<EntryRecord<K, V>> set = _expiryIndex.get(oldtime);
            if (set != null) {
                set.remove(entry);
                if (set.isEmpty()) {
                    _expiryIndex.remove(oldtime);
                }
            }
        }
        if (newtime != null) {
            Set<EntryRecord<K, V>> set =_expiryIndex.computeIfAbsent(newtime,
                                            (Instant i) -> new ConcurrentHashSet<>() );
            set.add(entry);
        }
    }

    void entryCreated(EntryRecord<K, V> e) {
        syncWrite(e, (EntryRecord<K, V> entry) -> {
            insertIntoLruQueue(entry);
            indexUpdate(entry, null, entry.getUpdate());
        });
    }

    void entryAccessed(EntryRecord<K, V> e) {
        syncWrite(e, (EntryRecord<K, V> entry) -> {
            updateLruQueue(entry);
        });
    }

    void entryUpdated(EntryRecord<K, V> entry, Instant oldtime) {
        syncWrite(entry, oldtime, (EntryRecord<K, V> e, Instant t) -> {
            indexUpdate(e, t, e.getUpdate());
            updateLruQueue(e);
        });
    }

    void entryDeleted(EntryRecord<K, V> entry) {
        syncWrite(entry, (EntryRecord<K, V> e) -> {
            deleteFromLruQueue(e);
            indexUpdate(e, e.getUpdate(), null);
        });
    }

    @Override
    public void clearExpired() {
        Set<EntryRecord<K, V>> deleted = new HashSet<>();
        Instant now = _clock.instant();
        _indexLock.readLock().lock();
        try {
            /* Remove expired entries */
            for (Map.Entry<Instant, Set<EntryRecord<K, V>>> bucket : _expiryIndex.entrySet()) {
                final Instant updated = bucket.getKey();
                final Instant expiry = updated.plus(getLastUpdateLimit());
                if (expiry.isBefore(now)) {
                    final Iterator<EntryRecord<K,V>> iter = bucket.getValue().iterator();
                    while (iter.hasNext()) {
                        final EntryRecord<K, V> e = iter.next();
                        final K key = e.getKey();
                        lockKey(key);
                        try {
                            // Make sure entry hasn't been updated
                            if (updated.equals(e.getUpdate())) {
                                e.markForDeletion();
                                deleted.add(e);
                            }
                        } finally {
                            unlockKey(key);
                        }
                    }
                } else {
                    break;
                }
            }

            /* Remove least recently used entries */
            final int removecount = _cache.size() - deleted.size() - getMaximumSize();
            for (int i = 0; i < removecount; i++) {
                EntryRecord<K, V> e = _lruQueue.peek();
                final K key = e.getKey();
                final Instant accessed = e.getAccess();
                lockKey(key);
                try {
                    // Make sure entry hasn't been "accessed"
                    if (accessed.equals(e.getAccess())) {
                        e.markForDeletion();
                        deleted.add(e);
                    }
                } finally {
                    unlockKey(key);
                }
            }
        } finally {
            _indexLock.readLock().unlock();
        }
        
        _indexLock.writeLock().lock();
        try {
            for (EntryRecord<K, V> e : deleted) {
                if (e.isDeleted() && _cache.remove(e.getKey(), e)) {
                    entryDeleted(e);
                }
            }
        } finally {
            _indexLock.writeLock().unlock();
        }
    }

    @Override
    public CacheParameters getParameters() {
        return this;
    }

    @Override
    public boolean isDisabled() {
        return syncReadBoolean(() -> _disabled);
    }

    @Override
    public void setDisabled(boolean value) {
        syncWriteBoolean(value, (Boolean disabled) -> {
            _disabled = disabled;
            if (disabled) {
                clear();
            }
        });
    }

    @Override
    public Duration getLastUpdateLimit() {
        return syncRead(() -> _lastUpdateLimit);
    }

    @Override
    public void setLastUpdateLimit(Duration value) {
        syncWrite(value, (Duration d) -> {
            _lastUpdateLimit = (d != null) ? d : DEFAULT_DURATION;
            clearExpired();
        });
    }

    @Override
    public int getMaximumSize() {
        return syncReadInt(() -> _maxSize);
    }

    @Override
    public void setMaximumSize(int value) {
        syncWriteInt(value, (int i) -> {
            _maxSize = i;
            clearExpired();
        });
    }
}
