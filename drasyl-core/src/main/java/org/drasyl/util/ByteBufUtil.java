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
