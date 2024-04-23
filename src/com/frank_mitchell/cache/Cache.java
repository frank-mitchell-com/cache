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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Instances cache a specified number of values by key for a specified time.
 * Entries are evicted when the entry has not been updated in a
 * specified interval, or when the cache is beyond its maximum size. The cache
 * will quietly remove expired entries, then remove the least recently used
 * entries until the cache is within its maximum size. Removed items
 * simply disappear; no query will show they were ever added.
 *
 * Implementations can either use timers to remove items in a timely manner or
 * lazily remove items if they've expired or if a new item would push the cache
 * size beyond its maximum. Performance issues aside, as long as the user
 * doesn't <strong>see</strong> expired items and more items than the maximum,
 * they were never there.
 * 
 * This interface implements only a subset of methods from 
 * [@link javax.cache.Cache} and a very few from {@link Map}.
 * In particular any method that iterates over the entire cache has been
 * removed, lest the iterator keep the cache from caching.  {@link #cacheViews()}
 * provides a snapshot of the cache's contents at the time it was called.
 *
 * @param <K> type of keys in cache
 * @param <V> type of values in cache
 */
public interface Cache<K, V> {

    /**
     * Counts the number of entries in the cache.
     *
     * @return number of entries
     * @see Map#size()
     */
    public int size();

    /**
     * Indicates whether the cache is empty.
     * 
     * @return whether cache has elements.
     * @see Map#isEmpty()
     */
    public boolean isEmpty();

    /**
     * Whether the cache contains the given key.
     * 
     * @param key potential key
     * @return whether argument is a key in the cache.
     * @throws NullPointerException if the key is null.
     * @see Map#containsKey(java.lang.Object) 
     */
    public boolean containsKey(K key);

    /**
     * Gets an entry from the cache.
     *
     * @param key
     * @return the value for the argument or {@code null} if not found.
     * @throws NullPointerException if the key is null.
     * @see javax.cache.Cache#get(java.lang.Object) 
     */
    public V get(K key);

    /**
     * Put a key-value pair into the cache.
     * 
     * @param key
     * @param value
     * @throws NullPointerException if the key or value are null.
     * @see javax.cache.Cache#put(java.lang.Object, java.lang.Object)
     */
    public void put(K key, V value);

    /**
     * Remove a key and its associated value from the cache, if present.
     * 
     * @param key
     * @throws NullPointerException if the key is null.
     * @see javax.cache.Cache#remove(java.lang.Object) 
     */
    public void remove(K key);

    /**
     * Remove all keys and values from the cache.
     * 
     * @see Map#clear() 
     */
    public void clear();

    /**
     * Get the value for a key from the cache, or the provided default
     * if not found.
     * 
     * @param key
     * @param defaultValue
     * @return the value for the key or the default value if not found.
     * @throws NullPointerException if the key is null.
     * @see Map#getOrDefault(java.lang.Object, java.lang.Object) 
     */
    public default V getOrDefault(K key, V defaultValue) {
        V value = get(key);
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }

    }

    /**
     * Put a key-value pair into the cache only if the key is not already defined.
     * 
     * @param key
     * @param value
     * @return {@code true} if the value was set.
     * @throws NullPointerException if the key or value are null.
     * @see javax.cache.Cache#putIfAbsent(java.lang.Object, java.lang.Object) 
     */
    public boolean putIfAbsent(K key, V value);

    /**
     * Get the value associated with the key in the cache; if the value is not defined,
     * run the function and use it as the new value.
     * 
     * @param key
     * @param mappingFunction
     * @return the result of the function or {@code null}
     * @throws NullPointerException if the key or function are null.
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
     * If the key is present in the cache, run the function to calculate a new value
     * and return it.
     * 
     * @param key
     * @param remappingFunction
     * @return the result of the remapping function or {@code null}
     * @throws NullPointerException if the key or function are null.
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
     * Run the function with the current value of the key as an input,
     * then cache the function's result for the key.
     * 
     * @param key
     * @param remappingFunction
     * @return the result of running the function.
     * @throws NullPointerException if the key or function are null.
     * @see Map#compute(java.lang.Object, java.util.function.BiFunction)
     */
    public default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        final V value = get(key);
        final V newValue = remappingFunction.apply(key, null);
        if (newValue != null) {
            put(key, newValue);
        } else if (value != null) {
            remove(key);
        }
        return newValue;
    }

    /**
     * Get all values with keys in the set.
     * 
     * @param set - a set of keys
     * @return a map with all the keys and their non-null values
     * @throws NullPointerException if the set is null.
     * @see javax.cache.Cache#getAll(java.util.Set) 
     */
    public default Map<K, V> getAll(Set<? extends K> set) {
        HashMap<K,V> result = new HashMap<>(set.size());
        for (K k : set) {
            V v = get(k);
            if (v != null) {
                result.put(k, v);
            }
        }
        return result;
    }

    /**
     * Get the previous value of a key and put a new value in its place.
     * 
     * @param key
     * @param value
     * @return the previous value of the key.
     * @throws NullPointerException if the key or value are null.
     * @see javax.cache.Cache#getAndPut(java.lang.Object, java.lang.Object) 
     */
    public V getAndPut(K key, V value);

    /**
     * Put all non-null keys and values in a map into the cache.
     * 
     * @param map 
     * @throws NullPointerException if the map is null.
     * @see javax.cache.Cache#putAll(java.util.Map) 
     */
    public default void putAll(Map<? extends K, ? extends V> map) {
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                put(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Removes the mapping for a key only if currently mapped to the given value.
     * 
     * @param key
     * @param value
     * @return whether a value was removed.
     * @throws NullPointerException if the key or value are null.
     * @see javax.cache.Cache#remove(java.lang.Object, java.lang.Object) 
     */
    public boolean remove(K key, V value);

    /**
     * Get the current mapping for a key, then remove it.
     * 
     * @param key
     * @return the previous mapping of the key.
     * @throws NullPointerException if the key is null.
     * @see javax.cache.Cache#getAndRemove(java.lang.Object) 
     */
    public V getAndRemove(K key);

    /**
     * Replace a key with a new value only if the current value matches the provided
     * value.
     * 
     * @param key      the key
     * @param value    the current value
     * @param newvalue the new value
     * @return 
     * @throws NullPointerException if the key or either value are null.
     * @see javax.cache.Cache#replace(java.lang.Object, java.lang.Object, java.lang.Object) 
     */
    public boolean replace(K key, V value, V newvalue);

    /**
     * Replace a key with a new value only if the key is already defined in the cache.
     * 
     * @param key
     * @param value
     * @return whether the cache was modified.
     * @throws NullPointerException if the key or value are null.
     * @see javax.cache.Cache#replace(java.lang.Object, java.lang.Object) 
     */
    public boolean replace(K key, V value);

    /**
     * Get the current value for a key and set it with a new value only if it is already
     * defined in the cache.
     * 
     * @param key
     * @param value
     * @return the previous value or {@code null} if not defined.
     * @throws NullPointerException if the key or value are null.
     * @see javax.cache.Cache#getAndReplace(java.lang.Object, java.lang.Object)
     */
    public V getAndReplace(K key, V value);

    /**
     * Remove all keys in the set.
     * 
     * @param set
     * @throws NullPointerException if the set is null.
     * @see javax.cache.Cache#removeAll(java.lang.Set) 
     */
    public default void removeAll(Set<? extends K> set) {
        for (K k : set) {
            remove(k);
        }
    }

    /**
     * Remove all keys in the cache.
     * 
     * @see javax.cache.Cache#removeAll() 
     */
    public void removeAll();

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
    public Iterable<CacheView<K, V>> cacheViews();

    /**
     * Remove all expired entries from the cache.
     * This includes the least recently used items from the cache.
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
