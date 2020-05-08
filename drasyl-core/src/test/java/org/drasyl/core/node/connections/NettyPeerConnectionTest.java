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
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Leave;
import org.drasyl.core.common.messages.NodeServerException;
import org.drasyl.core.common.messages.Response;
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
    private ConcurrentHashMap<String, Pair<Class<? extends IMessage>, SingleEmitter<IMessage>>> emitters;
    private Channel channel;
    private URI endpoint;
    private Identity myid;
    private String userAgent;
    private NodeServerException exceptionMessage;
    private IMessage message;
    private ChannelFuture channelFuture;
    private AtomicBoolean isClosed;
    private String msgID;
    private CompletableFuture<Boolean> closedCompletable;

    @BeforeEach
    void setUp() throws URISyntaxException {
        emitters = mock(ConcurrentHashMap.class);
        channel = mock(Channel.class);
        endpoint = new URI("ws://localhost:22527/");
        myid = IdentityTestHelper.random();
        userAgent = "";
        exceptionMessage = mock(NodeServerException.class);
        message = mock(IMessage.class);
        channelFuture = mock(ChannelFuture.class);
        ChannelId channelId = mock(ChannelId.class);
        isClosed = mock(AtomicBoolean.class);
        msgID = Crypto.randomString(16);
        closedCompletable = mock(CompletableFuture.class);

        when(channel.closeFuture()).thenReturn(channelFuture);
        when(channel.id()).thenReturn(channelId);
        when(channel.isOpen()).thenReturn(true);
        when(message.getMessageID()).thenReturn(msgID);
        when(channel.close()).thenReturn(channelFuture);
        when(channel.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.channel()).thenReturn(channel);
    }

    @AfterEach
    void tearDown() {
        validateMockitoUsage();
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void sendMessageShouldSendMessageToUnderlyingChannel(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);
        peerConnection.send(message);

        verify(channel).writeAndFlush(eq(message));
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void sendMessageWithResponseShouldSendMessageToUnderlyingChannelAndAddASingle(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        peerConnection.send(message, Leave.class).subscribe(onSuccess -> {
        }, onError -> {
        });

        when(emitters.containsKey(message.getMessageID())).thenReturn(false);

        verify(emitters).put(eq(message.getMessageID()), any(Pair.class));
        verify(channel).writeAndFlush(eq(message));
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void sendNothingIfSessionIsTerminated(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, new AtomicBoolean(true), emitters, closedCompletable);

        peerConnection.send(message, Leave.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        peerConnection.send(message);
        peerConnection.send(exceptionMessage);
        peerConnection.send(new Response<>(exceptionMessage, msgID));

        verify(channel, never()).writeAndFlush(any(IMessage.class));
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void setResponseShouldCompleteTheSingle(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        SingleEmitter<IMessage> singleEmitter = mock(SingleEmitter.class);

        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        when(emitters.get(message.getMessageID())).thenReturn(Pair.of(IMessage.class, singleEmitter));

        peerConnection.send(message, IMessage.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        peerConnection.setResponse(new Response<>(message, message.getMessageID()));

        verify(emitters).get(eq(message.getMessageID()));
        verify(singleEmitter).onSuccess(eq(message));
        verify(emitters).remove(eq(message.getMessageID()));
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void setResponseShouldDoNothingIfSingleDoesNotExists(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        when(emitters.get(message.getMessageID())).thenReturn(null);

        peerConnection.send(message, Leave.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        peerConnection.setResponse(new Response<>(message, message.getMessageID()));

        verify(emitters).get(eq(message.getMessageID()));
        verify(emitters, never()).remove(any());
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void closeShouldFreeMemory(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, String.class,
                Identity.class, URI.class, AtomicBoolean.class,
                ConcurrentHashMap.class, CompletableFuture.class).newInstance(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable);

        peerConnection.close();

        verify(channel).flush();
        verify(emitters).clear();
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        verify(channel).writeAndFlush(any(Leave.class));
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void sessionCreationShouldRegisterACloseListener(Class<NettyPeerConnection> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, URI.class, Identity.class)
                .newInstance(channel, endpoint, myid);

        verify(channelFuture).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void closeListenerShouldWorkOnClose(Class<NettyPeerConnection> clazz) throws Exception {
        when(channelFuture.isSuccess()).thenReturn(true);

        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, URI.class, Identity.class)
                .newInstance(channel, endpoint, myid);

        peerConnection.channelCloseFutureListener.operationComplete(channelFuture);

        verify(channelFuture, times(2)).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
        verify(channelFuture).isSuccess();
        verify(channel).flush();
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        verify(channel).writeAndFlush(any(Leave.class));
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
    void closeListenerOnFailure(Class<NettyPeerConnection> clazz) throws Exception {
        Exception e = mock(Exception.class);
        when(channelFuture.isSuccess()).thenReturn(false);
        when(channelFuture.cause()).thenReturn(e);

        NettyPeerConnection peerConnection = clazz.getDeclaredConstructor(Channel.class, URI.class, Identity.class)
                .newInstance(channel, endpoint, myid);

        peerConnection.channelCloseFutureListener.operationComplete(channelFuture);

        verify(channelFuture, times(2)).addListener(any(ChannelFutureListener.class));
        assertEquals(peerConnection.getCloseFuture(), channelFuture);
        verify(channelFuture).isSuccess();
        verify(channel).flush();
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        verify(channel).writeAndFlush(any(Leave.class));
        peerConnection.isClosed().whenComplete((suc, err) -> assertTrue(true));
    }

    @ParameterizedTest
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
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
    @ValueSource(classes = { NettyPeerConnection.class, ClientConnection.class })
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