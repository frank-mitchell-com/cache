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

package com.frank_mitchell.cache;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Instances cache a specified number of values by key for a specified time.
 * Entries are evicted when the entry has not been updated or accessed in a
 * specified interval, or when the cache is beyond its maximum size. The cache
 * will quietly remove expired entries, then remove the oldest or least used
 * (depending on a flag) until it's within its maximum size. Removed items
 * simply disappear; no query will show they were ever added.
 *
 * Implementations can either use timers to remove items in a timely manner or
 * lazily remove items if they've expired or if a new item would push the cache
 * size beyond its maximum. Performance issues aside, as long as the user
 * doesn't <strong>see</strong> expired items and more items than the maximum,
 * they were never there.
 * 
 * This interface implements only a subset of methods from {@link Map}.
 * In particular any method that iterates over the entire cache has been
 * removed, lest the iterator keep the cache from caching.  {@link #cacheViews()}
 * provides a snapshot of the cache's contents at the time it was called.
 *
 * @param <K> type of keys in cache
 * @param <V> type of values in cache
 */
public interface Cache<K, V> {

    /**
     *
     * @return number of entries
     * @see Map#size()
     */
    public int size();

    /**
     * 
     * @return whether cache has elements.
     * @see Map#isEmpty()
     */
    public boolean isEmpty();

    /**
     * 
     * @param o potential key
     * @return whether argument is a key
     * @see Map#containsKey(java.lang.Object) 
     */
    public boolean containsKey(Object o);

    /**
     * 
     * @param o
     * @return the entry for the argument or {@code null}.
     */
    public CacheEntry<K, V> getEntry(Object o);

    /**
     * 
     * @param o
     * @return the value for the argument or {@code null}
     * @see Map#get(java.lang.Object) 
     */
    public V get(Object o);

    /**
     * 
     * @param k
     * @param v
     * @return the previous value for the key or {@code null}
     * @see Map#put(java.lang.Object, java.lang.Object)
     */
    public V put(K k, V v);

    /**
     * 
     * @param o
     * @return the previous value for the key or {@code null}
     * @see Map#remove(java.lang.Object) 
     */
    public V remove(Object o);

    /**
     * @see Map#clear() 
     */
    public void clear();

    /**
     * 
     * @param key
     * @param defaultValue
     * @return the value for the key or the default value if not found.
     * @see Map#getOrDefault(java.lang.Object, java.lang.Object) 
     */
    public default V getOrDefault(Object key, V defaultValue) {
        V value = get(key);
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }

    }

    /**
     * 
     * @param key
     * @param value
     * @return the previous value or {@code null}
     * @see Map#putIfAbsent(java.lang.Object, java.lang.Object) 
     */
    public default V putIfAbsent(K key, V value) {
        if (!containsKey(key)) {
            return put(key, value);
        } else {
            return null;
        }
    }

    /**
     * 
     * @param key
     * @param mappingFunction
     * @return the result of the function or {@code null}
     * @see Map#computeIfAbsent(java.lang.Object, java.util.function.Function) 
     */
    public default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (!containsKey(key)) {
            V newValue = mappingFunction.apply(key);
            if (newValue != null) {
                put(key, newValue);
            }
            return newValue;
        }
        return null;
    }

    /**
     * 
     * @param key
     * @param remappingFunction
     * @return the result of the remapping function or {@code null}
     * @see Map#computeIfPresent(java.lang.Object, java.util.function.BiFunction) 
     */
    public default V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V value = get(key);
        if (value != null) {
            final V newValue = remappingFunction.apply(key, value);
            if (newValue == null) {
                remove(key);
            } else {
                put(key, newValue);
            }
            return newValue;
        }
        return null;
    }

    /**
     * 
     * @param key
     * @param remappingFunction
     * @return the result of running the function or {@code null}.
     * @see Map#compute(java.lang.Object, java.util.function.BiFunction)
     */
    public default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V value = get(key);
        if (value == null) {
            final V newValue = remappingFunction.apply(key, null);
            if (newValue != null) {
                put(key, newValue);
            }
            return newValue;
        } else {
            final V newValue = remappingFunction.apply(key, value);
            if (newValue == null) {
                remove(key);
            } else {
                put(key, newValue);
            }
            return newValue;
        }
    }

    /**
     * A partial snapshot of the cache's contents. Unlike {#link Map#entrySet()}
     * changing this method does not change the underlying cache.
     *
     * This operation may require creating intermediate objects or maintaining a
     * lot of state. It's included mainly for debugging purposes, either by
     * developers or operations personnel.
     *
     * @return collection of cache views
     */
    public Collection<CacheView<K, V>> cacheViews();

    /**
     * Remove all expired entries from the cache.
     *
     * If the implementation uses timers and threads to expire items, this
     * method may do nothing. If it removes objects lazily, this forces it to
     * remove references to expired items, which may reduce memory.
     */
    public void clearExpired();

    /**
     * Provide an interface to update the cache's parameters. Changes to the
     * return value <strong>directly</strong>
     * updates the cache's state.
     *
     * @return the interface to update the cache's parameters
     */
    public CacheParameters getParameters();
}