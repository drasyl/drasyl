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
package org.drasyl.util;

import com.sun.jna.Platform;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.util.function.Function;

/**
 * Utility class that can be used to detect properties specific to the current runtime environment,
 * such as Java version and the availability of the {@code sun.misc.Unsafe} object.
 */
public final class PlatformDependent {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformDependent.class);
    private static final int JAVA_VERSION = detectJavaVersion();
    private static final String OS_NAME = detectOSName();
    private static final String OS_VERSION = detectOSVersion();
    private static final String OS_ARCH = detectOSArch();
    private static final String CURRENT_USER = detectCurrentUser();
    private static final String HOSTNAME = detectHostname();
    private static final int AVAILABLE_PROCESSORS = detectAvailableProcessors();
    private static final String CPU_NAME = detectCPUName();
    private static final long TOTAL_MEMORY = detectTotalMemory();
    // See https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/
    // ImageInfo.java
    private static final boolean RUNNING_IN_NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;

    private PlatformDependent() {
        // util class
    }

    /**
     * Returns the Java version.
     *
     * @return the Java version
     */
    public static int javaVersion() {
        return JAVA_VERSION;
    }

    public static String osName() {
        return OS_NAME;
    }

    public static String osVersion() {
        return OS_VERSION;
    }

    public static String osArch() {
        return OS_ARCH;
    }

    public static String currentUser() {
        return CURRENT_USER;
    }

    public static String hostname() {
        return HOSTNAME;
    }

    public static int availableProcessors() {
        return AVAILABLE_PROCESSORS;
    }

    public static long totalMemory() {
        return TOTAL_MEMORY;
    }

    public static String cpuName() {
        return CPU_NAME;
    }

    /**
     * Returns {@code true} if access to {@link sun.misc.Unsafe#staticFieldOffset(Field)} is
     * supported, {@code false} otherwise.
     *
     * @return {@code true} if access to {@link sun.misc.Unsafe#staticFieldOffset(Field)} is
     * supported, {@code false} otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean unsafeStaticFieldOffsetSupported() {
        return !RUNNING_IN_NATIVE_IMAGE;
    }

    static int javaSpecificationVersion() {
        return Integer.parseInt(System.getProperty("java.specification.version", "11"));
    }

    private static int detectJavaVersion() {
        final int majorVersion = javaSpecificationVersion();

        LOG.debug("Java version: {}", majorVersion);

        return majorVersion;
    }

    private static String detectOSName() {
        final String osName = System.getProperty("os.name", "unknown");

        LOG.debug("OS name: {}", () -> osName);

        return osName;
    }

    private static String detectOSVersion() {
        final String osVersion = System.getProperty("os.version", "unknown");

        LOG.debug("OS version: {}", () -> osVersion);

        return osVersion;
    }

    private static String detectOSArch() {
        final String osArch = Platform.ARCH;

        LOG.debug("OS arch: {}", () -> osArch);

        return osArch;
    }

    private static String detectCurrentUser() {
        final String currentUser = System.getProperty("user.name", "unknown");

        LOG.debug("Current user: {}", () -> currentUser);

        return currentUser;
    }

    private static String detectHostname() {
        String hostname;
        try {
            final java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
            hostname = localMachine.getHostName();

            LOG.debug("Hostname: {}", hostname);
        }
        catch (final UnknownHostException e) {
            hostname = "localhost";
        }

        return hostname;
    }

    private static int detectAvailableProcessors() {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();

        LOG.debug("Available processors: {}", () -> availableProcessors);

        return availableProcessors;
    }

    private static long detectTotalMemory() {
        long totalMemory = Runtime.getRuntime().maxMemory();

        try {
            if (Platform.isWindows()) {
                final String[] commands = {
                        "cmd",
                        "/c",
                        "wmic ComputerSystem get TotalPhysicalMemory | findstr [0..9]"
                };
                totalMemory = commandDetectionHelper(commands, Long::parseLong);
            }
            else if (Platform.isMac()) {
                final String[] commands = { "system_profiler", "SPHardwareDataType" };

                totalMemory = commandDetectionHelper(commands, s -> {
                    if (s.startsWith("Memory:")) {
                        s = s.replace("Memory", "");
                        s = s.replace(":", "");
                        s = s.replace(" ", "");
                        s = s.replace("GB", "");

                        return Long.parseLong(s) * 1_073_741_824;
                    }

                    return null;
                });
            }
            else if (Platform.isLinux()) {
                final String[] commands = {
                        "/bin/bash",
                        "-c",
                        "cat /proc/meminfo | grep MemTotal"
                };
                totalMemory = commandDetectionHelper(commands, s -> {
                    if (s.startsWith("MemTotal")) {
                        s = s.replace("MemTotal", "");
                        s = s.replace(":", "");
                        s = s.replace("kB", "");
                        s = s.replace(" ", "");

                        return Long.parseLong(s) * 1_024;
                    }

                    return null;
                });
            }
        }
        catch (final Exception e) { // NOSONAR
            // ignore
        }

        LOG.debug("Total memory: {} bytes", totalMemory);

        return totalMemory;
    }

    private static String detectCPUName() {
        String cpuName = "unknown";

        try {
            if (Platform.isWindows()) {
                final String[] commands = {
                        "cmd",
                        "/c",
                        "wmic CPU get NAME | findstr [A-Za-z]"
                };
                cpuName = commandDetectionHelper(commands, s -> {
                    if (!s.startsWith("Name") && !s.isEmpty()) {
                        return s;
                    }

                    return null;
                });

                //stdInput.readLine()
            }
            else if (Platform.isMac()) {
                final String[] commands = { "system_profiler", "SPHardwareDataType" };

                cpuName = commandDetectionHelper(commands, s -> {
                    if (s.startsWith("Chip:")) {
                        s = s.replace("Chip", "");
                        s = s.replace(":", "");
                        s = s.trim();

                        return s;
                    }

                    return null;
                });
            }
            else if (Platform.isLinux()) {
                final String[] commands = {
                        "/bin/bash",
                        "-c",
                        "cat /proc/cpuinfo | grep \"model name\""
                };
                cpuName = commandDetectionHelper(commands, s -> {
                    if (s.startsWith("model name")) {
                        s = s.replace("model name", "");
                        s = s.replace(":", "");
                        s = s.trim();

                        return s;
                    }

                    return null;
                });
            }
        }
        catch (final Exception e) { // NOSONAR
            // ignore
        }

        LOG.debug("CPU name: {}", cpuName);

        return cpuName;
    }

    /**
     * Executes the given {@code commands} and applies for every result line the given {@code
     * matcher}. If the {@code matcher} returns a non-null value, it will be returned. If the {@code
     * matcher} does not match to any line, a IOException will be thrown.
     *
     * @param commands the commands to execute
     * @param matcher  the matcher to match for the wanted to be output
     * @return the wanted output or and IOException
     * @throws IOException if the wanted output can't be found or if an error occurred
     */
    private static <R> R commandDetectionHelper(final String[] commands,
                                                final Function<String, R> matcher) throws IOException {
        final Runtime rt = Runtime.getRuntime();
        final Process proc = rt.exec(commands);

        final BufferedReader stdError = new BufferedReader(
                new InputStreamReader(proc.getErrorStream()));

        String s;
        if ((s = stdError.readLine()) != null) {
            throw new IOException(s);
        }

        final BufferedReader stdInput = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));
        while ((s = stdInput.readLine()) != null) {
            s = s.trim();

            final R result = matcher.apply(s);

            if (result != null) {
                return result;
            }
        }

        throw new IOException();
    }
}
