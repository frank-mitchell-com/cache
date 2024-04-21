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

import java.util.SortedSet;

/**
 * A CacheManager manages a collection of {@link Cache}s by name.
 */
public interface CacheManager {

    /**
     * Get a cache named <i>name</i> that maps <i>keys</i> to <i>values</i>.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param name name of a new or existing cache
     * @param keys the class for keys
     * @param values the class for values
     * @return a cache that stores the specified classes
     * @throws IllegalArgumentException if either class argument is missing
     *   or inconsistent with the method's last invocation for <i>name</i>.
     */
    public <K,V> Cache<K,V> getCache(String name, Class<K> keys, Class<V> values)
        throws IllegalArgumentException;

    /**
     * Get a list of all cache names in use.
     * 
     * @return a set of cache names
     */
    public SortedSet<String> getCacheList();

    /**
     * Get a set of configurations used by this factory.
     * 
     * @return a set of configurations.
     */
    public CacheConfiguration[] getConfigurations();

    /**
     * Register a new configuration.
     * The implementer should call either
     * {@link CacheConfiguration#addClient(CacheManager)} or
     * {@link CacheConfiguration#addClient(CacheManager, java.lang.String)}
     * to register itself for updates.
     * 
     * @param cf configuration to add.
     */
    public void addConfiguration(CacheConfiguration cf);

    /**
     * Remove a configuration.
     * The implementer should call 
     * {@link CacheConfiguration#removeClient(CacheManager)}
     * to register itself for updates.
     * 
     * @param cf configuration to remove.
     */
    public void removeConfiguration(CacheConfiguration cf);

    /**
     * Get the named cache to update its parameters.
     * If that cache doesn't exist yet, the implementer should either create it
     * on the fly or create a dummy configuration for when it does exist.
     * 
     * @param name
     * @return a parameters object
     */
    public CacheParameters getParameters(String name);
}
