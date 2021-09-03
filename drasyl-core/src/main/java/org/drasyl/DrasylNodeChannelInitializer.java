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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.MessageSerializer;
import org.drasyl.event.InboundExceptionEvent;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.Null.NULL;

/**
 * Initialize child {@link Channel}s used by {@link DrasylNode}.
 */
public class DrasylNodeChannelInitializer extends ChannelInitializer<DrasylChannel> {
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

        // emit MessageEvents for every inbound message
        ch.pipeline().addFirst(new MessageEventHandler(node));

        // convert Object <-> ByteBuf
        ch.pipeline().addFirst(new MessageSerializer(config));

        // close inactive channels (to free up resources)
        final int inactivityTimeout = (int) config.getChannelInactivityTimeout().getSeconds();
        if (inactivityTimeout > 0) {
            ch.pipeline().addFirst(new IdleChannelCloser());
            ch.pipeline().addFirst(new IdleStateHandler(0, 0, inactivityTimeout));
        }
    }

    private static class MessageEventHandler extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(MessageEventHandler.class);
        private final DrasylNode node;

        public MessageEventHandler(final DrasylNode node) {
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
                LOG.error(e);
            }
            else {
                node.onEvent(InboundExceptionEvent.of(e));
            }
        }
    }

    private static class IdleChannelCloser extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(IdleChannelCloser.class);

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                final IdleStateEvent e = (IdleStateEvent) evt;
                if (e.state() == IdleState.ALL_IDLE) {
                    LOG.debug("Close channel to {} due to inactivity.", ctx.channel()::remoteAddress);
                    ctx.close();
                }
            }
            else {
                super.userEventTriggered(ctx, evt);
            }
        }
    }
}
