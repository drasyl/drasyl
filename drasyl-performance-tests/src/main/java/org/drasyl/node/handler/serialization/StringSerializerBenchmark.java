/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.node.handler.serialization;

import org.drasyl.AbstractBenchmark;
import org.drasyl.util.RandomUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

@State(Scope.Benchmark)
public class StringSerializerBenchmark extends AbstractBenchmark {
    private StringSerializer serializer;
    private String string;
    private byte[] bytes;

    @Setup
    public void setup() {
        serializer = new StringSerializer();
        string = RandomUtil.randomString(200_000);
        this.bytes = string.getBytes();
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void toByteArray(final Blackhole blackhole) {
        try {
            blackhole.consume(serializer.toByteArray(string));
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void fromByteArray(final Blackhole blackhole) {
        try {
            blackhole.consume(serializer.fromByteArray(bytes, String.class));
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }
}
