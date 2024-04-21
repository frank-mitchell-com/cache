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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A frozen clock that can be reset with any time desired.
 * Useful for unit tests where one doesn't want to wait on actual
 * time to tick over.
 * 
 * @author Frank Mitchell
 */
public class MockClock extends Clock {
    
    private ZoneId _zone;
    private volatile long _millis = 0; // It's the Unix epoch!

    public MockClock() {
        this(ZoneId.systemDefault());
    }

    MockClock(ZoneId zone) {
        _zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return _zone;
    }

    @Override
    public Clock withZone(ZoneId zoneid) {
        _zone = zoneid;
        return new MockClock(zoneid);
    }

    @Override
    public long millis() {
        return _millis;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(_millis);
    }

    public void advanceClock(long bymillis) {
        long time = _millis;
        _millis = time + bymillis;
    }

    public void advanceClock(Duration duration) {
        long time = _millis;
        _millis = time + duration.toMillis();
    }
}
