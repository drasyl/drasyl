/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import org.drasyl.channel.JavaDrasylChannel.State;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JavaDrasylChannelTest {
    @Nested
    class DoRegister {
        @Test
        void shouldActivateChannel(@Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannel parent,
                                   @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                   @Mock final EventLoop eventLoop,
                                   @Mock final ChannelPromise promise) {
            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, remoteAddress);

            channel.unsafe().register(eventLoop, promise);
            channel.doRegister();

            assertTrue(channel.isActive());
        }
    }

    @Nested
    class DoDisconnect {
        @Test
        void shouldCloseChannelAndRemoveLocalAddress(@Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannel parent,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final EventLoop eventLoop,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ChannelPromise channelPromise) {
            when(eventLoop.inEventLoop()).thenReturn(true);

            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, remoteAddress);
            channel.unsafe().register(eventLoop, channelPromise);

            channel.doDisconnect();

            assertFalse(channel.isOpen());
            assertNull(channel.localAddress0());
        }
    }

    @Nested
    class DoClose {
        @Test
        void shouldCloseChannelAndRemoveLocalAddress(@Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannel parent,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final EventLoop eventLoop,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ChannelPromise promise) {
            when(eventLoop.inEventLoop()).thenReturn(true);

            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, remoteAddress);
            channel.unsafe().register(eventLoop, promise);

            channel.doClose();

            assertFalse(channel.isOpen());
            assertNull(channel.localAddress0());
        }
    }

    @Nested
    class DoWrite {
        @Test
        void shouldThrowExceptionIfChannelIsNotBound(@Mock(answer = RETURNS_DEEP_STUBS) final Channel parent,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Identity identity,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress) {
            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, State.OPEN, identity, remoteAddress);

            assertThrows(NotYetConnectedException.class, () -> channel.doWrite(null));
        }

        @Test
        void shouldThrowExceptionIfChannelIsAlreadyClosed(@Mock(answer = RETURNS_DEEP_STUBS) final Channel parent,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final Identity identity,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress) {
            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, State.CLOSED, identity, remoteAddress);

            assertThrows(ClosedChannelException.class, () -> channel.doWrite(null));
        }

        @Test
        void shouldWriteMessageToParentChannel(@Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannel parent,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                               @Mock final Object msg,
                                               @Mock final ChannelPromise promise,
                                               @Mock final EventLoop eventLoop) throws Exception {
            when(parent.config().getNetworkId()).thenReturn(0);
            when(parent.udpChannel().isWritable()).thenReturn(true);

            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, remoteAddress);
            channel.unsafe().register(eventLoop, promise);
            channel.doRegister();

            final ChannelOutboundBuffer in = channel.unsafe().outboundBuffer();
            in.addMessage(msg, 1, promise);
            in.addFlush();

            channel.doWrite(in);

            verify(parent.udpChannel()).write(any());
            verify(parent.udpChannel()).flush();
        }
    }

    @Nested
    class FilterOutboundMessage {
        @Test
        void shouldRejectNonByteBufMessage(@Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannel parent,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress) {
            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, remoteAddress);

            assertThrows(UnsupportedOperationException.class, () -> channel.filterOutboundMessage("Hello World"));
        }

        @Test
        void shouldAcceptByteBufMessage(@Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannel parent,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress) throws Exception {
            final JavaDrasylChannel channel = new JavaDrasylChannel(parent, remoteAddress);

            final ByteBuf buffer = Unpooled.buffer();
            assertEquals(buffer, channel.filterOutboundMessage(buffer));

            buffer.release();
        }
    }
}
