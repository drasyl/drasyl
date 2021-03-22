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
