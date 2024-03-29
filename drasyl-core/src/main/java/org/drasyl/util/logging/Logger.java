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

import org.drasyl.util.internal.UnstableApi;

import java.util.function.Supplier;

@SuppressWarnings("unchecked")
@UnstableApi
public interface Logger {
    /**
     * Return the name of this {@code Logger} instance.
     *
     * @return name of this logger instance
     */
    String name();

    /**
     * Is the logger instance enabled for the TRACE level?
     *
     * @return {@code true} if this Logger is enabled for the TRACE level, {@code false} otherwise.
     */
    boolean isTraceEnabled();

    /**
     * Log a message at the TRACE level.
     *
     * @param msg the message string to be logged
     */
    void trace(String msg);

    /**
     * Log a message at the TRACE level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void trace(String format, Object arg);

    /**
     * Log a message at the TRACE level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void trace(String format, Supplier<Object> supplier);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     * @param supplier9 the ninth argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8,
               Supplier<Object> supplier9);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format     the format string
     * @param supplier1  the first argument supplier
     * @param supplier2  the second argument supplier
     * @param supplier3  the third argument supplier
     * @param supplier4  the fourth argument supplier
     * @param supplier5  the fifth argument supplier
     * @param supplier6  the sixth argument supplier
     * @param supplier7  the seventh argument supplier
     * @param supplier8  the eighth argument supplier
     * @param supplier9  the ninth argument supplier
     * @param supplier10 the tenth argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8,
               Supplier<Object> supplier9,
               Supplier<Object> supplier10);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void trace(String format, Object arg1, Object arg2);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     */
    void trace(String format, Object arg1, Object arg2, Object arg3);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     */
    void trace(String format, Object arg1, Object arg2, Object arg3, Object arg4);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     */
    void trace(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     */
    void trace(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     */
    void trace(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     */
    void trace(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     */
    void trace(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8,
               Object arg9);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     * @param arg10  the tenth argument
     */
    void trace(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8,
               Object arg9,
               Object arg10);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void trace(String format, Object... arguments);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void trace(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the TRACE level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void trace(String msg, Throwable t);

    /**
     * Log an exception (throwable) at the TRACE level.
     *
     * @param t the exception (throwable) to log
     */
    void trace(Throwable t);

    /**
     * Is the logger instance enabled for the DEBUG level?
     *
     * @return {@code true} if this Logger is enabled for the DEBUG level, {@code false} otherwise.
     */
    boolean isDebugEnabled();

    /**
     * Log a message at the DEBUG level.
     *
     * @param msg the message string to be logged
     */
    void debug(String msg);

    /**
     * Log a message at the DEBUG level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void debug(String format, Object arg);

    /**
     * Log a message at the DEBUG level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void debug(String format, Supplier<Object> supplier);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void debug(String format, Supplier<Object> supplier1, Supplier<Object> supplier2);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     * @param supplier9 the ninth argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8,
               Supplier<Object> supplier9);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format     the format string
     * @param supplier1  the first argument supplier
     * @param supplier2  the second argument supplier
     * @param supplier3  the third argument supplier
     * @param supplier4  the fourth argument supplier
     * @param supplier5  the fifth argument supplier
     * @param supplier6  the sixth argument supplier
     * @param supplier7  the seventh argument supplier
     * @param supplier8  the eighth argument supplier
     * @param supplier9  the ninth argument supplier
     * @param supplier10 the tenth argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8,
               Supplier<Object> supplier9,
               Supplier<Object> supplier10);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void debug(String format, Object arg1, Object arg2);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     */
    void debug(String format, Object arg1, Object arg2, Object arg3);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     */
    void debug(String format, Object arg1, Object arg2, Object arg3, Object arg4);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     */
    void debug(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     */
    void debug(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     */
    void debug(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     */
    void debug(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     */
    void debug(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8,
               Object arg9);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     * @param arg10  the tenth argument
     */
    void debug(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8,
               Object arg9,
               Object arg10);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void debug(String format, Object... arguments);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void debug(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the DEBUG level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void debug(String msg, Throwable t);

    /**
     * Log an exception (throwable) at the DEBUG level.
     *
     * @param t the exception (throwable) to log
     */
    void debug(Throwable t);

    /**
     * Is the logger instance enabled for the INFO level?
     *
     * @return {@code true} if this Logger is enabled for the INFO level, {@code false} otherwise.
     */
    boolean isInfoEnabled();

    /**
     * Log a message at the INFO level.
     *
     * @param msg the message string to be logged
     */
    void info(String msg);

    /**
     * Log a message at the INFO level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void info(String format, Object arg);

    /**
     * Log a message at the INFO level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the INFO level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void info(String format, Supplier<Object> supplier);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void info(String format, Supplier<Object> supplier1, Supplier<Object> supplier2);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7,
              Supplier<Object> supplier8);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     * @param supplier9 the ninth argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7,
              Supplier<Object> supplier8,
              Supplier<Object> supplier9);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format     the format string
     * @param supplier1  the first argument supplier
     * @param supplier2  the second argument supplier
     * @param supplier3  the third argument supplier
     * @param supplier4  the fourth argument supplier
     * @param supplier5  the fifth argument supplier
     * @param supplier6  the sixth argument supplier
     * @param supplier7  the seventh argument supplier
     * @param supplier8  the eighth argument supplier
     * @param supplier9  the ninth argument supplier
     * @param supplier10 the tenth argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7,
              Supplier<Object> supplier8,
              Supplier<Object> supplier9,
              Supplier<Object> supplier10);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void info(String format, Object arg1, Object arg2);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     */
    void info(String format, Object arg1, Object arg2, Object arg3);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     */
    void info(String format, Object arg1, Object arg2, Object arg3, Object arg4);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     */
    void info(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     */
    void info(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     */
    void info(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     */
    void info(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7,
              Object arg8);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     */
    void info(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7,
              Object arg8,
              Object arg9);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     * @param arg10  the tenth argument
     */
    void info(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7,
              Object arg8,
              Object arg9,
              Object arg10);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void info(String format, Object... arguments);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void info(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the INFO level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void info(String msg, Throwable t);

    /**
     * Log an exception (throwable) at the INFO level.
     *
     * @param t the exception (throwable) to log
     */
    void info(Throwable t);

    /**
     * Is the logger instance enabled for the WARN level?
     *
     * @return {@code true} if this Logger is enabled for the WARN level, {@code false} otherwise.
     */
    boolean isWarnEnabled();

    /**
     * Log a message at the WARN level.
     *
     * @param msg the message string to be logged
     */
    void warn(String msg);

    /**
     * Log a message at the WARN level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void warn(String format, Object arg);

    /**
     * Log a message at the WARN level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the WARN level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void warn(String format, Supplier<Object> supplier);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void warn(String format, Supplier<Object> supplier1, Supplier<Object> supplier2);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7,
              Supplier<Object> supplier8);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     * @param supplier9 the ninth argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7,
              Supplier<Object> supplier8,
              Supplier<Object> supplier9);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format     the format string
     * @param supplier1  the first argument supplier
     * @param supplier2  the second argument supplier
     * @param supplier3  the third argument supplier
     * @param supplier4  the fourth argument supplier
     * @param supplier5  the fifth argument supplier
     * @param supplier6  the sixth argument supplier
     * @param supplier7  the seventh argument supplier
     * @param supplier8  the eighth argument supplier
     * @param supplier9  the ninth argument supplier
     * @param supplier10 the tenth argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2,
              Supplier<Object> supplier3,
              Supplier<Object> supplier4,
              Supplier<Object> supplier5,
              Supplier<Object> supplier6,
              Supplier<Object> supplier7,
              Supplier<Object> supplier8,
              Supplier<Object> supplier9,
              Supplier<Object> supplier10);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void warn(String format, Object arg1, Object arg2);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     */
    void warn(String format, Object arg1, Object arg2, Object arg3);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     */
    void warn(String format, Object arg1, Object arg2, Object arg3, Object arg4);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     */
    void warn(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     */
    void warn(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     */
    void warn(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     */
    void warn(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7,
              Object arg8);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     */
    void warn(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7,
              Object arg8,
              Object arg9);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     * @param arg10  the tenth argument
     */
    void warn(String format,
              Object arg1,
              Object arg2,
              Object arg3,
              Object arg4,
              Object arg5,
              Object arg6,
              Object arg7,
              Object arg8,
              Object arg9,
              Object arg10);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void warn(String format, Object... arguments);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void warn(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the WARN level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void warn(String msg, Throwable t);

    /**
     * Log an exception (throwable) at the WARN level.
     *
     * @param t the exception (throwable) to log
     */
    void warn(Throwable t);

    /**
     * Is the logger instance enabled for the ERROR level?
     *
     * @return {@code true} if this Logger is enabled for the ERROR level, {@code false} otherwise.
     */
    boolean isErrorEnabled();

    /**
     * Log a message at the ERROR level.
     *
     * @param msg the message string to be logged
     */
    void error(String msg);

    /**
     * Log a message at the ERROR level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void error(String format, Object arg);

    /**
     * Log a message at the ERROR level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void error(String format, Supplier<Object> supplier);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void error(String format, Object arg1, Object arg2);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     */
    void error(String format, Object arg1, Object arg2, Object arg3);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     */
    void error(String format, Object arg1, Object arg2, Object arg3, Object arg4);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     */
    void error(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     */
    void error(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     */
    void error(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     */
    void error(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     */
    void error(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8,
               Object arg9);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     * @param arg10  the tenth argument
     */
    void error(String format,
               Object arg1,
               Object arg2,
               Object arg3,
               Object arg4,
               Object arg5,
               Object arg6,
               Object arg7,
               Object arg8,
               Object arg9,
               Object arg10);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     * @param supplier9 the ninth argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8,
               Supplier<Object> supplier9);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format     the format string
     * @param supplier1  the first argument supplier
     * @param supplier2  the second argument supplier
     * @param supplier3  the third argument supplier
     * @param supplier4  the fourth argument supplier
     * @param supplier5  the fifth argument supplier
     * @param supplier6  the sixth argument supplier
     * @param supplier7  the seventh argument supplier
     * @param supplier8  the eighth argument supplier
     * @param supplier9  the ninth argument supplier
     * @param supplier10 the tenth argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2,
               Supplier<Object> supplier3,
               Supplier<Object> supplier4,
               Supplier<Object> supplier5,
               Supplier<Object> supplier6,
               Supplier<Object> supplier7,
               Supplier<Object> supplier8,
               Supplier<Object> supplier9,
               Supplier<Object> supplier10);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void error(String format, Object... arguments);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void error(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the ERROR level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void error(String msg, Throwable t);

    /**
     * Log an exception (throwable) at the ERROR level.
     *
     * @param t the exception (throwable) to log
     */
    void error(Throwable t);

    /**
     * Is the logger instance enabled for the specified {@code level}?
     *
     * @param level the log level
     * @return {@code true} if this Logger is enabled for the specified {@code level}, {@code false}
     * otherwise.
     */
    boolean isEnabled(LogLevel level);

    /**
     * Log a message at the specified {@code level} level.
     *
     * @param level the log level
     * @param msg   the message string to be logged
     */
    void log(LogLevel level, String msg);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg    the argument
     */
    void log(LogLevel level, String format, Object arg);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level    the log level
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void log(LogLevel level, String format, Supplier<Object> supplier);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void log(LogLevel level, String format, Object arg1, Object arg2);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     */
    void log(LogLevel level, String format, Object arg1, Object arg2, Object arg3);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     */
    void log(LogLevel level, String format, Object arg1, Object arg2, Object arg3, Object arg4);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     */
    void log(LogLevel level,
             String format,
             Object arg1,
             Object arg2,
             Object arg3,
             Object arg4,
             Object arg5);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     */
    void log(LogLevel level,
             String format,
             Object arg1,
             Object arg2,
             Object arg3,
             Object arg4,
             Object arg5,
             Object arg6);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     */
    void log(LogLevel level,
             String format,
             Object arg1,
             Object arg2,
             Object arg3,
             Object arg4,
             Object arg5,
             Object arg6,
             Object arg7);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     */
    void log(LogLevel level,
             String format,
             Object arg1,
             Object arg2,
             Object arg3,
             Object arg4,
             Object arg5,
             Object arg6,
             Object arg7,
             Object arg8);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     */
    void log(LogLevel level,
             String format,
             Object arg1,
             Object arg2,
             Object arg3,
             Object arg4,
             Object arg5,
             Object arg6,
             Object arg7,
             Object arg8,
             Object arg9);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level  the log level
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     * @param arg3   the third argument
     * @param arg4   the fourth argument
     * @param arg5   the fifth argument
     * @param arg6   the sixth argument
     * @param arg7   the seventh argument
     * @param arg8   the eighth argument
     * @param arg9   the ninth argument
     * @param arg10  the tenth argument
     */
    void log(LogLevel level,
             String format,
             Object arg1,
             Object arg2,
             Object arg3,
             Object arg4,
             Object arg5,
             Object arg6,
             Object arg7,
             Object arg8,
             Object arg9,
             Object arg10);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void log(LogLevel level, String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3,
             Supplier<Object> supplier4);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3,
             Supplier<Object> supplier4,
             Supplier<Object> supplier5);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3,
             Supplier<Object> supplier4,
             Supplier<Object> supplier5,
             Supplier<Object> supplier6);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3,
             Supplier<Object> supplier4,
             Supplier<Object> supplier5,
             Supplier<Object> supplier6,
             Supplier<Object> supplier7);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3,
             Supplier<Object> supplier4,
             Supplier<Object> supplier5,
             Supplier<Object> supplier6,
             Supplier<Object> supplier7,
             Supplier<Object> supplier8);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     * @param supplier3 the third argument supplier
     * @param supplier4 the fourth argument supplier
     * @param supplier5 the fifth argument supplier
     * @param supplier6 the sixth argument supplier
     * @param supplier7 the seventh argument supplier
     * @param supplier8 the eighth argument supplier
     * @param supplier9 the ninth argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3,
             Supplier<Object> supplier4,
             Supplier<Object> supplier5,
             Supplier<Object> supplier6,
             Supplier<Object> supplier7,
             Supplier<Object> supplier8,
             Supplier<Object> supplier9);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level      the log level
     * @param format     the format string
     * @param supplier1  the first argument supplier
     * @param supplier2  the second argument supplier
     * @param supplier3  the third argument supplier
     * @param supplier4  the fourth argument supplier
     * @param supplier5  the fifth argument supplier
     * @param supplier6  the sixth argument supplier
     * @param supplier7  the seventh argument supplier
     * @param supplier8  the eighth argument supplier
     * @param supplier9  the ninth argument supplier
     * @param supplier10 the tenth argument supplier
     */
    void log(LogLevel level,
             String format,
             Supplier<Object> supplier1,
             Supplier<Object> supplier2,
             Supplier<Object> supplier3,
             Supplier<Object> supplier4,
             Supplier<Object> supplier5,
             Supplier<Object> supplier6,
             Supplier<Object> supplier7,
             Supplier<Object> supplier8,
             Supplier<Object> supplier9,
             Supplier<Object> supplier10);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * arguments.
     *
     * @param level     the log level
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void log(LogLevel level, String format, Object... arguments);

    /**
     * Log a message at the specified {@code level} level according to the specified format and
     * argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the specified {@code level}
     * level.</p>
     *
     * @param level     the log level
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void log(LogLevel level, String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the specified {@code level} level with an accompanying
     * message.
     *
     * @param level the log level
     * @param msg   the message accompanying the exception
     * @param t     the exception (throwable) to log
     */
    void log(LogLevel level, String msg, Throwable t);

    /**
     * Log an exception (throwable) at the specified {@code level} level.
     *
     * @param level the log level
     * @param t     the exception (throwable) to log
     */
    void log(LogLevel level, Throwable t);
}
