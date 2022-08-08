/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.rmi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.handler.rmi.message.RmiCancel;
import org.drasyl.handler.rmi.message.RmiError;
import org.drasyl.handler.rmi.message.RmiMessage;
import org.drasyl.handler.rmi.message.RmiRequest;
import org.drasyl.handler.rmi.message.RmiResponse;

import java.net.SocketAddress;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Encodes {@link RmiMessage} messages to {@link ByteBuf}s and vice versa.
 */
@Sharable
public class RmiCodec extends MessageToMessageCodec<AddressedEnvelope<ByteBuf, SocketAddress>, AddressedEnvelope<RmiMessage, SocketAddress>> {
    public static final int MAGIC_NUMBER_REQUEST = -760063585;
    public static final int MAGIC_NUMBER_RESPONSE = -760063584;
    public static final int MAGIC_NUMBER_ERROR = -760063583;
    public static final int MAGIC_NUMBER_CANCEL = -760063582;
    // magic number: 4 bytes
    // id: UUID 16 bytes
    public static final int MIN_MESSAGE_LENGTH = 20;

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptOutboundMessage(Object msg) {
        return msg instanceof AddressedEnvelope && ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof RmiMessage;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedEnvelope<RmiMessage, SocketAddress> msg,
                          final List<Object> out) throws Exception {
        if (msg.content() instanceof RmiRequest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_REQUEST);
            // id
            buf.writeLong(((RmiRequest) msg.content()).getId().getMostSignificantBits());
            buf.writeLong(((RmiRequest) msg.content()).getId().getLeastSignificantBits());
            // name
            buf.writeInt(((RmiRequest) msg.content()).getName());
            // method
            buf.writeInt(((RmiRequest) msg.content()).getMethod());
            // arguments
            buf.writeBytes(((RmiRequest) msg.content()).getArguments());

            out.add(new DefaultAddressedEnvelope<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof RmiResponse) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_RESPONSE);
            // id
            buf.writeLong(((RmiResponse) msg.content()).getId().getMostSignificantBits());
            buf.writeLong(((RmiResponse) msg.content()).getId().getLeastSignificantBits());
            // result
            buf.writeBytes(((RmiResponse) msg.content()).getResult());

            out.add(new DefaultAddressedEnvelope<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof RmiError) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_ERROR);
            // id
            buf.writeLong(((RmiError) msg.content()).getId().getMostSignificantBits());
            buf.writeLong(((RmiError) msg.content()).getId().getLeastSignificantBits());
            // message
            buf.writeCharSequence(((RmiError) msg.content()).getMessage(), UTF_8);

            out.add(new DefaultAddressedEnvelope<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof RmiCancel) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_CANCEL);
            // id
            buf.writeLong(((RmiCancel) msg.content()).getId().getMostSignificantBits());
            buf.writeLong(((RmiCancel) msg.content()).getId().getLeastSignificantBits());

            out.add(new DefaultAddressedEnvelope<>(buf, msg.recipient(), msg.sender()));
        }
        else {
            throw new EncoderException("Unknown RmiMessage type: " + StringUtil.simpleClassName(msg.content()));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msg instanceof AddressedEnvelope && ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedEnvelope<ByteBuf, SocketAddress> msg,
                          final List<Object> out) throws Exception {
        if (msg.content().readableBytes() >= MIN_MESSAGE_LENGTH) {
            msg.content().markReaderIndex();
            final int magicNumber = msg.content().readInt();
            switch (magicNumber) {
                case MAGIC_NUMBER_REQUEST: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());
                    // name
                    final int name = msg.content().readInt();
                    // method
                    final int method = msg.content().readInt();
                    // arguments
                    final ByteBuf parameters = msg.content().readRetainedSlice(msg.content().readableBytes());

                    out.add(new DefaultAddressedEnvelope<>(RmiRequest.of(id, name, method, parameters), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_RESPONSE: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());
                    // result
                    final ByteBuf result = msg.content().readRetainedSlice(msg.content().readableBytes());

                    out.add(new DefaultAddressedEnvelope<>(RmiResponse.of(id, result), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_ERROR: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());
                    // message
                    final String message = msg.content().readCharSequence(msg.content().readableBytes(), UTF_8).toString();

                    out.add(new DefaultAddressedEnvelope<>(RmiError.of(id, message), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_CANCEL: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());

                    out.add(new DefaultAddressedEnvelope<>(RmiCancel.of(id), msg.recipient(), msg.sender()));
                    break;
                }
                default: {
                    // wrong magic number -> pass through message
                    msg.content().resetReaderIndex();
                    out.add(msg.retain());
                    break;
                }
            }
        }
        else {
            // too short -> pass through message
            out.add(msg.retain());
        }
    }
}
