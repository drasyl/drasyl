/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.DrasylChannel.State;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;

import static org.drasyl.channel.DrasylServerChannelConfig.NETWORK_ID;
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
class DrasylChannelTest {
    @Nested
    class DoRegister {
        @Test
        void shouldActivateChannel(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel parent,
                                   @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                   @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                   @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                   @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel) {
            final DrasylChannel channel = new DrasylChannel(parent, remoteAddress, proofOfWork, peersManager, udpChannel);

            channel.doRegister();

            assertTrue(channel.isActive());
        }
    }

    @Nested
    class DoDisconnect {
        @Test
        void shouldCloseChannelAndRemoveLocalAddress(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel parent,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final EventLoop eventLoop,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ChannelPromise channelPromise) {
            when(eventLoop.inEventLoop()).thenReturn(true);

            final DrasylChannel channel = new DrasylChannel(parent, remoteAddress, proofOfWork, peersManager, udpChannel);
            channel.unsafe().register(eventLoop, channelPromise);

            channel.doDisconnect();

            assertFalse(channel.isOpen());
            assertNull(channel.localAddress0());
        }
    }

    @Nested
    class DoClose {
        @Test
        void shouldCloseChannelAndRemoveLocalAddress(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel parent,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final EventLoop eventLoop,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ChannelPromise promise) {
            when(eventLoop.inEventLoop()).thenReturn(true);

            final DrasylChannel channel = new DrasylChannel(parent, remoteAddress, proofOfWork, peersManager, udpChannel);
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
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress localAddress,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel) {
            final DrasylChannel channel = new DrasylChannel(parent, State.OPEN, localAddress, remoteAddress, proofOfWork, peersManager, udpChannel);

            assertThrows(NotYetConnectedException.class, () -> channel.doWrite(null));
        }

        @Test
        void shouldThrowExceptionIfChannelIsAlreadyClosed(@Mock(answer = RETURNS_DEEP_STUBS) final Channel parent,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress localAddress,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel) {
            final DrasylChannel channel = new DrasylChannel(parent, State.CLOSED, localAddress, remoteAddress, proofOfWork, peersManager, udpChannel);

            assertThrows(ClosedChannelException.class, () -> channel.doWrite(null));
        }

        @Test
        void shouldWriteMessageToParentChannel(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel parent,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey localAddress,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel,
                                               @Mock final Object msg,
                                               @Mock final ChannelPromise promise) throws Exception {
            when(parent.localAddress0()).thenReturn(localAddress);
            when(parent.config().getOption(NETWORK_ID)).thenReturn(0);

            final DrasylChannel channel = new DrasylChannel(parent, remoteAddress, proofOfWork, peersManager, udpChannel);
            channel.doRegister();

            final ChannelOutboundBuffer in = channel.unsafe().outboundBuffer();
            in.addMessage(msg, 1, promise);
            in.addFlush();

            channel.doWrite(in);

            verify(udpChannel).write(any());
            verify(udpChannel).flush();
        }
    }

    @Nested
    class FilterOutboundMessage {
        @Test
        void shouldRejectNonByteBufMessage(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel parent,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel) {
            final DrasylChannel channel = new DrasylChannel(parent, remoteAddress, proofOfWork, peersManager, udpChannel);

            assertThrows(UnsupportedOperationException.class, () -> channel.filterOutboundMessage("Hello World"));
        }

        @Test
        void shouldAcceptByteBufMessage(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel parent,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey remoteAddress,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final ProofOfWork proofOfWork,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final PeersManager peersManager,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel udpChannel) throws Exception {
            final DrasylChannel channel = new DrasylChannel(parent, remoteAddress, proofOfWork, peersManager, udpChannel);

            final ByteBuf buffer = Unpooled.buffer();
            assertEquals(buffer, channel.filterOutboundMessage(buffer));

            buffer.release();
        }
    }
}
