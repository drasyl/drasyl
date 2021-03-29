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

import io.netty.util.ReferenceCounted;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A Codec for on-the-fly encoding/decoding of message.
 * <p>
 * This can be thought of as a combination of {@link MessageToMessageDecoder} and {@link
 * MessageToMessageEncoder}.
 * <p>
 * Here is an example of a {@link MessageToMessageCodec} which just decode from {@link Integer} to
 * {@link Long} and encode from {@link Long} to {@link Integer}.
 *
 * <pre>
 *     public class NumberCodec extends
 *             {@link MessageToMessageCodec}&lt;{@link Integer}, {@link Long}, {@link Address}&gt; {
 *         {@code @Override}
 *         public {@link Long} decode({@link HandlerContext} ctx, {@link Address} recipient, {@link Integer} msg, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(msg.longValue());
 *         }
 *
 *         {@code @Override}
 *         public {@link Integer} encode({@link HandlerContext} ctx, {@link Address} sender, {@link Long} msg, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(msg.intValue());
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed
 * through if they are of type {@link ReferenceCounted}. This is needed as the {@link
 * MessageToMessageCodec} will call {@link ReferenceCounted#release()} on encoded / decoded
 * messages.
 */
@SuppressWarnings({ "java:S110", "java:S118" })
public abstract class MessageToMessageCodec<I, O, A extends Address> extends SimpleDuplexHandler<I, O, A> {
    private final MessageToMessageDecoder<I, A> decoder = new MessageToMessageDecoder<>() {
        @Override
        protected void decode(final HandlerContext ctx,
                              final A sender,
                              final I msg,
                              final List<Object> out) throws Exception {
            MessageToMessageCodec.this.decode(ctx, sender, msg, out);
        }
    };
    private final MessageToMessageEncoder<O, A> encoder = new MessageToMessageEncoder<>() {
        @Override
        protected void encode(final HandlerContext ctx,
                              final A recipient,
                              final O msg,
                              final List<Object> out) throws Exception {
            MessageToMessageCodec.this.encode(ctx, recipient, msg, out);
        }
    };

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final A sender,
                                  final I msg,
                                  final CompletableFuture<Void> future) throws Exception {
        decoder.matchedInbound(ctx, sender, msg, future);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final CompletableFuture<Void> future) throws Exception {
        encoder.matchedOutbound(ctx, recipient, msg, future);
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
    protected abstract void decode(final HandlerContext ctx,
                                   final A sender,
                                   final I msg,
                                   final List<Object> out) throws Exception;

    /**
     * Encode from one message to one or more other. This method will be called for each outbound
     * message that can be handled by this decoder.
     *
     * @param ctx       the {@link HandlerContext} which this {@link MessageToMessageDecoder}
     *                  belongs to
     * @param recipient the recipient of the message
     * @param msg       the message to encode
     * @param out       the {@link List} to which encoded messages should be added
     * @throws Exception is thrown if an error occurs
     */
    @SuppressWarnings("java:S112")
    protected abstract void encode(final HandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final List<Object> out) throws Exception;
}
