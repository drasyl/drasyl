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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * A serializer based on the <a href="https://github.com/FasterXML/jackson">Jackson Library</a> for
 * converting java objects to and from JSON.
 */
public class JacksonJsonSerializer implements Serializer {
    @Override
    public byte[] toByteArray(final Object o) throws IOException {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            JACKSON_WRITER.writeValue(out, o);
            return out.toByteArray();
        }
    }

    @Override
    public <T> T fromByteArray(final byte[] bytes, final Class<T> type) throws IOException {
        try (final ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return JACKSON_READER.readValue(in, type);
        }
    }
}
