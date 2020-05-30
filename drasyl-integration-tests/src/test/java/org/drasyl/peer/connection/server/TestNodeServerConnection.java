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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.AbstractNettyConnection;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.mock;

public class TestNodeServerConnection extends AbstractNettyConnection {
    private final static Logger LOG = LoggerFactory.getLogger(TestNodeServerConnection.class);
    protected final Subject<Message> receivedMessages;
    private final CompressedKeyPair keyPair;

    public TestNodeServerConnection(Channel channel, URI targetSystem, CompressedKeyPair keyPair) {
        super(channel, targetSystem, keyPair.getIdentity(), "JUnit-Test", mock(ConnectionsManager.class));
        receivedMessages = PublishSubject.create();
        this.keyPair = keyPair;
    }

    public Observable<Message> receivedMessages() {
        return receivedMessages;
    }

    public void sendRawString(final String string) {
        if (string != null && !isClosed.get() && channel.isOpen()) {
            channel.writeAndFlush(new TextWebSocketFrame(string));
        }
        else {
            LOG.info("[{} Can't send message {}", TestNodeServerConnection.this, string);
        }
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    /**
     * Creates a new session to the given server with a random identity
     */
    public static TestNodeServerConnection clientSession(NodeServer server) throws ExecutionException, InterruptedException, CryptoException {
        URI serverEntryPoint = URI.create("ws://" + server.getConfig().getServerBindHost() + ":" + server.getPort());
        return clientSession(serverEntryPoint,
                CompressedKeyPair.of(Crypto.generateKeys()), true, server.workerGroup);
    }

    /**
     * Creates a new session.
     */
    public static TestNodeServerConnection clientSession(URI targetSystem,
                                                         CompressedKeyPair keyPair,
                                                         boolean pingPong,
                                                         EventLoopGroup eventLoopGroup) throws InterruptedException,
            ExecutionException {
        CompletableFuture<TestNodeServerConnection> future = new CompletableFuture<>();

        if (eventLoopGroup == null) {
            eventLoopGroup = new NioEventLoopGroup();
        }

        OutboundConnectionFactory factory = new OutboundConnectionFactory(targetSystem, eventLoopGroup)
                .handler(new SimpleChannelInboundHandler<Message>() {
                    TestNodeServerConnection session;

                    @Override
                    public void handlerAdded(final ChannelHandlerContext ctx) {
                        session = new TestNodeServerConnection(ctx.channel(), targetSystem, keyPair);
                        future.complete(session);
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx,
                                                Message msg) throws Exception {
                        session.receiveMessage(msg);
                    }
                })
                .ssl(true)
                .idleTimeout(Duration.ZERO)
                .idleRetries(Short.MAX_VALUE)
                .pingPong(pingPong);

        factory.build();
        factory.getChannelReadyFuture().get();
        return future.get();
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
     * Creates a new session to the given server.
     */
    public static TestNodeServerConnection clientSession(NodeServer server,
                                                         CompressedKeyPair keyPair) throws ExecutionException, InterruptedException {
        URI serverEntryPoint = URI.create("ws://" + server.getConfig().getServerBindHost() + ":" + server.getPort());
        return clientSession(serverEntryPoint, keyPair, true, server.workerGroup);
    }

    /**
     * Creates a new session with the given sessionUID and joins the given server.
     */
    public static TestNodeServerConnection clientSessionAfterJoin(NodeServer server) throws ExecutionException,
            InterruptedException, CryptoException {
        TestNodeServerConnection session = clientSession(server, true);
        session.sendRequest(new JoinMessage(session.getPublicKey(), Set.of())).blockingGet();

        return session;
    }

    /**
     * Creates a new session to the given server.
     */
    public static TestNodeServerConnection clientSession(NodeServer server,
                                                         boolean pingPong) throws ExecutionException,
            InterruptedException, CryptoException {
        URI serverEntryPoint = URI.create("ws://" + server.getConfig().getServerBindHost() + ":" + server.getPort());
        return clientSession(serverEntryPoint,
                CompressedKeyPair.of(Crypto.generateKeys()), pingPong, server.workerGroup);
    }

    public CompressedPublicKey getPublicKey() {
        return keyPair.getPublicKey();
    }

    /**
     * Creates a new session.
     */
    public static TestNodeServerConnection clientSession(URI targetSystem,
                                                         CompressedKeyPair keyPair,
                                                         boolean pingPong) throws ExecutionException, InterruptedException {
        return clientSession(targetSystem, keyPair, pingPong, null);
    }
}
