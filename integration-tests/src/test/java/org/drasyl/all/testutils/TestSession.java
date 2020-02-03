/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.testutils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.drasyl.all.Drasyl;
import org.drasyl.all.connections.OutboundConnectionFactory;
import org.drasyl.all.messages.Join;
import org.drasyl.all.messages.Message;
import org.drasyl.all.messages.Response;
import org.drasyl.all.messages.Welcome;
import org.drasyl.all.models.IPAddress;
import org.drasyl.all.models.SessionChannel;
import org.drasyl.all.models.SessionUID;
import org.drasyl.all.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TestSession extends Session {
    private static final Logger LOG = LoggerFactory.getLogger(TestSession.class);

    public TestSession(Channel channel, IPAddress targetSystem, SessionUID clientUID, SessionUID relayUID, long defaultFutureTimeout) {
        super(channel, targetSystem, clientUID, relayUID, defaultFutureTimeout, "JUnit-Test");
    }

    /**
     * Creates a new session to the given drasyl with a random session UID
     */
    public static TestSession build(Drasyl drasyl) throws ExecutionException, InterruptedException {
        return build(new IPAddress(drasyl.getConfig().getRelayExternalIP(), drasyl.getConfig().getRelayPort()),
                SessionUID.random(),
                drasyl.getUID(), drasyl.getConfig().getRelayDefaultFutureTimeout(), true, drasyl.workerGroup);
    }

    /**
     * Creates a new session to the given drasyl.
     */
    public static TestSession build(Drasyl drasyl, boolean pingPong) throws ExecutionException,
            InterruptedException {
        return build(new IPAddress(drasyl.getConfig().getRelayExternalIP(), drasyl.getConfig().getRelayPort()),
                SessionUID.random(),
                drasyl.getUID(), drasyl.getConfig().getRelayDefaultFutureTimeout(), pingPong, drasyl.workerGroup);
    }

    /**
     * Creates a new session to the given drasyl.
     */
    public static TestSession build(Drasyl drasyl, SessionUID uid) throws ExecutionException, InterruptedException {
        return build(new IPAddress(drasyl.getConfig().getRelayExternalIP(), drasyl.getConfig().getRelayPort()), uid,
                drasyl.getUID(), drasyl.getConfig().getRelayDefaultFutureTimeout(), true, drasyl.workerGroup);
    }

    /**
     * Creates a new session with the given sessionUID and joins the given drasyl.
     */
    public static TestSession build(Drasyl drasyl, SessionUID uid, Set<SessionChannel> sessionChannels) throws ExecutionException,
            InterruptedException {
        TestSession session = build(new IPAddress(drasyl.getConfig().getRelayExternalIP(),
                        drasyl.getConfig().getRelayPort()), uid,
                drasyl.getUID(), drasyl.getConfig().getRelayDefaultFutureTimeout(), true, drasyl.workerGroup);
        session.sendMessageWithResponse(new Join(session.getUID(), sessionChannels), Welcome.class).get();

        return session;
    }

    /**
     * Creates a new session with random sessionUID and joins the given drasyl.
     */
    public static TestSession build(Drasyl drasyl, Set<SessionChannel> sessionChannels) throws ExecutionException,
            InterruptedException {
        return build(drasyl, SessionUID.random(), sessionChannels);
    }

    /**
     * Creates a new session.
     */
    public static TestSession build(IPAddress targetSystem, SessionUID uid, SessionUID myRelayUID,
                                    Duration defaultFutureTimeout, boolean pingPong, EventLoopGroup eventLoopGroup) throws InterruptedException,
            ExecutionException {
        CompletableFuture<TestSession> future = new CompletableFuture<>();

        if (eventLoopGroup == null)
            eventLoopGroup = new NioEventLoopGroup();

        OutboundConnectionFactory factory = new OutboundConnectionFactory(targetSystem, eventLoopGroup)
                .handler(new SimpleChannelInboundHandler<Message>() {
                    TestSession session;

                    @Override
                    public void handlerAdded(final ChannelHandlerContext ctx) {
                        session = new TestSession(ctx.channel(), targetSystem, uid, myRelayUID,
                                defaultFutureTimeout.toMillis());
                        future.complete(session);
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
                        session.receiveMessage(msg);
                    }
                })
                .ssl(true)
                .idleTimeout(Duration.ZERO)
                .idleRetries(Integer.MAX_VALUE)
                .pingPong(pingPong);

        factory.build();
        factory.getChannelReadyFuture().get();
        return future.get();
    }

    /**
     * Creates a new session.
     */
    public static TestSession build(IPAddress targetSystem, SessionUID uid, SessionUID myRelayUID,
                                    Duration defaultFutureTimeout, boolean pingPong) throws InterruptedException,
            ExecutionException {
        return build(targetSystem, uid, myRelayUID, defaultFutureTimeout, pingPong, null);
    }

    /**
     * Sends a message to the remote host.
     *
     * @param message message that should be sent
     */
    @Override
    protected void send(Message message) {
        if (message != null && !isTerminated && myChannel.isOpen()) {
            myChannel.writeAndFlush(message);

            totalSentMessages.incrementAndGet();
            LOG.debug("[{} => {}] {}", uid, myRelayUID, message);
        } else {
            totalFailedMessages.incrementAndGet();
            LOG.info("[{} Can't send message {}", self, message);
        }
    }

    /**
     * Handles incoming messages and notifies listeners.
     *
     * @param message incoming message
     */
    @Override
    public void receiveMessage(Message message) {
        LOG.debug("[{} <= {}] {}", uid, myRelayUID, message);
        if (isTerminated)
            return;

        if (message instanceof Response) {
            Response response = (Response) message;
            setResult(response.getMsgID(), response.getMessage());
        }

        notifyListeners(message);
    }

    public void sendRawString(final String string) {
        if (string != null && !isTerminated && myChannel.isOpen()) {
            myChannel.writeAndFlush(new TextWebSocketFrame(string)).addListener(future -> {
                if (future.isSuccess()) {
                    totalSentMessages.getAndIncrement();
                    LOG.debug("[{} => {}] {}", uid, myRelayUID, string);
                } else {
                    totalFailedMessages.getAndIncrement();
                    LOG.info("{} Can't send message {}:", self, string, future.cause());
                }
            });
        } else {
            totalFailedMessages.getAndIncrement();
            LOG.info("[{} Can't send message {}", self, string);
        }
    }
}
