/*
 * Copyright (c) 2021.
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
package org.drasyl.util;

import org.drasyl.util.scheduler.DrasylSchedulerUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.CompletableFuture;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
public class DrasylSchedulerUtilBenchmark {
    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void instanceLightScheduleDirectSingleThread() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        DrasylSchedulerUtil.getInstanceLight().scheduleDirect(() -> future.complete(null));
        future.join();
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void instanceLightScheduleDirectMaxThreads() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        DrasylSchedulerUtil.getInstanceLight().scheduleDirect(() -> future.complete(null));
        future.join();
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void instanceHeavyScheduleDirectSingleThread() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        DrasylSchedulerUtil.getInstanceHeavy().scheduleDirect(() -> future.complete(null));
        future.join();
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void instanceHeavyScheduleDirectMaxThreads() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        DrasylSchedulerUtil.getInstanceHeavy().scheduleDirect(() -> future.complete(null));
        future.join();
    }
}
