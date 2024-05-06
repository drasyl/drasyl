/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote.tcp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;
import java.util.Queue;

import static java.util.Objects.requireNonNull;

/**
 * This handler passes all receiving messages to the pipeline and updates {@link #clients} on
 * new/closed connections.
 */
@UnstableApi
public class TcpServerToDrasylHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(TcpServerToDrasylHandler.class);
    private final DrasylServerChannel parent;
    private final Queue<Object> outboundBuffer = PlatformDependent.newMpscQueue();
    private ChannelHandlerContext ctx;
    private boolean readCompletePending;
    private IdentityPublicKey remoteKey;

    TcpServerToDrasylHandler(final DrasylServerChannel parent) {
        this.parent = requireNonNull(parent);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        LOG.debug("New TCP connection from client `{}`.", ctx.channel()::remoteAddress);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        LOG.debug("TCP connection to client `{}` closed.", ctx.channel()::remoteAddress);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        LOG.trace("Read `{}` received via TCP from `{}`.", () -> msg, ctx.channel()::remoteAddress);
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) ((InetAddressedMessage<?>) msg).content();
            if (remoteKey == null) {
                remoteKey = (IdentityPublicKey) remoteMsg.getSender();
                parent.pipeline().get(TcpServer.class).tcpClientChannels.put(remoteKey, (SocketChannel) ctx.channel());
                LOG.trace("Lock in channel `{}` to peer `{}`.", ctx::channel, () -> remoteKey);
            }
            if (!Objects.equals(remoteKey, remoteMsg.getSender())) {
                LOG.trace("Channel `{}` is locked in to peer `{}` but we got message from `{}`. Close.", ctx::channel, () -> remoteKey, remoteMsg::getSender);
                ReferenceCountUtil.release(msg);
                ctx.channel().close();
            }

            if (remoteMsg instanceof ApplicationMessage && parent.localAddress().equals(remoteMsg.getRecipient())) {
                final PeersManager peersManager = parent.config().getPeersManager();

                final ApplicationMessage appMsg = (ApplicationMessage) remoteMsg;
                peersManager.applicationMessageReceived(appMsg.getSender());

                final DrasylChannel drasylChannel = parent.getChannel(appMsg.getSender());
                if (drasylChannel != null) {
                    drasylChannel.queueRead(appMsg.getPayload());
                }
                else {
                    parent.serve(appMsg.getSender()).addListener(future -> {
                        final DrasylChannel drasylChannel1 = (DrasylChannel) future.get();
                        drasylChannel1.queueRead(appMsg.getPayload());
                    });
                }
            }
            else {
                readCompletePending = true;
                parent.pipeline().fireChannelRead(msg);
            }
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.debug("Close TCP connection to `{}` due to an exception: ", ctx.channel()::remoteAddress, () -> cause);
        ctx.close();
    }
}
