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

import io.netty.channel.ChannelPromise;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.FullReadMessage;
import org.drasyl.remote.protocol.HopCount;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import static org.drasyl.remote.protocol.HopCount.MAX_HOP_COUNT;
import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HopCountGuardTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private IdentityPublicKey senderPublicKey;
    private IdentityPublicKey recipientPublicKey;
    @Mock
    private AgreementId agreementId;
    private Nonce correspondingId;

    @BeforeEach
    void setUp() {
        senderPublicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
        recipientPublicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
        correspondingId = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7");
    }

    @Test
    void shouldPassMessagesThatHaveNotReachedTheirHopCountLimitAndIncrementHopCount(@Mock final IdentityPublicKey recipient) {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 2);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        final FullReadMessage<AcknowledgementMessage> message = AcknowledgementMessage.of(1337, senderPublicKey, ProofOfWork.of(1), recipientPublicKey, correspondingId);

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            pipeline.pipeline().writeAndFlush(new AddressedMessage<>(message, recipient));

            assertEquals(new AddressedMessage<>(message.incrementHopCount(), recipient), pipeline.readOutbound());
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldDiscardMessagesThatHaveReachedTheirHopCountLimit() {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 1);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        final RemoteMessage message = AcknowledgementMessage.of(randomNonce(), 0, senderPublicKey, ProofOfWork.of(1), recipientPublicKey, HopCount.of(MAX_HOP_COUNT), agreementId, correspondingId);

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            final ChannelPromise promise = pipeline.newPromise();
            pipeline.pipeline().writeAndFlush(new AddressedMessage<>((Object) message, (Address) message.getSender()), promise);
            assertFalse(promise.isSuccess());

            assertNull(pipeline.readOutbound());
        }
        finally {
            pipeline.drasylClose();
        }
    }
}
