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
package org.drasyl.node.handler.serialization;

import com.google.protobuf.Message;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This Serializer (de)serializes {@link Message} objects.
 */
public class ProtobufSerializer extends BoundedSerializer<Message> {
    private static final Map<Class<?>, Optional<Method>> typeMethods = new HashMap<>();

    @Override
    byte[] matchedToByArray(final Message o) {
        return o.toByteArray();
    }

    @SuppressWarnings("java:S3878")
    @Override
    Message matchedFromByteArray(final byte[] bytes,
                                 final Class<Message> type) throws IOException {
        final Optional<Method> method = getParseFromMethod(type);

        if (method.isPresent()) {
            try {
                return (Message) method.get().invoke(null, new Object[]{ bytes });
            }
            catch (final IllegalAccessException | InvocationTargetException e) {
                throw new IOException(e);
            }
        }
        else {
            throw new IOException("parseFrom method for `" + type.getName() + "` not found");
        }
    }

    private static <T> Optional<Method> getParseFromMethod(final Class<T> type) {
        return typeMethods.computeIfAbsent(type, key -> {
            try {
                return Optional.of(key.getDeclaredMethod("parseFrom", byte[].class));
            }
            catch (final NoSuchMethodException e) {
                return Optional.empty();
            }
        });
    }
}
