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

import com.frank_mitchell.cache.CacheParameters;
import java.time.Duration;

/**
 *
 * @author fmitchell
 */
final class CacheParameterHolder implements CacheParameters {
    
    private static final Duration DEFAULT_DURATION = Duration.ofMillis(Long.MAX_VALUE);
    private static final int DEFAULT_MAX_SIZE = Integer.MAX_VALUE;
    private volatile boolean _disabled = false;
    private volatile boolean _lastAccessedDroppedFirst = false;
    private volatile Duration _lastAccessLimit = DEFAULT_DURATION;
    private volatile Duration _lastUpdateLimit = DEFAULT_DURATION;
    private volatile int _maximumSize = DEFAULT_MAX_SIZE;

    @Override
    public boolean isDisabled() {
        return _disabled;
    }

    @Override
    public void setDisabled(boolean disabled) {
        _disabled = disabled;
    }

    @Override
    public boolean isLastAccessedDroppedFirst() {
        return _lastAccessedDroppedFirst;
    }

    @Override
    public void setLastAccessedDroppedFirst(boolean value) {
        _lastAccessedDroppedFirst = value;
    }

    @Override
    public Duration getLastAccessLimit() {
        return _lastAccessLimit;
    }

    @Override
    public void setLastAccessLimit(Duration value) {
        _lastAccessLimit = value;
    }

    @Override
    public Duration getLastUpdateLimit() {
        return _lastUpdateLimit;
    }

    @Override
    public void setLastUpdateLimit(Duration value) {
        _lastUpdateLimit = value;
    }

    @Override
    public int getMaximumSize() {
        return _maximumSize;
    }

    @Override
    public void setMaximumSize(int value) {
        _maximumSize = value;
    }

    public void readParameters(CacheParameters source) {
        copyParameters(source, this);
    }

    public void writeParameters(CacheParameters dest) {
        copyParameters(this, dest);
    }

    public static void copyParameters(CacheParameters source, CacheParameters dest) {
        if (source != null && dest != null) {
            dest.setDisabled(source.isDisabled());
            dest.setLastAccessLimit(safe(source.getLastAccessLimit(), DEFAULT_DURATION));
            dest.setLastUpdateLimit(safe(source.getLastUpdateLimit(), DEFAULT_DURATION));
            dest.setLastAccessedDroppedFirst(source.isLastAccessedDroppedFirst());
            dest.setMaximumSize(safe(source.getMaximumSize(), DEFAULT_MAX_SIZE));
        }
    }

    public static <T> T safe(T value, T def) {
        return (value == null) ? def : value;
    }

    public static int safe(int value, int def) {
        return (value <= 0) ? def : value;
    }
    
}
