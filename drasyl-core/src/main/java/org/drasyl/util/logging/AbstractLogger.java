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

import java.util.Arrays;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Logger} that supports lazy evaluation of passed arguments.
 * <p>
 * Example:
 * <pre><code>
 * // without lazy logger
 * if (logger.isDebug()) {
 *     logger.debug("value: {}", this.expensiveComputation());
 * }
 *
 * // with lazy logger
 * logger.debug("value: {}", this::expensiveComputation);
 * </code></pre>
 */
@SuppressWarnings("DuplicatedCode")
abstract class AbstractLogger implements Logger {
    private static final String THROWABLE_MESSAGE = "Exception occurred:";
    private final String name;

    protected AbstractLogger(final String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void trace(final String format, final Supplier<Object> supplier) {
        if (isTraceEnabled()) {
            trace(format, supplier.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8,
                      final Supplier<Object> supplier9) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8,
                      final Supplier<Object> supplier9,
                      final Supplier<Object> supplier10) {
        if (isTraceEnabled()) {
            trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
        }
    }

    @Override
    @SafeVarargs
    public final void trace(final String format, final Supplier<Object>... suppliers) {
        if (isTraceEnabled()) {
            trace(format, Arrays.stream(suppliers).map(Supplier::get).toArray());
        }
    }

    @Override
    public void trace(final Throwable t) {
        trace(THROWABLE_MESSAGE, t);
    }

    @Override
    public void debug(final String format, final Supplier<Object> supplier) {
        if (isDebugEnabled()) {
            debug(format, supplier.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8,
                      final Supplier<Object> supplier9) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8,
                      final Supplier<Object> supplier9,
                      final Supplier<Object> supplier10) {
        if (isDebugEnabled()) {
            debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
        }
    }

    @Override
    @SafeVarargs
    public final void debug(final String format, final Supplier<Object>... suppliers) {
        if (isDebugEnabled()) {
            debug(format, Arrays.stream(suppliers).map(Supplier::get).toArray());
        }
    }

    @Override
    public void debug(final Throwable t) {
        debug(THROWABLE_MESSAGE, t);
    }

    @Override
    public void info(final String format, final Supplier<Object> supplier) {
        if (isInfoEnabled()) {
            info(format, supplier.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7,
                     final Supplier<Object> supplier8) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7,
                     final Supplier<Object> supplier8,
                     final Supplier<Object> supplier9) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7,
                     final Supplier<Object> supplier8,
                     final Supplier<Object> supplier9,
                     final Supplier<Object> supplier10) {
        if (isInfoEnabled()) {
            info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
        }
    }

    @Override
    @SafeVarargs
    public final void info(final String format, final Supplier<Object>... suppliers) {
        if (isInfoEnabled()) {
            info(format, Arrays.stream(suppliers).map(Supplier::get).toArray());
        }
    }

    @Override
    public void info(final Throwable t) {
        info(THROWABLE_MESSAGE, t);
    }

    @Override
    public void warn(final String format, final Supplier<Object> supplier) {
        if (isWarnEnabled()) {
            warn(format, supplier.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7,
                     final Supplier<Object> supplier8) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7,
                     final Supplier<Object> supplier8,
                     final Supplier<Object> supplier9) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> supplier1,
                     final Supplier<Object> supplier2,
                     final Supplier<Object> supplier3,
                     final Supplier<Object> supplier4,
                     final Supplier<Object> supplier5,
                     final Supplier<Object> supplier6,
                     final Supplier<Object> supplier7,
                     final Supplier<Object> supplier8,
                     final Supplier<Object> supplier9,
                     final Supplier<Object> supplier10) {
        if (isWarnEnabled()) {
            warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
        }
    }

    @Override
    @SafeVarargs
    public final void warn(final String format, final Supplier<Object>... suppliers) {
        if (isWarnEnabled()) {
            warn(format, Arrays.stream(suppliers).map(Supplier::get).toArray());
        }
    }

    @Override
    public void warn(final Throwable t) {
        warn(THROWABLE_MESSAGE, t);
    }

    @Override
    public void error(final String format, final Supplier<Object> supplier) {
        if (isErrorEnabled()) {
            error(format, supplier.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8,
                      final Supplier<Object> supplier9) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> supplier1,
                      final Supplier<Object> supplier2,
                      final Supplier<Object> supplier3,
                      final Supplier<Object> supplier4,
                      final Supplier<Object> supplier5,
                      final Supplier<Object> supplier6,
                      final Supplier<Object> supplier7,
                      final Supplier<Object> supplier8,
                      final Supplier<Object> supplier9,
                      final Supplier<Object> supplier10) {
        if (isErrorEnabled()) {
            error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
        }
    }

    @Override
    @SafeVarargs
    public final void error(final String format, final Supplier<Object>... suppliers) {
        if (isErrorEnabled()) {
            error(format, Arrays.stream(suppliers).map(Supplier::get).toArray());
        }
    }

    @Override
    public void error(final Throwable t) {
        error(THROWABLE_MESSAGE, t);
    }

    @Override
    public boolean isEnabled(final LogLevel level) {
        switch (level) {
            case TRACE:
                return isTraceEnabled();
            case DEBUG:
                return isDebugEnabled();
            case INFO:
                return isInfoEnabled();
            case WARN:
                return isWarnEnabled();
            case ERROR:
                return isErrorEnabled();
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String msg) {
        switch (level) {
            case TRACE:
                trace(msg);
                break;
            case DEBUG:
                debug(msg);
                break;
            case INFO:
                info(msg);
                break;
            case WARN:
                warn(msg);
                break;
            case ERROR:
                error(msg);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format, final Object arg) {
        switch (level) {
            case TRACE:
                trace(format, arg);
                break;
            case DEBUG:
                debug(format, arg);
                break;
            case INFO:
                info(format, arg);
                break;
            case WARN:
                warn(format, arg);
                break;
            case ERROR:
                error(format, arg);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Supplier<Object> supplier) {
        switch (level) {
            case TRACE:
                trace(format, supplier);
                break;
            case DEBUG:
                debug(format, supplier);
                break;
            case INFO:
                info(format, supplier);
                break;
            case WARN:
                warn(format, supplier);
                break;
            case ERROR:
                error(format, supplier);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2);
                break;
            case DEBUG:
                debug(format, arg1, arg2);
                break;
            case INFO:
                info(format, arg1, arg2);
                break;
            case WARN:
                warn(format, arg1, arg2);
                break;
            case ERROR:
                error(format, arg1, arg2);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2,
                    final Object arg3) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3);
                break;
            case INFO:
                info(format, arg1, arg2, arg3);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2,
                    final Object arg3,
                    final Object arg4) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3, arg4);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3, arg4);
                break;
            case INFO:
                info(format, arg1, arg2, arg3, arg4);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3, arg4);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3, arg4);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2,
                    final Object arg3,
                    final Object arg4,
                    final Object arg5) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3, arg4, arg5);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3, arg4, arg5);
                break;
            case INFO:
                info(format, arg1, arg2, arg3, arg4, arg5);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3, arg4, arg5);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3, arg4, arg5);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2,
                    final Object arg3,
                    final Object arg4,
                    final Object arg5,
                    final Object arg6) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3, arg4, arg5, arg6);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3, arg4, arg5, arg6);
                break;
            case INFO:
                info(format, arg1, arg2, arg3, arg4, arg5, arg6);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3, arg4, arg5, arg6);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3, arg4, arg5, arg6);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2,
                    final Object arg3,
                    final Object arg4,
                    final Object arg5,
                    final Object arg6,
                    final Object arg7) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
                break;
            case INFO:
                info(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2,
                    final Object arg3,
                    final Object arg4,
                    final Object arg5,
                    final Object arg6,
                    final Object arg7,
                    final Object arg8) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
                break;
            case INFO:
                info(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Object arg1,
                    final Object arg2,
                    final Object arg3,
                    final Object arg4,
                    final Object arg5,
                    final Object arg6,
                    final Object arg7,
                    final Object arg8,
                    final Object arg9) {
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
                break;
            case INFO:
                info(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
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
        switch (level) {
            case TRACE:
                trace(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
                break;
            case DEBUG:
                debug(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
                break;
            case INFO:
                info(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
                break;
            case WARN:
                warn(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
                break;
            case ERROR:
                error(format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2) {
        switch (level) {
            case TRACE:
                trace(format, supplier1, supplier2);
                break;
            case DEBUG:
                debug(format, supplier1, supplier2);
                break;
            case INFO:
                info(format, supplier1, supplier2);
                break;
            case WARN:
                warn(format, supplier1, supplier2);
                break;
            case ERROR:
                error(format, supplier1, supplier2);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level,
                    final String format,
                    final Supplier<Object> supplier1,
                    final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3, final Supplier<Object> supplier4) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3, final Supplier<Object> supplier4,
                    final Supplier<Object> supplier5) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3, final Supplier<Object> supplier4,
                    final Supplier<Object> supplier5, final Supplier<Object> supplier6) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3, final Supplier<Object> supplier4,
                    final Supplier<Object> supplier5, final Supplier<Object> supplier6,
                    final Supplier<Object> supplier7) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3, final Supplier<Object> supplier4,
                    final Supplier<Object> supplier5, final Supplier<Object> supplier6,
                    final Supplier<Object> supplier7, final Supplier<Object> supplier8) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3, final Supplier<Object> supplier4,
                    final Supplier<Object> supplier5, final Supplier<Object> supplier6,
                    final Supplier<Object> supplier7, final Supplier<Object> supplier8,
                    final Supplier<Object> supplier9) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format,
                    final Supplier<Object> supplier1, final Supplier<Object> supplier2,
                    final Supplier<Object> supplier3, final Supplier<Object> supplier4,
                    final Supplier<Object> supplier5, final Supplier<Object> supplier6,
                    final Supplier<Object> supplier7, final Supplier<Object> supplier8,
                    final Supplier<Object> supplier9, final Supplier<Object> supplier10) {
        switch (level) {
            case TRACE:
                trace(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
                break;
            case DEBUG:
                debug(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
                break;
            case INFO:
                info(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
                break;
            case WARN:
                warn(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
                break;
            case ERROR:
                error(format, supplier1.get(), supplier2.get(), supplier3.get(), supplier4.get(), supplier5.get(), supplier6.get(), supplier7.get(), supplier8.get(), supplier9.get(), supplier10.get());
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String format, final Object... arguments) {
        switch (level) {
            case TRACE:
                trace(format, arguments);
                break;
            case DEBUG:
                debug(format, arguments);
                break;
            case INFO:
                info(format, arguments);
                break;
            case WARN:
                warn(format, arguments);
                break;
            case ERROR:
                error(format, arguments);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @SafeVarargs

    @Override
    public final void log(final LogLevel level,
                          final String format,
                          final Supplier<Object>... suppliers) {
        switch (level) {
            case TRACE:
                trace(format, suppliers);
                break;
            case DEBUG:
                debug(format, suppliers);
                break;
            case INFO:
                info(format, suppliers);
                break;
            case WARN:
                warn(format, suppliers);
                break;
            case ERROR:
                error(format, suppliers);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final String msg, final Throwable t) {
        switch (level) {
            case TRACE:
                trace(msg, t);
                break;
            case DEBUG:
                debug(msg, t);
                break;
            case INFO:
                info(msg, t);
                break;
            case WARN:
                warn(msg, t);
                break;
            case ERROR:
                error(msg, t);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }

    @Override
    public void log(final LogLevel level, final Throwable t) {
        switch (level) {
            case TRACE:
                trace(t);
                break;
            case DEBUG:
                debug(t);
                break;
            case INFO:
                info(t);
                break;
            case WARN:
                warn(t);
                break;
            case ERROR:
                error(t);
                break;
            default:
                throw new Error("Unexpected level: " + level);
        }
    }
}
