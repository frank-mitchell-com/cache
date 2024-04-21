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

import java.util.*;

/**
 * A CacheConfiguration watches a specific resource for changes, then
 * reconfigures {@link Cache}s based on those changes.
 * In the most common use case a CacheConfiguration monitors a configuration
 * file.  When the file contents change, it rereads the file and configures
 * caches based on those changes.
 *
 * The application may use one configuration per file, network connection,
 * etc. and configuring caches may require multiple files.
 * In the general case, then, multiple configurations may support
 * a single {@link CacheManager}.
 * Likewise, while one factory per application may be the simplest option,
 * nothing in the API mandates that.  Different subsystems may allocate
 * their own factories, yet all may use the same external resources.
 *
 * In the general case, then, multiple factories may rely on multiple
 * configurations, and update multiple caches with the same name.
 * To avoid this last case, factories may register with a prefix.
 * For example if a factory has a prefix "foo" and the configuration
 * sees a change in "foobar", the configuration should only update
 * cache "bar" for factory "foo".
 *
 * Implementations must avoid changing cache parameters unnecessarily,
 * perhaps by retaining the previous values and only reconfiguring a cache
 * if its values change.
 * This is not a hard-and-fast rule, however.  Certain file formats
 * that describe object graphs may unavoidably remove and recreate
 * caches.
 */
public interface CacheConfiguration {

    /**
     * Get the current list of {@Link CacheManager} instances relying on this
     * instance.
     *
     * @return set of clients being watched
     */
    public Set<CacheManager> getClients();
    
    /**
     * Whether this client is currently in the configuration's client list.
     * 
     * @param client potential client
     * @return whether client is in list
     */
    public boolean hasClient(CacheManager client);

    /**
     * Get the client for this prefix.
     * 
     * @param prefix
     * @return the client or <code>null<code> if no such client
     */
    public CacheManager getClientByPrefix(String prefix);

    /**
     * Add a client with a zero-length prefix.
     * 
     * @param client 
     */
    default public void addClient(CacheManager client) {
        addClient(client, "");
    }

    /**
     * Add a client with a zero-length prefix.
     * 
     * @param client client for this configuration
     * @param prefix prefix to distinguish multiple caches with similar names
     */
    public void addClient(CacheManager client, String prefix);

    /**
     * Remove a client from the client list.
     * 
     * @param client client to remove
     */
    public void removeClient(CacheManager client);

    public void refresh();
}

