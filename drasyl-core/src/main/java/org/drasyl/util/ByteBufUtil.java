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

public class ByteBufUtil {
    private ByteBufUtil() {
        // util class
    }

    /**
     * Appends the given {@code element} at the start of the {@code byteBuf} and moves all readable
     * bytes accordingly.
     *
     * @param byteBuf the byteBuf to insert the element
     * @param element the element to insert
     * @return the composed {@code ByteBuf}
     */
    public static CompositeByteBuf appendFirst(final ByteBuf byteBuf,
                                               final ByteBuf element) {
        return Unpooled.compositeBuffer(2).addComponents(true, element, byteBuf.slice());
    }
}
