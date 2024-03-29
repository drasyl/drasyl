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
package org.drasyl.handler.remote;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.FullReadMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static test.util.IdentityTestUtil.ID_3;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {
    private Identity sender;
    private Identity ownIdentity;
    private Identity recipient;

    @BeforeEach
    void setUp() {
        sender = IdentityTestUtil.ID_1;
        ownIdentity = IdentityTestUtil.ID_2;
        recipient = ID_3;
    }

    @Test
    void shouldRejectAcknowledgementMessagesThatExceedTheRateLimit(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                   @Mock final InetSocketAddress msgSender,
                                                                   @Mock final Supplier<Long> timeProvider) {
        when(ctx.channel().localAddress()).thenReturn(ownIdentity.getAddress());
        when(timeProvider.get()).thenReturn(1_000L).thenReturn(1_050L).thenReturn(2_050L).thenReturn(2_150L);

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache = new ConcurrentHashMap<>();
        final AcknowledgementMessage msg = AcknowledgementMessage.of(0, ownIdentity.getIdentityPublicKey(), sender.getIdentityPublicKey(), sender.getProofOfWork(), System.currentTimeMillis());
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(2)).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(3)).fireChannelRead(any());
    }

    @Test
    void shouldRejectDiscoveryMessagesThatExceedTheRateLimit(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                             @Mock final InetSocketAddress msgSender,
                                                             @Mock final Supplier<Long> timeProvider) {
        when(ctx.channel().localAddress()).thenReturn(ownIdentity.getAddress());
        when(timeProvider.get()).thenReturn(1_000L).thenReturn(1_050L).thenReturn(2_050L).thenReturn(2_150L);

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache = new ConcurrentHashMap<>();
        final HelloMessage msg = HelloMessage.of(0, ownIdentity.getIdentityPublicKey(), sender.getIdentityPublicKey(), sender.getProofOfWork());
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(2)).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(3)).fireChannelRead(any());
    }

    @Test
    void shouldRejectUniteMessagesThatExceedTheRateLimit(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                         @Mock final InetSocketAddress msgSender,
                                                         @Mock final Supplier<Long> timeProvider) {
        when(ctx.channel().localAddress()).thenReturn(ownIdentity.getAddress());
        when(timeProvider.get()).thenReturn(1_000L).thenReturn(1_050L).thenReturn(2_050L).thenReturn(2_150L);

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache = new ConcurrentHashMap<>();
        final UniteMessage msg = UniteMessage.of(0, ownIdentity.getIdentityPublicKey(), sender.getIdentityPublicKey(), sender.getProofOfWork(), ID_3.getIdentityPublicKey(), Set.of(new InetSocketAddress(1337)));
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(2)).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(3)).fireChannelRead(any());
    }

    @Test
    void shouldNotRateLimitMessagesNotAddressedToUs(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                    @Mock final InetSocketAddress msgSender,
                                                    @Mock final Supplier<Long> timeProvider) {
        when(ctx.channel().localAddress()).thenReturn(ownIdentity.getAddress());
        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache = new ConcurrentHashMap<>();
        final UniteMessage msg = UniteMessage.of(0, recipient.getIdentityPublicKey(), sender.getIdentityPublicKey(), sender.getProofOfWork(), recipient.getIdentityPublicKey(), Set.of(new InetSocketAddress(1337)));
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(2)).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(3)).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(4)).fireChannelRead(any());
    }

    @Test
    void shouldNotRateLimitApplicationMessages(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                               @Mock final InetSocketAddress msgSender,
                                               @Mock final Supplier<Long> timeProvider) {
        when(ctx.channel().localAddress()).thenReturn(ownIdentity.getAddress());
        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache = new ConcurrentHashMap<>();
        final ApplicationMessage msg = ApplicationMessage.of(0, ownIdentity.getIdentityPublicKey(), sender.getIdentityPublicKey(), sender.getProofOfWork(), Unpooled.buffer());
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(2)).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(3)).fireChannelRead(any());

        rateLimiter.channelRead0(ctx, new InetAddressedMessage<>(msg, null, msgSender));
        verify(ctx, times(4)).fireChannelRead(any());
    }
}
