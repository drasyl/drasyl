/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.handler;

import org.drasyl.all.messages.RelayException;
import org.drasyl.all.messages.Join;
import org.drasyl.all.messages.Message;
import org.drasyl.all.messages.Response;
import org.drasyl.all.models.IPAddress;
import org.drasyl.all.Drasyl;
import org.drasyl.all.actions.ServerAction;
import org.drasyl.all.session.Session;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * This handler mange in-/oncoming messages and pass them to the correct sub-function.
 * It also creates a new {@link Session} object if a {@link Join} has pass the {@link JoinHandler} guard.
 */
public class SessionHandler extends SimpleChannelInboundHandler<ServerAction> {
    private static final Logger LOG = LoggerFactory.getLogger(SessionHandler.class);
    private final Drasyl relay;
    private CompletableFuture<Session> sessionReadyFuture;
    private Session session;
    private IPAddress ip;

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler}
     *
     * @param relay a reference to this relay instance
     */
    public SessionHandler(Drasyl relay) {
        this(relay, null, new CompletableFuture<>());
    }

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler} and completes the given future,
     * when the {@link Session} was created.
     *
     * @param relay                a reference to this relay instance
     * @param ip                   the {@link IPAddress} of the newly created {@link Session}, null to let this class guess the correct IP
     * @param sessionReadyListener the future, that should be completed a session creation
     */
    public SessionHandler(Drasyl relay, IPAddress ip, CompletableFuture<Session> sessionReadyListener) {
        this.sessionReadyFuture = sessionReadyListener;
        this.relay = relay;
        this.ip = ip;
    }

    /*
     * Adds a listener to the channel close event, to remove the session from various lists.
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> {
            if (session != null) {
                relay.getClientBucket().removeInitializedSession(session.getChannelId());
                relay.getClientBucket().removeClient(session.getUID());
                relay.getDeadClientsBucket().add(session);
            }
        });
    }

    /*
     * Reads an incoming message and pass it to the correct sub-function.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ServerAction msg) throws Exception {
        createSession(ctx, msg);

        ctx.executor().submit(() -> {
            if (session != null) {
                msg.onMessage(session, relay);
                session.receiveMessage(msg);
            }
        }).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.debug("Could not process the message {}: ", msg, future.cause());
            }
            ReferenceCountUtil.release(msg);
        });
    }

    /**
     * Creates a session, if not already there.
     *
     * @param ctx channel handler context
     * @param msg probably a {@link Join}
     */
    private void createSession(final ChannelHandlerContext ctx, Message msg) {
        if (msg instanceof Join && session == null) {
            Join jm = (Join) msg;
            Channel myChannel = ctx.channel();

            Session self = relay.getClientBucket().getSession(myChannel.id());

            if (self == null && relay.getClientBucket().getLocalClientSession(jm.getClientUID()) == null) {
                if (ip == null)
                    ip = getRemoteAddr(myChannel);

                session = new Session(ctx.channel(), ip, jm.getClientUID(), relay.getUID(),
                        relay.getConfig().getRelayDefaultFutureTimeout().toMillis(),
                        Optional.ofNullable(jm.getUserAgent()).orElse("U/A"));
                relay.getClientBucket().addInitializedSession(session);
                LOG.debug("{} Session was initialized", session);
                sessionReadyFuture.complete(session);
                LOG.debug("Create new channel {}, for Session {}", ctx.channel().id(), session);
            } else {
                ctx.writeAndFlush(new Response<>(
                        new RelayException(
                                "This client has already an open session with this relay server. Can't open more sockets."),
                        jm.getMessageID()));
                ctx.close();
            }
        }
    }

    /**
     * Returns the {@link IPAddress} of a {@link Channel}.
     *
     * @param channel the channel
     */
    public static IPAddress getRemoteAddr(Channel channel) {
        InetSocketAddress socketAddress = (InetSocketAddress) channel.remoteAddress();
        return new IPAddress(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
    }
}
