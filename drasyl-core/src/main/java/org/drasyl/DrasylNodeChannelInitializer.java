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
package org.drasyl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.MessageSerializer;
import org.drasyl.channel.chunk.ChunkedMessageAggregator;
import org.drasyl.channel.chunk.LargeByteBufToChunkedMessageEncoder;
import org.drasyl.channel.chunk.MessageChunkDecoder;
import org.drasyl.channel.chunk.MessageChunkEncoder;
import org.drasyl.channel.chunk.MessageChunksBuffer;
import org.drasyl.channel.chunk.ReassembledMessageDecoder;
import org.drasyl.event.InboundExceptionEvent;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.Null.NULL;

/**
 * Initialize child {@link DrasylChannel}s used by {@link DrasylNode}.
 */
public class DrasylNodeChannelInitializer extends ChannelInitializer<DrasylChannel> {
    // MessageSerializer: 6 bytes
    // SortedChunk: 6 bytes
    // RemoteMessage: 142 bytes
    public static final int PROTOCOL_OVERHEAD = 154;
    private final DrasylConfig config;
    private final DrasylNode node;

    public DrasylNodeChannelInitializer(final DrasylConfig config,
                                        final DrasylNode node) {
        this.config = requireNonNull(config);
        this.node = requireNonNull(node);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        node.channels.add(ch);

        idleStage(ch);
        serializationStage(ch);
        nodeEventStage(ch);
    }

    /**
     * This stage closes inactive channels (to free up memory).
     */
    protected void idleStage(final DrasylChannel ch) {
        final int inactivityTimeout = (int) config.getChannelInactivityTimeout().getSeconds();
        if (inactivityTimeout > 0) {
            ch.pipeline().addLast(new IdleChannelCloser(inactivityTimeout));
        }
    }

    /**
     * This stage serializes {@link java.util.Objects} to {@link io.netty.buffer.ByteBuf} and vice
     * vera.
     */
    protected void serializationStage(final DrasylChannel ch) {
        // convert Object <-> ByteBuf
        ch.pipeline().addLast(new MessageSerializer(config));

        // split ByteBufs that are too big for a single udp datagram
        ch.pipeline().addLast(
                MessageChunkEncoder.INSTANCE,
                new ChunkedWriteHandler(),
                new LargeByteBufToChunkedMessageEncoder(this.config.getRemoteMessageMtu() - PROTOCOL_OVERHEAD, this.config.getRemoteMessageMaxContentLength()),
                MessageChunkDecoder.INSTANCE,
                new MessageChunksBuffer(this.config.getRemoteMessageMaxContentLength(), (int) this.config.getRemoteMessageComposedMessageTransferTimeout().toMillis()),
                new ChunkedMessageAggregator(this.config.getRemoteMessageMaxContentLength()),
                ReassembledMessageDecoder.INSTANCE
        );
    }

    /**
     * This stage emits {@link org.drasyl.event.Event}s to {@link #node}.
     */
    protected void nodeEventStage(final DrasylChannel ch) {
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

    /**
     * Closes inactive channels (to free up memory).
     */
    private static class IdleChannelCloser extends IdleStateHandler {
        private static final Logger LOG = LoggerFactory.getLogger(IdleChannelCloser.class);

        public IdleChannelCloser(int inactivityTimeout) {
            super(0, 0, inactivityTimeout);
        }

        @Override
        protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
            LOG.debug("Close channel to {} due to inactivity.", ctx.channel()::remoteAddress);
            ctx.close();
        }
    }
}
