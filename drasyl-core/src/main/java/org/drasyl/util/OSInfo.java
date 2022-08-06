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
import java.net.UnknownHostException;
import java.util.function.Function;

/**
 * Utility class that can be used to detect properties specific to the current runtime
 * environment/operating system, such as os name.
 */
public final class OSInfo {
    private static final Logger LOG = LoggerFactory.getLogger(OSInfo.class);
    static volatile boolean lock;
    private final String osName = detectOSName();
    private final String osVersion = detectOSVersion();
    private final String osArch = detectOSArch();
    private final String currentUser = detectCurrentUser();
    private final String hostname = detectHostname();
    private final int availableProcessors = detectAvailableProcessors();
    private final String cpuName = detectCPUName();
    private final long totalMemory = detectTotalMemory();
    private static final String UNKNOWN = "unknown";

    private OSInfo() {
        // util class
    }

    public static OSInfo getInstance() {
        return LazyOSInfoHolder.INSTANCE;
    }

    private static String detectHostname() {
        String val;
        try {
            final java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
            val = localMachine.getHostName();

            LOG.debug("Hostname: {}", val);
        }
        catch (final UnknownHostException e) {
            val = "localhost";
        }

        return val;
    }

    private static int detectAvailableProcessors() {
        final int val = Runtime.getRuntime().availableProcessors();

        LOG.debug("Available processors: {}", () -> val);

        return val;
    }

    private static long detectTotalMemory() {
        long val = Runtime.getRuntime().maxMemory();

        try {
            if (Platform.isWindows()) {
                final String[] commands = {
                        "cmd",
                        "/c",
                        "wmic ComputerSystem get TotalPhysicalMemory | findstr [0..9]"
                };
                val = commandDetectionHelper(commands, Long::parseLong);
            }
            else if (Platform.isMac()) {
                final String[] commands = { "system_profiler", "SPHardwareDataType" };

                val = commandDetectionHelper(commands, s -> {
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
                val = commandDetectionHelper(commands, s -> {
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

        LOG.debug("Total memory: {} bytes", val);

        return val;
    }

    /**
     * Executes the given {@code commands} and applies for every result line the given
     * {@code matcher}. If the {@code matcher} returns a non-null value, it will be returned. If the
     * {@code matcher} does not match to any line, a IOException will be thrown.
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

    /**
     * @return the  OS name
     */
    public String osName() {
        return osName;
    }

    /**
     * @return the OS version
     */
    public String osVersion() {
        return osVersion;
    }

    /**
     * @return the OS arch
     */
    public String osArch() {
        return osArch;
    }

    /**
     * @return the current user, that is executing this JVM
     */
    public String currentUser() {
        return currentUser;
    }

    /**
     * @return the hostname of the executing JVM
     */
    public String hostname() {
        return hostname;
    }

    /**
     * @return the number of available cores (can be virtual)
     */
    public int availableProcessors() {
        return availableProcessors;
    }

    /**
     * This method does <b>NOT</b> return the JVM memory. Instead, it returns the total available
     * system memory.
     *
     * @return the amount of total system memory
     */
    public long totalMemory() {
        return totalMemory;
    }

    /**
     * @return return the CPU name
     */
    public String cpuName() {
        return cpuName;
    }

    private static String detectOSName() {
        final String val = System.getProperty("os.name", UNKNOWN);

        LOG.debug("OS name: {}", () -> val);

        return val;
    }

    private static String detectOSVersion() {
        final String val = System.getProperty("os.version", UNKNOWN);

        LOG.debug("OS version: {}", () -> val);

        return val;
    }

    private static String detectOSArch() {
        final String val = Platform.ARCH;

        LOG.debug("OS arch: {}", () -> val);

        return val;
    }

    private static String detectCurrentUser() {
        final String val = System.getProperty("user.name", UNKNOWN);

        LOG.debug("Current user: {}", () -> val);

        return val;
    }

    private static String detectCPUName() {
        String val = UNKNOWN;

        try {
            if (Platform.isWindows()) {
                final String[] commands = {
                        "cmd",
                        "/c",
                        "wmic CPU get NAME | findstr [A-Za-z]"
                };
                val = commandDetectionHelper(commands, s -> {
                    if (!s.startsWith("Name") && !s.isEmpty()) {
                        return s;
                    }

                    return null;
                });

                //stdInput.readLine()
            }
            else if (Platform.isMac()) {
                final String[] commands = { "system_profiler", "SPHardwareDataType" };

                val = commandDetectionHelper(commands, s -> {
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
                val = commandDetectionHelper(commands, s -> {
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

        LOG.debug("CPU name: {}", val);

        return val;
    }

    private static final class LazyOSInfoHolder {
        static final OSInfo INSTANCE = new OSInfo();
        @SuppressWarnings("unused")
        static final boolean LOCK = lock = true;
    }
}
