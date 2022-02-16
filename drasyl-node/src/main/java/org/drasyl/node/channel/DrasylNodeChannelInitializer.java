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
package org.drasyl.node.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.handler.stream.ChunkedMessageAggregator;
import org.drasyl.handler.stream.LargeByteBufToChunkedMessageEncoder;
import org.drasyl.handler.stream.MessageChunkDecoder;
import org.drasyl.handler.stream.MessageChunkEncoder;
import org.drasyl.handler.stream.MessageChunksBuffer;
import org.drasyl.handler.stream.ReassembledMessageDecoder;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.handler.crypto.ArmHeaderCodec;
import org.drasyl.node.handler.crypto.LongTimeArmHandler;
import org.drasyl.node.handler.crypto.PFSArmHandler;
import org.drasyl.node.handler.serialization.MessageSerializer;
import org.drasyl.node.handler.timeout.IdleChannelCloser;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.Null.NULL;

/**
 * Initialize child {@link DrasylChannel}s used by {@link DrasylNode}.
 */
public class DrasylNodeChannelInitializer extends ChannelInitializer<DrasylChannel> {
    // SortedChunk: 6 bytes
    // PublicHeader: 98 bytes + 4 bytes MagicNumber
    // PrivateHeader: 3 byte + 16 bytes MAC
    public static final int PROTOCOL_OVERHEAD = 127;
    private static final int CHUNK_NO_LENGTH = 2;
    private static final int MAX_CHUNKS = (int) Math.pow(256, CHUNK_NO_LENGTH) - 1;
    private static final MessageChunkEncoder MESSAGE_CHUNK_ENCODER = new MessageChunkEncoder(CHUNK_NO_LENGTH);
    private static final MessageChunkDecoder MESSAGE_CHUNK_DECODER = new MessageChunkDecoder(CHUNK_NO_LENGTH);
    private static final ReassembledMessageDecoder REASSEMBLED_MESSAGE_DECODER = new ReassembledMessageDecoder();
    private static final ArmHeaderCodec ARM_HEADER_CODEC = new ArmHeaderCodec();
    private final DrasylConfig config;
    private final DrasylNode node;

    public DrasylNodeChannelInitializer(final DrasylConfig config,
                                        final DrasylNode node) {
        this.config = requireNonNull(config);
        this.node = requireNonNull(node);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        addToChannelGroup(ch);
        firstStage(ch);
        chunkingStage(ch);
        armStage(ch);
        serializationStage(ch);
        lastStage(ch);
    }

    protected void addToChannelGroup(final DrasylChannel ch) {
        node.channels.add(ch);
    }

    protected void firstStage(final DrasylChannel ch) {
        final int inactivityTimeout = (int) config.getChannelInactivityTimeout().getSeconds();
        if (inactivityTimeout > 0) {
            ch.pipeline().addLast(new IdleChannelCloser(inactivityTimeout));
        }
    }

    /**
     * This stages plits {@link io.netty.buffer.ByteBuf}s that are too big for a single udp
     * datagram.
     */
    protected void chunkingStage(final DrasylChannel ch) {
        // split ByteBufs that are too big for a single udp datagram
        ch.pipeline().addLast(
                MESSAGE_CHUNK_ENCODER,
                new ChunkedWriteHandler(),
                new LargeByteBufToChunkedMessageEncoder(config.getRemoteMessageMtu() - PROTOCOL_OVERHEAD, config.getRemoteMessageMaxContentLength()),
                MESSAGE_CHUNK_DECODER,
                new MessageChunksBuffer(config.getRemoteMessageMaxContentLength(), (int) config.getRemoteMessageComposedMessageTransferTimeout().toMillis(), MAX_CHUNKS),
                new ChunkedMessageAggregator(config.getRemoteMessageMaxContentLength()),
                REASSEMBLED_MESSAGE_DECODER
        );
    }

    /**
     * This stage arms outbound and disarms inbound messages.
     */
    protected void armStage(final DrasylChannel ch) throws CryptoException {
        // arm outbound and disarm inbound messages
        if (config.isRemoteMessageArmApplicationEnabled()) {
            ch.pipeline().addLast(ARM_HEADER_CODEC);
            // PFS is enabled
            if (config.getRemoteMessageArmApplicationAgreementMaxCount() > 0) {
                ch.pipeline().addLast(new PFSArmHandler(
                        Crypto.INSTANCE,
                        config.getRemoteMessageArmApplicationAgreementExpireAfter(),
                        config.getRemoteMessageArmApplicationAgreementRetryInterval(),
                        config.getRemoteMessageArmApplicationAgreementMaxCount(),
                        node.identity(),
                        (IdentityPublicKey) ch.remoteAddress()
                ));
            }
            else {
                ch.pipeline().addLast(new LongTimeArmHandler(
                        Crypto.INSTANCE,
                        config.getRemoteMessageArmApplicationAgreementExpireAfter(),
                        config.getRemoteMessageArmApplicationAgreementMaxCount(),
                        node.identity(),
                        (IdentityPublicKey) ch.remoteAddress()
                ));
            }
        }
    }

    /**
     * This stage serializes {@link java.util.Objects} to {@link io.netty.buffer.ByteBuf} and vice
     * versa.
     */
    protected void serializationStage(final DrasylChannel ch) {
        ch.pipeline().addLast(new MessageSerializer(config));
    }

    /**
     * This stage emits {@link org.drasyl.node.event.Event}s to {@link #node}.
     */
    protected void lastStage(final DrasylChannel ch) {
        ch.pipeline().addLast(new NodeEventHandler(node));
    }

    /**
     * Creates a {@link MessageEvent} for every inbound message and a {@link InboundExceptionEvent}
     * for every exception.
     */
    private static class NodeEventHandler extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(NodeEventHandler.class);
        private final DrasylNode node;

        public NodeEventHandler(final DrasylNode node) {
            this.node = requireNonNull(node);
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
            if (evt instanceof Event) {
                node.onEvent((Event) evt);
            }
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx,
                                Object msg) {
            if (msg == NULL) {
                msg = null;
            }

            final MessageEvent event = MessageEvent.of((IdentityPublicKey) ctx.channel().remoteAddress(), msg);
            node.onEvent(event);
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                                    final Throwable e) {
            if (e instanceof EncoderException) {
                // exception has been propably caused by an outbound message
                LOG.error(e);
            }
            else {
                node.onEvent(InboundExceptionEvent.of(e));
            }
        }
    }
}
