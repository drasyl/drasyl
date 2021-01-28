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
package org.drasyl.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * This class is used for obtaining full generics type information by sub-classing.
 *
 * <p>Adapted from <a href="http://gafter.blogspot.com/2006/12/super-type-tokens.html">http://gafter.blogspot.com/2006/12/super-type-tokens.html</a>
 *
 * @param <T> the type we need the full generic type information from
 */
@SuppressWarnings("java:S2326")
public abstract class TypeReference<T> {
    private final Type type;

    protected TypeReference() {
        final Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof Class) {
            throw new IllegalArgumentException("Missing type parameter.");
        }
        this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    }

    /**
     * Gets the referenced type.
     */
    public Type getType() {
        return this.type;
    }
}
