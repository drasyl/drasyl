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
package org.drasyl.peer.connection.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.awaitility.Awaitility.await;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.drasyl.peer.connection.server.NodeServerChannelGroup.ATTRIBUTE_PUBLIC_KEY;

/**
 * A {@link TestNodeServerConnection} object represents a connection to another peer, e.g. local or
 * remote. For this purpose, this object provides a standardized interface so that the actual
 * connection type is abstracted and the same operations are always available.
 */
@SuppressWarnings({ "java:S1452" })
public class TestNodeServerConnection {
    private final static Logger LOG = LoggerFactory.getLogger(TestNodeServerConnection.class);
    private final Channel channel;
    private final Subject<Message> receivedMessages;
    private final ConcurrentHashMap<String, CompletableFuture<ResponseMessage<?>>> futures;
    private final CompressedKeyPair keyPair;
    private Identity identity;
    private AtomicBoolean isClosed;
    private CompletableFuture<Boolean> closedCompletable;

    /**
     * Creates a new connection.
     *
     * @param channel  channel of the connection
     * @param identity the identity of this {@link TestNodeServerConnection}
     */
    public TestNodeServerConnection(Channel channel, Identity identity) {
        this(identity, channel, new AtomicBoolean(false), new CompletableFuture<>());

        this.channel.closeFuture().addListener((ChannelFutureListener) this::onChannelClose);
    }

    protected TestNodeServerConnection(Identity identity,
                                       Channel channel,
                                       AtomicBoolean isClosed,
                                       CompletableFuture<Boolean> closedCompletable) {
        this.identity = identity;
        this.channel = channel;
        this.isClosed = isClosed;
        this.closedCompletable = closedCompletable;
        this.receivedMessages = PublishSubject.create();
        this.futures = new ConcurrentHashMap<>();
        this.keyPair = identity.getKeyPair();
    }

    /**
     * This method is called when the netty channel closes.
     *
     * @param future
     */
    protected void onChannelClose(ChannelFuture future) {
        if (future.isSuccess()) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("[{}]: The channel have been closed successfully.", future.channel().id().asShortText());
            }
            closedCompletable.complete(true);
        }
        else {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("[{}]: The channel could not be closed: ", future.channel().id().asShortText(), future.cause());
            }
        }
    }

    protected Logger getLogger() {
        return TestNodeServerConnection.LOG;
    }

    public Observable<Message> receivedMessages() {
        return receivedMessages;
    }

    public void sendRawBinary(final ByteBuf byteBuf) {
        channel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
    }

    /**
     * Creates a new session to the given server.
     */
    public static TestNodeServerConnection clientSession(DrasylConfig config,
                                                         NodeServer server,
                                                         Identity identity) throws ExecutionException, InterruptedException {
        URI serverEndpoint = config.getSuperPeerEndpoints().iterator().next();
        return TestNodeServerConnection.clientSession(serverEndpoint, identity, true, server.workerGroup, config.getMessageMaxContentLength(), config.getServerSSLEnabled(), config.getComposedMessageTransferTimeout());
    }

    public void send(Message message) {
        requireNonNull(message);
        if (!isClosed.get() && channel.isOpen()) {
            channel.writeAndFlush(message);
        }
        else {
            getLogger().info("[{} Can't send message {}", TestNodeServerConnection.this, message);
        }
    }

    public CompletableFuture<Boolean> isClosed() {
        return closedCompletable;
    }

    /**
     * Sends a message to the peer and returns a {@link Single} object for potential responses to
     * this message.
     *
     * @param message message that should be sent
     * @return a {@link Single} object that can be fulfilled with a {@link Message response} to the
     * message
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<ResponseMessage<?>> sendRequest(RequestMessage message) {
        if (isClosed.get()) {
            return failedFuture(new IllegalStateException("This connection is already prompt to close."));
        }

        CompletableFuture<ResponseMessage<?>> future = new CompletableFuture<>();

        if (futures.putIfAbsent(message.getId(), future) == null) {
            send(message);
        }

        return future;
    }

    public CompressedPublicKey getPublicKey() {
        return keyPair.getPublicKey();
    }

    /**
     * Creates a new session.
     */
    public static TestNodeServerConnection clientSession(URI targetSystem,
                                                         Identity identity,
                                                         boolean pingPong,
                                                         EventLoopGroup eventLoopGroup,
                                                         int maxContentLength,
                                                         boolean ssl,
                                                         Duration transferTimeout) throws InterruptedException,
            ExecutionException {
        CompletableFuture<TestNodeServerConnection> future = new CompletableFuture<>();

        if (eventLoopGroup == null) {
            eventLoopGroup = new NioEventLoopGroup();
        }

        OutboundConnectionFactory factory = new OutboundConnectionFactory(targetSystem, eventLoopGroup, identity)
                .handler(new SimpleChannelInboundHandler<Message>() {
                    TestNodeServerConnection session;

                    @Override
                    public void handlerAdded(final ChannelHandlerContext ctx) {
                        session = new TestNodeServerConnection(ctx.channel(), identity);
                        future.complete(session);
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx,
                                                Message msg) {
                        if (msg instanceof WelcomeMessage) {
                            ctx.channel().attr(ATTRIBUTE_PUBLIC_KEY).set(((WelcomeMessage) msg).getPublicKey());
                        }
                        session.receiveMessage(msg);
                    }
                })
                .ssl(ssl)
                .idleTimeout(Duration.ZERO)
                .idleRetries(Short.MAX_VALUE)
                .pingPong(pingPong)
                .maxContentLength(maxContentLength)
                .transferTimeout(transferTimeout);

        factory.build();
        factory.getChannelReadyFuture().get();
        return future.get();
    }

    /**
     * Creates a new session with the given sessionUID and joins the given server.
     */
    public static TestNodeServerConnection clientSessionAfterJoin(DrasylConfig config,
                                                                  NodeServer server,
                                                                  Identity identity) throws ExecutionException,
            InterruptedException {
        TestNodeServerConnection session = TestNodeServerConnection.clientSession(config, server, identity, true);
        ResponseMessage<?> responseMessage = session.sendRequest(new JoinMessage(session.getIdentity().getProofOfWork(), session.getIdentity().getPublicKey(), Set.of())).get();
        session.send(new StatusMessage(STATUS_OK, responseMessage.getId()));
        await().until(() -> server.getChannelGroup().find(session.getIdentity().getPublicKey()) != null);

        return session;
    }

    /**
     * Handles incoming messages and notifies listeners.
     *
     * @param message incoming message
     */
    public void receiveMessage(Message message) {
        if (isClosed.get()) {
            return;
        }

        receivedMessages.onNext(message);

        if (message instanceof ResponseMessage) {
            ResponseMessage<RequestMessage> response = (ResponseMessage<RequestMessage>) message;
            setResponse(response);
        }
    }

    /**
     * Sets the result of a {@link Single} object from a {@link #sendRequest(RequestMessage)} call.
     *
     * @param response the response
     */
    private void setResponse(ResponseMessage<? extends RequestMessage> response) {
        CompletableFuture<ResponseMessage<?>> future = futures.remove(response.getCorrespondingId());
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * Creates a new session to the given server.
     */
    public static TestNodeServerConnection clientSession(DrasylConfig config,
                                                         NodeServer server,
                                                         Identity identity,
                                                         boolean pingPong) throws ExecutionException,
            InterruptedException {
        URI serverEndpoint = config.getSuperPeerEndpoints().iterator().next();

        return TestNodeServerConnection.clientSession(serverEndpoint,
                identity, pingPong, server.workerGroup, config.getMessageMaxContentLength(), config.getServerSSLEnabled(), config.getComposedMessageTransferTimeout());
    }

    /**
     * Returns the identity of the peer.
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Creates a new session.
     */
    public static TestNodeServerConnection clientSession(URI targetSystem,
                                                         Identity identity,
                                                         boolean pingPong,
                                                         int maxContentLength,
                                                         boolean ssl,
                                                         Duration transferTimeout) throws ExecutionException, InterruptedException {
        return TestNodeServerConnection.clientSession(targetSystem, identity, pingPong, null, maxContentLength, ssl, transferTimeout);
    }
}