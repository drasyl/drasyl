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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.superpeer.TestClientChannelInitializer;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.awaitility.Awaitility.await;

public class TestSuperPeerClient extends SuperPeerClient {
    private final Identity identity;
    private final Subject<Event> receivedEvents;

    public TestSuperPeerClient(DrasylConfig config,
                               Identity identity,
                               EventLoopGroup workerGroup,
                               boolean doPingPong,
                               boolean doJoin,
                               Set<URI> endpoints) {
        this(DrasylConfig.newBuilder(config).superPeerEnabled(true).superPeerEndpoints(endpoints).build(), identity, new PeerChannelGroup(), workerGroup, ReplaySubject.create(), doPingPong, doJoin);
    }

    private TestSuperPeerClient(DrasylConfig config,
                                Identity identity,
                                PeerChannelGroup channelGroup,
                                EventLoopGroup workerGroup,
                                Subject<Event> receivedEvents,
                                boolean doPingPong,
                                boolean doJoin) {
        this(config, identity, workerGroup, receivedEvents, new PeersManager(receivedEvents::onNext), channelGroup, BehaviorSubject.createDefault(false), doPingPong, doJoin);
    }

    private TestSuperPeerClient(DrasylConfig config,
                                Identity identity,
                                EventLoopGroup workerGroup,
                                Subject<Event> receivedEvents,
                                PeersManager peersManager,
                                PeerChannelGroup channelGroup,
                                Subject<Boolean> connected,
                                boolean doPingPong,
                                boolean doJoin) {
        super(config, workerGroup, connected, endpoint -> new TestClientChannelInitializer(new ClientEnvironment(config, identity, endpoint, new Messenger((message ->
                completedFuture(null)), peersManager, channelGroup), channelGroup, peersManager, connected, receivedEvents::onNext, true,
                config.getSuperPeerPublicKey(), config.getSuperPeerIdleRetries(), config.getSuperPeerIdleTimeout(), config.getSuperPeerHandshakeTimeout(), publicKey -> {
        }), doPingPong, doJoin), () -> true);
        this.identity = identity;
        this.receivedEvents = receivedEvents;
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
        await().until(() -> channel != null);
        return ((TestClientChannelInitializer) channelInitializer).sentMessages();
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
        await().until(() -> channel != null);
        return ((TestClientChannelInitializer) channelInitializer).receivedMessages();
    }

    public void send(Message message) {
        await().until(() -> channelInitializer != null);
        ((TestClientChannelInitializer) channelInitializer).websocketHandshake().join();
        ChannelFuture future = channel.writeAndFlush(message).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }

    public void sendRawBinary(ByteBuf byteBuf) {
        await().until(() -> channelInitializer != null);
        ((TestClientChannelInitializer) channelInitializer).websocketHandshake().join();
        ChannelFuture future = channel.writeAndFlush(new BinaryWebSocketFrame(byteBuf)).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }
}
