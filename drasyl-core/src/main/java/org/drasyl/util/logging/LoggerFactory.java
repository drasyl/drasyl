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
package org.drasyl.util.logging;

/**
 * Provides loggers. If available, <a href="https://www.slf4j.org/">SLF4J</a> loggers are used.
 * Otherwise, <a href="https://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">java.util.logging</a>
 * loggers are used.
 */
@SuppressWarnings("java:S118")
public abstract class LoggerFactory {
    private static LoggerFactory defaultFactory;

    /**
     * Creates a new logger with name of specified {@code clazz}.
     */
    public static Logger getLogger(final Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * Creates a new logger with specified {@code name}.
     */
    public static Logger getLogger(final String name) {
        return getDefaultFactory().newLogger(name);
    }

    protected abstract Logger newLogger(final String name);

    private static LoggerFactory getDefaultFactory() {
        if (defaultFactory == null) {
            defaultFactory = getSlf4JLoggerFactory();

            if (defaultFactory == null) {
                // fallback to jdk logging
                defaultFactory = getJdkLoggerFactory();
            }
        }
        return defaultFactory;
    }

    @SuppressWarnings("java:S1166")
    private static LoggerFactory getSlf4JLoggerFactory() {
        try {
            final LoggerFactory factory = new Slf4JLoggerFactory();
            factory.newLogger(LoggerFactory.class.getName()).debug("Using SLF4J as the default logging framework");
            return factory;
        }
        catch (final LinkageError ignore) {
            return null;
        }
    }

    private static LoggerFactory getJdkLoggerFactory() {
        final LoggerFactory factory = JdkLoggerFactory.INSTANCE;
        factory.newLogger(LoggerFactory.class.getName()).debug("Using java.util.logging as the default logging framework");
        return factory;
    }
}
