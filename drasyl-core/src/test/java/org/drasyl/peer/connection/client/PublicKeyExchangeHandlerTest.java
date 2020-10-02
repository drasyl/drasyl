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
package org.drasyl.peer.connection.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.Attribute;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.IamMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.WhoAreYouMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.drasyl.peer.connection.PeerChannelGroup.ATTRIBUTE_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_WRONG_PUBLIC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicKeyExchangeHandlerTest {
    @Mock
    private EventExecutor eventExecutor;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private Attribute<CompressedPublicKey> mockedAttribute;
    @Mock
    private Channel mockedChannel;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private CompressedPublicKey mockedPublicKey;
    private Duration timeout;
    @Mock
    private ScheduledFuture<?> timeoutFuture;
    private MessageId requestID;

    @BeforeEach
    void setUp() {
        timeout = Duration.ofSeconds(10);
        requestID = new MessageId("89ba3cd9efb7570eb3126d11");
    }

    @Test
    void shouldSendRequestOnHandlerAdded() {
        final PublicKeyExchangeHandler handler = new PublicKeyExchangeHandler(mockedPublicKey, timeout, requestID, timeoutFuture);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertEquals(new WhoAreYouMessage(), channel.readOutbound());
    }

    @Test
    void shouldEmitEventKeyIsInConfig() {
        when(ctx.channel()).thenReturn(mockedChannel);
        when(mockedChannel.attr(ATTRIBUTE_PUBLIC_KEY)).thenReturn(mockedAttribute);
        when(ctx.pipeline()).thenReturn(pipeline);

        final PublicKeyExchangeHandler handler = new PublicKeyExchangeHandler(mockedPublicKey, timeout, requestID, timeoutFuture);

        final IamMessage msg = new IamMessage(mockedPublicKey, requestID);
        handler.channelRead0(ctx, msg);

        verify(pipeline).fireUserEventTriggered(eq(PublicKeyExchangeHandler.PublicKeyExchangeState.KEY_AVAILABLE));
        verify(pipeline).remove(handler);
    }

    @Test
    void shouldEmitEventKeyIsNotInConfig() {
        when(ctx.channel()).thenReturn(mockedChannel);
        when(mockedChannel.attr(ATTRIBUTE_PUBLIC_KEY)).thenReturn(mockedAttribute);
        when(ctx.pipeline()).thenReturn(pipeline);

        final PublicKeyExchangeHandler handler = new PublicKeyExchangeHandler(null, timeout, requestID, timeoutFuture);

        final IamMessage msg = new IamMessage(mockedPublicKey, requestID);
        handler.channelRead0(ctx, msg);

        verify(pipeline).fireUserEventTriggered(eq(PublicKeyExchangeHandler.PublicKeyExchangeState.KEY_AVAILABLE));
        verify(pipeline).remove(handler);
    }

    @Test
    void shouldCloseConnectionOnWrongKey() {
        when(ctx.writeAndFlush(any())).thenReturn(mock(ChannelFuture.class));

        final PublicKeyExchangeHandler handler = new PublicKeyExchangeHandler(mock(CompressedPublicKey.class), timeout, requestID, timeoutFuture);

        final IamMessage msg = new IamMessage(mockedPublicKey, requestID);
        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(eq(new ConnectionExceptionMessage(CONNECTION_ERROR_WRONG_PUBLIC_KEY)));
        verify(pipeline, never()).fireUserEventTriggered(eq(PublicKeyExchangeHandler.PublicKeyExchangeState.KEY_AVAILABLE));
        verify(pipeline, never()).remove(handler);
    }

    @Test
    void shouldNotMatchingMessagePassOn() {
        final PublicKeyExchangeHandler handler = new PublicKeyExchangeHandler(mock(CompressedPublicKey.class), timeout, requestID, timeoutFuture);

        final IamMessage msg = new IamMessage(mockedPublicKey, new MessageId("6d1a9c2d27fa8281a4933a60"));
        handler.channelRead0(ctx, msg);

        verify(ctx).fireChannelRead(msg);
        verify(ctx, never()).writeAndFlush(eq(new ConnectionExceptionMessage(CONNECTION_ERROR_WRONG_PUBLIC_KEY)));
        verify(pipeline, never()).fireUserEventTriggered(eq(PublicKeyExchangeHandler.PublicKeyExchangeState.KEY_AVAILABLE));
        verify(pipeline, never()).remove(handler);
    }

    @Test
    void shouldSendMessageOnTimeout() throws Exception {
        final ArgumentCaptor<Callable> captor = ArgumentCaptor.forClass(Callable.class);
        when(ctx.executor()).thenReturn(eventExecutor);

        final PublicKeyExchangeHandler handler = new PublicKeyExchangeHandler(mockedPublicKey, timeout);
        handler.handlerAdded(ctx);

        verify(eventExecutor).schedule(captor.capture(), eq(timeout.toMillis()), eq(TimeUnit.MILLISECONDS));
        captor.getValue().call();
        verify(ctx).writeAndFlush(eq(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE_TIMEOUT)));
    }
}