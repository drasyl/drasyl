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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * This Serializer (de)serializes {@link Float} objects.
 */
public class FloatSerializer extends BoundedSerializer<Float> {
    @Override
    protected byte[] matchedToByArray(final Float o) {
        return ByteBuffer.allocate(4).putFloat(o).array();
    }

    @Override
    protected Float matchedFromByteArray(final byte[] bytes,
                                         final Class<Float> type) throws IOException {
        try {
            return ByteBuffer.wrap(bytes).getFloat();
        }
        catch (final BufferUnderflowException e) {
            throw new IOException(e);
        }
    }
}
