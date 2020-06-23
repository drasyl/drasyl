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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.server.NodeServer;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.drasyl.event.EventType.EVENT_NODE_ONLINE;

public class TestSuperPeerClient extends SuperPeerClient {
    private final Subject<Event> receivedEvents;

    public TestSuperPeerClient(DrasylConfig config,
                               NodeServer server,
                               Identity identity,
                               EventLoopGroup workerGroup,
                               boolean pingPong) throws SuperPeerClientException {
        this(DrasylConfig.newBuilder(config).superPeerEnabled(true).superPeerEndpoints(server.getEndpoints()).serverIdleTimeout(Duration.ZERO).build(), identity, workerGroup);
    }

    public TestSuperPeerClient(DrasylConfig config,
                               NodeServer server,
                               Identity identity,
                               EventLoopGroup workerGroup) throws SuperPeerClientException {
        this(DrasylConfig.newBuilder(config).superPeerEnabled(true).superPeerEndpoints(server.getEndpoints()).build(), identity, workerGroup);
    }

    public TestSuperPeerClient(DrasylConfig config,
                               Identity identity,
                               EventLoopGroup workerGroup) throws SuperPeerClientException {
        this(config, () -> identity, workerGroup, ReplaySubject.create());
    }

    private TestSuperPeerClient(DrasylConfig config,
                                Supplier<Identity> identitySupplier,
                                EventLoopGroup workerGroup,
                                Subject<Event> receivedEvents) throws SuperPeerClientException {
        super(config, identitySupplier, new PeersManager(receivedEvents::onNext), new Messenger(), workerGroup, receivedEvents::onNext);
        this.receivedEvents = receivedEvents;
    }

    public Observable<Event> receivedEvents() {
        return receivedEvents;
    }

    public void openAndAwaitOnline() {
        open();
        awaitOnline();
    }

    public CompletableFuture<Boolean> isClosed() {
        return connectionEstablished().map(b -> !b).firstElement().toCompletionStage().toCompletableFuture();
    }

    public void awaitOnline() {
        receivedEvents.map(Event::getType).filter(type -> type == EVENT_NODE_ONLINE).blockingFirst();
    }

    public Observable<Message> receivedMessages() {
        awaitOnline();
        return ((TestSuperPeerClientChannelInitializer) channelInitializer).receivedMessages();
    }

    public Observable<Message> sentMessages() {
        awaitOnline();
        return ((TestSuperPeerClientChannelInitializer) channelInitializer).sentMessages();
    }

    public CompressedPublicKey getPublicKey() {
        return getIdentity().getPublicKey();
    }

    public void send(Message message) {
        ChannelFuture future = clientChannel.writeAndFlush(message).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }

    public CompletableFuture<ResponseMessage<?>> sendRequest(RequestMessage request) {
        Observable<ResponseMessage<?>> responses = receivedMessages().filter(m -> m instanceof ResponseMessage<?> && ((ResponseMessage<?>) m).getCorrespondingId().equals(request.getId())).map(m -> (ResponseMessage) m);
        CompletableFuture<ResponseMessage<?>> future = responses.firstElement().toCompletionStage().toCompletableFuture();
        send(request);
        return future;

    }

    public Identity getIdentity() {
        return super.getIdentity();
    }

    public void sendRawBinary(ByteBuf byteBuf) {
        ChannelFuture future = clientChannel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }
}
