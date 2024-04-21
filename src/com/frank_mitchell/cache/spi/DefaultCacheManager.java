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
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import com.frank_mitchell.cache.CacheManager;
import com.frank_mitchell.cache.CacheConfiguration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A standard implementation of {@link CacheManager}
 * 
 * @author Frank Mitchell
 */
public class DefaultCacheManager implements CacheManager {

    static class CacheRecord<K, V> {

        private Cache<K, V> _cache;
        private Class<K> _keys;
        private Class<V> _values;

        CacheRecord(Cache<K, V> cache, Class<K> keys, Class<V> values) {
            _cache = cache;
            _keys = keys;
            _values = values;
        }

        /**
         * @return the _cache
         */
        public Cache<K, V> getCache() {
            return _cache;
        }

        /**
         * @param _cache the _cache to set
         */
        public void setCache(Cache<K, V> _cache) {
            this._cache = _cache;
        }

        /**
         * @return the _keys
         */
        public Class<K> getKeys() {
            return _keys;
        }

        /**
         * @param _keys the _keys to set
         */
        public void setKeys(Class<K> _keys) {
            this._keys = _keys;
        }

        /**
         * @return the _values
         */
        public Class<V> getValues() {
            return _values;
        }

        /**
         * @param _values the _values to set
         */
        public void setValues(Class<V> _values) {
            this._values = _values;
        }
    }

    private final Map<String, CacheRecord<?,?>> _caches = new ConcurrentSkipListMap<>();
    private final Map<String, CacheParameterHolder> _params = new ConcurrentHashMap<>();
    private final Set<CacheConfiguration> _configs = new CopyOnWriteArraySet<>();

    @Override
    public <K, V> Cache<K, V> getCache(String name, Class<K> keys, Class<V> values) throws IllegalArgumentException {
        Cache<K, V> result = null;
        synchronized (this) {
            CacheRecord<K, V> rec = getCacheRecord(name, keys, values);
            if (rec != null) {
                result = rec.getCache();
            } else {
                rec = new CacheRecord<>(new DefaultCache<>(), keys, values);
                _caches.put(name, rec);
            }
            if (_params.containsKey(name)) {
                final CacheParameterHolder cph = _params.get(name);
                final CacheParameters parameters = result.getParameters();
                if (parameters != null) {
                    cph.writeParameters(parameters);
                }
                _params.remove(name);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <K, V> CacheRecord<K, V> getCacheRecord(String name, Class<K> keys, Class<V> values) {
        final CacheRecord<K,V> rec = (CacheRecord<K,V>)getCacheRecord(name);
        if (!rec.getKeys().equals(keys) || !rec.getValues().equals(values)) {
            throw new IllegalArgumentException(
                    "cache <" + name + "> created for <"
                    + rec.getKeys() + "," + rec.getValues()
                    + "> called with <"
                    + keys + "," + values + ">");
        }
        return rec;
    }

    private CacheRecord<?,?> getCacheRecord(String name) {
        return _caches.get(name);
    }

    @Override
    public SortedSet<String> getCacheList() {
        return new TreeSet<>(_caches.keySet());
    }

    @Override
    public CacheConfiguration[] getConfigurations() {
        return _configs.toArray(new CacheConfiguration[_configs.size()]);
    }

    @Override
    public void addConfiguration(CacheConfiguration cf) {
        _configs.add(cf);
        cf.addClient(this);
    }

    @Override
    public void removeConfiguration(CacheConfiguration cf) {
        cf.removeClient(this);
        _configs.remove(cf);
    }

    @Override
    public CacheParameters getParameters(String name) {
        CacheParameters result;
        synchronized (this) {
            CacheRecord<?,?> rec = getCacheRecord(name);
            if (rec != null) {
                result = rec.getCache().getParameters();
            } else {
                CacheParameterHolder holder = new CacheParameterHolder();
                _params.put(name, holder);
                result = holder;
            }
        }
        return result;
    }
}
