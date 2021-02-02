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

import org.drasyl.util.TypeParameterMatcher;

import java.io.IOException;

abstract class BoundedSerializer<B> implements Serializer {
    private final TypeParameterMatcher matcher;

    protected BoundedSerializer() {
        matcher = TypeParameterMatcher.find(this, BoundedSerializer.class, "B");
    }

    @SuppressWarnings("unchecked")
    @Override
    public byte[] toByteArray(final Object o) throws IOException {
        if (matcher.match(o)) {
            return matchedToByArray((B) o);
        }
        else {
            throw new IOException("Object must be of type `" + matcher.getType().getName() + "`");
        }
    }

    protected abstract byte[] matchedToByArray(final B o) throws IOException;

    @SuppressWarnings("unchecked")
    @Override
    public <T> T fromByteArray(final byte[] bytes, final Class<T> type) throws IOException {
        if (matcher.matchClass(type)) {
            return (T) matchedFromByteArray(bytes, (Class<B>) type);
        }
        else {
            throw new IOException("Type must be a subclass of `" + matcher.getType().getName() + "`");
        }
    }

    protected abstract B matchedFromByteArray(final byte[] bytes, Class<B> type) throws IOException;
}
