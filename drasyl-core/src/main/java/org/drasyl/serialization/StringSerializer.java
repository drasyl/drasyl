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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This Serializer (de)serializes {@link String} objects.
 */
public class StringSerializer extends BoundedSerializer<String> {
    @Override
    protected byte[] matchedToByArray(final String o) {
        return o.getBytes(UTF_8);
    }

    @Override
    protected String matchedFromByteArray(final byte[] bytes,
                                          final Class<String> type) {
        return new String(bytes, UTF_8);
    }
}
