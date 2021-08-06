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
package org.drasyl.remote.handler;

import com.google.protobuf.ByteString;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.address.Address;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.FullReadMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.UniteMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {
    private Identity sender;
    private Identity ownIdentity;
    private Identity recipient;

    @BeforeEach
    void setUp() {
        sender = IdentityTestUtil.ID_1;
        ownIdentity = IdentityTestUtil.ID_2;
        recipient = IdentityTestUtil.ID_3;
    }

    @Test
    void shouldRejectAcknowledgementMessagesThatExceedTheRateLimit(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                                                   @Mock final Address msgSender,
                                                                   @Mock final Supplier<Long> timeProvider) throws Exception {
        when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
        when(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()).thenReturn(ownIdentity.getIdentityPublicKey());
        when(timeProvider.get()).thenReturn(1_000L).thenReturn(1_050L).thenReturn(2_050L).thenReturn(2_150L);

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, IdentityPublicKey>, Long> cache = new ConcurrentHashMap<>();
        final AcknowledgementMessage msg = AcknowledgementMessage.of(0, sender.getIdentityPublicKey(), sender.getProofOfWork(), ownIdentity.getIdentityPublicKey(), Nonce.randomNonce());
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertFalse(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
    }

    @Test
    void shouldRejectDiscoveryMessagesThatExceedTheRateLimit(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                                             @Mock final Address msgSender,
                                                             @Mock final Supplier<Long> timeProvider) throws Exception {
        when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
        when(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()).thenReturn(ownIdentity.getIdentityPublicKey());
        when(timeProvider.get()).thenReturn(1_000L).thenReturn(1_050L).thenReturn(2_050L).thenReturn(2_150L);

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, IdentityPublicKey>, Long> cache = new ConcurrentHashMap<>();
        final DiscoveryMessage msg = DiscoveryMessage.of(0, sender.getIdentityPublicKey(), sender.getProofOfWork(), ownIdentity.getIdentityPublicKey(), 0);
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertFalse(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
    }

    @Test
    void shouldRejectUniteMessagesThatExceedTheRateLimit(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                                         @Mock final Address msgSender,
                                                         @Mock final Supplier<Long> timeProvider) throws Exception {
        when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
        when(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()).thenReturn(ownIdentity.getIdentityPublicKey());
        when(timeProvider.get()).thenReturn(1_000L).thenReturn(1_050L).thenReturn(2_050L).thenReturn(2_150L);

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, IdentityPublicKey>, Long> cache = new ConcurrentHashMap<>();
        final UniteMessage msg = UniteMessage.of(0, sender.getIdentityPublicKey(), sender.getProofOfWork(), ownIdentity.getIdentityPublicKey(), IdentityTestUtil.ID_3.getIdentityPublicKey(), new InetSocketAddress(1337));
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertFalse(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
    }

    @Test
    void shouldNotRateLimitMessagesNotAddressedToUs(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                                    @Mock final Address msgSender,
                                                    @Mock final Supplier<Long> timeProvider) throws Exception {
        when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
        when(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()).thenReturn(ownIdentity.getIdentityPublicKey());

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, IdentityPublicKey>, Long> cache = new ConcurrentHashMap<>();
        final UniteMessage msg = UniteMessage.of(0, sender.getIdentityPublicKey(), sender.getProofOfWork(), recipient.getIdentityPublicKey(), recipient.getIdentityPublicKey(), new InetSocketAddress(1337));
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
    }

    @Test
    void shouldNotRateLimitApplicationMessages(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                               @Mock final Address msgSender,
                                               @Mock final Supplier<Long> timeProvider) throws Exception {
        when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
        when(ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()).thenReturn(ownIdentity.getIdentityPublicKey());

        final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, IdentityPublicKey>, Long> cache = new ConcurrentHashMap<>();
        final ApplicationMessage msg = ApplicationMessage.of(0, sender.getIdentityPublicKey(), sender.getProofOfWork(), ownIdentity.getIdentityPublicKey(), byte[].class.getName(), ByteString.EMPTY);
        final RateLimiter rateLimiter = new RateLimiter(timeProvider, cache);

        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
        assertTrue(rateLimiter.accept(ctx, msgSender, msg));
    }
}
