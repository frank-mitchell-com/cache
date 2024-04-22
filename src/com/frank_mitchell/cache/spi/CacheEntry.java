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

import java.time.Clock;
import java.time.Instant;

/**
 * A container for the cache entry key, value, and access and update timestamps.
 * 
 * @author Frank Mitchell
 * @param <K> key type
 * @param <V> value type
 */
public final class CacheEntry<K, V> {
    
    private final AbstractCache<K,V> _cache;
    private final K _key;
    
    private V _value;
    private Instant _access;
    private Instant _update;

    CacheEntry(AbstractCache<K,V> cache, K key, V value) {
        final Instant now = cache.getClock().instant();
        _key = key;
        _value = value;
        _cache = cache;
        _access = now;
        _update = now;
    }

    public K getKey() {
        return _key;
    }

    public V getValueWithAccess() {
        V result;
        Instant oldtime;
        synchronized (this) {
            oldtime = _access;
            _access = getClock().instant();
            result = _value;
        }
        _cache.entryAccessed(this, oldtime);
        return result;
    }
    
    public V getValueNoAccess() {
        synchronized (this) {
            return _value;
        }
    }

    public V setValue(V v) {
        V oldvalue;
        Instant oldtime;
        synchronized (this) {
            oldvalue = _value;
            oldtime = _update;
            _value = v;
            _update = getClock().instant();
        }
        _cache.entryUpdated(this, oldtime);
        return oldvalue;
    }

    public Instant getAccess() {
        synchronized (this) {
            return _access;
        }
    }

    public Instant getUpdate() {
        synchronized (this) {
            return _update;
        }
    }

    /**
     * @return the _clock
     */
    public Clock getClock() {
        return _cache.getClock();
    }
}
