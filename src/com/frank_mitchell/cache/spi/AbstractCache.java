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
import com.frank_mitchell.cache.CacheParameters;
import com.frank_mitchell.cache.CacheView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Abstract class for most {@link Cache} implementations.
 * 
 * @author Frank Mitchell
 * @param <K> key type
 * @param <V> value type
 */
public abstract class AbstractCache<K,V>
	implements Cache<K,V>, CacheParameters {

    protected volatile boolean _disabled = false;
    protected Duration _lastUpdateLimit = Duration.ofMillis(Long.MAX_VALUE);
    protected int _maxSize = Integer.MAX_VALUE;
    
    protected abstract Clock getClock();
    
    
    @Override
    public Duration getLastUpdateLimit() {
        synchronized (this) {
            return _lastUpdateLimit;
        }
    }

    @Override
    public int getMaximumSize() {
        synchronized (this) {
            return _maxSize;
        }
    }

    @Override
    public void setLastUpdateLimit(Duration value) {
        synchronized (this) {
            _lastUpdateLimit = value.abs();
            clearExpired();
        }
    }

    @Override
    public void setMaximumSize(int value) {
        synchronized (this) {
            _maxSize = Math.abs(value);
            clearExpired();
        }
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
    public void setDisabled(boolean value) {
        synchronized (this) {
            _disabled = value;
            if (value) {
                clear();
            }
        }
    }
    
    protected abstract CacheEntry<K, V> rawget(K key);
    
    protected abstract boolean rawput(K key, V value);
    
    protected abstract boolean rawremove(K key);
    
    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (isDisabled()) {
            return;
        }
        rawput(key, value);
    }
    
    /**
     * 
     * @param key
     * @param value
     * @return the previous value or {@code null}
     * @see javax.cache.Cache#putIfAbsent(java.lang.Object, java.lang.Object) 
     */
    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (isDisabled()) {
            return false;
        }
        if (!containsKey(key)) {
            put(key, value);
            return true;
        } else {
            return false;
        }
    }

    /**
     *
     * @param key
     * @param value
     * @return the previous value of the key.
     * @see javax.cache.Cache#getAndPut(java.lang.Object, java.lang.Object)
     */
    @Override
    public V getAndPut(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (isDisabled()) {
            return null;
        }
        CacheEntry<K, V> rec = rawget(key);
        V oldv = rec == null ? null : rec.getValueWithAccess();
        rawput(key, value);
        return oldv;
    }

    /**
     *
     * @param key
     * @param value
     * @return
     * @see javax.cache.Cache#remove(java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean remove(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (isDisabled()) {
            return false;
        }
        CacheEntry<K, V> e = rawget(key);
        if (e == null) {
            return false;
        }
        if (e.getValueNoAccess().equals(value)) {
            rawremove(key);
            return true;
        }
        return false;
    }

    /**
     *
     * @param key
     * @return
     * @see javax.cache.Cache#getAndRemove(java.lang.Object)
     */
    @Override
    public V getAndRemove(K key) {
        Objects.requireNonNull(key);
        if (isDisabled()) {
            return null;
        }
        V oldv = get(key);
        rawremove(key);
        return oldv;
    }

    /**
     *
     * @param key
     * @param value
     * @param newvalue
     * @return
     * @see javax.cache.Cache#replace(java.lang.Object, java.lang.Object,
     * java.lang.Object)
     */
    @Override
    public boolean replace(K key, V value, V newvalue) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(newvalue);
        if (isDisabled()) {
            return false;
        }
        final V oldv = get(key);
        if (oldv != null && oldv.equals(value)) {
            replace(key, newvalue);
            return true;
        }
        return false;
    }

    /**
     *
     * @param key
     * @param value
     * @return
     * @see javax.cache.Cache#replace(java.lang.Object, java.lang.Object)
     */
    @Override
    public boolean replace(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (isDisabled()) {
            return false;
        }
        final V oldv = get(key);
        if (oldv != null) {
            put(key, value);
            return true;
        }
        return false;
    }

    /**
     *
     * @param key
     * @param value
     * @return
     * @see javax.cache.Cache#getAndReplace(java.lang.Object)
     */
    @Override
    public V getAndReplace(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (isDisabled()) {
            return null;
        }
        V oldv = get(key);
        replace(key, value);
        return oldv;
    }

    /**
     * @see javax.cache.Cache#removeAll()
     */
    @Override
    public void removeAll() {
        clear();
    }
    
    protected abstract void entryAccessed(CacheEntry<K, V> cache, Instant old);
    
    protected abstract void entryUpdated(CacheEntry<K, V> cache, Instant old);
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(this.getClass().getCanonicalName())
                .append("@").append(this.hashCode());
        result.append("[").append(this.size()).append("]").append("{");
        for (CacheView<K,V> v : this.cacheViews()) {
            result.append(v.getKey()).append("=")
                    .append("(access=").append(v.getAccess())
                    .append(",update=").append(v.getUpdate())
                    .append(",value=").append(v.getValue())
                    .append("),");
        }
        result.append("}");
        result.append("<")
            .append("disabled=").append(this.isDisabled()).append(",")
            .append("maxsize=").append(this.getMaximumSize()).append(",")
            .append("update=").append(this.getLastUpdateLimit()).append(",")
            .append("dropaccess=").append(true)
            .append(">");
        return result.toString();
    }
}
