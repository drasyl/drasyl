/*
 * Copyright (c) 2020.
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

import org.junit.jupiter.api.extension.*;

import static java.lang.System.currentTimeMillis;
import static org.junit.platform.commons.util.AnnotationUtils.isAnnotated;

public class BenchmarkExtension implements
        BeforeAllCallback, BeforeTestExecutionCallback,
        AfterTestExecutionCallback, AfterAllCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create("org", "codefx", "BenchmarkExtension");

    // EXTENSION POINTS

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!shouldBeBenchmarked(context)) {
            return;
        }

        storeNowAsLaunchTime(context, LaunchTimeKey.CLASS);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        if (!shouldBeBenchmarked(context)) {
            return;
        }

        storeNowAsLaunchTime(context, LaunchTimeKey.TEST);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (!shouldBeBenchmarked(context)) {
            return;
        }

        long launchTime = loadLaunchTime(context, LaunchTimeKey.TEST);
        long elapsedTime = currentTimeMillis() - launchTime;
        report("Test", context, elapsedTime);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (!shouldBeBenchmarked(context)) {
            return;
        }

        long launchTime = loadLaunchTime(context, LaunchTimeKey.CLASS);
        long elapsedTime = currentTimeMillis() - launchTime;
        report("Test container", context, elapsedTime);
    }

    // HELPER

    private static boolean shouldBeBenchmarked(ExtensionContext context) {
        return context.getElement()
                .map(el -> isAnnotated(el, Benchmark.class))
                .orElse(false);
    }

    private static void storeNowAsLaunchTime(
            ExtensionContext context, LaunchTimeKey key) {
        context.getStore(NAMESPACE).put(key, currentTimeMillis());
    }

    private static long loadLaunchTime(
            ExtensionContext context, LaunchTimeKey key) {
        return context.getStore(NAMESPACE).get(key, long.class);
    }

    private static void report(
            String unit, ExtensionContext context, long elapsedTime) {
        String message = String.format(
                "%s '%s' took %d ms.",
                unit, context.getDisplayName(), elapsedTime);
        context.publishReportEntry("benchmark", message);
    }

    private enum LaunchTimeKey {
        CLASS, TEST
    }
}
