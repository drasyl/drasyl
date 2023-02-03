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
package org.drasyl.handler.pubsub;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.channel.OverlayAddressedMessage;

import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Encodes {@link PubSubMessage}s to {@link ByteBuf}s and vice versa.
 */
@Sharable
public class PubSubCodec extends MessageToMessageCodec<OverlayAddressedMessage<ByteBuf>, OverlayAddressedMessage<PubSubMessage>> {
    static final int MAGIC_NUMBER_PUBLISH = -616_382_829;
    static final int MAGIC_NUMBER_PUBLISHED = -616_382_828;
    static final int MAGIC_NUMBER_SUBSCRIBE = -616_382_827;
    static final int MAGIC_NUMBER_SUBSCRIBED = -616_382_826;
    static final int MAGIC_NUMBER_UNSUBSCRIBE = -616_382_825;
    static final int MAGIC_NUMBER_UNSUBSCRIBED = -616_382_824;
    // magic number: 4 bytes
    // id: UUID 16 bytes
    public static final int MIN_MESSAGE_LENGTH = 20;

    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        return msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubMessage;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<PubSubMessage> msg,
                          final List<Object> out) throws Exception {
        if (msg.content() instanceof PubSubPublish) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_PUBLISH);
            // id
            buf.writeLong(msg.content().getId().getMostSignificantBits());
            buf.writeLong(msg.content().getId().getLeastSignificantBits());
            // topic
            final String topic = ((PubSubPublish) msg.content()).getTopic();
            buf.writeInt(topic.length());
            buf.writeCharSequence(topic, UTF_8);
            // content
            buf.writeBytes(((PubSubPublish) msg.content()).getContent());

            out.add(new OverlayAddressedMessage<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof PubSubPublished) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_PUBLISHED);
            // id
            buf.writeLong(msg.content().getId().getMostSignificantBits());
            buf.writeLong(msg.content().getId().getLeastSignificantBits());

            out.add(new OverlayAddressedMessage<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof PubSubSubscribe) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_SUBSCRIBE);
            // id
            buf.writeLong(msg.content().getId().getMostSignificantBits());
            buf.writeLong(msg.content().getId().getLeastSignificantBits());
            // topic
            buf.writeCharSequence(((PubSubSubscribe) msg.content()).getTopic(), UTF_8);

            out.add(new OverlayAddressedMessage<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof PubSubSubscribed) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_SUBSCRIBED);
            // id
            buf.writeLong(msg.content().getId().getMostSignificantBits());
            buf.writeLong(msg.content().getId().getLeastSignificantBits());

            out.add(new OverlayAddressedMessage<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof PubSubUnsubscribe) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_UNSUBSCRIBE);
            // id
            buf.writeLong(msg.content().getId().getMostSignificantBits());
            buf.writeLong(msg.content().getId().getLeastSignificantBits());
            // topic
            buf.writeCharSequence(((PubSubUnsubscribe) msg.content()).getTopic(), UTF_8);

            out.add(new OverlayAddressedMessage<>(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof PubSubUnsubscribed) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_UNSUBSCRIBED);
            // id
            buf.writeLong(msg.content().getId().getMostSignificantBits());
            buf.writeLong(msg.content().getId().getLeastSignificantBits());

            out.add(new OverlayAddressedMessage<>(buf, msg.recipient(), msg.sender()));
        }
        else {
            throw new EncoderException("Unknown PubSubMessage type: " + StringUtil.simpleClassName(msg.content()));
        }
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof ByteBuf;
    }

    @SuppressWarnings("java:S1151")
    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<ByteBuf> msg,
                          final List<Object> out) throws Exception {
        if (msg.content().readableBytes() >= MIN_MESSAGE_LENGTH) {
            msg.content().markReaderIndex();
            final int magicNumber = msg.content().readInt();
            switch (magicNumber) {
                case MAGIC_NUMBER_PUBLISH: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());
                    // name
                    final String topic = msg.content().readCharSequence(msg.content().readInt(), UTF_8).toString();
                    // content
                    final ByteBuf content = msg.content().retain();

                    out.add(new OverlayAddressedMessage<>(PubSubPublish.of(id, topic, content), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_PUBLISHED: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());

                    out.add(new OverlayAddressedMessage<>(PubSubPublished.of(id), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_SUBSCRIBE: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());
                    // name
                    final String topic = msg.content().readCharSequence(msg.content().readableBytes(), UTF_8).toString();

                    out.add(new OverlayAddressedMessage<>(PubSubSubscribe.of(id, topic), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_SUBSCRIBED: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());

                    out.add(new OverlayAddressedMessage<>(PubSubSubscribed.of(id), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_UNSUBSCRIBE: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());
                    // name
                    final String topic = msg.content().readCharSequence(msg.content().readableBytes(), UTF_8).toString();

                    out.add(new OverlayAddressedMessage<>(PubSubUnsubscribe.of(id, topic), msg.recipient(), msg.sender()));
                    break;
                }
                case MAGIC_NUMBER_UNSUBSCRIBED: {
                    // id
                    final UUID id = new UUID(msg.content().readLong(), msg.content().readLong());

                    out.add(new OverlayAddressedMessage<>(PubSubUnsubscribed.of(id), msg.recipient(), msg.sender()));
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
