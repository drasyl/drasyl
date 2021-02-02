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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This Serializer (de)serializes {@link Character} objects.
 */
public class CharacterSerializer extends BoundedSerializer<Character> {
    @Override
    protected byte[] matchedToByArray(final Character o) {
        return new String(new char[]{ o }).getBytes(UTF_8);
    }

    @Override
    protected Character matchedFromByteArray(final byte[] bytes,
                                             final Class<Character> type) throws IOException {
        if (bytes.length == 1) {
            return (char) bytes[0];
        }
        else {
            throw new IOException("bytes must have a length of 1");
        }
    }
}
