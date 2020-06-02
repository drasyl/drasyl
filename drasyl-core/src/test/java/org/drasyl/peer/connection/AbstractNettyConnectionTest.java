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
package org.drasyl.peer.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.identity.Address;
import org.drasyl.identity.AddressTestHelper;
import org.drasyl.peer.connection.message.*;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractNettyConnectionTest {
    private Channel channel;
    private Address myid;
    private String userAgent;
    private ConnectionExceptionMessage connectionExceptionMessage;
    private RequestMessage message;
    private ChannelFuture channelFuture;
    private AtomicBoolean isClosed;
    private String msgID;
    private CompletableFuture<Boolean> closedCompletable;
    private ResponseMessage<? extends RequestMessage> responseMessage;
    private ConnectionsManager connectionsManager;
    private PeerConnection.CloseReason reason;
    private Consumer<Event> eventConsumer;

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        myid = AddressTestHelper.random();
        userAgent = "";
        connectionExceptionMessage = mock(ConnectionExceptionMessage.class);
        message = mock(ApplicationMessage.class);
        channelFuture = mock(ChannelFuture.class);
        ChannelId channelId = mock(ChannelId.class);
        isClosed = mock(AtomicBoolean.class);
        msgID = Crypto.randomString(16);
        closedCompletable = mock(CompletableFuture.class);
        responseMessage = mock(StatusMessage.class);
        connectionsManager = mock(ConnectionsManager.class);
        reason = PeerConnection.CloseReason.REASON_SHUTTING_DOWN;
        eventConsumer = mock(Consumer.class);

        when(channel.closeFuture()).thenReturn(channelFuture);
        when(channel.id()).thenReturn(channelId);
        when(channel.isOpen()).thenReturn(true);
        when(message.getId()).thenReturn(msgID);
        when(channel.close()).thenReturn(channelFuture);
        when(channel.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.channel()).thenReturn(channel);
    }

    @AfterEach
    void tearDown() {
        validateMockitoUsage();
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerClientConnection.class, NodeServerConnection.class })
    void sendMessageShouldSendMessageToUnderlyingChannel(Class<AbstractNettyConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<AbstractNettyConnection> constructor = clazz.getDeclaredConstructor(Channel.class, String.class,
                Address.class, AtomicBoolean.class,
                CompletableFuture.class, ConnectionsManager.class);
        constructor.setAccessible(true);
        AbstractNettyConnection peerConnection = constructor.newInstance(channel, userAgent, myid,
                isClosed, closedCompletable, connectionsManager);
        peerConnection.send(message);

        verify(channel).writeAndFlush(eq(message));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerClientConnection.class, NodeServerConnection.class })
    void closeShouldFreeMemory(Class<AbstractNettyConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        ConnectionsManager connectionsManager = new ConnectionsManager(eventConsumer);
        Constructor<AbstractNettyConnection> constructor = clazz.getDeclaredConstructor(Channel.class, String.class,
                Address.class, AtomicBoolean.class,
                CompletableFuture.class, ConnectionsManager.class);
        constructor.setAccessible(true);
        AbstractNettyConnection peerConnection = constructor.newInstance(channel, userAgent, myid,
                isClosed, closedCompletable, connectionsManager);

        connectionsManager.closeConnection(peerConnection, reason);

        verify(channel).writeAndFlush(any(QuitMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerClientConnection.class, NodeServerConnection.class })
    void sessionCreationShouldRegisterACloseListener(Class<AbstractNettyConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<AbstractNettyConnection> constructor = clazz.getDeclaredConstructor(Channel.class, Address.class, ConnectionsManager.class);
        constructor.setAccessible(true);
        AbstractNettyConnection peerConnection = constructor
                .newInstance(channel, myid, connectionsManager);

        verify(channelFuture).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerClientConnection.class, NodeServerConnection.class })
    void closeListenerShouldWorkOnClose(Class<AbstractNettyConnection> clazz) throws Exception {
        when(channelFuture.isSuccess()).thenReturn(true);

        Constructor<AbstractNettyConnection> constructor = clazz.getDeclaredConstructor(Channel.class, Address.class, ConnectionsManager.class);
        constructor.setAccessible(true);
        AbstractNettyConnection peerConnection = constructor
                .newInstance(channel, myid, connectionsManager);

        peerConnection.onChannelClose(channelFuture);

        verify(channelFuture).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
        verify(channelFuture).isSuccess();
        assertTrue(peerConnection.isClosed().get());
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerClientConnection.class, NodeServerConnection.class })
    void closeListenerOnFailure(Class<AbstractNettyConnection> clazz) throws Exception {
        Exception e = mock(Exception.class);
        when(channelFuture.isSuccess()).thenReturn(false);
        when(channelFuture.cause()).thenReturn(e);

        Constructor<AbstractNettyConnection> constructor = clazz.getDeclaredConstructor(Channel.class, Address.class, ConnectionsManager.class);
        constructor.setAccessible(true);
        AbstractNettyConnection peerConnection = constructor
                .newInstance(channel, myid, connectionsManager);

        peerConnection.onChannelClose(channelFuture);

        verify(channelFuture).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
        verify(channelFuture).isSuccess();
        assertFalse(peerConnection.isClosed().isDone());
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerClientConnection.class, NodeServerConnection.class })
    void equalsAndHashCodeTest(Class<AbstractNettyConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<AbstractNettyConnection> constructor = clazz.getDeclaredConstructor(Channel.class, String.class,
                Address.class, AtomicBoolean.class,
                CompletableFuture.class, ConnectionsManager.class);
        constructor.setAccessible(true);
        AbstractNettyConnection peerConnection = constructor.newInstance(channel, userAgent, myid,
                isClosed, closedCompletable, connectionsManager);
        AbstractNettyConnection peerConnection2 = clazz.getDeclaredConstructor(Channel.class, Address.class, ConnectionsManager.class)
                .newInstance(channel, myid, connectionsManager);

        assertNotEquals(peerConnection, peerConnection2);
        assertNotEquals(peerConnection.hashCode(), peerConnection2.hashCode());
        assertNotEquals(peerConnection, message);
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerClientConnection.class, NodeServerConnection.class })
    void getterTest(Class<AbstractNettyConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<AbstractNettyConnection> constructor = clazz.getDeclaredConstructor(Channel.class, String.class,
                Address.class, AtomicBoolean.class,
                CompletableFuture.class, ConnectionsManager.class);
        constructor.setAccessible(true);
        AbstractNettyConnection peerConnection = constructor.newInstance(channel, userAgent, myid,
                isClosed, closedCompletable, connectionsManager);

        assertEquals(myid, peerConnection.getAddress());
        assertEquals(userAgent, peerConnection.getUserAgent());
        assertNotEquals(peerConnection, mock(clazz));
        assertEquals(peerConnection, peerConnection);
        assertEquals(peerConnection.hashCode(), peerConnection.hashCode());
        assertNotNull(peerConnection.getConnectionId());
    }
}
