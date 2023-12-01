/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.util.logging;

import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Fork
@Warmup
@Measurement
@State(Scope.Benchmark)
public class LoggerBenchmark extends AbstractBenchmark {
    private MyLogger logger;

    @Setup
    public void setup(final Blackhole blackhole) {
        logger = new MyLogger("MyLogger", blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logDisabledMsgOnly() {
        logger.debug("some important logging information here...");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logDisabledWith1Arg() {
        logger.debug("some important logging information here...", "foo");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logDisabledWith10Args() {
        logger.debug("some important logging information here...", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logDisabledWith100Args() {
        logger.debug("some important logging information here...", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logDisabledWith1Supplier() {
        logger.debug("some important logging information here...", () -> "foo");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logDisabledWith10Suppliers() {
        logger.debug("some important logging information here...", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logDisabledWith100Suppliers() {
        logger.debug("some important logging information here...", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logEnabledMsgOnly() {
        logger.info("some important logging information here...");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logEnabledWith1Arg() {
        logger.info("some important logging information here...", "foo");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logEnabledWith10Args() {
        logger.info("some important logging information here...", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logEnabledWith100Args() {
        logger.info("some important logging information here...", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logEnabledWith1Supplier() {
        logger.info("some important logging information here...", () -> "foo");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logEnabledWith10Suppliers() {
        logger.info("some important logging information here...", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Threads(1)
    public void logEnabledWith100Suppliers() {
        logger.info("some important logging information here...", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred", () -> "foo", () -> "bar", () -> "baz", () -> "qux", () -> "quux", () -> "corge", () -> "grault", () -> "garply", () -> "waldo", () -> "fred");
    }

    static class MyLogger extends AbstractLogger {
        protected MyLogger(final String name, final Blackhole blackhole) {
            super(name);
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public void trace(final String msg) {

        }

        @Override
        public void trace(final String format, final Object arg) {

        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2) {

        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2, final Object arg3) {

        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2, final Object arg3, final Object arg4) {

        }

        @Override
        public void trace(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5) {

        }

        @Override
        public void trace(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6) {

        }

        @Override
        public void trace(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7) {

        }

        @Override
        public void trace(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8) {

        }

        @Override
        public void trace(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8,
                          final Object arg9) {

        }

        @Override
        public void trace(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8,
                          final Object arg9,
                          final Object arg10) {

        }

        @Override
        public void trace(final String format, final Object... arguments) {

        }

        @Override
        public void trace(final String msg, final Throwable t) {

        }

        @Override
        public boolean isDebugEnabled() {
            return false; // must be false for benchmark
        }

        @Override
        public void debug(final String msg) {

        }

        @Override
        public void debug(final String format, final Object arg) {

        }

        @Override
        public void debug(final String format, final Object arg1, final Object arg2) {

        }

        @Override
        public void debug(final String format, final Object arg1, final Object arg2, final Object arg3) {

        }

        @Override
        public void debug(final String format, final Object arg1, final Object arg2, final Object arg3, final Object arg4) {

        }

        @Override
        public void debug(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5) {

        }

        @Override
        public void debug(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6) {

        }

        @Override
        public void debug(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7) {

        }

        @Override
        public void debug(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8) {

        }

        @Override
        public void debug(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8,
                          final Object arg9) {

        }

        @Override
        public void debug(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8,
                          final Object arg9,
                          final Object arg10) {

        }

        @Override
        public void debug(final String format, final Object... arguments) {

        }

        @Override
        public void debug(final String msg, final Throwable t) {

        }

        @Override
        public boolean isInfoEnabled() {
            return true; // must be true for benchmark
        }

        @Override
        public void info(final String msg) {

        }

        @Override
        public void info(final String format, final Object arg) {

        }

        @Override
        public void info(final String format, final Object arg1, final Object arg2) {

        }

        @Override
        public void info(final String format, final Object arg1, final Object arg2, final Object arg3) {

        }

        @Override
        public void info(final String format, final Object arg1, final Object arg2, final Object arg3, final Object arg4) {

        }

        @Override
        public void info(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5) {

        }

        @Override
        public void info(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6) {

        }

        @Override
        public void info(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7) {

        }

        @Override
        public void info(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7,
                         final Object arg8) {

        }

        @Override
        public void info(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7,
                         final Object arg8,
                         final Object arg9) {

        }

        @Override
        public void info(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7,
                         final Object arg8,
                         final Object arg9,
                         final Object arg10) {

        }

        @Override
        public void info(final String format, final Object... arguments) {

        }

        @Override
        public void info(final String msg, final Throwable t) {

        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(final String msg) {

        }

        @Override
        public void warn(final String format, final Object arg) {

        }

        @Override
        public void warn(final String format, final Object arg1, final Object arg2) {

        }

        @Override
        public void warn(final String format, final Object arg1, final Object arg2, final Object arg3) {

        }

        @Override
        public void warn(final String format, final Object arg1, final Object arg2, final Object arg3, final Object arg4) {

        }

        @Override
        public void warn(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5) {

        }

        @Override
        public void warn(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6) {

        }

        @Override
        public void warn(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7) {

        }

        @Override
        public void warn(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7,
                         final Object arg8) {

        }

        @Override
        public void warn(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7,
                         final Object arg8,
                         final Object arg9) {

        }

        @Override
        public void warn(final String format,
                         final Object arg1,
                         final Object arg2,
                         final Object arg3,
                         final Object arg4,
                         final Object arg5,
                         final Object arg6,
                         final Object arg7,
                         final Object arg8,
                         final Object arg9,
                         final Object arg10) {

        }

        @Override
        public void warn(final String format, final Object... arguments) {

        }

        @Override
        public void warn(final String msg, final Throwable t) {

        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(final String msg) {

        }

        @Override
        public void error(final String format, final Object arg) {

        }

        @Override
        public void error(final String format, final Object arg1, final Object arg2) {

        }

        @Override
        public void error(final String format, final Object arg1, final Object arg2, final Object arg3) {

        }

        @Override
        public void error(final String format, final Object arg1, final Object arg2, final Object arg3, final Object arg4) {

        }

        @Override
        public void error(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5) {

        }

        @Override
        public void error(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6) {

        }

        @Override
        public void error(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7) {

        }

        @Override
        public void error(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8) {

        }

        @Override
        public void error(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8,
                          final Object arg9) {

        }

        @Override
        public void error(final String format,
                          final Object arg1,
                          final Object arg2,
                          final Object arg3,
                          final Object arg4,
                          final Object arg5,
                          final Object arg6,
                          final Object arg7,
                          final Object arg8,
                          final Object arg9,
                          final Object arg10) {

        }

        @Override
        public void error(final String format, final Object... arguments) {

        }

        @Override
        public void error(final String msg, final Throwable t) {

        }
    }
}
