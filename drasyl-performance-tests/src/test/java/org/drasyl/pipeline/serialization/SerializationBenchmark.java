/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline.serialization;

import org.drasyl.AbstractBenchmark;
import org.drasyl.DrasylConfig;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class SerializationBenchmark extends AbstractBenchmark {
    private Serialization serializer;

    @Setup
    public void setup() {
        final DrasylConfig config = new DrasylConfig();
        serializer = new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound());
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void findSerializerFor(final Blackhole blackhole) {
        blackhole.consume(serializer.findSerializerFor("Hello"));
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void addSerializerWithHit() {
        serializer.addSerializer(java.io.Serializable.class, Serialization.NULL_SERIALIZER); // has the most implementations
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void removeSerializerWithHit() {
        serializer.removeSerializer(java.io.Serializable.class); // has the most implementations
    }
}
