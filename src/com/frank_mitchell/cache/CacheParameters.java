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

import java.time.Duration;

/**
 * Represents the configurable parameters of a {@link Cache} instance.
 */
public interface CacheParameters {

    /**
     * Whether this instance is disabled.
     * A disabled cache contains no entries and accepts none.
     * @return whether cache is disabled
     */
    public boolean isDisabled();

    /**
     * Whether this instance is enabled.
     * 
     * @return whether cache is not disabled
     * @see #isDisabled() 
     */
    default public boolean isEnabled() {
        return !isDisabled();
    }
    
    /**
     * Set whether this cache is disabled.
     * 
     * @param disabled whether this cache should be enabled.
     * @see #isDisabled() 
     */
    public void setDisabled(boolean disabled);
    
    /**
     * Set whether this cache is enabled.
     * 
     * @param enabled whether this cache should be enabled.
     * @see #isDisabled() 
     */
    default public void setEnabled(boolean enabled) {
        setDisabled(!enabled);
    }

    /**
     * Whether this cache was configured to drop the least recently read
     * entry.  The default is to drop the least recently written entry.
     *
     * @return whether dropping least recently read.
     */
    public boolean isLastAccessedDroppedFirst();

    /*
     * Set whether the cache drops the least recently accessed entry.
     * The default is to drop the least recently updated entry.
     * @param value the value to set.
     */
    public void setLastAccessedDroppedFirst(boolean value);

    /**
     * The maximum interval between now and an entry's last access time 
     * before an entry will be removed.
     *
     * @return maximum read interval
     */
    public Duration getLastAccessLimit();

    /**
     * Set the maximum interval between now and an 
     * entry's last access time before an entry will be removed.
     * 
     * @param value new maximum interval
     */
    public void setLastAccessLimit(Duration value);

    /**
     * The maximum interval between now and an entry's last update time 
     * before an entry will be removed.
     *
     * @return maximum write interval
     */
    public Duration getLastUpdateLimit();

    /**
     * Set maximum interval between now and an entry's last update time 
     * before an entry will be removed.
     * 
     * @param value new maximum interval
     */
    public void setLastUpdateLimit(Duration value);

    /**
     * The maximum number of entries in the cache before it drops an
     * old one.  "Old" depends on whether the cache deletes by read time
     * or write time.
     *
     * @return maximum number of cache entries.
     */
    public int getMaximumSize();

    /**
     * Set the maximum number of entries in the cache before it drops an
     * old one.  "Old" depends on whether the cache deletes by read time
     * or write time.
     * 
     * @param value new maximum size
     */
    public void setMaximumSize(int value);
}

