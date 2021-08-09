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

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.ReferenceCountUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@link SimpleInboundHandler} which decodes from one message to one ore more other message(s).
 * <p>
 * For example here is an implementation which decodes an {@link String} to an {@link Integer}.
 *
 * <pre>
 *     public class StringToIntegerDecoder extends
 *             {@link MessageToMessageDecoder}&lt;{@link String},{@link Address}&gt; {
 *
 *         {@code @Override}
 *         public void decode({@link HandlerContext} ctx, {@link Address} recipient, {@link String} message, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(message.length());
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed
 * through if they are of type {@link ReferenceCounted}. This is needed as the {@link
 * MessageToMessageDecoder} will call {@link ReferenceCounted#release()} on encoded messages.
 */
@SuppressWarnings("java:S118")
public abstract class MessageToMessageDecoder<I, A extends Address> extends SimpleInboundHandler<I, A> {
    @SuppressWarnings({ "java:S112", "java:S2221" })
    @Override
    protected void matchedInbound(final ChannelHandlerContext ctx,
                                  final A sender,
                                  final I msg) throws Exception {
        final List<Object> out = new ArrayList<>();
        try {
            try {
                decode(ctx, sender, msg, out);
            }
            finally {
                ReferenceCountUtil.safeRelease(msg);
            }

            if (out.isEmpty()) {
                throw new DecoderException(this.getClass().getSimpleName() + " must produce at least one message.");
            }

            final int size = out.size();
            if (size == 1) {
                ctx.fireChannelRead(new AddressedMessage<>(out.get(0), (Address) sender));
            }
            else {
                final FutureCombiner combiner = FutureCombiner.getInstance();

                for (final Object o : out) {
                    final CompletableFuture<Void> future1 = new CompletableFuture<>();
                    ctx.fireChannelRead(new AddressedMessage<>(o, (Address) sender));
                    combiner.add(future1);
                }

                combiner.combine(new CompletableFuture<>());
            }
        }
        catch (final DecoderException e) {
            throw e;
        }
        catch (final Exception e) {
            throw new DecoderException(e);
        }
    }

    /**
     * Decode from one message to one or more other. This method will be called for each inbound
     * message that can be handled by this decoder.
     *
     * @param ctx    the {@link HandlerContext} which this {@link MessageToMessageDecoder} belongs
     *               to
     * @param sender the sender of the message
     * @param msg    the message to decode
     * @param out    the {@link List} to which decoded messages should be added
     * @throws Exception is thrown if an error occurs
     */
    @SuppressWarnings("java:S112")
    protected abstract void decode(final ChannelHandlerContext ctx,
                                   final A sender,
                                   final I msg,
                                   final List<Object> out) throws Exception;
}
