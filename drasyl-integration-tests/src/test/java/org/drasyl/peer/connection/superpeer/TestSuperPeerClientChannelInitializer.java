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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.Message;

import java.util.concurrent.CompletableFuture;

import static io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE;
import static io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.handler.PingPongHandler.PING_PONG_HANDLER;
import static org.drasyl.peer.connection.superpeer.SuperPeerClientConnectionHandler.SUPER_PEER_CLIENT_CONNECTION_HANDLER;

public class TestSuperPeerClientChannelInitializer extends DefaultSuperPeerClientChannelInitializer {
    private final PublishSubject<Message> sentMessages;
    private final PublishSubject<Message> receivedMessages;
    private final boolean doPingPong;
    private final boolean doJoin;

    private final CompletableFuture<Void> websocketHandshake;

    public TestSuperPeerClientChannelInitializer(SuperPeerClientEnvironment environment, boolean doPingPong, boolean doJoin) {
        super(environment);
        this.doPingPong = doPingPong;
        this.doJoin = doJoin;
        sentMessages = PublishSubject.create();
        receivedMessages = PublishSubject.create();
        websocketHandshake = new CompletableFuture<>();
    }

    public PublishSubject<Message> sentMessages() {
        return sentMessages;
    }

    public PublishSubject<Message> receivedMessages() {
        return receivedMessages;
    }

    @Override
    protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
        super.afterPojoMarshalStage(pipeline);
        pipeline.addLast(new SimpleChannelDuplexHandler<Message, Message>(false, false, false) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx,
                                        Message msg) {
                receivedMessages.onNext(msg);
                ctx.fireChannelRead(msg);
            }

            @Override
            protected void channelWrite0(ChannelHandlerContext ctx,
                                         Message msg,
                                         ChannelPromise promise) {
                sentMessages.onNext(msg);
                ctx.write(msg, promise);
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                sentMessages.onComplete();
                receivedMessages.onComplete();
                super.channelUnregistered(ctx);
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                super.userEventTriggered(ctx, evt);

                if (evt instanceof WebSocketClientProtocolHandler.ClientHandshakeStateEvent) {
                    WebSocketClientProtocolHandler.ClientHandshakeStateEvent e = (WebSocketClientProtocolHandler.ClientHandshakeStateEvent) evt;
                    if (e == HANDSHAKE_COMPLETE) {
                        websocketHandshake.complete(null);
                    }
                    else if (e == HANDSHAKE_TIMEOUT) {
                        websocketHandshake.completeExceptionally(new Exception("WebSocket Handshake Timeout"));
                    }
                }
            }
        });
    }

    @Override
    protected void idleStage(ChannelPipeline pipeline) {
        super.idleStage(pipeline);

        if (!doPingPong) {
            pipeline.remove(PING_PONG_HANDLER);
        }
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        super.customStage(pipeline);

        if (!doJoin) {
            pipeline.remove(DRASYL_HANDSHAKE_AFTER_WEBSOCKET_HANDSHAKE);
        }
    }

    public CompletableFuture<Void> websocketHandshake() {
        return websocketHandshake;
    }
}
