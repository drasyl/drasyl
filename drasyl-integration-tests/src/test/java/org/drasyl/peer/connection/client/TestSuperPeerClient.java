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
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler;
import org.drasyl.pipeline.DrasylPipeline;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.drasyl.peer.connection.handler.RelayableMessageGuard.HOP_COUNT_GUARD;
import static org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler.DIRECT_CONNECTION_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler.LOOPBACK_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler.SUPER_PEER_SINK_HANDLER;

public class TestSuperPeerClient extends SuperPeerClient {
    private final Identity identity;
    private final Subject<Event> receivedEvents;
    private final PublishSubject<Message> sentMessages;
    private final PublishSubject<Message> receivedMessages;

    public TestSuperPeerClient(final DrasylConfig config,
                               final Identity identity,
                               final EventLoopGroup workerGroup,
                               final boolean doPingPong,
                               final boolean doJoin,
                               final Set<Endpoint> endpoints) {
        this(DrasylConfig.newBuilder(config).superPeerEnabled(true).superPeerEndpoints(endpoints).build(), identity, new PeerChannelGroup(config.getNetworkId(), identity), workerGroup, ReplaySubject.create(), doPingPong, doJoin);
    }

    private TestSuperPeerClient(final DrasylConfig config,
                                final Identity identity,
                                final PeerChannelGroup channelGroup,
                                final EventLoopGroup workerGroup,
                                final Subject<Event> receivedEvents,
                                final boolean doPingPong,
                                final boolean doJoin) {
        this(config, identity, workerGroup, receivedEvents, new PeersManager(receivedEvents::onNext, identity), channelGroup, doPingPong, doJoin);
    }

    private TestSuperPeerClient(final DrasylConfig config,
                                final Identity identity,
                                final EventLoopGroup workerGroup,
                                final Subject<Event> receivedEvents,
                                final PeersManager peersManager,
                                final PeerChannelGroup channelGroup,
                                final boolean doPingPong,
                                final boolean doJoin) {
        super(
                config,
                workerGroup,
                () -> true,
                endpoint -> new Bootstrap()
                        .group(workerGroup)
                        .channel(AbstractClient.getBestSocketChannel())
                        .handler(new TestClientChannelInitializer(new ClientEnvironment(
                                config,
                                identity,
                                endpoint,
                                new TestPipeline(peersManager, channelGroup, receivedEvents::onNext, config, identity),
                                channelGroup,
                                peersManager,
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

    public ProofOfWork getProofOfWork() {
        return getIdentity().getProofOfWork();
    }

    public Identity getIdentity() {
        return identity;
    }

    public CompletableFuture<ResponseMessage<?>> sendRequest(final RequestMessage request) {
        final Observable<ResponseMessage<?>> responses = receivedMessages().filter(m -> m instanceof ResponseMessage<?> && ((ResponseMessage<?>) m).getCorrespondingId().equals(request.getId())).map(m -> (ResponseMessage<?>) m);
        final CompletableFuture<ResponseMessage<?>> future = responses.firstElement().toCompletionStage().toCompletableFuture();
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

    public void send(final Message message) {
        waitUntilHandshakeIsDone();
        final ChannelFuture future = channel.writeAndFlush(message).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }

    public void sendRawBinary(final ByteBuf byteBuf) {
        waitUntilHandshakeIsDone();
        final ChannelFuture future = channel.writeAndFlush(new BinaryWebSocketFrame(byteBuf)).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }

    @Override
    public void open() {
        super.open();

        await().until(() -> channel != null);
        channel.pipeline().addAfter(HOP_COUNT_GUARD, "TestSuperPeerClient", new SimpleChannelDuplexHandler<Message, Message>(false, false, false) {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx,
                                        final Message msg) {
                receivedMessages.onNext(msg);
                ctx.fireChannelRead(msg);
            }

            @Override
            protected void channelWrite0(final ChannelHandlerContext ctx,
                                         final Message msg,
                                         final ChannelPromise promise) {
                sentMessages.onNext(msg);
                ctx.write(msg, promise);
            }

            @Override
            public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
                sentMessages.onComplete();
                receivedMessages.onComplete();
                super.channelUnregistered(ctx);
            }
        });
    }

    public static class TestPipeline extends DrasylPipeline {
        public TestPipeline(final PeersManager peersManager,
                            final PeerChannelGroup channelGroup,
                            final Consumer<Event> eventConsumer,
                            final DrasylConfig config,
                            final Identity identity) {
            super(eventConsumer, config, identity);

            addFirst(SUPER_PEER_SINK_HANDLER, new SuperPeerMessageSinkHandler(channelGroup, peersManager));
            addAfter(SUPER_PEER_SINK_HANDLER, DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, new DirectConnectionMessageSinkHandler(channelGroup));
            addAfter(DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, LOOPBACK_MESSAGE_SINK_HANDLER, new LoopbackMessageSinkHandler(new AtomicBoolean(true), config.getNetworkId(), identity, peersManager, Set.of()));
        }
    }
}