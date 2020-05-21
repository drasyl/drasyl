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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

public class SuperPeerClientJoinHandler extends SimpleChannelInboundHandler<Message<?>> {
    public static final String JOIN_HANDLER = "superPeerClientJoinHandler";
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientJoinHandler.class);
    private final Message<?> joinRequest;
    private ChannelPromise joinFuture;

    public SuperPeerClientJoinHandler(CompressedPublicKey publicKey, Set<URI> endpoints) {
        this(new JoinMessage(publicKey, endpoints), null);
    }

    SuperPeerClientJoinHandler(Message<?> joinRequest, ChannelPromise joinFuture) {
        this.joinRequest = joinRequest;
        this.joinFuture = joinFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        joinFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        initiateJoin(ctx);
    }

    private void initiateJoin(ChannelHandlerContext ctx) {
        LOG.trace("[{}]: Send join request to server.", ctx.channel().id().asShortText());
        ctx.writeAndFlush(joinRequest).addListener(future -> {
            if (future.isSuccess()) {
                confirmJoin(ctx);
            }
            else {
                failJoin(ctx, future.cause());
            }
        });
    }

    private void confirmJoin(ChannelHandlerContext ctx) {
        LOG.debug("[{}]: Join confirmation received from super peer. Remove this handler from pipeline.", ctx.channel().id().asShortText());
        joinFuture.setSuccess();
        ctx.pipeline().remove(this);
    }

    private void failJoin(ChannelHandlerContext ctx, Throwable cause) {
        LOG.trace("[{}]: Join failed: {}", ctx.channel().id().asShortText(), cause.getMessage());
        joinFuture.setFailure(cause);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Message msg) {
        ctx.fireChannelRead(msg);
    }

    public ChannelFuture joinFuture() {
        return joinFuture;
    }
}
