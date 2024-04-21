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
package com.frank_mitchell.cache.test;

import static org.junit.Assert.*;

import com.frank_mitchell.cache.Cache;
import com.frank_mitchell.cache.CacheParameters;
import com.frank_mitchell.cache.spi.DefaultCache;
import java.time.Clock;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for all {@link Cache} instances.
 * These test the public API only!
 *
 * @author Frank Mitchell
 */
public class DefaultCacheTest {

    protected MockClock _clock;
    protected Cache<String, String> _cache;

    @Before
    public void beforeTest() {
        _clock = new MockClock();
        _cache = newStringCache(_clock);
    }

    /**
     * Override this method to test another Cache instance
     * 
     * @param clock
     * @return
     */
    protected Cache<String, String> newStringCache(Clock clock) {
        return new DefaultCache<>(clock);
    }

    protected void advanceClock(Duration increment) {
        long oldtime = _clock.millis();
        _clock.advanceClock(increment);
        // Testing the test code? Hoo boy.
        assertEquals("time",
                increment.toMillis(), _clock.millis() - oldtime);
    }

    @Test
    public void testNullKey() {
        _cache.put(null, "A");

        assertNull(_cache.get(null));
        assertEquals(0, _cache.size());
    }

    @Test
    public void testNullValue() {
        _cache.put("alpha", null);

        assertNull(_cache.get("alpha"));
        assertEquals(0, _cache.size());
    }

    @Test
    public void testRemoveNonexistentKey() {
        assertNull(_cache.remove("alpha"));
    }

    @Test
    public void testBasicCaching() {

        _cache.put("alpha", "A");
        _cache.put("beta", "B");
        _cache.put("gamma", "G");

        assertEquals("G", _cache.get("gamma"));
        assertEquals("B", _cache.get("beta"));
        assertEquals("A", _cache.get("alpha"));
        assertNull(_cache.get("delta"));
    }

    @Test
    public void testCacheDisabled() {

        _cache.put("alpha", "A");
        _cache.put("beta", "B");

        _cache.getParameters().setDisabled(true);

        _cache.put("gamma", "G");

        assertEquals(0, _cache.size());
        assertNull(_cache.get("gamma"));
        assertNull(_cache.get("beta"));
        assertNull(_cache.get("alpha"));
        assertNull(_cache.get("delta"));
    }

    @Test
    public void testExpiration() {
        final Duration timeout = Duration.ofSeconds(3);
        final Duration increment = Duration.ofSeconds(1);
        final CacheParameters parameters = _cache.getParameters();

        parameters.setLastUpdateLimit(timeout);
        assertEquals("lastUpdateLimit", timeout, parameters.getLastUpdateLimit());

        _cache.put("alpha", "A");
        assertEquals("A", _cache.get("alpha"));
        advanceClock(increment);

        _cache.put("beta", "B");
        assertEquals("A", _cache.get("alpha"));
        assertEquals("B", _cache.get("beta"));
        advanceClock(increment);

        _cache.put("gamma", "G");
        assertEquals("G", _cache.get("gamma"));
        assertEquals("B", _cache.get("beta"));
        assertEquals("A", _cache.get("alpha"));
        advanceClock(increment);

        advanceClock(increment);

        assertEquals("G", _cache.get("gamma"));
        assertEquals("B", _cache.get("beta"));
        assertNull(_cache.get("alpha"));
        assertEquals("size", 2, _cache.size());
    }

    @Test
    public void testTooManyItems() {
        final Duration increment = Duration.ofSeconds(1);
        final CacheParameters parameters = _cache.getParameters();

        parameters.setMaximumSize(2);
        assertEquals("maximumSize", 2, parameters.getMaximumSize());

        _cache.put("alpha", "A");
        advanceClock(increment);
        _cache.put("beta", "B");
        advanceClock(increment);
        _cache.put("gamma", "G");
        advanceClock(increment);

        assertEquals("G", _cache.get("gamma"));
        assertEquals("B", _cache.get("beta"));
        assertNull(_cache.get("alpha"));
        assertEquals("size", 2, _cache.size());
    }
}
