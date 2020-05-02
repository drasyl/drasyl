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
package org.drasyl.core.server.session;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableEmitter;
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
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerSessionTest {
    private ConcurrentHashMap<String, Pair<Class<? extends IMessage>, SingleEmitter<IMessage>>> emitters;
    private Channel channel;
    private URI endpoint;
    private Identity myid;
    private String userAgent;
    private NodeServerException exceptionMessage;
    private IMessage message;
    private ChannelFuture channelFuture;
    private boolean isClosed;
    private String msgID;
    private Completable closedCompletable;
    private CompletableEmitter closedCompletableEmitter;

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
        isClosed = false;
        msgID = Crypto.randomString(16);
        closedCompletable = mock(Completable.class);
        closedCompletableEmitter = mock(CompletableEmitter.class);

        when(channel.closeFuture()).thenReturn(channelFuture);
        when(channel.id()).thenReturn(channelId);
        when(channel.isOpen()).thenReturn(true);
        when(message.getMessageID()).thenReturn(msgID);
        when(channel.close()).thenReturn(channelFuture);
    }

    @AfterEach
    void tearDown() {
        validateMockitoUsage();
    }

    @Test
    void sendMessageShouldSendMessageToUnderlyingChannel() {
        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable, closedCompletableEmitter);
        serverSession.send(message);

        verify(channel, times(1)).writeAndFlush(eq(message));
    }

    @Test
    void sendMessageWithResponseShouldSendMessageToUnderlyingChannelAndAddASingle() {
        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable, closedCompletableEmitter);

        serverSession.send(message, Leave.class).subscribe(onSuccess -> {
        }, onError -> {
        });

        when(emitters.containsKey(message.getMessageID())).thenReturn(false);

        verify(emitters, times(1)).put(eq(message.getMessageID()), any(Pair.class));
        verify(channel, times(1)).writeAndFlush(eq(message));
    }

    @Test
    void sendNothingIfSessionIsTerminated() {
        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, true, emitters, closedCompletable, closedCompletableEmitter);

        serverSession.send(message, Leave.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        serverSession.send(message);
        serverSession.send(exceptionMessage);
        serverSession.send(new Response<>(exceptionMessage, msgID));

        verify(channel, never()).writeAndFlush(any(IMessage.class));
    }

    @Test
    void setResponseShouldCompleteTheSingle() {
        SingleEmitter<IMessage> singleEmitter = mock(SingleEmitter.class);

        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable, closedCompletableEmitter);

        when(emitters.get(message.getMessageID())).thenReturn(Pair.of(IMessage.class, singleEmitter));

        serverSession.send(message, IMessage.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        serverSession.setResponse(new Response<>(message, message.getMessageID()));

        verify(emitters, times(1)).get(eq(message.getMessageID()));
        verify(singleEmitter, times(1)).onSuccess(eq(message));
        verify(emitters, times(1)).remove(eq(message.getMessageID()));
    }

    @Test
    void setResponseShouldDoNothingIfSingleDoesNotExists() {
        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable, closedCompletableEmitter);

        when(emitters.get(message.getMessageID())).thenReturn(null);

        serverSession.send(message, Leave.class).subscribe(onSuccess -> {
        }, onError -> {
        });
        serverSession.setResponse(new Response<>(message, message.getMessageID()));

        verify(emitters, times(1)).get(eq(message.getMessageID()));
        verify(emitters, never()).remove(any());
    }

    @Test
    void closeShouldFreeMemory() {
        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable, closedCompletableEmitter);

        serverSession.close();

        verify(channel, times(1)).flush();
        verify(channel, times(1)).close();
        verify(emitters, times(1)).clear();
        verify(channelFuture, times(1)).addListener(any());
        serverSession.isClosed().subscribe(() -> assertTrue(true));
    }

    @Test
    void sessionCreationShouldRegisterACloseListener() {
        ServerSession serverSession = new ServerSession(channel, endpoint, myid);

        verify(channelFuture, times(1)).addListener(any(ChannelFutureListener.class));
        assertEquals(serverSession.getCloseFuture(), channelFuture);
    }

    @Test
    void equalsAndHashCodeTest() {
        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable, closedCompletableEmitter);
        ServerSession serverSession2 = new ServerSession(channel, endpoint, myid);

        assertEquals(serverSession, serverSession2);
        assertEquals(serverSession.hashCode(), serverSession2.hashCode());
        assertNotEquals(serverSession, message);
    }

    @Test
    void getterTest() {
        ServerSession serverSession = new ServerSession(channel, userAgent, myid,
                endpoint, isClosed, emitters, closedCompletable, closedCompletableEmitter);

        assertEquals(myid, serverSession.getIdentity());
        assertEquals(endpoint, serverSession.getEndpoint());
        assertEquals(userAgent, serverSession.getUserAgent());
    }
}