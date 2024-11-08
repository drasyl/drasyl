/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.remote.internet.UnconfirmedAddressResolveHandler.PATH_ID;

/**
 * This handler passes messages from the {@link io.netty.channel.socket.DatagramChannel} to the
 * {@link org.drasyl.channel.DrasylServerChannel}'s context.
 */
@UnstableApi
public class UdpServerToDrasylHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(UdpServerToDrasylHandler.class);
    private final DrasylServerChannel parent;
    private final Set<DrasylAddress> readCompletePending = ConcurrentHashMap.newKeySet();
    private ChannelHandlerContext ctx;
    private boolean parentReadCompletePending;
    private Set<Channel> flushChannels = new HashSet<>();

    public UdpServerToDrasylHandler(final DrasylServerChannel parent) {
        this.parent = requireNonNull(parent);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.trace("{} Read `{}`", ctx.channel(), msg);
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage && parent.localAddress().equals(((ApplicationMessage) ((InetAddressedMessage<?>) msg).content()).getRecipient())) {
            final PeersManager peersManager = parent.config().getPeersManager();

            final ApplicationMessage appMsg = (ApplicationMessage) ((InetAddressedMessage<?>) msg).content();
            peersManager.applicationMessageReceived(appMsg.getSender());

            // UnconfirmedAddressResolveHandler discovery
            peersManager.tryAddChildrenPath(ctx, appMsg.getSender(), PATH_ID, ((InetAddressedMessage<?>) msg).sender());
            peersManager.helloMessageReceived(appMsg.getSender(), PATH_ID); // consider every message as hello. this is fine here

            final DrasylChannel drasylChannel = parent.getChannel(appMsg.getSender());
            if (drasylChannel != null) {
                LOG.trace("{} Pass read to `{}` to `{}`.", ctx.channel(), msg, drasylChannel);
                drasylChannel.queueRead(appMsg.getPayload());
            }
            else {
                readCompletePending.add(appMsg.getSender());
                parent.serve(appMsg.getSender()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture future) throws Exception {
                        final DrasylChannel drasylChannel1 = (DrasylChannel) future.channel();
                        LOG.trace("{} Pass read to `{}` to `{}`.", ctx.channel(), msg, drasylChannel);
                        drasylChannel1.queueRead(appMsg.getPayload());
                    }
                });
            }
        }
        else {
            parentReadCompletePending = true;
            parent.pipeline().fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        if (parentReadCompletePending) {
            parentReadCompletePending = false;
            parent.pipeline().fireChannelReadComplete();
        }
        for (final DrasylChannel drasylChannel : parent.getChannels().values()) {
            if (drasylChannel.isRegistered()) {
                LOG.trace("{} Pass read complete to `{}`.", ctx.channel(), drasylChannel);
                drasylChannel.finishRead();
            }
        }
        for (final DrasylAddress address : readCompletePending) {
            parent.serve(address).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    final DrasylChannel drasylChannel1 = (DrasylChannel) future.channel();
                    LOG.trace("{} Pass read complete to `{}`.", ctx.channel(), drasylChannel1);
                    drasylChannel1.finishRead();
                }
            });
        }
        readCompletePending.clear();
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            final Iterator<Channel> iterator = flushChannels.iterator();
            while (iterator.hasNext()) {
                final Channel channel = iterator.next();
                iterator.remove();
                channel.flush();
            }
        }

        ctx.fireChannelWritabilityChanged();
    }

    public void flushIfBecomeWritable(final Channel channel) {
        if (ctx.channel().eventLoop().inEventLoop()) {
            if (ctx.channel().isWritable()) {
                channel.flush();
            }
            flushChannels.add(channel);
        }
        else {
            ctx.channel().eventLoop().execute(() -> {
                if (ctx.channel().isWritable()) {
                    channel.flush();
                }
                flushChannels.add(channel);
            });
        }

    }
}
