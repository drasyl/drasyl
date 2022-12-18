/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.util;

import com.google.common.cache.CacheBuilder;
import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;

import static java.time.Duration.ofHours;

@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Benchmark)
public class ExpiringMapBenchmark extends AbstractBenchmark {
    private ExpiringMap<String, String> emptyExpiringMap;
    private HashMap<String, String> emptyHashMap;
    private Map<Object, Object> emptyGuavaCache;
    private ExpiringMap<String, String> nonEmptyExpiringMap;
    private HashMap<String, String> nonEmptyHashMap;
    private Map<Object, Object> nonEmptyGuavaCache;

    @Setup(Level.Invocation)
    public void setup() {
        // empty
        emptyExpiringMap = new ExpiringMap<>(100, 3600_000, -1);
        emptyHashMap = new HashMap<>();
        emptyGuavaCache = CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(ofHours(1)).build().asMap();

        // non-empty
        nonEmptyExpiringMap = new ExpiringMap<>(100, 3_600_000, -1);
        nonEmptyExpiringMap.put("Hello", "World");
        nonEmptyHashMap = new HashMap<>();
        nonEmptyHashMap.put("Hello", "World");
        nonEmptyGuavaCache = CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(ofHours(1)).build().asMap();
        nonEmptyGuavaCache.put("Hello", "World");
    }

    // containsKey

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void emptyContainsKey(final Blackhole blackhole) {
        blackhole.consume(emptyExpiringMap.containsKey("Hello"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void emptyContainsKeyHashSet(final Blackhole blackhole) {
        blackhole.consume(emptyHashMap.containsKey("Hello"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void emptyContainsKeyGuavaCache(final Blackhole blackhole) {
        blackhole.consume(emptyGuavaCache.containsKey("Hello"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void nonEmptyContainsKey(final Blackhole blackhole) {
        blackhole.consume(nonEmptyExpiringMap.containsKey("Hello"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void nonEmptyContainsKeyHashSet(final Blackhole blackhole) {
        blackhole.consume(nonEmptyHashMap.containsKey("Hello"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void nonEmptyContainsKeyGuavaCache(final Blackhole blackhole) {
        blackhole.consume(nonEmptyGuavaCache.containsKey("Hello"));
    }

    // put

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void newEntryPut(final Blackhole blackhole) {
        blackhole.consume(emptyExpiringMap.put("Hello", "World"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void newEntryPutHashSet(final Blackhole blackhole) {
        blackhole.consume(emptyHashMap.put("Hello", "World"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void newEntryPutGuavaCache(final Blackhole blackhole) {
        blackhole.consume(emptyGuavaCache.put("Hello", "World"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void existingEntryPut(final Blackhole blackhole) {
        blackhole.consume(nonEmptyExpiringMap.put("Hello", "World"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void existingEntryPutHashSet(final Blackhole blackhole) {
        blackhole.consume(nonEmptyHashMap.put("Hello", "World"));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void existingEntryPutGuavaCache(final Blackhole blackhole) {
        blackhole.consume(nonEmptyGuavaCache.put("Hello", "World"));
    }
}
