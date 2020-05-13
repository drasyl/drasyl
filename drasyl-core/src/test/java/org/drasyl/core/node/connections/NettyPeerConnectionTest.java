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
package org.drasyl.core.node.connections;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import io.reactivex.rxjava3.core.SingleEmitter;
import org.drasyl.core.common.message.*;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityTestHelper;
import org.drasyl.crypto.Crypto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NettyPeerConnectionTest {
    private ConcurrentHashMap<String, Pair<Class<? extends Message<?>>, SingleEmitter<Message<?>>>> emitters;
    private Channel channel;
    private URI endpoint;
    private Identity myid;
    private String userAgent;
    private MessageExceptionMessage messageExceptionMessage;
    private RequestMessage<?> message;
    private ChannelFuture channelFuture;
    private AtomicBoolean isClosed;
    private String msgID;
    private CompletableFuture<Boolean> closedCompletable;
    private ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>> responseMessage;

    @BeforeEach
    void setUp() throws URISyntaxException {
        emitters = mock(ConcurrentHashMap.class);
        channel = mock(Channel.class);
        endpoint = new URI("ws://localhost:22527/");
        myid = IdentityTestHelper.random();
        userAgent = "";
        messageExceptionMessage = mock(MessageExceptionMessage.class);
        message = mock(ApplicationMessage.class);
        channelFuture = mock(ChannelFuture.class);
        ChannelId channelId = mock(ChannelId.class);
        isClosed = mock(AtomicBoolean.class);
        msgID = Crypto.randomString(16);
        closedCompletable = mock(CompletableFuture.class);
        responseMessage = mock(StatusMessage.class);

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
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void sendMessageShouldSendMessageToUnderlyingChannel(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);
        peerConnection.send(message);

        verify(channel).writeAndFlush(eq(message));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void sendMessageWithResponseShouldSendMessageToUnderlyingChannelAndAddASingle(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        peerConnection.send(message, StatusMessage.class).subscribe(onSuccess -> {
        }, onError -> {
        });

        verify(emitters).putIfAbsent(eq(message.getId()), any(Pair.class));
        verify(channel).writeAndFlush(eq(message));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void sendNothingIfSessionIsTerminated(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, new AtomicBoolean(true), emitters, closedCompletable);

        when(responseMessage.getCorrespondingId()).thenReturn(msgID);

        peerConnection.send(message, StatusMessage.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        peerConnection.send(message);
        peerConnection.send(messageExceptionMessage);
        peerConnection.send(responseMessage);

        verify(channel, never()).writeAndFlush(any(Message.class));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void setResponseShouldCompleteTheSingle(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        SingleEmitter<Message<?>> singleEmitter = mock(SingleEmitter.class);

        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        when(emitters.remove(message.getId())).thenReturn(Pair.of(StatusMessage.class, singleEmitter));
        when(responseMessage.getCorrespondingId()).thenReturn(msgID);

        peerConnection.send(message, StatusMessage.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        peerConnection.setResponse(responseMessage);

        verify(singleEmitter).onSuccess(eq(responseMessage));
        verify(emitters).remove(eq(message.getId()));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void setResponseShouldDoNothingIfSingleDoesNotExists(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        when(responseMessage.getCorrespondingId()).thenReturn(msgID);

        peerConnection.send(message, StatusMessage.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        peerConnection.setResponse(responseMessage);

        verify(emitters).remove(eq(message.getId()));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void closeShouldFreeMemory(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        peerConnection.close();

        verify(emitters).clear();
        verify(channel).writeAndFlush(any(LeaveMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void sessionCreationShouldRegisterACloseListener(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, URI.class, Identity.class)
                .newInstance(channel, endpoint, myid);

        verify(channelFuture).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void closeListenerShouldWorkOnClose(Class<NettyPeerConnection> clazz) throws Exception {
        when(channelFuture.isSuccess()).thenReturn(true);

        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, URI.class, Identity.class)
                .newInstance(channel, endpoint, myid);

        peerConnection.channelCloseFutureListener.operationComplete(channelFuture);

        verify(channelFuture).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
        verify(channelFuture).isSuccess();
        assertTrue(peerConnection.isClosed().get());
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void closeListenerOnFailure(Class<NettyPeerConnection> clazz) throws Exception {
        Exception e = mock(Exception.class);
        when(channelFuture.isSuccess()).thenReturn(false);
        when(channelFuture.cause()).thenReturn(e);

        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, URI.class, Identity.class)
                .newInstance(channel, endpoint, myid);

        peerConnection.channelCloseFutureListener.operationComplete(channelFuture);

        verify(channelFuture).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
        verify(channelFuture).isSuccess();
        assertFalse(peerConnection.isClosed().isDone());
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void equalsAndHashCodeTest(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);
        NettyPeerConnection peerConnection2 = clazz.getDeclaredConstructor(Channel.class, URI.class, Identity.class)
                .newInstance(channel, endpoint, myid);

        assertEquals(peerConnection, peerConnection2);
        assertEquals(peerConnection.hashCode(), peerConnection2.hashCode());
        assertNotEquals(peerConnection, message);
    }

    @ParameterizedTest
    @ValueSource(classes = { SuperPeerConnection.class, ClientConnection.class })
    void getterTest(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        assertEquals(myid, peerConnection.getIdentity());
        assertEquals(endpoint, peerConnection.getEndpoint());
        assertEquals(userAgent, peerConnection.getUserAgent());
    }
}
