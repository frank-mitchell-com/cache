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
 * An interface providing a "safe" read-only facade to inspect a cache.
 * 
 * @author Frank Mitchell
 * @param <K> key type
 * @param <V> value type
 */
public interface CacheView<K,V> {
    /**
     * The key in the cache for this entry.
     * 
     * @return the key in the cache
     * @see Map.Entry#getKey() 
     */
    K getKey();

    /**
     * A "safe" look in the cache for this entry.
     * 
     * @return the key in the cache
     * @see Map.Entry#getValue()
     */
    V getValueSnapshot();

    /**
     * Time entry was last accessed.
     *
     * @return timestamp of last access
     */
    Instant getAccess();

    /**
     * Time entry was last updated.
     *
     * @return timestamp of last update
     */
    Instant getUpdate();
}