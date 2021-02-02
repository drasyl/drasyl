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

import java.io.IOException;

/**
 * This Serializer (de)serializes {@link Byte} objects.
 */
public class ByteSerializer extends BoundedSerializer<Byte> {
    @Override
    protected byte[] matchedToByArray(final Byte o) throws IOException {
        return new byte[]{ o };
    }

    @Override
    protected Byte matchedFromByteArray(final byte[] bytes,
                                        final Class<Byte> type) throws IOException {
        if (bytes.length == 1) {
            return bytes[0];
        }
        else {
            throw new IOException("bytes must have a length of 1");
        }
    }
}
