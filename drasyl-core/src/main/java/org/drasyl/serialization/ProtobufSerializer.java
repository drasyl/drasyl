/*
 * Copyright (c) 2021.
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
package org.drasyl.serialization;

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
    protected byte[] matchedToByArray(final Message o) {
        return o.toByteArray();
    }

    @Override
    protected Message matchedFromByteArray(final byte[] bytes,
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

    private <T> Optional<Method> getParseFromMethod(final Class<T> type) {
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
