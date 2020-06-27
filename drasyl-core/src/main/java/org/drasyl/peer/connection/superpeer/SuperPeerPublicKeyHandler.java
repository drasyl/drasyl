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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.IamMessage;
import org.drasyl.peer.connection.message.WhoAreYouMessage;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_WRONG_PUBLIC_KEY;
import static org.drasyl.peer.connection.server.NodeServerChannelGroup.ATTRIBUTE_PUBLIC_KEY;

/**
 * This handler obtains the PublicKey of the super peer so that a join proof can be issued later.
 * The handler issues a UserEvent as soon as the PublicKey is available.
 */
public class SuperPeerPublicKeyHandler extends SimpleChannelInboundHandler<IamMessage> {
    public static final String SUPER_PEER_PUBLIC_KEY = "superPeerPublicKey";
    private final CompressedPublicKey superPeerPublicKey;
    private final Duration timeout;
    private String requestID;
    protected ScheduledFuture<?> timeoutFuture;

    SuperPeerPublicKeyHandler(CompressedPublicKey superPeerPublicKey,
                              Duration timeout,
                              String requestID,
                              ScheduledFuture<?> timeoutFuture) {
        this.superPeerPublicKey = superPeerPublicKey;
        this.timeout = timeout;
        this.requestID = requestID;
        this.timeoutFuture = timeoutFuture;
    }

    public SuperPeerPublicKeyHandler(CompressedPublicKey superPeerPublicKey, Duration timeout) {
        this(superPeerPublicKey, timeout, null, null);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.timeoutFuture = ctx.executor().schedule(() ->
                        ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE_TIMEOUT))
                , timeout.toMillis(), MILLISECONDS);

        // Request identity
        WhoAreYouMessage request = new WhoAreYouMessage();
        requestID = request.getId();
        ctx.writeAndFlush(request);
    }

    private void attachIdentityToChannel(ChannelHandlerContext ctx, CompressedPublicKey identity) {
        // attach identity to channel (this information is required for validation signatures of incoming messages)
        ctx.channel().attr(ATTRIBUTE_PUBLIC_KEY).set(identity);
        // emit event
        ctx.pipeline().fireUserEventTriggered(SuperPeerPublicKeyState.KEY_AVAILABLE);
        // remove this handler from pipeline
        ctx.pipeline().remove(this);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                IamMessage msg) throws Exception {
        if (msg.getCorrespondingId().equals(requestID)) {
            timeoutFuture.cancel(true);
            if (superPeerPublicKey != null && !superPeerPublicKey.equals(msg.getPublicKey())) {
                ctx.writeAndFlush(new ConnectionExceptionMessage(CONNECTION_ERROR_WRONG_PUBLIC_KEY)).addListener(ChannelFutureListener.CLOSE);
            }
            else {
                attachIdentityToChannel(ctx, msg.getPublicKey());
            }
        }
        else {
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
        }
    }

    public enum SuperPeerPublicKeyState {
        KEY_AVAILABLE
    }
}
