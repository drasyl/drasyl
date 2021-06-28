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
package org.drasyl.pipeline.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.ReferenceCountUtil;

import java.util.concurrent.CompletableFuture;

/**
 * {@link SimpleOutboundHandler} which encodes message in a stream-like fashion from one message to
 * an {@link ByteBuf}.
 * <p>
 * For example here is an implementation which decodes an {@link Integer} to a {@link ByteBuf}.
 *
 * <pre>
 *     public class IntegerEncoder extends {@link MessageToByteEncoder}&lt;{@link Integer},{@link Address}&gt; {
 *         {@code @Override}
 *         public void encode({@link HandlerContext} ctx, {@link Address} recipient, {@link Integer} message, {@link ByteBuf} out)
 *                 throws {@link Exception} {
 *             out.writeInt(msg);
 *         }
 *     }
 * </pre>
 */
@SuppressWarnings("java:S118")
public abstract class MessageToByteEncoder<O, A extends Address> extends SimpleOutboundHandler<O, A> {
    private final boolean preferDirect;

    /**
     * @param preferDirect {@code true} if a direct {@link ByteBuf} should be tried to be used as
     *                     target for the encoded messages. If {@code false} is used it will
     *                     allocate a heap {@link ByteBuf}, which is backed by an byte array.
     */
    protected MessageToByteEncoder(final boolean preferDirect) {
        this.preferDirect = preferDirect;
    }

    protected MessageToByteEncoder() {
        this(true);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final CompletableFuture<Void> future) throws Exception {
        try {
            final ByteBuf buf = allocateBuffer(ctx, recipient, msg, preferDirect);
            try {
                encode(ctx, recipient, msg, buf);
            }
            finally {
                ReferenceCountUtil.safeRelease(msg);
            }

            if (buf.isReadable()) {
                ctx.passOutbound(recipient, buf, future);
            }
            else {
                buf.release();
                ctx.passOutbound(recipient, Unpooled.EMPTY_BUFFER, future);
            }
        }
        catch (final EncoderException e) {
            throw e;
        }
        catch (final Exception e) {
            throw new EncoderException(e);
        }
    }

    /**
     * Allocate a {@link ByteBuf} which will be used as argument of {@link #encode(HandlerContext,
     * Address, Object, ByteBuf)}. Sub-classes may override this method to return {@link ByteBuf}
     * with a perfect matching {@code initialCapacity}.
     */
    @SuppressWarnings("unused")
    protected ByteBuf allocateBuffer(final HandlerContext ctx,
                                     final A recipient,
                                     final O msg,
                                     final boolean preferDirect) {
        final PooledByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
        if (preferDirect) {
            return allocator.ioBuffer();
        }
        else {
            return allocator.heapBuffer();
        }
    }

    /**
     * Encode a message into a {@link ByteBuf}. This method will be called for each outbound message
     * that can be handled by this decoder.
     *
     * @param ctx       the {@link HandlerContext} which this {@link MessageToByteEncoder} belongs
     *                  to
     * @param recipient the recipient of the message
     * @param msg       the message to encode
     * @param out       the {@link ByteBuf} into which the encoded message will be written
     * @throws Exception is thrown if an error occurs
     */
    @SuppressWarnings("java:S112")
    protected abstract void encode(final HandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final ByteBuf out) throws Exception;

    /**
     * @return {@code true} if a direct {@link ByteBuf} should be tried to be used as target for the
     * encoded messages. If {@code false} is used it will allocate a heap {@link ByteBuf}, which is
     * backed by an byte array.
     */
    @SuppressWarnings("unused")
    protected boolean isPreferDirect() {
        return preferDirect;
    }
}
