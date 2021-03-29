/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
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
@SuppressWarnings({ "java:S118", "java:S1694", "java:S2326" })
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
