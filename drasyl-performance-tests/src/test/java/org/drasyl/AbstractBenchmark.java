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
package org.drasyl;

import io.netty.util.internal.SystemPropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("java:S5786")
public abstract class AbstractBenchmark {
    @Test
    // prevent parallel execution of benchmarks
    @ResourceLock("Benchmark")
    void run() throws Exception {
        final Collection<RunResult> runResults = new Runner(newOptionsBuilder().build()).run();

        assertFalse(runResults.isEmpty());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected ChainedOptionsBuilder newOptionsBuilder() throws IOException {
        final String className = getClass().getSimpleName();

        final ChainedOptionsBuilder runnerOptions = new OptionsBuilder()
                .include(className);

        if (getForks() > 0) {
            runnerOptions.forks(getForks());
        }

        if (getWarmupIterations() > 0) {
            runnerOptions.warmupIterations(getWarmupIterations());
        }

        if (getMeasureIterations() > 0) {
            runnerOptions.measurementIterations(getMeasureIterations());
        }

        if (getTimeout() > 0) {
            runnerOptions.timeout(TimeValue.minutes(getTimeout()));
        }

        if (getReportDir() != null) {
            final String filePath = getReportDir() + className + ".json";
            final File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
            else {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            runnerOptions.resultFormat(ResultFormatType.JSON);
            runnerOptions.result(filePath);
        }

        return runnerOptions;
    }

    protected int getForks() {
        return SystemPropertyUtil.getInt("forks", -1);
    }

    protected int getWarmupIterations() {
        return SystemPropertyUtil.getInt("warmups", -1);
    }

    protected int getMeasureIterations() {
        return SystemPropertyUtil.getInt("measurements", -1);
    }

    protected int getTimeout() {
        return SystemPropertyUtil.getInt("timeout", -1);
    }

    protected String getReportDir() {
        return SystemPropertyUtil.get("perfReportDir");
    }

    @SuppressWarnings("unused")
    public static void handleUnexpectedException(final Throwable t) {
        assertNull(t);
    }
}
