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
