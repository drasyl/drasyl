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
package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.superpeer.TestClientChannelInitializer;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.awaitility.Awaitility.await;
import static org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler.CHUNK_HANDLER;

public class TestSuperPeerClient extends SuperPeerClient {
    private final Identity identity;
    private final Subject<Event> receivedEvents;
    private final PublishSubject<Message> sentMessages;
    private final PublishSubject<Message> receivedMessages;

    public TestSuperPeerClient(DrasylConfig config,
                               Identity identity,
                               EventLoopGroup workerGroup,
                               boolean doPingPong,
                               boolean doJoin,
                               Set<Endpoint> endpoints) {
        this(DrasylConfig.newBuilder(config).superPeerEnabled(true).superPeerEndpoints(endpoints).build(), identity, new PeerChannelGroup(), workerGroup, ReplaySubject.create(), doPingPong, doJoin);
    }

    private TestSuperPeerClient(DrasylConfig config,
                                Identity identity,
                                PeerChannelGroup channelGroup,
                                EventLoopGroup workerGroup,
                                Subject<Event> receivedEvents,
                                boolean doPingPong,
                                boolean doJoin) {
        this(config, identity, workerGroup, receivedEvents, new PeersManager(receivedEvents::onNext), channelGroup, doPingPong, doJoin);
    }

    private TestSuperPeerClient(DrasylConfig config,
                                Identity identity,
                                EventLoopGroup workerGroup,
                                Subject<Event> receivedEvents,
                                PeersManager peersManager,
                                PeerChannelGroup channelGroup,
                                boolean doPingPong,
                                boolean doJoin) {
        super(
                config,
                workerGroup,
                () -> true,
                endpoint -> new Bootstrap()
                        .group(workerGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new TestClientChannelInitializer(new ClientEnvironment(
                                config,
                                identity,
                                endpoint,
                                new Messenger((message -> completedFuture(null)), peersManager, channelGroup),
                                channelGroup,
                                peersManager,
                                receivedEvents::onNext,
                                true,
                                config.getSuperPeerIdleRetries(),
                                config.getSuperPeerIdleTimeout(),
                                config.getSuperPeerHandshakeTimeout()
                        ),
                                doPingPong,
                                doJoin
                        ))
                        .remoteAddress(endpoint.getHost(), endpoint.getPort())
        );
        this.identity = identity;
        this.receivedEvents = receivedEvents;
        this.sentMessages = PublishSubject.create();
        this.receivedMessages = PublishSubject.create();
    }

    public Observable<Event> receivedEvents() {
        return receivedEvents;
    }

    public void openAndAwaitOnline() {
        open();
        awaitOnline();
    }

    public void awaitOnline() {
        receivedEvents.filter(e -> e instanceof NodeOnlineEvent).blockingFirst();
    }

    public boolean isClosed() {
        return channel == null || !channel.isOpen();
    }

    public Observable<Message> sentMessages() {
        return sentMessages;
    }

    public CompressedPublicKey getPublicKey() {
        return getIdentity().getPublicKey();
    }

    public Identity getIdentity() {
        return identity;
    }

    public CompletableFuture<ResponseMessage<?>> sendRequest(RequestMessage request) {
        Observable<ResponseMessage<?>> responses = receivedMessages().filter(m -> m instanceof ResponseMessage<?> && ((ResponseMessage<?>) m).getCorrespondingId().equals(request.getId())).map(m -> (ResponseMessage) m);
        CompletableFuture<ResponseMessage<?>> future = responses.firstElement().toCompletionStage().toCompletableFuture();
        send(request);
        return future;
    }

    public Observable<Message> receivedMessages() {
        return receivedMessages;
    }

    /**
     * We want to wait until the websocket handshake is done. After the handshake, netty removes the
     * WebSocketClientProtocolHandshakeHandler from the pipeline
     */
    public void waitUntilHandshakeIsDone() {
        await().until(() -> channel.pipeline().get("io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandshakeHandler") == null);
    }

    public void send(Message message) {
        waitUntilHandshakeIsDone();
        ChannelFuture future = channel.writeAndFlush(message).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }

    public void sendRawBinary(ByteBuf byteBuf) {
        waitUntilHandshakeIsDone();
        ChannelFuture future = channel.writeAndFlush(new BinaryWebSocketFrame(byteBuf)).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }

    @Override
    public void open() {
        super.open();

        await().until(() -> channel != null);
        channel.pipeline().addAfter(CHUNK_HANDLER, "TestSuperPeerClient", new SimpleChannelDuplexHandler<Message, Message>(false, false, false) {
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
        });
    }
}