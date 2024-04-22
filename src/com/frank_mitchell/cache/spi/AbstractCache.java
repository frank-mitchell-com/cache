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
    protected boolean _dropLastAccessed = false;
    protected Duration _lastAccessLimit = Duration.ofMillis(Long.MAX_VALUE);
    protected Duration _lastUpdateLimit = Duration.ofMillis(Long.MAX_VALUE);
    protected int _maxSize = Integer.MAX_VALUE;
    
    protected abstract Clock getClock();
    
    @Override
    public boolean isLastAccessedDroppedFirst() {
        synchronized (this) {
            return _dropLastAccessed;
        }
    }

    @Override
    public Duration getLastAccessLimit() {
        synchronized (this) {
            return _lastAccessLimit;
        }
    }

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
    public void setLastAccessedDroppedFirst(boolean value) {
        synchronized (this) {
            _dropLastAccessed = value;
            clearExpired();
        }
    }

    @Override
    public void setLastAccessLimit(Duration value) {
        synchronized (this) {
            _lastAccessLimit = value.abs();
            clearExpired();
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
    
    @Override
    public V put(K key, V value) {
        V result = null;
        if (!isDisabled() && key != null && value != null) {
            result = rawput(key, value);
        }
        return result;
    }
    
    /**
     * 
     * @param key
     * @param value
     * @return 
     */
    protected abstract V rawput(K key, V value);
    
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
            .append("access=").append(this.getLastAccessLimit()).append(",")
            .append("dropaccess=").append(this.isLastAccessedDroppedFirst())
            .append(">");
        return result.toString();
    }
}
