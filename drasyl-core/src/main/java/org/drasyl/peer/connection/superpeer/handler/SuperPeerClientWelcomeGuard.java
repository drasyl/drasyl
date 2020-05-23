/*
 * Copyright (c) 2020.
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
package org.drasyl.peer.connection.superpeer.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.*;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;

/**
 * Acts as a guard for in- and outbound connections. A channel is only created, when {@link
 * WelcomeMessage} was received. Outgoing messages are dropped unless a {@link WelcomeMessage} was
 * received. Every other incoming message is also dropped unless a {@link WelcomeMessage} was
 * received.
 */
public class SuperPeerClientWelcomeGuard extends SimpleChannelDuplexHandler<Message<?>, Message<?>> {
    public static final String WELCOME_GUARD = "superPeerClientWelcomeGuard";
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientWelcomeGuard.class);
    private final CompressedPublicKey expectedPublicKey;
    private final CompressedPublicKey ownPublicKey;
    private final Duration timeout;
    private ScheduledFuture<?> timeoutFuture;
    private ChannelPromise handshakeFuture;

    public SuperPeerClientWelcomeGuard(String expectedPublicKey,
                                       CompressedPublicKey ownPublicKey,
                                       Duration timeout) {
        CompressedPublicKey expectedPublicKey1;
        if (expectedPublicKey == null || expectedPublicKey.equals("")) {
            expectedPublicKey1 = null;
        }
        else {
            try {
                expectedPublicKey1 = CompressedPublicKey.of(expectedPublicKey);
            }
            catch (CryptoException e) {
                LOG.error("", e);
                expectedPublicKey1 = null;
            }
        }
        this.expectedPublicKey = expectedPublicKey1;
        this.ownPublicKey = ownPublicKey;
        this.timeout = timeout;
    }

    public ChannelPromise handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        // schedule connection close if handshake did not take place within timeout
        timeoutFuture = ctx.executor().schedule(() -> {
            if (!timeoutFuture.isCancelled()) {
                handshakeFuture.setFailure(new Exception("Timeout"));
                ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE)).addListener(ChannelFutureListener.CLOSE);
                LOG.debug("[{}]: Handshake did not take place successfully in {}ms. "
                        + "Connection is closed.", ctx.channel().id().asShortText(), timeout.toMillis());
            }
        }, timeout.toMillis(), MILLISECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message<?> msg) {
        if (msg instanceof WelcomeMessage) {
            // do handshake
            WelcomeMessage welcomeMessage = (WelcomeMessage) msg;
            CompressedPublicKey superPeerPublicKey = welcomeMessage.getPublicKey();
            if (expectedPublicKey != null && !superPeerPublicKey.equals(expectedPublicKey)) {
                LOG.warn("Super Peer has sent an unexpected public key '{}'. This could indicate a configuration error or man-in-the-middle attack. Close connection.", superPeerPublicKey);
                ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_SUPER_PEER_WRONG_PUBLIC_KEY)).addListener(ChannelFutureListener.CLOSE);
            }
            else if (superPeerPublicKey.equals(ownPublicKey)) {
                LOG.warn("Super Peer has sent same public key. You can't use yourself as a Super Peer. This would mean an endless loop. This could indicate a configuration error. Close connection.");
                ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_SUPER_PEER_SAME_PUBLIC_KEY)).addListener(ChannelFutureListener.CLOSE);
            }
            else {
                timeoutFuture.cancel(true);
                handshakeFuture.setSuccess();
                ctx.fireChannelRead(msg);
                ctx.pipeline().remove(WELCOME_GUARD);
            }
        }
        else {
            // reject all non-welcome messages if handshake is not done
            ctx.writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, msg.getId()));
            ReferenceCountUtil.release(msg);
            LOG.debug("[{}] Server is not authenticated. Inbound message was dropped: '{}'", ctx, msg);
        }
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 Message<?> msg, ChannelPromise promise) {
        if (msg instanceof JoinMessage) {
            ctx.write(msg, promise);
        }
        else {
            ReferenceCountUtil.release(msg);
            // is visible to the listening futures
            throw new IllegalStateException("Server has not yet responded with a " + WelcomeMessage.class.getSimpleName() + ". Outbound message was dropped: '" + msg + "'");
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(true);
        }
        ctx.close(promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // close connection if an error occurred before handshake
        ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_INITIALIZATION)).addListener(ChannelFutureListener.CLOSE);
    }
}
