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
package org.drasyl.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * This Serializer (de)serializes {@link Serializable} objects.
 */
public class JavaSerializer extends BoundedSerializer<Serializable> {
    @Override
    protected byte[] matchedToByArray(final Serializable o) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (final ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
            return bos.toByteArray();
        }
    }

    @Override
    protected Serializable matchedFromByteArray(final byte[] bytes,
                                                final Class<Serializable> type) throws IOException {
        final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        try (final ObjectInputStream in = new ObjectInputStream(bis)) {
            try {
                return (Serializable) in.readObject();
            }
            catch (final ClassNotFoundException e) {
                throw new IOException(e);
            }
        }
    }
}
