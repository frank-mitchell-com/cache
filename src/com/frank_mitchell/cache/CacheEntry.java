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

import java.time.Instant;
import java.util.Map;

/**
 * An entry in a {@link Cache}, including access and update times. The access
 * time changes whenever the value of an entry is read, and the update time
 * changes whenever the value of an entry is written.
 *
 * @author Frank Mitchell
 * @param <K> key type
 * @param <V> value type
 */
public interface CacheEntry<K, V> {

    /**
     * Get the key associated with this entry.
     *
     * @return key value
     * @see Map.Entry#getKey()
     */
    public K getKey();

    /**
     * Get the value associated with this entry.
     *
     * @return value
     * @see Map.Entry#getValue()
     */
    public V getValue();

    /**
     * Get a snapshot of the value associated with this entry.
     * This method should <strong>not</strong> affect {@link #getAccess()}.
     *
     * @return value
     * @see Map.Entry#getValue()
     */
    public V getValueSnapshot();

    /**
     * Set the value associated with this entry.
     *
     * @param v new value
     * @return old value
     * @see Map.Entry#setValue(java.lang.Object)
     */
    public V setValue(V v);

    /**
     * Time entry was last accessed. This is defined as calling the
     * {@link Map.Entry#getValue() } method.
     *
     * @return timestamp of last access
     */
    Instant getAccess();

    /**
     * Time entry was last updated. This is defined as calling the
     * {@link Map.Entry#setValue(java.lang.Object) } method.
     *
     * @return timestamp of last update
     */
    Instant getUpdate();
}
