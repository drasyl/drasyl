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
 * This handler denies any (de)serialization and can be used to disable existing serializers.
 */
public class DisabledSerializer implements Serializer {
    @Override
    public byte[] toByteArray(final Object o) throws IOException {
        throw new IOException("Serialization of type `" + o.getClass().getName() + "` has been disabled");
    }

    @Override
    public <T> T fromByteArray(final byte[] bytes, final Class<T> type) throws IOException {
        throw new IOException("Deserialization of type `" + type.getName() + "` has been disabled");
    }
}
