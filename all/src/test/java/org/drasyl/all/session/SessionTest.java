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

package org.drasyl.all.session;

import org.drasyl.all.messages.RelayException;
import org.drasyl.all.messages.Leave;
import org.drasyl.all.messages.Message;
import org.drasyl.all.messages.Response;
import org.drasyl.all.models.IPAddress;
import org.drasyl.all.models.Pair;
import org.drasyl.all.models.SessionUID;
import org.drasyl.all.util.random.RandomUtil;
import org.drasyl.all.util.listener.IResponseListener;
import io.netty.channel.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

class SessionTest {

    private ConcurrentHashMap<String, Pair<Class<? extends Message>, CompletableFuture<Message>>> futures;
    private List<IResponseListener<Message>> listeners;
    private Channel channel;
    private IPAddress targetSystem;
    private SessionUID uid;
    private SessionUID myRelayUID;
    private long defaultFutureTimeout;
    private String userAgent;
    private RelayException exceptionMessage;
    private Message message;
    private ChannelFuture channelFuture;
    private long bootTime;
    private AtomicLong totalFailedMessages;
    private AtomicLong totalSentMessages;
    private AtomicLong totalReceivedMessages;
    private AtomicLong futuresTimeouted;
    private boolean isTerminated;
    private boolean isClosed;
    private String msgID;
    private IResponseListener listener;

    @BeforeEach
    void setUp() {
        listeners = mock(List.class);
        futures = mock(ConcurrentHashMap.class);
        channel = mock(Channel.class);
        targetSystem = mock(IPAddress.class);
        uid = SessionUID.random();
        myRelayUID = SessionUID.random();
        defaultFutureTimeout = 1;
        bootTime = 1;
        userAgent = "";
        exceptionMessage = mock(RelayException.class);
        message = mock(Message.class);
        channelFuture = mock(ChannelFuture.class);
        ChannelId channelId = mock(ChannelId.class);
        totalFailedMessages = new AtomicLong();
        totalSentMessages = new AtomicLong();
        totalReceivedMessages = new AtomicLong();
        futuresTimeouted = new AtomicLong();
        isTerminated = false;
        isClosed = false;
        msgID = RandomUtil.randomString(16);
        listener = mock(IResponseListener.class);

        when(channel.closeFuture()).thenReturn(channelFuture);
        when(channel.id()).thenReturn(channelId);
        when(channel.isOpen()).thenReturn(true);
        when(message.getMessageID()).thenReturn(msgID);
    }

    @AfterEach
    void tearDown() {
        validateMockitoUsage();
    }

    @Test
    void sendExceptionShouldSendExceptionToUnderlyingChannel() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);
        session.sendException(exceptionMessage);

        verify(channel, times(1)).writeAndFlush(eq(exceptionMessage));
        assertEquals(1, session.getTotalSentMessages());
    }

    @Test
    void sendExceptionWithResponseShouldSendExceptionToUnderlyingChannelAsResponseMessage() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        session.sendExceptionAsResponse(exceptionMessage, msgID);

        verify(channel, times(1)).writeAndFlush(eq(new Response(exceptionMessage, msgID)));
        assertEquals(1, session.getTotalSentMessages());
    }

    @Test
    void sendMessageShouldSendMessageToUnderlyingChannel() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);
        session.sendMessage(message);

        verify(channel, times(1)).writeAndFlush(eq(message));
        assertEquals(1, session.getTotalSentMessages());
    }

    @Test
    void sendMessageWithResponseShouldSendMessageToUnderlyingChannelAndAddAFuture() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);
        session.sendMessageWithResponse(message, Leave.class);

        verify(futures, times(1)).putIfAbsent(eq(message.getMessageID()), any(Pair.class));
        verify(channel, times(1)).writeAndFlush(eq(message));
        assertEquals(1, session.getTotalSentMessages());
    }

    @Test
    void sendMessageWithResponseShouldSendMessageToUnderlyingChannelAndRemoveFutureOnTimeout() throws InterruptedException {
        Session session = new Session(futures, listeners, 0, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        when(futures.contains(message.getMessageID())).thenReturn(true);

        session.sendMessageWithResponse(message, Leave.class);

        verify(futures, times(1)).putIfAbsent(eq(message.getMessageID()), any(Pair.class));
        Thread.sleep(300); // NOSONAR
        verify(futures, times(1)).remove(eq(message.getMessageID()));
        verify(channel, times(1)).writeAndFlush(eq(message));
        assertEquals(1, session.getTotalSentMessages());
        assertEquals(1, session.timeoutedFutures());
    }

    @Test
    void sendNothingIfSessionIsTerminated() {
        Session session = new Session(futures, listeners, 0, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, true, true);

        session.sendMessageWithResponse(message, Leave.class);
        session.sendMessage(message);
        session.sendException(exceptionMessage);
        session.sendExceptionAsResponse(exceptionMessage, msgID);

        verify(channel, never()).writeAndFlush(any(Message.class));
        assertEquals(0, session.getTotalSentMessages());
        assertEquals(0, session.timeoutedFutures());
        assertEquals(2, session.getTotalFailedMessages());
    }

    @Test
    void setResultShouldCompleteTheFuture() {
        Session session = new Session(futures, listeners, 10000L, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        when(futures.get(message.getMessageID())).thenReturn(Pair.of(Leave.class,
                new CompletableFuture<Message>()));

        session.sendMessageWithResponse(message, Leave.class);
        session.setResult(message.getMessageID(), new Leave());

        verify(futures, times(1)).putIfAbsent(eq(message.getMessageID()), any(Pair.class));
        verify(channel, times(1)).writeAndFlush(eq(message));
        assertEquals(1, session.getTotalSentMessages());
        verify(futures, times(1)).remove(eq(message.getMessageID()));
    }

    @Test
    void setResultShouldDoNothingIfFutureDoesNotExists() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        session.sendMessageWithResponse(message, Leave.class);
        session.setResult(message.getMessageID(), new Leave());

        verify(futures, times(1)).putIfAbsent(eq(message.getMessageID()), any(Pair.class));
        verify(channel, times(1)).writeAndFlush(eq(message));
        assertEquals(1, session.getTotalSentMessages());
        verify(futures, never()).remove(eq(message.getMessageID()));
    }

    @Test
    void addListenerShouldAddAListener() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        session.addListener(listener);

        verify(listeners, times(1)).add(eq(listener));
    }

    @Test
    void removeListenerShouldRemoveAListener() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        session.removeListener(listener);

        verify(listeners, times(1)).remove(eq(listener));
    }

    @Test
    void receiveMessageShouldNotifyListeners() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        session.receiveMessage(message);

        verify(listeners, times(1)).forEach(any());
        assertEquals(1, session.getTotalReceivedMessages());
    }

    @Test
    void receiveMessageShouldDoNothingIfSessionIsTerminated() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, true, isClosed);

        session.receiveMessage(message);

        verify(listeners, never()).forEach(any());
        assertEquals(1, session.getTotalReceivedMessages());
    }

    @Test
    void closeShouldFreeMemory() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        session.close();

        verify(channel, times(1)).flush();
        verify(channel, times(1)).close();
        verify(futures, times(1)).clear();
        verify(listeners, times(1)).clear();
    }

    @Test
    void sessionCreationShouldRegisterACloseListener() {
        Session session = new Session(channel, targetSystem, uid, myRelayUID, defaultFutureTimeout);

        verify(channelFuture, times(1)).addListener(any(ChannelFutureListener.class));
        assertEquals(session.getCloseFuture(), channelFuture);
    }

    @Test
    void getPendingFuturesShouldReturnTheAmountOfPendingFutures() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        when(futures.size()).thenReturn(1);

        assertEquals(1, session.pendingFutures());
    }

    @Test
    void equalsAndHashCodeTest() {
        Session session1 = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);
        Session session2 = new Session(channel, targetSystem, uid, myRelayUID, defaultFutureTimeout);

        assertEquals(session1, session2);
        assertEquals(session1.hashCode(), session2.hashCode());
        assertNotEquals(session1, message);
    }

    @Test
    void getterTest() {
        Session session = new Session(futures, listeners, defaultFutureTimeout, myRelayUID, channel, userAgent, uid,
                bootTime, totalFailedMessages, totalSentMessages, totalReceivedMessages, futuresTimeouted,
                targetSystem, isTerminated, isClosed);

        assertEquals(uid, session.getUID());
        assertEquals(targetSystem, session.getTargetSystem());
        assertEquals(bootTime, session.getBootTime());
        assertEquals(isTerminated, session.isTerminated);
        assertEquals(userAgent, session.getUserAgent());
    }
}