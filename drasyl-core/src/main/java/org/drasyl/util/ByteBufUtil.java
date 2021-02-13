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
package org.drasyl.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Utility class for operations on {@link ByteBuf}s.
 */
public final class ByteBufUtil {
    private ByteBufUtil() {
        // util class
    }

    /**
     * Prepends the given {@code elements} at the start of the {@code byteBuf} and moves all
     * readable bytes accordingly.
     * <p>
     * Note: {@link ByteBuf#release()} ownership of all {@link ByteBuf} objects in {@code elements}
     * is transferred to this {@link CompositeByteBuf}.
     *
     * @param byteBuf  the byteBuf to append the elements
     * @param elements the elements to append
     * @return the composed {@code ByteBuf}
     */
    public static CompositeByteBuf prepend(final ByteBuf byteBuf,
                                           final ByteBuf... elements) {
        final ByteBuf[] buffers = new ByteBuf[elements.length + 1];
        System.arraycopy(elements, 0, buffers, 0, elements.length);
        buffers[elements.length] = byteBuf.slice();

        return Unpooled.compositeBuffer(elements.length + 1).addComponents(true, buffers);
    }
}
