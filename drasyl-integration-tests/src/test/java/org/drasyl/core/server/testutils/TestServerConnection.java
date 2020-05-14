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
package org.drasyl.core.server.testutils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.drasyl.core.common.message.*;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.node.ConnectionsManager;
import org.drasyl.core.node.connections.NettyPeerConnection;
import org.drasyl.core.node.connections.OutboundConnectionFactory;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.mock;

public class TestServerConnection extends NettyPeerConnection {
    private final static Logger LOG = LoggerFactory.getLogger(TestServerConnection.class);

    public interface IResponseListener<T> {
        /**
         * Notifies about an event.
         *
         * @param event event data
         */
        void emitEvent(T event);
    }

    protected final List<IResponseListener<Message<?>>> listeners;

    public TestServerConnection(Channel channel, URI targetSystem, Identity clientUID) {
        super(channel, targetSystem, clientUID, "JUnit-Test", mock(ConnectionsManager.class));
        listeners = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Creates a new session to the given server with a random identity
     */
    public static TestServerConnection build(NodeServer server) throws ExecutionException, InterruptedException {
        URI serverEntryPoint = URI.create("ws://" + server.getConfig().getServerBindHost() + ":" + server.getPort());
        return build(serverEntryPoint,
                TestHelper.random(), true, server.workerGroup);
    }

    /**
     * Creates a new session to the given server.
     */
    public static TestServerConnection build(NodeServer server,
                                             boolean pingPong) throws ExecutionException,
            InterruptedException {
        URI serverEntryPoint = URI.create("ws://" + server.getConfig().getServerBindHost() + ":" + server.getPort());
        return build(serverEntryPoint,
                TestHelper.random(), pingPong, server.workerGroup);
    }

    /**
     * Creates a new session to the given server.
     */
    public static TestServerConnection build(NodeServer server,
                                             Identity uid) throws ExecutionException, InterruptedException {
        URI serverEntryPoint = URI.create("ws://" + server.getConfig().getServerBindHost() + ":" + server.getPort());
        return build(serverEntryPoint, uid, true, server.workerGroup);
    }

    /**
     * Creates a new session with the given sessionUID and joins the given server.
     */
    public static TestServerConnection buildAutoJoin(NodeServer server) throws ExecutionException,
            InterruptedException, CryptoException {
        KeyPair keyPair = Crypto.generateKeys();
        CompressedPublicKey publicKey = CompressedPublicKey.of(keyPair.getPublic());
        URI serverEntryPoint = URI.create("ws://" + server.getConfig().getServerBindHost() + ":" + server.getPort());
        TestServerConnection session = build(serverEntryPoint, Identity.of(publicKey), true, server.workerGroup);
        session.send(new JoinMessage(publicKey, Set.of()), WelcomeMessage.class).blockingGet();

        return session;
    }

    /**
     * Creates a new session.
     */
    public static TestServerConnection build(URI targetSystem,
                                             Identity uid,
                                             boolean pingPong,
                                             EventLoopGroup eventLoopGroup) throws InterruptedException,
            ExecutionException {
        CompletableFuture<TestServerConnection> future = new CompletableFuture<>();

        if (eventLoopGroup == null) {
            eventLoopGroup = new NioEventLoopGroup();
        }

        OutboundConnectionFactory factory = new OutboundConnectionFactory(targetSystem, eventLoopGroup)
                .handler(new SimpleChannelInboundHandler<Message>() {
                    TestServerConnection session;

                    @Override
                    public void handlerAdded(final ChannelHandlerContext ctx) {
                        session = new TestServerConnection(ctx.channel(), targetSystem, uid);
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
     * Creates a new session.
     */
    public static TestServerConnection build(URI targetSystem,
                                             Identity uid,
                                             boolean pingPong) throws ExecutionException, InterruptedException {
        return build(targetSystem, uid, pingPong, null);
    }

    public void sendRawString(final String string) {
        if (string != null && !isClosed.get() && myChannel.isOpen()) {
            myChannel.writeAndFlush(new TextWebSocketFrame(string));
        }
        else {
            LOG.info("[{} Can't send message {}", TestServerConnection.this, string);
        }
    }

    /**
     * Handles incoming messages and notifies listeners.
     *
     * @param message incoming message
     */
    public void receiveMessage(Message<?> message) {
        if (isClosed.get()) {
            return;
        }

        if (message instanceof ResponseMessage) {
            ResponseMessage<RequestMessage<?>, Message<?>> response = (ResponseMessage<RequestMessage<?>, Message<?>>) message;
            setResponse(response);
        }

        for (IResponseListener<Message<?>> listener : listeners) {
            listener.emitEvent(message);
        }
    }

    /**
     * Registers a {@link IResponseListener} listener on the session.
     *
     * @param listener Listener to be called at an event
     */
    public void addListener(IResponseListener<Message<?>> listener) {
        listeners.add(listener);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
