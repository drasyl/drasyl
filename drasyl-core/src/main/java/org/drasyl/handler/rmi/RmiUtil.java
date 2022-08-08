/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.rmi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Utility class for remote method invocation-related operations.
 */
public final class RmiUtil {
    public static final ObjectMapper OBJECT_MAPPER = new CBORMapper();

    private RmiUtil() {
        // util class
    }

    static void marshalValue(final Object value, final OutputStream out) throws IOException {
        OBJECT_MAPPER.writeValue(out, value);
    }

    static <T> T unmarshalValue(final Class<T> type,
                                final InputStream in) throws IOException {
        return OBJECT_MAPPER.readValue(in, type);
    }

    static ByteBuf marshalArgs(final Object[] args, final ByteBuf buf) throws IOException {
        try (final OutputStream out = new ByteBufOutputStream(buf)) {
            marshalValue(args, out);
            return buf;
        }
    }

    static Object unmarshalResult(final Class<?> resultType,
                                  final ByteBuf buf) throws IOException {
        try (final InputStream in = new ByteBufInputStream(buf)) {
            return unmarshalValue(resultType, in);
        }
        finally {
            buf.release();
        }
    }

    static ByteBuf marshalResult(final Object result, final ByteBuf buf) throws IOException {
        try (final OutputStream out = new ByteBufOutputStream(buf)) {
            marshalValue(result, out);
            return buf;
        }
    }

    static Object[] unmarshalArgs(final Class<?>[] parameterTypes,
                                  final ByteBuf buf) throws IOException {
        try (final InputStream in = new ByteBufInputStream(buf)) {
            Object[] args = unmarshalValue(Object[].class, in);
            if (args == null) {
                args = new Object[0];
            }
            if (parameterTypes.length != args.length) {
                throw new IOException("Expected " + parameterTypes.length + " arguments, but got " + args.length + " arguments.");
            }

            return args;
        }
        finally {
            buf.release();
        }
    }

    public static int computeMethodHash(final Method m) {
        final Class<?>[] parameterTypes = m.getParameterTypes();
        final Object[] values = new Object[1 + parameterTypes.length + 1];

        // name
        values[0] = m.getName();

        // parameters
        for (int i = 0; i < parameterTypes.length; i++) {
            values[i + 1] = parameterTypes[i].getName();
        }

        // return
        values[values.length - 1] = m.getReturnType().getName();

        return Arrays.hashCode(values);
    }
}
